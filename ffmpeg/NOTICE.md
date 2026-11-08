# Third-party code in this module

- **FFmpeg** (https://ffmpeg.org), built by `scripts/build_ffmpeg.sh` from release `n7.1.5`, LGPL
  v2.1+
  configuration (decoders only; no GPL or non-free components). It is statically linked into
  `libffmpegJNI.so`; the build script and the exact configuration are in this repository so the
  library can be rebuilt or relinked against a modified FFmpeg.
  Source: https://github.com/FFmpeg/FFmpeg
- **androidx.media3 FFmpeg extension** (Apache License 2.0, Copyright The Android Open Source
  Project),
  release 1.11.1: `FfmpegLibrary`, `FfmpegAudioDecoder`, `FfmpegAudioRenderer`,
  `FfmpegDecoderException`
  and `src/main/cpp/ffmpeg_jni.cc` are adapted from it (package renamed, Layer I/II decoder mapping
  fixed, MPEG-2 video added). `FfmpegVideoDecoder`, `FfmpegVideoRenderer` and
  `ffmpeg_video_jni.cc` are original to this project.
- Test clips in `src/androidTest/assets/video` are synthetic (ffmpeg `testsrc2` + sine); the MP2
  fixtures
  are short cuts of the app's live channels.
