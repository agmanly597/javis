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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.javis.assistant.navigation.JavisNavGraph
import com.javis.assistant.service.JavisActivationBus
import com.javis.assistant.service.JavisBackgroundService
import com.javis.assistant.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* proceed regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestAllPermissions()
        JavisBackgroundService.start(this)

        // If launched from tile/notification with activation intent, signal the ViewModel
        if (intent?.action == JavisBackgroundService.ACTION_ACTIVATE) {
            JavisActivationBus.emitActivation()
        }

        setContent {
            JavisTheme {
                var showNotifDialog by remember {
                    mutableStateOf(!isNotificationListenerEnabled())
                }

                JavisNavGraph()

                if (showNotifDialog) {
                    NotifAccessDialog(
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Called when app is already open and tile/notification is tapped again
        if (intent.action == JavisBackgroundService.ACTION_ACTIVATE) {
            JavisActivationBus.emitActivation()
        }
    }

    private fun requestAllPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
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
        permLauncher.launch(permissions.toTypedArray())
    }

    private fun isNotificationListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}

@Composable
private fun NotifAccessDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JavisSurface,
        icon = {
            Icon(
                Icons.Default.MicNone, null,
                tint = JavisBlue,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "One Quick Step",
                color = JavisOnSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                "To read and reply to WhatsApp messages, JAVIS needs Notification Access.\n\n" +
                "Tap Grant → find JAVIS in the list → toggle it on.",
                color = JavisOnSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = JavisBlue)
            ) { Text("Grant Access", color = JavisBackground) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later", color = JavisOnSurfaceVariant) }
        }
    )
}
