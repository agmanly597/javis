package com.javis.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.javis.assistant.navigation.Routes
import com.javis.assistant.ui.theme.*

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.CHAT, Icons.Default.Chat, "Chat"),
    BottomNavItem(Routes.NOTIFICATIONS, Icons.Default.Notifications, "Alerts"),
    BottomNavItem(Routes.MEMORY, Icons.Default.Psychology, "Memory"),
    BottomNavItem(Routes.SETTINGS, Icons.Default.Settings, "Settings")
)

@Composable
fun JavisBottomBar(
    navController: NavHostController,
    currentRoute: String
) {
    NavigationBar(
        containerColor = JavisSurface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .background(JavisSurface)
            .navigationBarsPadding()
    ) {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.CHAT) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = JavisBlue,
                    selectedTextColor = JavisBlue,
                    unselectedIconColor = JavisOnSurfaceVariant,
                    unselectedTextColor = JavisOnSurfaceVariant,
                    indicatorColor = JavisBlue.copy(alpha = 0.1f)
                )
            )
        }
    }
}
