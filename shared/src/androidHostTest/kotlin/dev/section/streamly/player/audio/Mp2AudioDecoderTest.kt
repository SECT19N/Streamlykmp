package dev.section.streamly.player.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Drives [Mp2AudioDecoder] the way ExoPlayer does: queue input buffers, dequeue PCM. */
class Mp2AudioDecoderTest {

    private val frameSize = 384 // 128 kbps at 48 kHz
    private val sample = resource("sample_128k.mp2")
    private val reference = resource("reference_128k.pcm")

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/mp2/$name")) { "missing $name" }.use { it.readBytes() }

    private fun Mp2AudioDecoder.decode(data: ByteArray, timeUs: Long): ByteArray? {
        val input = dequeueInputBuffer()
        assertNotNull(input)
        input.timeUs = timeUs
        input.ensureSpaceForWrite(data.size)
        input.data!!.put(data)
        input.flip()
        queueInputBuffer(input)

        repeat(500) {
            val output = dequeueOutputBuffer()
            if (output != null) {
                val bytes = ByteArray(output.data!!.remaining()).also { output.data!!.get(it) }
                output.release()
                return bytes
            }
            Thread.sleep(2)
        }
        return null
    }

    @Test
    fun decodesFramesToLittleEndianStereoPcm() {
        val decoder = Mp2AudioDecoder()
        try {
            val frames = sample.size / frameSize
            val pcm = ByteBuffer.allocate(frames * 4608).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frames) {
                val out = decoder.decode(sample.copyOfRange(i * frameSize, (i + 1) * frameSize), i * 24_000L)
                assertNotNull(out, "frame $i produced no output")
                assertEquals(4608, out.size)
                pcm.put(out)
            }
            assertEquals(48_000, decoder.sampleRate)

            val expected = ByteBuffer.wrap(reference).order(ByteOrder.LITTLE_ENDIAN)
            pcm.flip()
            assertEquals(expected.remaining(), pcm.remaining())
            var maxError = 0
            while (pcm.hasRemaining()) maxError = maxOf(maxError, abs(pcm.short - expected.short))
            assertTrue(maxError <= 16, "max error $maxError LSB")
        } finally {
            decoder.release()
        }
    }

    @Test
    fun aSampleHoldingTwoFramesYieldsTwoFramesOfPcm() {
        val decoder = Mp2AudioDecoder()
        try {
            val out = decoder.decode(sample.copyOfRange(0, frameSize * 2), 0)
            assertNotNull(out)
            assertEquals(2 * 4608, out.size)
        } finally {
            decoder.release()
        }
    }

    @Test
    fun aSampleWithoutAValidHeaderIsSkipped() {
        val decoder = Mp2AudioDecoder()
        try {
            val corrupt = sample.copyOfRange(0, frameSize).also { it[1] = 0xFB.toByte() } // not Layer II
            // No Layer II header: nothing is decoded or emitted, and the renderer carries on.
            assertEquals(null, decoder.decode(corrupt, 0))
        } finally {
            decoder.release()
        }
    }
}
