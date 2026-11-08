# Adapted from androidx/media3 libraries/decoder_ffmpeg (Apache-2.0).
cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

project(libffmpegJNI C CXX)
set(CMAKE_CXX_STANDARD 17)

# Produced by scripts/build_ffmpeg.sh.
set(ffmpeg_root "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg-build/${ANDROID_ABI}")

foreach(ffmpeg_lib avcodec swscale swresample avutil)
    add_library(${ffmpeg_lib} STATIC IMPORTED)
    set_target_properties(${ffmpeg_lib} PROPERTIES
            IMPORTED_LOCATION ${ffmpeg_root}/lib/lib${ffmpeg_lib}.a)
endforeach()

add_library(ffmpegJNI SHARED ffmpeg_jni.cc ffmpeg_video_jni.cc)

target_include_directories(ffmpegJNI PRIVATE ${ffmpeg_root}/include)

set_target_properties(ffmpegJNI PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN ON)

find_library(android_log_lib log)

# avcodec depends on swresample/avutil, so they must follow it on the link line.
target_link_libraries(ffmpegJNI PRIVATE
        android
        avcodec swscale swresample avutil
        ${android_log_lib})

# Hide FFmpeg's own symbols and avoid TEXTREL on 32-bit ARM.
target_link_options(ffmpegJNI PRIVATE "-Wl,--exclude-libs,ALL")

# See https://github.com/google/ExoPlayer/issues/9933#issuecomment-1029775358.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()
