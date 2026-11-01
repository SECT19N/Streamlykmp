package dev.section.streamly

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform