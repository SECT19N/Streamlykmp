package dev.section.streamly.core.image

import coil3.PlatformContext
import okio.FileSystem
import okio.Path

internal actual fun PlatformContext.imageCacheDirectory(): Path =
    FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache"
