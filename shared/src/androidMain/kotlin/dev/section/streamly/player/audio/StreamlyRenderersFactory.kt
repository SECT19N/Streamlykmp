package dev.section.streamly.player.audio

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import dev.section.streamly.ffmpeg.FfmpegAudioRenderer
import dev.section.streamly.ffmpeg.FfmpegVideoRenderer

/**
 * The default renderers plus FFmpeg software fallbacks: [FfmpegAudioRenderer] (MP2 and friends)
 * and [FfmpegVideoRenderer] (MPEG-2, H.264, HEVC).
 *
 * Both are added last so MediaCodec keeps winning whenever the device can decode the format itself.
 * If the native libraries are missing they report every format as unsupported and stay out of the way.
 */
@OptIn(UnstableApi::class)
internal class StreamlyRenderersFactory(
    context: Context,
    /** When true MediaCodec is hidden for video, so ExoPlayer selects [FfmpegVideoRenderer]. */
    private val softwareVideoOnly: () -> Boolean = { false },
) : DefaultRenderersFactory(context) {

    init {
        // If the best MediaCodec decoder fails to initialise, try the device's next one
        // (typically the platform software decoder) before giving up.
        setEnableDecoderFallback(true)
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            hideVideoWhenSoftwareOnly(mediaCodecSelector),
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )
        out.add(
            FfmpegVideoRenderer(
                allowedVideoJoiningTimeMs,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAMES_TO_NOTIFY,
            ),
        )
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out,
        )
        out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
    }

    /**
     * Some devices (notably emulators) advertise a video decoder that then fails to configure.
     * The renderer is chosen when tracks are selected, so the only way to hand over to FFmpeg is to
     * make MediaCodec report no video decoders and select tracks again.
     */
    private fun hideVideoWhenSoftwareOnly(delegate: MediaCodecSelector) =
        MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            if (softwareVideoOnly() && MimeTypes.isVideo(mimeType)) {
                emptyList()
            } else {
                delegate.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }
        }

    private companion object {
        /** Same value DefaultRenderersFactory uses for its own video renderers. */
        const val MAX_DROPPED_VIDEO_FRAMES_TO_NOTIFY = 50
    }
}
