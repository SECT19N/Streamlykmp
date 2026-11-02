/*
 * MPEG-1 Layer II ("MP2") audio decoder.
 *
 * Kotlin port of the audio decoder in PL_MPEG (https://github.com/phoboslab/pl_mpeg), which is itself
 * based on kjmp2 by Martin J. Fiedler (http://keyj.emphy.de/kjmp2/).
 *
 * PL_MPEG is MIT licensed:
 *
 *   Copyright (c) 2010-2024 Dominic Szablewski
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 *   associated documentation files (the "Software"), to deal in the Software without restriction,
 *   including without limitation the rights to use, copy, modify, merge, publish, distribute,
 *   sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all copies or
 *   substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 *   NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 *   DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
 *   OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package dev.section.streamly.player.audio

import kotlin.math.roundToInt

/**
 * Decodes MPEG-1 Layer II frames, one complete frame per call, to interleaved 16-bit stereo PCM.
 *
 * Android has no guaranteed MP2 decoder, and most of our live channels carry MP2 audio.
 * Mono streams come out as stereo (the one channel duplicated). The decoder keeps filter-bank
 * state between frames, so use one instance per stream and call [reset] after a seek.
 *
 * Malformed input never throws: [decodeFrame] returns 0 and the caller skips the frame.
 */
internal class Mp2Decoder {

    /** Sample rate of the last decoded frame, or 0 before the first. */
    var sampleRate: Int = 0
        private set

    private val allocation = Array(2) { arrayOfNulls<Quantizer>(32) }
    private val scaleFactorInfo = Array(2) { IntArray(32) }
    private val scaleFactor = Array(2) { Array(32) { IntArray(3) } }
    private val sample = Array(2) { FloatArray(32 * 3) } // [subband * 3 + part]
    private val v = Array(2) { FloatArray(1024) }
    private val u = FloatArray(32)
    private var vPos = 0

    // Bit reader state for the frame being decoded.
    private var data = ByteArray(0)
    private var bitIndex = 0
    private var bitLimit = 0

    fun reset() {
        v.forEach { it.fill(0f) }
        vPos = 0
    }

    /**
     * Size in bytes of the frame whose header is at [offset], or 0 if there is no supported
     * (MPEG-1 Layer II) header there. Doesn't check that the whole frame is present.
     */
    fun frameLength(input: ByteArray, offset: Int, length: Int): Int {
        if (length < 4 || offset < 0 || offset + length > input.size) return 0
        val b0 = input[offset].toInt() and 0xFF
        val b1 = input[offset + 1].toInt() and 0xFF
        val b2 = input[offset + 2].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xFE) != 0xFC) return 0
        val bitrateIndex = (b2 shr 4) - 1
        val sampleRateIndex = (b2 shr 2) and 3
        if (bitrateIndex !in 0..13 || sampleRateIndex == 3) return 0
        return 144000 * BIT_RATE[bitrateIndex] / SAMPLE_RATE[sampleRateIndex] + ((b2 shr 1) and 1)
    }

    /**
     * Decodes the frame at [offset] in [input] into [output] (needs [SAMPLES_PER_FRAME] * 2 shorts).
     *
     * @return the number of samples per channel written ([SAMPLES_PER_FRAME]), or 0 if the data
     *   isn't a complete, supported frame.
     */
    fun decodeFrame(input: ByteArray, offset: Int, length: Int, output: ShortArray): Int {
        if (length < 4 || offset < 0 || offset + length > input.size) return 0
        if (output.size < SAMPLES_PER_FRAME * 2) return 0

        val b0 = input[offset].toInt() and 0xFF
        val b1 = input[offset + 1].toInt() and 0xFF
        val b2 = input[offset + 2].toInt() and 0xFF
        val b3 = input[offset + 3].toInt() and 0xFF
        // 11-bit sync, MPEG-1 (0b11), Layer II (0b10); the low bit is "no CRC".
        if (b0 != 0xFF || (b1 and 0xFE) != 0xFC) return 0
        val hasCrc = (b1 and 1) == 0

        val bitrateIndex = (b2 shr 4) - 1
        if (bitrateIndex !in 0..13) return 0
        val sampleRateIndex = (b2 shr 2) and 3
        if (sampleRateIndex == 3) return 0
        val padding = (b2 shr 1) and 1
        val mode = b3 shr 6
        val modeExtension = (b3 shr 4) and 3

        val bitrate = BIT_RATE[bitrateIndex]
        val rate = SAMPLE_RATE[sampleRateIndex]
        val frameSize = 144000 * bitrate / rate + padding
        if (length < frameSize) return 0

        val headerSize = if (hasCrc) 6 else 4
        data = input
        bitIndex = (offset + headerSize) * 8
        bitLimit = (offset + frameSize) * 8
        sampleRate = rate

        val mono = mode == MODE_MONO
        var bound = when {
            mode == MODE_JOINT_STEREO -> (modeExtension + 1) shl 2
            mono -> 0
            else -> 32
        }

        // Quantizer table selection.
        val tab1 = if (mono) 0 else 1
        val tab2 = QUANT_LUT_STEP_1[tab1][bitrateIndex]
        var tab3 = QUANT_LUT_STEP_2[tab2][sampleRateIndex]
        val sbLimit = tab3 and 63
        tab3 = tab3 shr 6
        if (bound > sbLimit) bound = sbLimit

        for (sb in 0 until bound) {
            allocation[0][sb] = readAllocation(sb, tab3)
            allocation[1][sb] = readAllocation(sb, tab3)
        }
        for (sb in bound until sbLimit) {
            val a = readAllocation(sb, tab3)
            allocation[0][sb] = a
            allocation[1][sb] = a
        }

        val channels = if (mono) 1 else 2
        for (sb in 0 until sbLimit) {
            for (ch in 0 until channels) {
                if (allocation[ch][sb] != null) scaleFactorInfo[ch][sb] = read(2)
            }
            if (mono) scaleFactorInfo[1][sb] = scaleFactorInfo[0][sb]
        }

        for (sb in 0 until sbLimit) {
            for (ch in 0 until channels) {
                if (allocation[ch][sb] == null) continue
                val sf = scaleFactor[ch][sb]
                when (scaleFactorInfo[ch][sb]) {
                    0 -> {
                        sf[0] = read(6); sf[1] = read(6); sf[2] = read(6)
                    }

                    1 -> {
                        sf[0] = read(6); sf[1] = sf[0]; sf[2] = read(6)
                    }

                    2 -> {
                        sf[0] = read(6); sf[1] = sf[0]; sf[2] = sf[0]
                    }

                    else -> {
                        sf[0] = read(6); sf[1] = read(6); sf[2] = sf[1]
                    }
                }
            }
            if (mono) {
                for (p in 0 until 3) scaleFactor[1][sb][p] = scaleFactor[0][sb][p]
            }
        }

        // Coefficient input and reconstruction.
        var outPos = 0
        for (part in 0 until 3) {
            for (granule in 0 until 4) {
                for (sb in 0 until bound) {
                    readSamples(0, sb, part)
                    readSamples(1, sb, part)
                }
                for (sb in bound until sbLimit) {
                    readSamples(0, sb, part)
                    for (i in 0 until 3) sample[1][sb * 3 + i] = sample[0][sb * 3 + i]
                }
                for (sb in sbLimit until 32) {
                    for (i in 0 until 3) {
                        sample[0][sb * 3 + i] = 0f
                        sample[1][sb * 3 + i] = 0f
                    }
                }

                // Synthesis filter bank.
                for (p in 0 until 3) {
                    vPos = (vPos - 64) and 1023
                    for (ch in 0 until 2) {
                        idct36(sample[ch], p, v[ch], vPos)
                        u.fill(0f)

                        var dIndex = 512 - (vPos shr 1)
                        var vIndex = (vPos % 128) shr 1
                        while (vIndex < 1024) {
                            for (i in 0 until 32) u[i] += WINDOW[dIndex++] * v[ch][vIndex++]
                            vIndex += 128 - 32
                            dIndex += 64 - 32
                        }
                        dIndex -= 512 - 32
                        vIndex = (128 - 32 + 1024) - vIndex
                        while (vIndex < 1024) {
                            for (i in 0 until 32) u[i] += WINDOW[dIndex++] * v[ch][vIndex++]
                            vIndex += 128 - 32
                            dIndex += 64 - 32
                        }

                        for (j in 0 until 32) {
                            val s = (u[j] * OUTPUT_SCALE).roundToInt()
                            output[((outPos + j) shl 1) + ch] = s.coerceIn(-32768, 32767).toShort()
                        }
                    }
                    outPos += 32
                }
            }
        }
        data = EMPTY
        return SAMPLES_PER_FRAME
    }

    /** Reads [count] (0..16) bits MSB first; reads past the frame end yield zeros. */
    private fun read(count: Int): Int {
        var result = 0
        var remaining = count
        while (remaining > 0) {
            if (bitIndex >= bitLimit) {
                result = result shl remaining; break
            }
            val byte = data[bitIndex shr 3].toInt() and 0xFF
            val bitsLeftInByte = 8 - (bitIndex and 7)
            val take = minOf(remaining, bitsLeftInByte)
            val chunk = (byte shr (bitsLeftInByte - take)) and ((1 shl take) - 1)
            result = (result shl take) or chunk
            bitIndex += take
            remaining -= take
        }
        return result
    }

    private fun readAllocation(sb: Int, tab3: Int): Quantizer? {
        val tab4 = QUANT_LUT_STEP_3[tab3][sb]
        val qtab = QUANT_LUT_STEP_4[tab4 and 15][read(tab4 shr 4)]
        return if (qtab != 0) QUANT_TAB[qtab - 1] else null
    }

    private fun readSamples(ch: Int, sb: Int, part: Int) {
        val q = allocation[ch][sb]
        val out = sample[ch]
        val base = sb * 3
        if (q == null) {
            out[base] = 0f; out[base + 1] = 0f; out[base + 2] = 0f
            return
        }

        val sf = scaleFactor[ch][sb][part]
        val levels = q.levels
        val raw0: Int
        val raw1: Int
        val raw2: Int
        if (q.group) {
            var value = read(q.bits)
            raw0 = value % levels
            value /= levels
            raw1 = value % levels
            raw2 = value / levels
        } else {
            raw0 = read(q.bits)
            raw1 = read(q.bits)
            raw2 = read(q.bits)
        }

        // Requantisation per ISO 11172-3: a code v of an L-level quantizer is (2v - (L - 1)) / L,
        // scaled by the scale factor 2^(1 - sf/3). (PL_MPEG uses fixed point and divides by L + 1,
        // which under-scales the coarse quantizers; we do it in floats, which also avoids the
        // truncation of its shifts.) The 1/256 puts the result in the filter bank's input range.
        val sfValue = if (sf == 63) 0f else SCALEFACTOR_BASE[sf % 3].toFloat() / (1 shl (sf / 3))
        val unit = sfValue / (levels * 256f)
        val center = (levels - 1) shr 1
        out[base] = (center - raw0) * unit
        out[base + 1] = (center - raw1) * unit
        out[base + 2] = (center - raw2) * unit
    }

    private class Quantizer(val levels: Int, val group: Boolean, val bits: Int)

    internal fun idct36(s: FloatArray, ss: Int, d: FloatArray, dp: Int) {
        var t01 = 0f;
        var t02 = 0f;
        var t03 = 0f;
        var t04 = 0f;
        var t05 = 0f;
        var t06 = 0f;
        var t07 = 0f;
        var t08 = 0f;
        var t09 = 0f;
        var t10 = 0f;
        var t11 = 0f;
        var t12 = 0f;
        var t13 = 0f;
        var t14 = 0f;
        var t15 = 0f;
        var t16 = 0f;
        var t17 = 0f;
        var t18 = 0f;
        var t19 = 0f;
        var t20 = 0f;
        var t21 = 0f;
        var t22 = 0f;
        var t23 = 0f;
        var t24 = 0f;
        var t25 = 0f;
        var t26 = 0f;
        var t27 = 0f;
        var t28 = 0f;
        var t29 = 0f;
        var t30 = 0f;
        var t31 = 0f;
        var t32 = 0f;
        var t33 = 0f
        t01 = (s[0 * 3 + ss] + s[31 * 3 + ss])
        t02 = (s[0 * 3 + ss] - s[31 * 3 + ss]) * 0.500602998235f
        t03 = (s[1 * 3 + ss] + s[30 * 3 + ss])
        t04 = (s[1 * 3 + ss] - s[30 * 3 + ss]) * 0.505470959898f
        t05 = (s[2 * 3 + ss] + s[29 * 3 + ss])
        t06 = (s[2 * 3 + ss] - s[29 * 3 + ss]) * 0.515447309923f
        t07 = (s[3 * 3 + ss] + s[28 * 3 + ss])
        t08 = (s[3 * 3 + ss] - s[28 * 3 + ss]) * 0.53104259109f
        t09 = (s[4 * 3 + ss] + s[27 * 3 + ss])
        t10 = (s[4 * 3 + ss] - s[27 * 3 + ss]) * 0.553103896034f
        t11 = (s[5 * 3 + ss] + s[26 * 3 + ss])
        t12 = (s[5 * 3 + ss] - s[26 * 3 + ss]) * 0.582934968206f
        t13 = (s[6 * 3 + ss] + s[25 * 3 + ss])
        t14 = (s[6 * 3 + ss] - s[25 * 3 + ss]) * 0.622504123036f
        t15 = (s[7 * 3 + ss] + s[24 * 3 + ss])
        t16 = (s[7 * 3 + ss] - s[24 * 3 + ss]) * 0.674808341455f
        t17 = (s[8 * 3 + ss] + s[23 * 3 + ss])
        t18 = (s[8 * 3 + ss] - s[23 * 3 + ss]) * 0.744536271002f
        t19 = (s[9 * 3 + ss] + s[22 * 3 + ss])
        t20 = (s[9 * 3 + ss] - s[22 * 3 + ss]) * 0.839349645416f
        t21 = (s[10 * 3 + ss] + s[21 * 3 + ss])
        t22 = (s[10 * 3 + ss] - s[21 * 3 + ss]) * 0.972568237862f
        t23 = (s[11 * 3 + ss] + s[20 * 3 + ss])
        t24 = (s[11 * 3 + ss] - s[20 * 3 + ss]) * 1.16943993343f
        t25 = (s[12 * 3 + ss] + s[19 * 3 + ss])
        t26 = (s[12 * 3 + ss] - s[19 * 3 + ss]) * 1.48416461631f
        t27 = (s[13 * 3 + ss] + s[18 * 3 + ss])
        t28 = (s[13 * 3 + ss] - s[18 * 3 + ss]) * 2.05778100995f
        t29 = (s[14 * 3 + ss] + s[17 * 3 + ss])
        t30 = (s[14 * 3 + ss] - s[17 * 3 + ss]) * 3.40760841847f
        t31 = (s[15 * 3 + ss] + s[16 * 3 + ss])
        t32 = (s[15 * 3 + ss] - s[16 * 3 + ss]) * 10.1900081235f
        t33 = t01 + t31
        t31 = (t01 - t31) * 0.502419286188f
        t01 = t03 + t29
        t29 = (t03 - t29) * 0.52249861494f
        t03 = t05 + t27
        t27 = (t05 - t27) * 0.566944034816f
        t05 = t07 + t25
        t25 = (t07 - t25) * 0.64682178336f
        t07 = t09 + t23
        t23 = (t09 - t23) * 0.788154623451f
        t09 = t11 + t21
        t21 = (t11 - t21) * 1.06067768599f
        t11 = t13 + t19
        t19 = (t13 - t19) * 1.72244709824f
        t13 = t15 + t17
        t17 = (t15 - t17) * 5.10114861869f
        t15 = t33 + t13
        t13 = (t33 - t13) * 0.509795579104f
        t33 = t01 + t11
        t01 = (t01 - t11) * 0.601344886935f
        t11 = t03 + t09
        t09 = (t03 - t09) * 0.899976223136f
        t03 = t05 + t07
        t07 = (t05 - t07) * 2.56291544774f
        t05 = t15 + t03
        t15 = (t15 - t03) * 0.541196100146f
        t03 = t33 + t11
        t11 = (t33 - t11) * 1.30656296488f
        t33 = t05 + t03
        t05 = (t05 - t03) * 0.707106781187f
        t03 = t15 + t11
        t15 = (t15 - t11) * 0.707106781187f
        t03 += t15
        t11 = t13 + t07
        t13 = (t13 - t07) * 0.541196100146f
        t07 = t01 + t09
        t09 = (t01 - t09) * 1.30656296488f
        t01 = t11 + t07
        t07 = (t11 - t07) * 0.707106781187f
        t11 = t13 + t09
        t13 = (t13 - t09) * 0.707106781187f
        t11 += t13
        t01 += t11
        t11 += t07
        t07 += t13
        t09 = t31 + t17
        t31 = (t31 - t17) * 0.509795579104f
        t17 = t29 + t19
        t29 = (t29 - t19) * 0.601344886935f
        t19 = t27 + t21
        t21 = (t27 - t21) * 0.899976223136f
        t27 = t25 + t23
        t23 = (t25 - t23) * 2.56291544774f
        t25 = t09 + t27
        t09 = (t09 - t27) * 0.541196100146f
        t27 = t17 + t19
        t19 = (t17 - t19) * 1.30656296488f
        t17 = t25 + t27
        t27 = (t25 - t27) * 0.707106781187f
        t25 = t09 + t19
        t19 = (t09 - t19) * 0.707106781187f
        t25 += t19
        t09 = t31 + t23
        t31 = (t31 - t23) * 0.541196100146f
        t23 = t29 + t21
        t21 = (t29 - t21) * 1.30656296488f
        t29 = t09 + t23
        t23 = (t09 - t23) * 0.707106781187f
        t09 = t31 + t21
        t31 = (t31 - t21) * 0.707106781187f
        t09 += t31
        t29 += t09
        t09 += t23
        t23 += t31
        t17 += t29
        t29 += t25
        t25 += t09
        t09 += t27
        t27 += t23
        t23 += t19
        t19 += t31
        t21 = t02 + t32
        t02 = (t02 - t32) * 0.502419286188f
        t32 = t04 + t30
        t04 = (t04 - t30) * 0.52249861494f
        t30 = t06 + t28
        t28 = (t06 - t28) * 0.566944034816f
        t06 = t08 + t26
        t08 = (t08 - t26) * 0.64682178336f
        t26 = t10 + t24
        t10 = (t10 - t24) * 0.788154623451f
        t24 = t12 + t22
        t22 = (t12 - t22) * 1.06067768599f
        t12 = t14 + t20
        t20 = (t14 - t20) * 1.72244709824f
        t14 = t16 + t18
        t16 = (t16 - t18) * 5.10114861869f
        t18 = t21 + t14
        t14 = (t21 - t14) * 0.509795579104f
        t21 = t32 + t12
        t32 = (t32 - t12) * 0.601344886935f
        t12 = t30 + t24
        t24 = (t30 - t24) * 0.899976223136f
        t30 = t06 + t26
        t26 = (t06 - t26) * 2.56291544774f
        t06 = t18 + t30
        t18 = (t18 - t30) * 0.541196100146f
        t30 = t21 + t12
        t12 = (t21 - t12) * 1.30656296488f
        t21 = t06 + t30
        t30 = (t06 - t30) * 0.707106781187f
        t06 = t18 + t12
        t12 = (t18 - t12) * 0.707106781187f
        t06 += t12
        t18 = t14 + t26
        t26 = (t14 - t26) * 0.541196100146f
        t14 = t32 + t24
        t24 = (t32 - t24) * 1.30656296488f
        t32 = t18 + t14
        t14 = (t18 - t14) * 0.707106781187f
        t18 = t26 + t24
        t24 = (t26 - t24) * 0.707106781187f
        t18 += t24
        t32 += t18
        t18 += t14
        t26 = t14 + t24
        t14 = t02 + t16
        t02 = (t02 - t16) * 0.509795579104f
        t16 = t04 + t20
        t04 = (t04 - t20) * 0.601344886935f
        t20 = t28 + t22
        t22 = (t28 - t22) * 0.899976223136f
        t28 = t08 + t10
        t10 = (t08 - t10) * 2.56291544774f
        t08 = t14 + t28
        t14 = (t14 - t28) * 0.541196100146f
        t28 = t16 + t20
        t20 = (t16 - t20) * 1.30656296488f
        t16 = t08 + t28
        t28 = (t08 - t28) * 0.707106781187f
        t08 = t14 + t20
        t20 = (t14 - t20) * 0.707106781187f
        t08 += t20
        t14 = t02 + t10
        t02 = (t02 - t10) * 0.541196100146f
        t10 = t04 + t22
        t22 = (t04 - t22) * 1.30656296488f
        t04 = t14 + t10
        t10 = (t14 - t10) * 0.707106781187f
        t14 = t02 + t22
        t02 = (t02 - t22) * 0.707106781187f
        t14 += t02
        t04 += t14
        t14 += t10
        t10 += t02
        t16 += t04
        t04 += t08
        t08 += t14
        t14 += t28
        t28 += t10
        t10 += t20
        t20 += t02
        t21 += t16
        t16 += t32
        t32 += t04
        t04 += t06
        t06 += t08
        t08 += t18
        t18 += t14
        t14 += t30
        t30 += t28
        t28 += t26
        t26 += t10
        t10 += t12
        t12 += t20
        t20 += t24
        t24 += t02
        d[dp + 48] = -t33
        d[dp + 49] = -t21; d[dp + 47] = d[dp + 49]
        d[dp + 50] = -t17; d[dp + 46] = d[dp + 50]
        d[dp + 51] = -t16; d[dp + 45] = d[dp + 51]
        d[dp + 52] = -t01; d[dp + 44] = d[dp + 52]
        d[dp + 53] = -t32; d[dp + 43] = d[dp + 53]
        d[dp + 54] = -t29; d[dp + 42] = d[dp + 54]
        d[dp + 55] = -t04; d[dp + 41] = d[dp + 55]
        d[dp + 56] = -t03; d[dp + 40] = d[dp + 56]
        d[dp + 57] = -t06; d[dp + 39] = d[dp + 57]
        d[dp + 58] = -t25; d[dp + 38] = d[dp + 58]
        d[dp + 59] = -t08; d[dp + 37] = d[dp + 59]
        d[dp + 60] = -t11; d[dp + 36] = d[dp + 60]
        d[dp + 61] = -t18; d[dp + 35] = d[dp + 61]
        d[dp + 62] = -t09; d[dp + 34] = d[dp + 62]
        d[dp + 63] = -t14; d[dp + 33] = d[dp + 63]
        d[dp + 32] = -t05
        d[dp + 0] = t05
        d[dp + 31] = -t30
        d[dp + 1] = t30
        d[dp + 30] = -t27
        d[dp + 2] = t27
        d[dp + 29] = -t28
        d[dp + 3] = t28
        d[dp + 28] = -t07
        d[dp + 4] = t07
        d[dp + 27] = -t26
        d[dp + 5] = t26
        d[dp + 26] = -t23
        d[dp + 6] = t23
        d[dp + 25] = -t10
        d[dp + 7] = t10
        d[dp + 24] = -t15
        d[dp + 8] = t15
        d[dp + 23] = -t12
        d[dp + 9] = t12
        d[dp + 22] = -t19
        d[dp + 10] = t19
        d[dp + 21] = -t20
        d[dp + 11] = t20
        d[dp + 20] = -t13
        d[dp + 12] = t13
        d[dp + 19] = -t24
        d[dp + 13] = t24
        d[dp + 18] = -t31
        d[dp + 14] = t31
        d[dp + 17] = -t02
        d[dp + 15] = t02
        d[dp + 16] = 0.0f
    }

    companion object {
        const val SAMPLES_PER_FRAME = 1152

        // Filter-bank output is -2^30 times full scale (PL_MPEG's 1090519040 = 2^30 * 65 / 64 folds in
        // a fudge factor for its approximate requantisation, which we replaced with the exact one).
        private const val OUTPUT_SCALE = 32768f / -1073741824f

        private const val MODE_JOINT_STEREO = 1
        private const val MODE_MONO = 3

        private val EMPTY = ByteArray(0)
        private val SAMPLE_RATE = intArrayOf(44100, 48000, 32000)
        private val BIT_RATE =
            intArrayOf(32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
        private val SCALEFACTOR_BASE = intArrayOf(0x02000000, 0x01965FEA, 0x01428A30)

        // Quantizer lookup, step 1: bitrate classes (mono / stereo) per bitrate index.
        private val QUANT_LUT_STEP_1 = arrayOf(
            intArrayOf(0, 0, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2),
            intArrayOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 2),
        )

        // Step 2: bitrate class x sample rate -> table (bit 6 = high-rate table) | sblimit.
        private const val TAB_A = 27 or 64
        private const val TAB_B = 30 or 64
        private const val TAB_C = 8
        private const val TAB_D = 12
        private val QUANT_LUT_STEP_2 = arrayOf(
            intArrayOf(TAB_C, TAB_C, TAB_D),
            intArrayOf(TAB_A, TAB_A, TAB_A),
            intArrayOf(TAB_B, TAB_A, TAB_B),
        )

        // Step 3: table, subband -> (nbal << 4) | row index. Only the MPEG-1 tables (0, 1) are used.
        private val QUANT_LUT_STEP_3 = arrayOf(
            intArrayOf(0x44, 0x44) + IntArray(10) { 0x34 },
            intArrayOf(
                0x43,
                0x43,
                0x43
            ) + IntArray(8) { 0x42 } + IntArray(12) { 0x31 } + IntArray(7) { 0x20 },
        )

        // Step 4: table row, allocation value -> quantizer index. Rows are padded to 16 entries.
        private val QUANT_LUT_STEP_4 = arrayOf(
            intArrayOf(0, 1, 2, 17, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 17, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17),
            intArrayOf(0, 1, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17),
            intArrayOf(0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        )

        private val QUANT_TAB = arrayOf(
            Quantizer(3, true, 5), Quantizer(5, true, 7), Quantizer(7, false, 3),
            Quantizer(9, true, 10), Quantizer(15, false, 4), Quantizer(31, false, 5),
            Quantizer(63, false, 6), Quantizer(127, false, 7), Quantizer(255, false, 8),
            Quantizer(511, false, 9), Quantizer(1023, false, 10), Quantizer(2047, false, 11),
            Quantizer(4095, false, 12), Quantizer(8191, false, 13), Quantizer(16383, false, 14),
            Quantizer(32767, false, 15), Quantizer(65535, false, 16),
        )

        /** The 512-entry synthesis window, stored twice (the decoder indexes up to 1023). */
        private val WINDOW: FloatArray = run {
            val half = floatArrayOf(
                0.0f,
                -0.5f,
                -0.5f,
                -0.5f,
                -0.5f,
                -0.5f,
                -0.5f,
                -1.0f,
                -1.0f,
                -1.0f,
                -1.0f,
                -1.5f,
                -1.5f,
                -2.0f,
                -2.0f,
                -2.5f,
                -2.5f,
                -3.0f,
                -3.5f,
                -3.5f,
                -4.0f,
                -4.5f,
                -5.0f,
                -5.5f,
                -6.5f,
                -7.0f,
                -8.0f,
                -8.5f,
                -9.5f,
                -10.5f,
                -12.0f,
                -13.0f,
                -14.5f,
                -15.5f,
                -17.5f,
                -19.0f,
                -20.5f,
                -22.5f,
                -24.5f,
                -26.5f,
                -29.0f,
                -31.5f,
                -34.0f,
                -36.5f,
                -39.5f,
                -42.5f,
                -45.5f,
                -48.5f,
                -52.0f,
                -55.5f,
                -58.5f,
                -62.5f,
                -66.0f,
                -69.5f,
                -73.5f,
                -77.0f,
                -80.5f,
                -84.5f,
                -88.0f,
                -91.5f,
                -95.0f,
                -98.0f,
                -101.0f,
                -104.0f,
                106.5f,
                109.0f,
                111.0f,
                112.5f,
                113.5f,
                114.0f,
                114.0f,
                113.5f,
                112.0f,
                110.5f,
                107.5f,
                104.0f,
                100.0f,
                94.5f,
                88.5f,
                81.5f,
                73.0f,
                63.5f,
                53.0f,
                41.5f,
                28.5f,
                14.5f,
                -1.0f,
                -18.0f,
                -36.0f,
                -55.5f,
                -76.5f,
                -98.5f,
                -122.0f,
                -147.0f,
                -173.5f,
                -200.5f,
                -229.5f,
                -259.5f,
                -290.5f,
                -322.5f,
                -355.5f,
                -389.5f,
                -424.0f,
                -459.5f,
                -495.5f,
                -532.0f,
                -568.5f,
                -605.0f,
                -641.5f,
                -678.0f,
                -714.0f,
                -749.0f,
                -783.5f,
                -817.0f,
                -849.0f,
                -879.5f,
                -908.5f,
                -935.0f,
                -959.5f,
                -981.0f,
                -1000.5f,
                -1016.0f,
                -1028.5f,
                -1037.5f,
                -1042.5f,
                -1043.5f,
                -1040.0f,
                -1031.5f,
                1018.5f,
                1000.0f,
                976.0f,
                946.5f,
                911.0f,
                869.5f,
                822.0f,
                767.5f,
                707.0f,
                640.0f,
                565.5f,
                485.0f,
                397.0f,
                302.5f,
                201.0f,
                92.5f,
                -22.5f,
                -144.0f,
                -272.5f,
                -407.0f,
                -547.5f,
                -694.0f,
                -846.0f,
                -1003.0f,
                -1165.0f,
                -1331.5f,
                -1502.0f,
                -1675.5f,
                -1852.5f,
                -2031.5f,
                -2212.5f,
                -2394.0f,
                -2576.5f,
                -2758.5f,
                -2939.5f,
                -3118.5f,
                -3294.5f,
                -3467.5f,
                -3635.5f,
                -3798.5f,
                -3955.0f,
                -4104.5f,
                -4245.5f,
                -4377.5f,
                -4499.0f,
                -4609.5f,
                -4708.0f,
                -4792.5f,
                -4863.5f,
                -4919.0f,
                -4958.0f,
                -4979.5f,
                -4983.0f,
                -4967.5f,
                -4931.5f,
                -4875.0f,
                -4796.0f,
                -4694.5f,
                -4569.5f,
                -4420.0f,
                -4246.0f,
                -4046.0f,
                -3820.0f,
                -3567.0f,
                3287.0f,
                2979.5f,
                2644.0f,
                2280.5f,
                1888.0f,
                1467.5f,
                1018.5f,
                541.0f,
                35.0f,
                -499.0f,
                -1061.0f,
                -1650.0f,
                -2266.5f,
                -2909.0f,
                -3577.0f,
                -4270.0f,
                -4987.5f,
                -5727.5f,
                -6490.0f,
                -7274.0f,
                -8077.5f,
                -8899.5f,
                -9739.0f,
                -10594.5f,
                -11464.5f,
                -12347.0f,
                -13241.0f,
                -14144.5f,
                -15056.0f,
                -15973.5f,
                -16895.5f,
                -17820.0f,
                -18744.5f,
                -19668.0f,
                -20588.0f,
                -21503.0f,
                -22410.5f,
                -23308.5f,
                -24195.0f,
                -25068.5f,
                -25926.5f,
                -26767.0f,
                -27589.0f,
                -28389.0f,
                -29166.5f,
                -29919.0f,
                -30644.5f,
                -31342.0f,
                -32009.5f,
                -32645.0f,
                -33247.0f,
                -33814.5f,
                -34346.0f,
                -34839.5f,
                -35295.0f,
                -35710.0f,
                -36084.5f,
                -36417.5f,
                -36707.5f,
                -36954.0f,
                -37156.5f,
                -37315.0f,
                -37428.0f,
                -37496.0f,
                37519.0f,
                37496.0f,
                37428.0f,
                37315.0f,
                37156.5f,
                36954.0f,
                36707.5f,
                36417.5f,
                36084.5f,
                35710.0f,
                35295.0f,
                34839.5f,
                34346.0f,
                33814.5f,
                33247.0f,
                32645.0f,
                32009.5f,
                31342.0f,
                30644.5f,
                29919.0f,
                29166.5f,
                28389.0f,
                27589.0f,
                26767.0f,
                25926.5f,
                25068.5f,
                24195.0f,
                23308.5f,
                22410.5f,
                21503.0f,
                20588.0f,
                19668.0f,
                18744.5f,
                17820.0f,
                16895.5f,
                15973.5f,
                15056.0f,
                14144.5f,
                13241.0f,
                12347.0f,
                11464.5f,
                10594.5f,
                9739.0f,
                8899.5f,
                8077.5f,
                7274.0f,
                6490.0f,
                5727.5f,
                4987.5f,
                4270.0f,
                3577.0f,
                2909.0f,
                2266.5f,
                1650.0f,
                1061.0f,
                499.0f,
                -35.0f,
                -541.0f,
                -1018.5f,
                -1467.5f,
                -1888.0f,
                -2280.5f,
                -2644.0f,
                -2979.5f,
                3287.0f,
                3567.0f,
                3820.0f,
                4046.0f,
                4246.0f,
                4420.0f,
                4569.5f,
                4694.5f,
                4796.0f,
                4875.0f,
                4931.5f,
                4967.5f,
                4983.0f,
                4979.5f,
                4958.0f,
                4919.0f,
                4863.5f,
                4792.5f,
                4708.0f,
                4609.5f,
                4499.0f,
                4377.5f,
                4245.5f,
                4104.5f,
                3955.0f,
                3798.5f,
                3635.5f,
                3467.5f,
                3294.5f,
                3118.5f,
                2939.5f,
                2758.5f,
                2576.5f,
                2394.0f,
                2212.5f,
                2031.5f,
                1852.5f,
                1675.5f,
                1502.0f,
                1331.5f,
                1165.0f,
                1003.0f,
                846.0f,
                694.0f,
                547.5f,
                407.0f,
                272.5f,
                144.0f,
                22.5f,
                -92.5f,
                -201.0f,
                -302.5f,
                -397.0f,
                -485.0f,
                -565.5f,
                -640.0f,
                -707.0f,
                -767.5f,
                -822.0f,
                -869.5f,
                -911.0f,
                -946.5f,
                -976.0f,
                -1000.0f,
                1018.5f,
                1031.5f,
                1040.0f,
                1043.5f,
                1042.5f,
                1037.5f,
                1028.5f,
                1016.0f,
                1000.5f,
                981.0f,
                959.5f,
                935.0f,
                908.5f,
                879.5f,
                849.0f,
                817.0f,
                783.5f,
                749.0f,
                714.0f,
                678.0f,
                641.5f,
                605.0f,
                568.5f,
                532.0f,
                495.5f,
                459.5f,
                424.0f,
                389.5f,
                355.5f,
                322.5f,
                290.5f,
                259.5f,
                229.5f,
                200.5f,
                173.5f,
                147.0f,
                122.0f,
                98.5f,
                76.5f,
                55.5f,
                36.0f,
                18.0f,
                1.0f,
                -14.5f,
                -28.5f,
                -41.5f,
                -53.0f,
                -63.5f,
                -73.0f,
                -81.5f,
                -88.5f,
                -94.5f,
                -100.0f,
                -104.0f,
                -107.5f,
                -110.5f,
                -112.0f,
                -113.5f,
                -114.0f,
                -114.0f,
                -113.5f,
                -112.5f,
                -111.0f,
                -109.0f,
                106.5f,
                104.0f,
                101.0f,
                98.0f,
                95.0f,
                91.5f,
                88.0f,
                84.5f,
                80.5f,
                77.0f,
                73.5f,
                69.5f,
                66.0f,
                62.5f,
                58.5f,
                55.5f,
                52.0f,
                48.5f,
                45.5f,
                42.5f,
                39.5f,
                36.5f,
                34.0f,
                31.5f,
                29.0f,
                26.5f,
                24.5f,
                22.5f,
                20.5f,
                19.0f,
                17.5f,
                15.5f,
                14.5f,
                13.0f,
                12.0f,
                10.5f,
                9.5f,
                8.5f,
                8.0f,
                7.0f,
                6.5f,
                5.5f,
                5.0f,
                4.5f,
                4.0f,
                3.5f,
                3.5f,
                3.0f,
                2.5f,
                2.5f,
                2.0f,
                2.0f,
                1.5f,
                1.5f,
                1.0f,
                1.0f,
                1.0f,
                1.0f,
                0.5f,
                0.5f,
                0.5f,
                0.5f,
                0.5f,
                0.5f,
            )
            half + half
        }
    }
}
