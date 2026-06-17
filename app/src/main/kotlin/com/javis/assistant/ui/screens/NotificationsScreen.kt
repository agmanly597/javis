package com.javis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javis.assistant.data.model.NotificationItem
import com.javis.assistant.notifications.JavisNotificationListenerService
import com.javis.assistant.ui.components.JavisBottomBar
import com.javis.assistant.ui.theme.*
import com.javis.assistant.ui.viewmodel.NotificationsViewModel
import java.text.SimpleDateFormat
import java.util.*

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isServiceEnabled = JavisNotificationListenerService.isEnabled()

    Scaffold(
        bottomBar = { JavisBottomBar(navController) },
        containerColor = DarkBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Notifications", style = MaterialTheme.typography.headlineMedium, color = CyanAccent)
                Row {
                    TextButton(onClick = viewModel::markAllRead) {
                        Text("Read All", color = TextSecondary)
                    }
                    TextButton(onClick = viewModel::clearAll) {
                        Text("Clear", color = ErrorRed)
                    }
                }
            }

            if (!isServiceEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "Notification access needed",
                            fontWeight = FontWeight.SemiBold,
                            color = CyanAccent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "To read notifications from all apps, enable Notification Access for JAVIS in Settings.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsNone, null,
                            tint = TextSecondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No notifications", color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(notifications) { item ->
                        NotificationCard(item = item, onDismiss = { viewModel.dismiss(item.key) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(item: NotificationItem, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (!item.isRead) SurfaceDark else SurfaceDark.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!item.isRead) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CyanAccent)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent.copy(0.8f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    timeFormat.format(Date(item.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(0.5f)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary,
                fontWeight = FontWeight.Medium)
            if (item.text.isNotBlank()) {
                Text(item.text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = TextSecondary.copy(0.4f), modifier = Modifier.size(14.dp))
        }
    }
}
