// JNI glue for FfmpegVideoDecoder: software decoding of MPEG-2 / H.264 / HEVC.
//
// Decoded frames are normalised to 8-bit planar YUV 4:2:0 into the output buffer's direct
// ByteBuffer (so the Java side stays codec-agnostic), and rendered to a Surface by converting
// to RGBA straight into the ANativeWindow buffer.

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <string.h>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libavutil/opt.h>
#include <libavutil/pixdesc.h>
#include <libswscale/swscale.h>
}

#define LOG_TAG "ffmpeg_video_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

// LINT.IfChange
static const jint RESULT_OK = 0;
static const jint RESULT_NO_FRAME = 1;
static const jint RESULT_ERROR = -1;
// Matches VideoDecoderOutputBuffer.COLORSPACE_*; bit 8 flags full-range (JPEG) video.
static const int COLORSPACE_BT601 = 1;
static const int COLORSPACE_BT709 = 2;
static const int COLORSPACE_BT2020 = 3;
static const int COLORSPACE_FULL_RANGE_FLAG = 1 << 8;
// LINT.ThenChange(../java/dev/section/streamly/ffmpeg/FfmpegVideoDecoder.java)

namespace {

struct VideoContext {
  AVCodecContext* codec = nullptr;
  AVPacket* packet = nullptr;
  AVFrame* frame = nullptr;
  // True when `frame` holds a decoded frame that has not been handed out yet.
  bool hasPendingFrame = false;
  SwsContext* toI420 = nullptr;
  SwsContext* toRgba = nullptr;
};

void releaseVideoContext(VideoContext* ctx) {
  if (!ctx) return;
  sws_freeContext(ctx->toI420);
  sws_freeContext(ctx->toRgba);
  av_frame_free(&ctx->frame);
  av_packet_free(&ctx->packet);
  avcodec_free_context(&ctx->codec);
  delete ctx;
}

int colorspaceOf(const AVFrame* frame) {
  int cs;
  switch (frame->colorspace) {
    case AVCOL_SPC_BT709:
      cs = COLORSPACE_BT709;
      break;
    case AVCOL_SPC_BT2020_NCL:
    case AVCOL_SPC_BT2020_CL:
      cs = COLORSPACE_BT2020;
      break;
    case AVCOL_SPC_BT470BG:
    case AVCOL_SPC_SMPTE170M:
    case AVCOL_SPC_SMPTE240M:
      cs = COLORSPACE_BT601;
      break;
    default:
      // Unspecified: HD is conventionally BT.709, SD BT.601.
      cs = frame->height >= 720 ? COLORSPACE_BT709 : COLORSPACE_BT601;
  }
  if (frame->color_range == AVCOL_RANGE_JPEG ||
      frame->format == AV_PIX_FMT_YUVJ420P) {
    cs |= COLORSPACE_FULL_RANGE_FLAG;
  }
  return cs;
}

}  // namespace

static jlong videoInitialize(JNIEnv* env, jobject, jstring jCodecName,
                             jbyteArray extraData, jint threads) {
  const char* name = env->GetStringUTFChars(jCodecName, nullptr);
  const AVCodec* codec = avcodec_find_decoder_by_name(name);
  env->ReleaseStringUTFChars(jCodecName, name);
  if (!codec) {
    LOGE("Video codec not found.");
    return 0;
  }
  VideoContext* ctx = new VideoContext();
  ctx->codec = avcodec_alloc_context3(codec);
  ctx->packet = av_packet_alloc();
  ctx->frame = av_frame_alloc();
  if (!ctx->codec || !ctx->packet || !ctx->frame) {
    LOGE("Failed to allocate video decoder.");
    releaseVideoContext(ctx);
    return 0;
  }
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    ctx->codec->extradata =
        (uint8_t*)av_mallocz(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!ctx->codec->extradata) {
      releaseVideoContext(ctx);
      return 0;
    }
    ctx->codec->extradata_size = size;
    env->GetByteArrayRegion(extraData, 0, size, (jbyte*)ctx->codec->extradata);
  }
  ctx->codec->pkt_timebase = AVRational{1, 1000000};
  ctx->codec->thread_count = threads > 0 ? threads : 1;
  ctx->codec->thread_type = FF_THREAD_FRAME | FF_THREAD_SLICE;
  // Live streams start mid-GOP and may be damaged; show what can be decoded.
  ctx->codec->err_recognition = AV_EF_IGNORE_ERR;
  ctx->codec->flags2 |= AV_CODEC_FLAG2_SHOW_ALL;
  int result = avcodec_open2(ctx->codec, codec, nullptr);
  if (result < 0) {
    char buf[AV_ERROR_MAX_STRING_SIZE];
    LOGE("avcodec_open2 failed: %s", av_make_error_string(buf, sizeof(buf), result));
    releaseVideoContext(ctx);
    return 0;
  }
  return reinterpret_cast<jlong>(ctx);
}

static jint videoDecode(JNIEnv* env, jobject, jlong jCtx, jobject data,
                        jint size, jlong timeUs) {
  VideoContext* ctx = reinterpret_cast<VideoContext*>(jCtx);
  uint8_t* input = (uint8_t*)env->GetDirectBufferAddress(data);
  if (!ctx || !input || size < 0) {
    LOGE("Invalid decode arguments.");
    return RESULT_ERROR;
  }
  // av_new_packet allocates the zeroed padding libavcodec requires after the payload,
  // which the ExoPlayer buffer does not have.
  if (av_new_packet(ctx->packet, size) < 0) return RESULT_ERROR;
  memcpy(ctx->packet->data, input, size);
  ctx->packet->pts = timeUs;
  ctx->packet->dts = AV_NOPTS_VALUE;

  int result = avcodec_send_packet(ctx->codec, ctx->packet);
  if (result == AVERROR(EAGAIN) && !ctx->hasPendingFrame) {
    // Decoder is full: park one frame so it accepts the packet.
    if (avcodec_receive_frame(ctx->codec, ctx->frame) == 0) {
      ctx->hasPendingFrame = true;
    }
    result = avcodec_send_packet(ctx->codec, ctx->packet);
  }
  av_packet_unref(ctx->packet);
  if (result < 0 && result != AVERROR_INVALIDDATA) {
    // Corrupt packets are normal on live streams; drop them without failing playback.
    char buf[AV_ERROR_MAX_STRING_SIZE];
    LOGE("avcodec_send_packet failed: %s", av_make_error_string(buf, sizeof(buf), result));
    return RESULT_ERROR;
  }
  return RESULT_OK;
}

static jint videoGetFrame(JNIEnv* env, jobject, jlong jCtx, jobject outputBuffer,
                          jboolean copyFrame) {
  VideoContext* ctx = reinterpret_cast<VideoContext*>(jCtx);
  if (!ctx) return RESULT_ERROR;

  if (!ctx->hasPendingFrame) {
    int result = avcodec_receive_frame(ctx->codec, ctx->frame);
    if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) return RESULT_NO_FRAME;
    if (result == AVERROR_INVALIDDATA) return RESULT_NO_FRAME;
    if (result < 0) {
      char buf[AV_ERROR_MAX_STRING_SIZE];
      LOGE("avcodec_receive_frame failed: %s", av_make_error_string(buf, sizeof(buf), result));
      return RESULT_ERROR;
    }
  }
  ctx->hasPendingFrame = false;
  AVFrame* frame = ctx->frame;
  jint status = RESULT_OK;

  jclass bufferClass = env->GetObjectClass(outputBuffer);
  jfieldID timeUsField = env->GetFieldID(bufferClass, "timeUs", "J");
  int64_t pts = frame->pts != AV_NOPTS_VALUE ? frame->pts : frame->best_effort_timestamp;
  if (timeUsField && pts != AV_NOPTS_VALUE) {
    env->SetLongField(outputBuffer, timeUsField, (jlong)pts);
  }

  if (copyFrame) {
    const int width = frame->width;
    const int height = frame->height;
    const int yStride = width;
    const int uvStride = (width + 1) / 2;
    const int uvHeight = (height + 1) / 2;
    jmethodID init = env->GetMethodID(bufferClass, "initForYuvFrame", "(IIIII)Z");
    jfieldID dataField = env->GetFieldID(bufferClass, "data", "Ljava/nio/ByteBuffer;");
    jboolean ok = init && dataField &&
                  env->CallBooleanMethod(outputBuffer, init, width, height, yStride,
                                         uvStride, colorspaceOf(frame));
    jobject dataBuffer = ok && !env->ExceptionCheck()
                             ? env->GetObjectField(outputBuffer, dataField)
                             : nullptr;
    uint8_t* dst = dataBuffer ? (uint8_t*)env->GetDirectBufferAddress(dataBuffer) : nullptr;
    if (!dst) {
      env->ExceptionClear();
      status = RESULT_ERROR;
    } else {
      uint8_t* planes[4] = {dst, dst + yStride * height,
                            dst + yStride * height + uvStride * uvHeight, nullptr};
      int strides[4] = {yStride, uvStride, uvStride, 0};
      if (frame->format == AV_PIX_FMT_YUV420P || frame->format == AV_PIX_FMT_YUVJ420P) {
        av_image_copy_plane(planes[0], strides[0], frame->data[0], frame->linesize[0],
                            width, height);
        av_image_copy_plane(planes[1], strides[1], frame->data[1], frame->linesize[1],
                            uvStride, uvHeight);
        av_image_copy_plane(planes[2], strides[2], frame->data[2], frame->linesize[2],
                            uvStride, uvHeight);
      } else {
        // 10-bit, 4:2:2, ...: let swscale bring it down to 8-bit 4:2:0.
        ctx->toI420 = sws_getCachedContext(
            ctx->toI420, width, height, (AVPixelFormat)frame->format, width, height,
            AV_PIX_FMT_YUV420P, SWS_BILINEAR, nullptr, nullptr, nullptr);
        if (!ctx->toI420 ||
            sws_scale(ctx->toI420, frame->data, frame->linesize, 0, height, planes,
                      strides) < 0) {
          LOGE("Pixel format conversion failed (format %d).", frame->format);
          status = RESULT_ERROR;
        }
      }
    }
  }
  av_frame_unref(frame);
  env->DeleteLocalRef(bufferClass);
  return status;
}

static jint videoRenderFrame(JNIEnv* env, jobject, jlong jCtx, jobject surface,
                             jobject yuvData, jint width, jint height,
                             jint yStride, jint uvStride, jint colorspace) {
  VideoContext* ctx = reinterpret_cast<VideoContext*>(jCtx);
  uint8_t* yuv = (uint8_t*)env->GetDirectBufferAddress(yuvData);
  if (!ctx || !yuv || width <= 0 || height <= 0) return RESULT_ERROR;

  ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
  if (!window) {
    LOGE("ANativeWindow_fromSurface failed.");
    return RESULT_ERROR;
  }
  jint status = RESULT_ERROR;
  const int uvHeight = (height + 1) / 2;
  if (ANativeWindow_setBuffersGeometry(window, width, height,
                                       WINDOW_FORMAT_RGBA_8888) == 0) {
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, nullptr) == 0) {
      if (buffer.format == WINDOW_FORMAT_RGBA_8888 ||
          buffer.format == WINDOW_FORMAT_RGBX_8888) {
        ctx->toRgba = sws_getCachedContext(ctx->toRgba, width, height, AV_PIX_FMT_YUV420P,
                                           width, height, AV_PIX_FMT_RGBA, SWS_BILINEAR,
                                           nullptr, nullptr, nullptr);
        if (ctx->toRgba) {
          int swsColorspace = SWS_CS_ITU601;
          switch (colorspace & 0xFF) {
            case COLORSPACE_BT709: swsColorspace = SWS_CS_ITU709; break;
            case COLORSPACE_BT2020: swsColorspace = SWS_CS_BT2020; break;
          }
          const int fullRange = (colorspace & COLORSPACE_FULL_RANGE_FLAG) ? 1 : 0;
          sws_setColorspaceDetails(ctx->toRgba, sws_getCoefficients(swsColorspace),
                                   fullRange, sws_getCoefficients(SWS_CS_DEFAULT), 1, 0,
                                   1 << 16, 1 << 16);
          const uint8_t* src[4] = {yuv, yuv + yStride * height,
                                   yuv + yStride * height + uvStride * uvHeight, nullptr};
          const int srcStride[4] = {yStride, uvStride, uvStride, 0};
          uint8_t* dst[4] = {(uint8_t*)buffer.bits, nullptr, nullptr, nullptr};
          const int dstStride[4] = {buffer.stride * 4, 0, 0, 0};
          if (sws_scale(ctx->toRgba, src, srcStride, 0, height, dst, dstStride) > 0) {
            status = RESULT_OK;
          }
        }
      } else {
        LOGE("Unexpected window format %d.", buffer.format);
      }
      ANativeWindow_unlockAndPost(window);
    } else {
      LOGE("ANativeWindow_lock failed.");
    }
  }
  ANativeWindow_release(window);
  return status;
}

static void videoFlush(JNIEnv*, jobject, jlong jCtx) {
  VideoContext* ctx = reinterpret_cast<VideoContext*>(jCtx);
  if (!ctx) return;
  avcodec_flush_buffers(ctx->codec);
  av_frame_unref(ctx->frame);
  ctx->hasPendingFrame = false;
}

static void videoRelease(JNIEnv*, jobject, jlong jCtx) {
  releaseVideoContext(reinterpret_cast<VideoContext*>(jCtx));
}

bool registerVideoNatives(JNIEnv* env) {
  jclass clazz = env->FindClass("dev/section/streamly/ffmpeg/FfmpegVideoDecoder");
  if (!clazz) {
    LOGE("FindClass failed for FfmpegVideoDecoder");
    return false;
  }
  static const JNINativeMethod kMethods[] = {
      {"videoInitialize", "(Ljava/lang/String;[BI)J", (void*)videoInitialize},
      {"videoDecode", "(JLjava/nio/ByteBuffer;IJ)I", (void*)videoDecode},
      {"videoGetFrame", "(JLandroidx/media3/decoder/VideoDecoderOutputBuffer;Z)I",
       (void*)videoGetFrame},
      {"videoRenderFrame", "(JLandroid/view/Surface;Ljava/nio/ByteBuffer;IIIII)I",
       (void*)videoRenderFrame},
      {"videoFlush", "(J)V", (void*)videoFlush},
      {"videoRelease", "(J)V", (void*)videoRelease},
  };
  return env->RegisterNatives(clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) == 0;
}
