#!/usr/bin/env bash
#
# Builds the minimal, LGPL-only FFmpeg static libraries that :ffmpeg links against.
#
#   ffmpeg/scripts/build_ffmpeg.sh [abi ...]      (default: arm64-v8a armeabi-v7a x86_64)
#
# Environment:
#   ANDROID_NDK_HOME  NDK to use (default: newest under $ANDROID_HOME/ndk or ~/Android/Sdk/ndk)
#   MIN_API           Android API level to target (default: 23, must not exceed the app's minSdk)
#   FFMPEG_TAG        FFmpeg release tag to build (default: n7.1.5)
#
# Output (git-ignored): ffmpeg/src/main/cpp/ffmpeg-build/<abi>/{include,lib}
set -euo pipefail

FFMPEG_TAG="${FFMPEG_TAG:-n7.1.5}"
MIN_API="${MIN_API:-23}"

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CPP_DIR="${MODULE_DIR}/src/main/cpp"
SRC_DIR="${CPP_DIR}/ffmpeg-src"
OUT_DIR="${CPP_DIR}/ffmpeg-build"
OBJ_DIR="${MODULE_DIR}/build/ffmpeg-obj"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  ANDROID_NDK_HOME="$(ls -d "${SDK}"/ndk/*/ 2>/dev/null | sort -V | tail -n1 || true)"
  ANDROID_NDK_HOME="${ANDROID_NDK_HOME%/}"
fi
[[ -d "${ANDROID_NDK_HOME:-}" ]] || { echo "NDK not found; set ANDROID_NDK_HOME" >&2; exit 1; }

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64) HOST=linux-x86_64 ;;
  Darwin-*)     HOST=darwin-x86_64 ;;
  *) echo "Unsupported host $(uname -sm)" >&2; exit 1 ;;
esac
TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${HOST}/bin"
[[ -d "${TOOLCHAIN}" ]] || { echo "Toolchain not found at ${TOOLCHAIN}" >&2; exit 1; }

ABIS=("$@")
[[ ${#ABIS[@]} -gt 0 ]] || ABIS=(arm64-v8a armeabi-v7a x86_64)

if [[ ! -d "${SRC_DIR}/.git" ]]; then
  git clone --depth 1 --branch "${FFMPEG_TAG}" https://github.com/FFmpeg/FFmpeg.git "${SRC_DIR}"
fi

# Decoders only, no demuxers/encoders/protocols. LGPL: no --enable-gpl / --enable-nonfree.
DECODERS=(mpeg2video h264 hevc mp1 mp2 mp3 aac ac3 eac3)
COMMON=(
  --target-os=android --enable-cross-compile
  --enable-static --disable-shared --enable-pic
  --disable-everything --disable-autodetect
  --disable-doc --disable-programs --disable-network
  --disable-avdevice --disable-avformat --disable-avfilter --disable-postproc
  --disable-symver --disable-debug
  --enable-avcodec --enable-swscale --enable-swresample
)
for d in "${DECODERS[@]}"; do COMMON+=("--enable-decoder=${d}"); done

build_abi() {
  local abi="$1" arch cpu triple extra=()
  case "${abi}" in
    arm64-v8a)   arch=aarch64; cpu=armv8-a;  triple="aarch64-linux-android${MIN_API}" ;;
    armeabi-v7a) arch=arm;     cpu=armv7-a;  triple="armv7a-linux-androideabi${MIN_API}"
                 extra=(--extra-cflags="-march=armv7-a -mfloat-abi=softfp" --extra-ldflags="-Wl,--fix-cortex-a8") ;;
    x86_64)      arch=x86_64;  cpu=x86-64;   triple="x86_64-linux-android${MIN_API}"; extra=(--disable-asm) ;;
    x86)         arch=x86;     cpu=i686;     triple="i686-linux-android${MIN_API}";   extra=(--disable-asm) ;;
    *) echo "Unknown ABI ${abi}" >&2; exit 1 ;;
  esac
  echo "=== FFmpeg ${FFMPEG_TAG} for ${abi} (API ${MIN_API}) ==="
  rm -rf "${OBJ_DIR}/${abi}" "${OUT_DIR}/${abi}"
  mkdir -p "${OBJ_DIR}/${abi}"
  (
    cd "${OBJ_DIR}/${abi}"
    "${SRC_DIR}/configure" \
      --prefix="${OUT_DIR}/${abi}" \
      --arch="${arch}" --cpu="${cpu}" \
      --cc="${TOOLCHAIN}/${triple}-clang" \
      --cxx="${TOOLCHAIN}/${triple}-clang++" \
      --nm="${TOOLCHAIN}/llvm-nm" --ar="${TOOLCHAIN}/llvm-ar" \
      --ranlib="${TOOLCHAIN}/llvm-ranlib" --strip="${TOOLCHAIN}/llvm-strip" \
      "${COMMON[@]}" "${extra[@]}"
    make -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"
    make install
  )
}

for abi in "${ABIS[@]}"; do build_abi "${abi}"; done
echo "Done. Libraries are in ${OUT_DIR}"
