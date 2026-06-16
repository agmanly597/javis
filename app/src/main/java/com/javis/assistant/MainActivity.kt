package com.javis.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.javis.assistant.navigation.JavisNavGraph
import com.javis.assistant.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val runtimePermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted/denied — app proceeds either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request all runtime permissions immediately
        permissionLauncher.launch(runtimePermissions.toTypedArray())

        setContent {
            JavisTheme {
                val needsNotifAccess = !isNotificationListenerEnabled()
                var showNotifDialog by remember { mutableStateOf(needsNotifAccess) }

                JavisNavGraph()

                // Ask for notification listener access (can't request programmatically)
                if (showNotifDialog) {
                    NotificationAccessDialog(
                        onGrant = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            showNotifDialog = false
                        },
                        onDismiss = { showNotifDialog = false }
                    )
                }
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }
}

@Composable
private fun NotificationAccessDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JavisSurface,
        icon = {
            Icon(Icons.Default.MicNone, contentDescription = null, tint = JavisBlue, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                "One More Step",
                color = JavisOnSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                "To read and reply to WhatsApp messages, JAVIS needs Notification Access.\n\nTap Grant, find JAVIS in the list, and toggle it on.",
                color = JavisOnSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = JavisBlue)
            ) {
                Text("Grant Access", color = JavisBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = JavisOnSurfaceVariant)
            }
        }
    )
}
