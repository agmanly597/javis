package com.javis.assistant.ui.screens.chat

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.*
import com.javis.assistant.domain.model.ChatMessage
import com.javis.assistant.domain.model.MessageRole
import com.javis.assistant.navigation.Routes
import com.javis.assistant.ui.components.JavisBottomBar
import com.javis.assistant.ui.theme.*
import com.javis.assistant.voice.VoiceState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavHostController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        containerColor = JavisBackground,
        topBar = { JavisTopBar(navController) },
        bottomBar = {
            Column {
                ChatInputBar(
                    text = textInput,
                    onTextChange = { textInput = it },
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendMessage(textInput)
                            textInput = ""
                        }
                    },
                    isLoading = uiState.isLoading
                )
                JavisBottomBar(navController = navController, currentRoute = Routes.CHAT)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.messages.isEmpty()) {
                WelcomeContent()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatBubble(message = message)
                    }
                    if (uiState.isLoading) {
                        item {
                            ThinkingBubble()
                        }
                    }
                }
            }

            // Floating mic button
            MicButton(
                voiceState = uiState.voiceState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
                onClick = {
                    when (uiState.voiceState) {
                        is VoiceState.Listening -> viewModel.stopListening()
                        is VoiceState.Speaking -> viewModel.stopSpeaking()
                        else -> {
                            if (micPermission.status.isGranted) {
                                viewModel.startListening()
                            } else {
                                micPermission.launchPermissionRequest()
                            }
                        }
                    }
                }
            )

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss", color = JavisBlue)
                        }
                    },
                    containerColor = JavisSurface
                ) {
                    Text(error, color = JavisError, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun JavisTopBar(navController: NavHostController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JavisBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(JavisBlue.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, JavisBlue.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("J", color = JavisBlue, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("JAVIS", color = JavisBlue, style = MaterialTheme.typography.titleMedium)
                Text("AI Assistant", color = JavisOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = JavisOnSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f, label = "scale",
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse)
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .background(JavisBlue.copy(alpha = 0.1f), CircleShape)
                .border(1.5.dp, JavisBlue.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("J", color = JavisBlue, style = MaterialTheme.typography.displayLarge)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Hello, I'm JAVIS",
            color = JavisOnSurface,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your intelligent voice assistant.\nTap the microphone to start talking.",
            color = JavisOnSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip("Open YouTube", Icons.Default.PlayArrow)
            SuggestionChip("Open WhatsApp", Icons.Default.Chat)
        }
    }
}

@Composable
private fun SuggestionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = JavisSurfaceVariant,
        border = BorderStroke(1.dp, JavisDivider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = JavisBlue, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = JavisOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(JavisBlue.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, JavisBlue.copy(alpha = 0.3f), CircleShape)
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Text("J", color = JavisBlue, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) JavisBlue.copy(alpha = 0.2f) else JavisSurface,
                border = BorderStroke(
                    0.5.dp,
                    if (isUser) JavisBlue.copy(alpha = 0.4f) else JavisDivider
                )
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = JavisOnSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = formatTime(message.timestamp),
                color = JavisOnSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
        if (isUser) Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1 by infiniteTransition.animateFloat(0.3f, 1f, label = "d1",
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse))
    val dot2 by infiniteTransition.animateFloat(0.3f, 1f, label = "d2",
        animationSpec = infiniteRepeatable(tween(600, 200, EaseInOut), RepeatMode.Reverse))
    val dot3 by infiniteTransition.animateFloat(0.3f, 1f, label = "d3",
        animationSpec = infiniteRepeatable(tween(600, 400, EaseInOut), RepeatMode.Reverse))

    Row(modifier = Modifier.padding(start = 36.dp)) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
            color = JavisSurface, border = BorderStroke(0.5.dp, JavisDivider)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(dot1, dot2, dot3).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(alpha)
                            .background(JavisBlue, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun MicButton(
    voiceState: VoiceState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_ring")
    val ring by infiniteTransition.animateFloat(0.8f, 1.0f, label = "ring",
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse))

    val isActive = voiceState is VoiceState.Listening || voiceState is VoiceState.Speaking

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(ring)
                    .alpha(0.3f)
                    .background(JavisBlue, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(ring)
                    .alpha(0.15f)
                    .background(JavisBlue, CircleShape)
            )
        }
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            containerColor = when (voiceState) {
                is VoiceState.Listening -> JavisBlue
                is VoiceState.Speaking -> JavisSuccess
                is VoiceState.Processing -> JavisWarning
                else -> JavisSurface
            },
            contentColor = when (voiceState) {
                is VoiceState.Listening -> JavisBackground
                else -> JavisBlue
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp, pressedElevation = 2.dp
            )
        ) {
            Icon(
                imageVector = when (voiceState) {
                    is VoiceState.Listening -> Icons.Default.MicOff
                    is VoiceState.Speaking -> Icons.Default.VolumeOff
                    is VoiceState.Processing -> Icons.Default.HourglassEmpty
                    else -> Icons.Default.Mic
                },
                contentDescription = "Microphone",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        color = JavisSurface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...", color = JavisOnSurfaceVariant.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JavisBlue.copy(alpha = 0.6f),
                    unfocusedBorderColor = JavisDivider,
                    cursorColor = JavisBlue,
                    focusedTextColor = JavisOnSurface,
                    unfocusedTextColor = JavisOnSurface,
                    focusedContainerColor = JavisBackground,
                    unfocusedContainerColor = JavisBackground
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (text.isNotBlank()) JavisBlue.copy(alpha = 0.15f) else Color.Transparent,
                        CircleShape
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = JavisBlue,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) JavisBlue else JavisOnSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
