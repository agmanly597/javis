package com.javis.assistant.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import com.javis.assistant.ui.navigation.Screen
import com.javis.assistant.ui.theme.*

data class PermDef(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val permission: String?,
    val isSystem: Boolean = false,
    val systemAction: String? = null
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(navController: NavController) {
    val context = LocalContext.current

    val permDefs = remember {
        listOf(
            PermDef("Microphone", "Record voice commands", Icons.Default.Mic,
                Manifest.permission.RECORD_AUDIO),
            PermDef("Contacts", "Call people by name", Icons.Default.Contacts,
                Manifest.permission.READ_CONTACTS),
            PermDef("Phone", "Make calls for you", Icons.Default.Phone,
                Manifest.permission.CALL_PHONE),
            PermDef("Storage", "Access your files", Icons.Default.FolderOpen,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            PermDef("Notifications (Android 13+)", "Post notifications", Icons.Default.Notifications,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.POST_NOTIFICATIONS else null
            ),
            PermDef("Notification Listener", "Read notifications from all apps",
                Icons.Default.NotificationsActive,
                null, isSystem = true, systemAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            PermDef("Accessibility Service", "Global shortcut button + app automation",
                Icons.Default.Accessibility,
                null, isSystem = true, systemAction = Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "JAVIS needs access",
            style = MaterialTheme.typography.headlineMedium,
            color = CyanAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            "Grant each permission to unlock all features. You can deny any permission — JAVIS will work with limited capabilities.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(16.dp))

        permDefs.forEach { def ->
            if (def.permission != null) {
                val permState = rememberPermissionState(def.permission)
                PermissionRow(
                    def = def,
                    isGranted = permState.status.isGranted,
                    onRequest = { permState.launchPermissionRequest() }
                )
            } else if (def.isSystem) {
                PermissionRow(
                    def = def,
                    isGranted = false,
                    isSystem = true,
                    onRequest = {
                        def.systemAction?.let { action ->
                            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { navController.navigate(Screen.Chat.route) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
            Text("Continue to JAVIS", color = DarkBg, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PermissionRow(
    def: PermDef,
    isGranted: Boolean,
    isSystem: Boolean = false,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(def.icon, null,
            tint = if (isGranted) SuccessGreen else CyanAccent,
            modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(def.title, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(def.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (isGranted) {
            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
        } else {
            TextButton(onClick = onRequest) {
                Text(if (isSystem) "Open" else "Allow", color = CyanAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
