package com.javis.assistant.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.javis.assistant.domain.model.AiProvider
import com.javis.assistant.ui.theme.*

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var showGroqKey by remember { mutableStateOf(false) }
    var showDeepSeekKey by remember { mutableStateOf(false) }
    var groqKeyInput by remember(settings.groqApiKey) { mutableStateOf(settings.groqApiKey) }
    var deepSeekKeyInput by remember(settings.deepSeekApiKey) { mutableStateOf(settings.deepSeekApiKey) }
    var userNameInput by remember(settings.userName) { mutableStateOf(settings.userName) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = JavisBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JavisBackground)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JavisOnSurface)
                }
                Text("Settings", color = JavisOnSurface, style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile section
            item {
                SettingsSection(title = "Profile") {
                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        label = { Text("Your Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = javisTextFieldColors(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.setUserName(userNameInput) }) {
                                Icon(Icons.Default.Check, contentDescription = "Save", tint = JavisBlue)
                            }
                        }
                    )
                }
            }

            // AI Provider section
            item {
                SettingsSection(title = "AI Provider") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProvider.entries.forEach { provider ->
                            ProviderCard(
                                provider = provider,
                                isSelected = settings.provider == provider,
                                onClick = { viewModel.setProvider(provider) }
                            )
                        }
                    }
                }
            }

            // API Keys
            item {
                SettingsSection(title = "API Keys") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = groqKeyInput,
                            onValueChange = { groqKeyInput = it },
                            label = { Text("Groq API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = javisTextFieldColors(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { showGroqKey = !showGroqKey }) {
                                        Icon(
                                            if (showGroqKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null, tint = JavisOnSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.setGroqApiKey(groqKeyInput) }) {
                                        Icon(Icons.Default.Check, contentDescription = "Save", tint = JavisBlue)
                                    }
                                }
                            },
                            supportingText = { Text("Required for Groq provider", style = MaterialTheme.typography.labelSmall) }
                        )
                        OutlinedTextField(
                            value = deepSeekKeyInput,
                            onValueChange = { deepSeekKeyInput = it },
                            label = { Text("DeepSeek API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showDeepSeekKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = javisTextFieldColors(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { showDeepSeekKey = !showDeepSeekKey }) {
                                        Icon(
                                            if (showDeepSeekKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null, tint = JavisOnSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.setDeepSeekApiKey(deepSeekKeyInput) }) {
                                        Icon(Icons.Default.Check, contentDescription = "Save", tint = JavisBlue)
                                    }
                                }
                            },
                            supportingText = { Text("Required for DeepSeek provider", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Voice settings
            item {
                SettingsSection(title = "Voice") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Speech Rate: ${"%.1f".format(settings.speechRate)}x",
                            color = JavisOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = settings.speechRate,
                            onValueChange = { viewModel.setSpeechRate(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = JavisBlue,
                                activeTrackColor = JavisBlue,
                                inactiveTrackColor = JavisDivider
                            )
                        )
                        SettingsToggle(
                            label = "Continuous Mode",
                            description = "Automatically restart listening after each response",
                            checked = settings.continuousMode,
                            onCheckedChange = { viewModel.setContinuousMode(it) }
                        )
                        SettingsToggle(
                            label = "Notifications",
                            description = "Allow JAVIS to read notifications aloud",
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                    }
                }
            }

            // Data management
            item {
                SettingsSection(title = "Data") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.clearMemory() },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JavisError.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null,
                                tint = JavisError, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Memory", color = JavisError)
                        }
                        OutlinedButton(
                            onClick = { showClearHistoryDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JavisError.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null,
                                tint = JavisError, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Chat History", color = JavisError)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = JavisSurface,
            title = { Text("Clear Chat History", color = JavisOnSurface) },
            text = { Text("All conversation history will be permanently deleted.", color = JavisOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearChatHistory(); showClearHistoryDialog = false }) {
                    Text("Clear", color = JavisError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = JavisOnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = JavisBlue, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JavisSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, JavisDivider, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun ProviderCard(provider: AiProvider, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) JavisBlue.copy(alpha = 0.1f) else JavisBackground,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isSelected) JavisBlue.copy(alpha = 0.5f) else JavisDivider, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = JavisBlue)
            )
            Spacer(Modifier.width(8.dp))
            Text(provider.displayName, color = JavisOnSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = JavisOnSurface, style = MaterialTheme.typography.bodyMedium)
            Text(description, color = JavisOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JavisBackground,
                checkedTrackColor = JavisBlue
            )
        )
    }
}

@Composable
private fun javisTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JavisBlue.copy(alpha = 0.6f),
    unfocusedBorderColor = JavisDivider,
    cursorColor = JavisBlue,
    focusedTextColor = JavisOnSurface,
    unfocusedTextColor = JavisOnSurface,
    focusedContainerColor = JavisBackground,
    unfocusedContainerColor = JavisBackground,
    focusedLabelColor = JavisBlue,
    unfocusedLabelColor = JavisOnSurfaceVariant,
    focusedSupportingTextColor = JavisOnSurfaceVariant,
    unfocusedSupportingTextColor = JavisOnSurfaceVariant
)
