package dev.section.streamly.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.section.streamly.about.AboutScreen
import dev.section.streamly.details.DetailsScreen
import dev.section.streamly.favorites.FavoritesScreen
import dev.section.streamly.home.HomeScreen
import dev.section.streamly.livetv.LiveTvScreen
import dev.section.streamly.player.PlayerScreen
import dev.section.streamly.search.SearchScreen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import streamlykmp.shared.generated.resources.Res
import streamlykmp.shared.generated.resources.ic_favorite
import streamlykmp.shared.generated.resources.ic_favorite_filled
import streamlykmp.shared.generated.resources.ic_home
import streamlykmp.shared.generated.resources.ic_home_filled
import streamlykmp.shared.generated.resources.ic_live_tv
import streamlykmp.shared.generated.resources.ic_live_tv_filled
import streamlykmp.shared.generated.resources.ic_search
import streamlykmp.shared.generated.resources.ic_search_filled

private class TopLevelDestination(
    val route: Route,
    val label: String,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Route.Home, "Home", Res.drawable.ic_home, Res.drawable.ic_home_filled),
    TopLevelDestination(
        Route.Search,
        "Search",
        Res.drawable.ic_search,
        Res.drawable.ic_search_filled
    ),
    TopLevelDestination(
        Route.LiveTv,
        "Live TV",
        Res.drawable.ic_live_tv,
        Res.drawable.ic_live_tv_filled
    ),
    TopLevelDestination(
        Route.Favorites,
        "Favorites",
        Res.drawable.ic_favorite,
        Res.drawable.ic_favorite_filled
    ),
)

/** The label next to the icon already names the tab, so the icon itself is decorative. */
@Composable
private fun NavIcon(destination: TopLevelDestination, selected: Boolean) {
    Icon(
        painter = painterResource(if (selected) destination.selectedIcon else destination.icon),
        contentDescription = null,
    )
}

/** Width from which the navigation rail replaces the bottom bar (TV, tablets). */
private val RailMinWidth = 840.dp

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selected = topLevelDestinations.firstOrNull { top ->
        destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true
    }
    val showNavigation = selected != null

    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val useRail = widthDp >= RailMinWidth

    Scaffold(
        bottomBar = {
            if (showNavigation && !useRail) {
                NavigationBar {
                    topLevelDestinations.forEach { top ->
                        NavigationBarItem(
                            selected = top == selected,
                            onClick = { navController.navigateTopLevel(top.route) },
                            icon = { NavIcon(top, top == selected) },
                            label = { Text(top.label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showNavigation && useRail) {
                NavigationRail {
                    topLevelDestinations.forEach { top ->
                        NavigationRailItem(
                            selected = top == selected,
                            onClick = { navController.navigateTopLevel(top.route) },
                            icon = { NavIcon(top, top == selected) },
                            label = { Text(top.label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = Route.Home,
                modifier = Modifier.weight(1f),
            ) {
                composable<Route.Home> {
                    HomeScreen(
                        onOpenDetails = { id, isMovie ->
                            navController.navigate(Route.Details(id, isMovie))
                        },
                        onOpenAbout = { navController.navigate(Route.About) },
                    )
                }
                composable<Route.About> { AboutScreen(onBack = { navController.popBackStack() }) }
                composable<Route.Search> { SearchScreen() }
                composable<Route.LiveTv> {
                    LiveTvScreen(onOpenPlayer = { navController.navigate(Route.Player(it)) })
                }
                composable<Route.Favorites> { FavoritesScreen() }
                composable<Route.Details> { DetailsScreen() }
                composable<Route.Player> { entry ->
                    val route = entry.toRoute<Route.Player>()
                    PlayerScreen(
                        channelId = route.channelId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Route) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
