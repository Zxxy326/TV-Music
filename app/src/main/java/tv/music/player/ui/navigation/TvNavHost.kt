package tv.music.player.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.music.player.core.lx.LxJsEngine
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.settings.SettingsRepository
import tv.music.player.ui.home.HomeScreen
import tv.music.player.ui.player.PlayerScreen
import tv.music.player.ui.search.SearchScreen
import tv.music.player.ui.settings.SettingsGroupScreen
import tv.music.player.ui.settings.SettingsScreen

@Composable
fun TvNavHost(
    navController: NavHostController,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    playback: PlaybackController,
    onlineSearch: OnlineSearchApi,
    lxEngine: LxJsEngine,
) {
    NavHost(
        navController = navController,
        startDestination = TvRoute.Home.route,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(TvRoute.Home.route) {
            HomeScreen(playback = playback, navController = navController)
        }
        composable(TvRoute.Search.route) {
            SearchScreen(
                playback = playback,
                onlineSearch = onlineSearch,
                navController = navController,
            )
        }
        composable(TvRoute.Player.route) {
            PlayerScreen(
                playback = playback,
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(TvRoute.Settings.route) {
            SettingsScreen(settings = settings, navController = navController)
        }
        composable(
            route = TvRoute.SettingsGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { entry ->
            SettingsGroupScreen(
                groupId = entry.arguments?.getString("groupId").orEmpty(),
                settings = settings,
                settingsRepository = settingsRepository,
                lxEngine = lxEngine,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
