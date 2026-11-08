package dev.section.streamly.ffmpeg;

import static com.google.common.base.Preconditions.checkNotNull;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * FFmpeg software video decoder (MPEG-2, H.264, HEVC).
 *
 * <p>Frames are always handed to the renderer as 8-bit planar YUV 4:2:0; the native side converts
 * anything else (10-bit, 4:2:2, ...) first. Rendering to a {@link Surface} converts to RGBA in
 * native code.
 */
@UnstableApi
/* package */ final class FfmpegVideoDecoder
    extends SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException> {

  // LINT.IfChange
  private static final int NO_ERROR = 0;
  private static final int NO_FRAME = 1;
  // LINT.ThenChange(../../../../../cpp/ffmpeg_video_jni.cc)

  private final String codecName;
  private long nativeContext;
  private volatile @C.VideoOutputMode int outputMode = C.VIDEO_OUTPUT_MODE_NONE;

  public FfmpegVideoDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int initialInputBufferSize,
      Format format,
      int threads)
      throws FfmpegDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new VideoDecoderOutputBuffer[numOutputBuffers]);
    if (!FfmpegLibrary.isAvailable()) {
      throw new FfmpegDecoderException("Failed to load decoder native libraries.");
    }
    checkNotNull(format.sampleMimeType);
    codecName = checkNotNull(FfmpegLibrary.getCodecName(format.sampleMimeType));
    nativeContext =
        videoInitialize(codecName, getExtraData(format.initializationData), threads);
    if (nativeContext == 0) {
      throw new FfmpegDecoderException("Failed to initialize decoder: " + codecName);
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "ffmpeg-" + codecName + "-" + FfmpegLibrary.getVersion();
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected VideoDecoderOutputBuffer createOutputBuffer() {
    return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected FfmpegDecoderException createUnexpectedDecodeException(Throwable error) {
    return new FfmpegDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected FfmpegDecoderException decode(
      DecoderInputBuffer inputBuffer, VideoDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      videoFlush(nativeContext);
    }
    ByteBuffer inputData = checkNotNull(inputBuffer.data);
    if (videoDecode(nativeContext, inputData, inputData.limit(), inputBuffer.timeUs)
        != NO_ERROR) {
      return new FfmpegDecoderException("Decode error: " + codecName);
    }

    // The decoder may lag behind the input (frame threading, B-frame reordering) so the frame
    // that comes out is not necessarily the one that went in; the native side sets timeUs.
    outputBuffer.init(inputBuffer.timeUs, outputMode, /* supplementalData= */ null);
    boolean wantFrame = outputMode != C.VIDEO_OUTPUT_MODE_NONE;
    int result = videoGetFrame(nativeContext, outputBuffer, wantFrame);
    if (result < 0) {
      return new FfmpegDecoderException("Failed to get decoded frame: " + codecName);
    }
    if (result == NO_FRAME || !wantFrame || !isAtLeastOutputStartTimeUs(outputBuffer.timeUs)) {
      outputBuffer.shouldBeSkipped = true;
    }
    outputBuffer.format = inputBuffer.format;
    return null;
  }

  @Override
  public void release() {
    super.release();
    if (nativeContext != 0) {
      videoRelease(nativeContext);
      nativeContext = 0;
    }
  }

  public void setOutputMode(@C.VideoOutputMode int outputMode) {
    this.outputMode = outputMode;
  }

  public void renderToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws FfmpegDecoderException {
    ByteBuffer data = checkNotNull(outputBuffer.data);
    if (videoRenderFrame(
            nativeContext,
            surface,
            data,
            outputBuffer.width,
            outputBuffer.height,
            outputBuffer.yStride,
            outputBuffer.uvStride,
            outputBuffer.colorspace)
        != NO_ERROR) {
      throw new FfmpegDecoderException("Failed to render frame to surface.");
    }
  }

  /**
   * Annex-B parameter sets, as the Media3 extractors (TS and MP4 alike) deliver them, are used
   * as-is; FFmpeg detects the format from the start code.
   */
  @Nullable
  private static byte[] getExtraData(List<byte[]> initializationData) {
    int size = 0;
    for (byte[] part : initializationData) {
      size += part.length;
    }
    if (size == 0) {
      return null;
    }
    byte[] extraData = new byte[size];
    int offset = 0;
    for (byte[] part : initializationData) {
      System.arraycopy(part, 0, extraData, offset, part.length);
      offset += part.length;
    }
    return extraData;
  }

  private native long videoInitialize(String codecName, @Nullable byte[] extraData, int threads);

  private native int videoDecode(long context, ByteBuffer data, int size, long timeUs);

  private native int videoGetFrame(
      long context, VideoDecoderOutputBuffer outputBuffer, boolean copyFrame);

  private native int videoRenderFrame(
      long context,
      Surface surface,
      ByteBuffer yuvData,
      int width,
      int height,
      int yStride,
      int uvStride,
      int colorspace);

  private native void videoFlush(long context);

  private native void videoRelease(long context);
}
