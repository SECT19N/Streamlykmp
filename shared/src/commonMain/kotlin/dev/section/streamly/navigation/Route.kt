package dev.section.streamly.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object Home : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object LiveTv : Route

    @Serializable
    data object Favorites : Route

    @Serializable
    data class Details(val id: Int, val isMovie: Boolean) : Route

    @Serializable
    data class Player(val channelId: String) : Route
}
