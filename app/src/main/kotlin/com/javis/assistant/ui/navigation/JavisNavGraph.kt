package com.javis.assistant.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.javis.assistant.ui.screens.*

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Memory : Screen("memory")
    object Notifications : Screen("notifications")
    object Settings : Screen("settings")
    object Permissions : Screen("permissions")
}

@Composable
fun JavisNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 }
        },
        exitTransition = {
            fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 8 }
        },
        popEnterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 8 }
        },
        popExitTransition = {
            fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 }
        }
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(navController = navController)
        }
        composable(Screen.Memory.route) {
            MemoryScreen(navController = navController)
        }
        composable(Screen.Notifications.route) {
            NotificationsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(navController = navController)
        }
    }
}
