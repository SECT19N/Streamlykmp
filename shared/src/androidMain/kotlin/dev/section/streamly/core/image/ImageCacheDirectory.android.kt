package dev.section.streamly.core.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

internal actual fun PlatformContext.imageCacheDirectory(): Path =
    cacheDir.resolve("image_cache").toOkioPath()
