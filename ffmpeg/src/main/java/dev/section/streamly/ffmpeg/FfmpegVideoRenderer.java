package dev.section.streamly.ffmpeg;

import static java.lang.Runtime.getRuntime;

import android.os.Handler;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.video.DecoderVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import java.util.Objects;

/**
 * Software video renderer backed by FFmpeg, for MPEG-2, H.264 and HEVC.
 *
 * <p>Meant to sit <em>behind</em> {@code MediaCodecVideoRenderer} in the renderer list: it claims
 * formats at the same support level, so ExoPlayer's track selector keeps preferring MediaCodec and
 * only falls through here when MediaCodec cannot decode the format at all.
 */
@UnstableApi
public final class FfmpegVideoRenderer extends DecoderVideoRenderer {

    private static final String TAG = "FfmpegVideoRenderer";
    private static final int NUM_INPUT_BUFFERS = 4;
    private static final int NUM_OUTPUT_BUFFERS = 4;
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 768 * 1024;
    private static final int MAX_THREADS = 4;

    private final int threads;
    @Nullable
    private FfmpegVideoDecoder decoder;

    public FfmpegVideoRenderer(
            long allowedJoiningTimeMs,
            @Nullable Handler eventHandler,
            @Nullable VideoRendererEventListener eventListener,
            int maxDroppedFramesToNotify) {
        super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
        threads = Math.max(1, Math.min(MAX_THREADS, getRuntime().availableProcessors()));
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public @Capabilities int supportsFormat(Format format) {
        String mimeType = format.sampleMimeType;
        if (mimeType == null
                || !MimeTypes.isVideo(mimeType)
                || !FfmpegLibrary.isAvailable()
                || !FfmpegLibrary.supportsFormat(mimeType)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM);
        }
        return RendererCapabilities.create(
                C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED);
    }

    @Override
    protected FfmpegVideoDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
            throws FfmpegDecoderException {
        TraceUtil.beginSection("createFfmpegVideoDecoder");
        int initialInputBufferSize =
                format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
        FfmpegVideoDecoder decoder =
                new FfmpegVideoDecoder(
                        NUM_INPUT_BUFFERS, NUM_OUTPUT_BUFFERS, initialInputBufferSize, format, threads);
        this.decoder = decoder;
        TraceUtil.endSection();
        return decoder;
    }

    @Override
    protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
            throws FfmpegDecoderException {
        try {
            if (decoder == null) {
                throw new FfmpegDecoderException(
                        "Failed to render output buffer to surface: decoder is not initialized.");
            }
            decoder.renderToSurface(outputBuffer, surface);
        } finally {
            outputBuffer.release();
        }
    }

    @Override
    protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
        if (decoder != null) {
            decoder.setOutputMode(outputMode);
        }
    }

    @Override
    protected DecoderReuseEvaluation canReuseDecoder(
            String decoderName, Format oldFormat, Format newFormat) {
        boolean sameMimeType = Objects.equals(oldFormat.sampleMimeType, newFormat.sampleMimeType);
        return new DecoderReuseEvaluation(
                decoderName,
                oldFormat,
                newFormat,
                sameMimeType
                        ? DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION
                        : DecoderReuseEvaluation.REUSE_RESULT_NO,
                sameMimeType ? 0 : DecoderReuseEvaluation.DISCARD_REASON_MIME_TYPE_CHANGED);
    }
}
