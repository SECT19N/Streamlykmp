package dev.section.streamly.player.audio

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

/**
 * Plays MP2 audio, which most of our live channels use and Android only decodes on some devices.
 *
 * It is registered after the platform's MediaCodec renderer ([StreamlyRenderersFactory]), so a
 * device that does have an MP2 decoder keeps using it and this one is only the fallback.
 */
@OptIn(UnstableApi::class)
internal class Mp2AudioRenderer(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
) : DecoderAudioRenderer<Mp2AudioDecoder>(eventHandler, eventListener, audioSink) {

    override fun getName(): String = "Mp2AudioRenderer"

    override fun supportsFormatInternal(format: Format): Int {
        if (format.sampleMimeType != MimeTypes.AUDIO_MPEG_L2) return C.FORMAT_UNSUPPORTED_TYPE
        if (format.sampleRate !in SUPPORTED_RATES && format.sampleRate != Format.NO_VALUE) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE
        }
        // The decoder always outputs stereo PCM, whatever the stream's own channel count.
        return if (sinkSupportsFormat(pcmFormat(format.sampleRate.takeIf { it > 0 } ?: 48_000))) {
            C.FORMAT_HANDLED
        } else {
            C.FORMAT_UNSUPPORTED_SUBTYPE
        }
    }

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?) = Mp2AudioDecoder()

    override fun getOutputFormat(decoder: Mp2AudioDecoder): Format = pcmFormat(decoder.sampleRate)

    private fun pcmFormat(sampleRate: Int): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(2)
            .setSampleRate(sampleRate)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

    private companion object {
        val SUPPORTED_RATES = setOf(32_000, 44_100, 48_000)
    }
}
