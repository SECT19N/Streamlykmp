plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "dev.section.streamly.ffmpeg"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = libs.versions.android.ndk.get()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Only wire up the native build once FFmpeg has been built (scripts/build_ffmpeg.sh), so a
    // fresh clone still syncs and builds; the renderers then report FFmpeg as unavailable and
    // playback falls back to MediaCodec only.
    val ffmpegBuilt = file("src/main/cpp/ffmpeg-build").isDirectory
    if (ffmpegBuilt) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = libs.versions.android.cmake.get()
            }
        }
    } else {
        logger.warn("FFmpeg libraries not found; run ffmpeg/scripts/build_ffmpeg.sh to enable FFmpeg decoding.")
    }
}

dependencies {
    api(libs.media3.exoplayer)
    api(libs.media3.decoder)
    implementation(libs.androidx.annotation)
    compileOnly(libs.checker.qual)

    androidTestImplementation(libs.androidx.testRunner)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.junit)
}
