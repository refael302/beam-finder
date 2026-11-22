package io.github.refael302.beamfinder.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.refael302.beamfinder.ui.about.AboutScreen
import io.github.refael302.beamfinder.ui.about.HelpScreen
import io.github.refael302.beamfinder.ui.detect.DetectScreen
import io.github.refael302.beamfinder.ui.menu.MenuScreen
import io.github.refael302.beamfinder.ui.settings.SettingsScreen

object Routes {
    const val MENU = "menu"
    const val DETECT = "detect"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val HELP = "help"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            MenuScreen(
                onStart = { navController.navigate(Routes.DETECT) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.DETECT) {
            DetectScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onHelp = { navController.navigate(Routes.HELP) },
            )
        }
        composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
    }
}
