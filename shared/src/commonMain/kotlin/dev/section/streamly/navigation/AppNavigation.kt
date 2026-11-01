package dev.section.streamly.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.section.streamly.details.DetailsScreen
import dev.section.streamly.favorites.FavoritesScreen
import dev.section.streamly.home.HomeScreen
import dev.section.streamly.livetv.LiveTvScreen
import dev.section.streamly.player.PlayerScreen
import dev.section.streamly.search.SearchScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Route.Home) {
        composable<Route.Home> { HomeScreen() }
        composable<Route.Search> { SearchScreen() }
        composable<Route.LiveTv> { LiveTvScreen() }
        composable<Route.Favorites> { FavoritesScreen() }
        composable<Route.Details> { DetailsScreen() }
        composable<Route.Player> { PlayerScreen() }
    }
}
