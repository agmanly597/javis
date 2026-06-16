package com.javis.assistant.ui.screens.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.javis.assistant.domain.model.NotificationItem
import com.javis.assistant.navigation.Routes
import com.javis.assistant.ui.components.JavisBottomBar
import com.javis.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationsScreen(
    navController: NavHostController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val notifications by viewModel.notifications.collectAsState()
    val hasPermission = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

    Scaffold(
        containerColor = JavisBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JavisBackground)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JavisOnSurface)
                    }
                    Text("Notifications", color = JavisOnSurface, style = MaterialTheme.typography.titleLarge)
                }
                Row {
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = { viewModel.readAllUnread() }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Read All", tint = JavisBlue)
                        }
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = JavisError)
                        }
                    }
                }
            }
        },
        bottomBar = { JavisBottomBar(navController = navController, currentRoute = Routes.NOTIFICATIONS) }
    ) { padding ->
        if (!hasPermission) {
            PermissionBanner(
                modifier = Modifier.padding(padding),
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            )
        } else if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = null,
                        tint = JavisOnSurfaceVariant, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No notifications", color = JavisOnSurface, style = MaterialTheme.typography.titleMedium)
                    Text("Notifications from your apps will appear here",
                        color = JavisOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onRead = { viewModel.markAsRead(notification) },
                        onReadAloud = { viewModel.readAloud(notification) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(modifier: Modifier = Modifier, onGrant: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null,
                tint = JavisBlue, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("Notification Access Required",
                color = JavisOnSurface, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant JAVIS permission to read your notifications so it can summarize and read them aloud.",
                color = JavisOnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = JavisBlue)
            ) {
                Text("Grant Permission", color = JavisBackground)
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    onRead: () -> Unit,
    onReadAloud: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (notification.isRead) JavisSurface else JavisSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.5.dp,
                if (!notification.isRead) JavisBlue.copy(alpha = 0.3f) else JavisDivider,
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (!notification.isRead) JavisBlue else JavisOnSurfaceVariant.copy(alpha = 0.3f),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(notification.appName, color = JavisBlue, style = MaterialTheme.typography.labelSmall)
                    Text(formatRelativeTime(notification.timestamp),
                        color = JavisOnSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall)
                }
                Text(notification.title, color = JavisOnSurface, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (notification.text.isNotBlank()) {
                    Text(notification.text, color = JavisOnSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onReadAloud, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Read Aloud",
                    tint = JavisBlue.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
