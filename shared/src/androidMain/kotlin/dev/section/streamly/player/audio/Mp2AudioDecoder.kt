package dev.section.streamly.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 decoder that turns MP2 audio samples into 16-bit stereo PCM using [Mp2Decoder].
 *
 * The TS extractor delivers one MPEG audio frame per sample; if a sample ever holds several
 * frames (or trailing junk), every complete frame in it is decoded.
 */
@OptIn(UnstableApi::class)
internal class Mp2AudioDecoder :
    SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, DecoderException>(
        // SimpleDecoder fills these arrays itself (createInputBuffer/createOutputBuffer).
        @Suppress("UNCHECKED_CAST")
        (arrayOfNulls<DecoderInputBuffer>(INPUT_BUFFERS) as Array<DecoderInputBuffer>),
        @Suppress("UNCHECKED_CAST")
        (arrayOfNulls<SimpleDecoderOutputBuffer>(OUTPUT_BUFFERS) as Array<SimpleDecoderOutputBuffer>),
    ) {

    private val mp2 = Mp2Decoder()
    private val frame = ShortArray(Mp2Decoder.SAMPLES_PER_FRAME * 2)

    /** Sample rate of the stream (48 kHz for our channels) once a frame has been decoded. */
    val sampleRate: Int get() = mp2.sampleRate

    override fun getName(): String = "Mp2AudioDecoder"

    override fun createInputBuffer() =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer() = SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }

    override fun createUnexpectedDecodeException(error: Throwable) =
        DecoderException("Unexpected MP2 decode error", error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: SimpleDecoderOutputBuffer,
        reset: Boolean,
    ): DecoderException? {
        if (reset) mp2.reset()

        val source = inputBuffer.data ?: return null
        val bytes = ByteArray(source.remaining()).also { source.duplicate().get(it) }

        // First pass: how many complete frames are in this sample?
        val frames = frameOffsets(bytes)
        if (frames.isEmpty()) {
            outputBuffer.shouldBeSkipped = true
            return null
        }

        val pcm = outputBuffer.init(inputBuffer.timeUs, frames.size * FRAME_PCM_BYTES)
        pcm.order(ByteOrder.LITTLE_ENDIAN)
        for (offset in frames) {
            val size = mp2.frameLength(bytes, offset, bytes.size - offset)
            if (mp2.decodeFrame(bytes, offset, size, frame) == 0) {
                frame.fill(0) // corrupt frame: keep timing with silence instead of dropping it
            }
            writeShorts(pcm, frame)
        }
        pcm.flip()
        return null
    }

    private fun frameOffsets(bytes: ByteArray): List<Int> {
        val offsets = ArrayList<Int>(1)
        var position = 0
        while (position + 4 <= bytes.size) {
            val size = mp2.frameLength(bytes, position, bytes.size - position)
            if (size == 0) {
                position++ // not a header: resync byte by byte
            } else if (position + size <= bytes.size) {
                offsets += position
                position += size
            } else {
                break // truncated last frame
            }
        }
        return offsets
    }

    private fun writeShorts(target: ByteBuffer, samples: ShortArray) {
        for (sample in samples) target.putShort(sample)
    }

    private companion object {
        const val INPUT_BUFFERS = 4
        const val OUTPUT_BUFFERS = 4
        const val FRAME_PCM_BYTES = Mp2Decoder.SAMPLES_PER_FRAME * 2 * 2
    }
}
