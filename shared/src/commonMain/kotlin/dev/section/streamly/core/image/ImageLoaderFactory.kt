package dev.section.streamly.core.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path

private const val MemoryCachePercent = 0.25
private const val DiskCacheBytes = 100L * 1024 * 1024

/** Where the on-disk image cache lives; platform-specific because only the platform knows its cache dir. */
internal expect fun PlatformContext.imageCacheDirectory(): Path

/**
 * One app-wide loader with explicit cache sizes. Posters and cast photos already seen are served from
 * disk, which is what keeps Favorites and Home looking right when the device is offline.
 */
fun createImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MemoryCachePercent).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.imageCacheDirectory())
                .maxSizeBytes(DiskCacheBytes)
                .build()
        }
        .build()
