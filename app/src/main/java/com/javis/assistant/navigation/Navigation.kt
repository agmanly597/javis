package com.javis.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.javis.assistant.ui.screens.chat.ChatScreen
import com.javis.assistant.ui.screens.memory.MemoryScreen
import com.javis.assistant.ui.screens.notifications.NotificationsScreen
import com.javis.assistant.ui.screens.settings.SettingsScreen

object Routes {
    const val CHAT = "chat"
    const val MEMORY = "memory"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
}

@Composable
fun JavisNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT
    ) {
        composable(Routes.CHAT) {
            ChatScreen(navController = navController)
        }
        composable(Routes.MEMORY) {
            MemoryScreen(navController = navController)
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController)
        }
    }
}
