package dev.section.streamly.player.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the decoder against FFmpeg. Each `sample_<rate>k.mp2` is a few frames cut from one of our
 * live channels (48 kHz stereo) and `reference_<rate>k.pcm` is what `ffmpeg -f s16le` decodes it to.
 * The bitrates matter: they select different quantizer tables inside the codec.
 */
class Mp2DecoderTest {

    private val perFrame = Mp2Decoder.SAMPLES_PER_FRAME * 2

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/mp2/$name")) { "missing $name" }.use { it.readBytes() }

    private fun shorts(bytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun frameSize(kbps: Int) = 144_000 * kbps / 48_000

    private fun decodeAll(kbps: Int, decoder: Mp2Decoder = Mp2Decoder()): ShortArray {
        val data = resource("sample_${kbps}k.mp2")
        val size = frameSize(kbps)
        val frames = data.size / size
        val pcm = ShortArray(frames * perFrame)
        val out = ShortArray(perFrame)
        for (i in 0 until frames) {
            assertEquals(
                Mp2Decoder.SAMPLES_PER_FRAME,
                decoder.decodeFrame(data, i * size, size, out),
                "frame $i at $kbps kbps",
            )
            out.copyInto(pcm, i * perFrame)
        }
        return pcm
    }

    private fun assertMatchesFfmpeg(kbps: Int) {
        val pcm = decodeAll(kbps)
        val reference = shorts(resource("reference_${kbps}k.pcm"))
        assertEquals(reference.size, pcm.size, "length at $kbps kbps")

        var maxError = 0
        var signal = 0.0
        var noise = 0.0
        for (i in pcm.indices) {
            val diff = pcm[i] - reference[i]
            maxError = maxOf(maxError, abs(diff))
            signal += reference[i].toDouble() * reference[i]
            noise += diff.toDouble() * diff
        }
        val rmsError = sqrt(noise / pcm.size)

        assertTrue(signal > 0, "$kbps kbps: reference is silent, so the test would prove nothing")
        // FFmpeg rounds differently (fixed point), so allow a few LSB. Absolute limits, not an SNR,
        // because some channels are near-silent.
        assertTrue(maxError <= 16, "$kbps kbps: max error $maxError LSB")
        assertTrue(rmsError < 2.0, "$kbps kbps: RMS error $rmsError LSB")
    }

    @Test
    fun matchesFfmpeg64k() = assertMatchesFfmpeg(64) // low-rate quantizer tables
    @Test
    fun matchesFfmpeg96k() = assertMatchesFfmpeg(96)
    @Test
    fun matchesFfmpeg128k() = assertMatchesFfmpeg(128)
    @Test
    fun matchesFfmpeg192k() = assertMatchesFfmpeg(192)
    @Test
    fun matchesFfmpeg320k() = assertMatchesFfmpeg(320)

    @Test
    fun matrixingMatchesTheDefinition() {
        val random = java.util.Random(7)
        val s = FloatArray(96) { (random.nextInt(2001) - 1000).toFloat() }
        val d = FloatArray(1024)
        Mp2Decoder().idct36(s, 1, d, 0)
        for (i in 0 until 64) {
            var expected = 0.0
            for (k in 0 until 32) expected += s[k * 3 + 1] * cos((16 + i) * (2 * k + 1) * Math.PI / 64)
            assertEquals(expected, d[i].toDouble(), 0.05, "output $i")
        }
    }

    @Test
    fun reportsSampleRate() {
        val decoder = Mp2Decoder()
        decodeAll(128, decoder)
        assertEquals(48_000, decoder.sampleRate)
    }

    @Test
    fun resetClearsFilterState() {
        val decoder = Mp2Decoder()
        val first = decodeAll(128, decoder)
        decoder.reset()
        assertTrue(first.contentEquals(decodeAll(128, decoder)))
    }

    @Test
    fun truncatedFrameIsRejectedWithoutThrowing() {
        val data = resource("sample_128k.mp2")
        val out = ShortArray(perFrame)
        assertEquals(0, Mp2Decoder().decodeFrame(data, 0, frameSize(128) - 1, out))
        assertEquals(0, Mp2Decoder().decodeFrame(data, 0, 3, out))
        assertEquals(0, Mp2Decoder().decodeFrame(data, 0, 0, out))
    }

    @Test
    fun unsupportedHeadersAreRejected() {
        val out = ShortArray(perFrame)
        val frame = resource("sample_128k.mp2").copyOfRange(0, frameSize(128))

        val layer3 = frame.copyOf().also { it[1] = 0xFB.toByte() } // MPEG-1 Layer III
        assertEquals(0, Mp2Decoder().decodeFrame(layer3, 0, layer3.size, out))

        val mpeg2 = frame.copyOf().also { it[1] = 0xF4.toByte() } // MPEG-2 Layer II
        assertEquals(0, Mp2Decoder().decodeFrame(mpeg2, 0, mpeg2.size, out))

        val noSync = frame.copyOf().also { it[0] = 0 }
        assertEquals(0, Mp2Decoder().decodeFrame(noSync, 0, noSync.size, out))

        val badRate =
            frame.copyOf().also { it[2] = (it[2].toInt() or 0x0C).toByte() } // rate index 3
        assertEquals(0, Mp2Decoder().decodeFrame(badRate, 0, badRate.size, out))
    }

    @Test
    fun corruptPayloadNeverThrows() {
        val out = ShortArray(perFrame)
        val random = java.util.Random(42)
        val size = frameSize(128)
        val original = resource("sample_128k.mp2").copyOfRange(0, size)
        repeat(300) {
            val frame = original.copyOf()
            repeat(20) { frame[4 + random.nextInt(size - 4)] = random.nextInt().toByte() }
            Mp2Decoder().decodeFrame(frame, 0, frame.size, out) // must not throw
        }
    }

    @Test
    fun tooSmallOutputBufferIsRejected() {
        val data = resource("sample_128k.mp2")
        assertEquals(0, Mp2Decoder().decodeFrame(data, 0, frameSize(128), ShortArray(10)))
    }
}
