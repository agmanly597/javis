package com.javis.assistant.ui.screens

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import com.javis.assistant.ui.components.*
import com.javis.assistant.ui.navigation.Screen
import com.javis.assistant.ui.theme.*
import com.javis.assistant.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        bottomBar = { JavisBottomBar(navController) },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(DarkBg, SurfaceDark)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.radialGradient(listOf(CyanAccent.copy(0.3f), BlueAccent.copy(0.1f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, null, tint = CyanAccent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("JAVIS", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CyanAccent)
                        Text(
                            if (uiState.voiceState.isListening) "Listening…"
                            else if (uiState.isLoading) "Thinking…"
                            else "Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.voiceState.isListening) CyanAccent else TextSecondary
                        )
                    }
                }
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                    Icon(Icons.Default.Settings, null, tint = TextSecondary)
                }
            }

            HorizontalDivider(color = CyanAccent.copy(alpha = 0.08f))

            // ── Conversation ────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.messages) { msg ->
                    MessageBubble(msg)
                }
                if (uiState.isLoading) {
                    item {
                        Row(Modifier.padding(horizontal = 12.dp)) {
                            TypingIndicator()
                        }
                    }
                }
            }

            // ── Confirmation prompt ─────────────────────────────────────
            AnimatedVisibility(uiState.pendingCommand != null) {
                uiState.pendingCommand?.let { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceMid)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.confirmPendingCommand(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Yes, do it", color = DarkBg, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.confirmPendingCommand(false) },
                            border = BorderStroke(1.dp, TextSecondary.copy(0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                }
            }

            // ── Partial text ────────────────────────────────────────────
            AnimatedVisibility(uiState.voiceState.partialText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceMid.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = uiState.voiceState.partialText,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent.copy(alpha = 0.8f)
                    )
                }
            }

            // ── Error snackbar ───────────────────────────────────────────
            uiState.error?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(8.dp),
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("OK", color = CyanAccent)
                        }
                    },
                    containerColor = SurfaceMid
                ) {
                    Text(err, color = TextPrimary)
                }
            }

            // ── Input bar ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::updateInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask JAVIS anything…", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage(uiState.inputText) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent.copy(0.5f),
                        unfocusedBorderColor = TextSecondary.copy(0.2f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    trailingIcon = if (uiState.inputText.isNotBlank()) {
                        {
                            IconButton(onClick = { viewModel.sendMessage(uiState.inputText) }) {
                                Icon(Icons.Default.Send, null, tint = CyanAccent)
                            }
                        }
                    } else null
                )

                MicButton(
                    isListening = uiState.voiceState.isListening,
                    isProcessing = uiState.isLoading,
                    onPress = {
                        if (micPermission.status.isGranted) {
                            viewModel.startListening()
                        } else {
                            micPermission.launchPermissionRequest()
                        }
                    },
                    onRelease = { viewModel.stopListening() }
                )
            }
        }
    }
}
