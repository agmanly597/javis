package com.javis.assistant.ui.screens.memory

import androidx.compose.animation.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.javis.assistant.domain.model.MemoryCategory
import com.javis.assistant.domain.model.UserMemory
import com.javis.assistant.navigation.Routes
import com.javis.assistant.ui.components.JavisBottomBar
import com.javis.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MemoryScreen(
    navController: NavHostController,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

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
                    Text("Memory", color = JavisOnSurface, style = MaterialTheme.typography.titleLarge)
                }
                if (memories.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = JavisError)
                    }
                }
            }
        },
        bottomBar = { JavisBottomBar(navController = navController, currentRoute = Routes.MEMORY) }
    ) { padding ->
        if (memories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Psychology, contentDescription = null,
                        tint = JavisOnSurfaceVariant, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No memories yet", color = JavisOnSurface, style = MaterialTheme.typography.titleMedium)
                    Text("JAVIS will remember things as you chat",
                        color = JavisOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            val grouped = memories.groupBy { it.category }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (category, items) ->
                    item {
                        Text(
                            text = category.displayName,
                            color = JavisBlue,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(items, key = { it.id }) { memory ->
                        MemoryItem(memory = memory, onDelete = { viewModel.deleteMemory(memory) })
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = JavisSurface,
            title = { Text("Clear Memory", color = JavisOnSurface) },
            text = { Text("This will permanently delete all stored memories.", color = JavisOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text("Clear", color = JavisError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = JavisOnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun MemoryItem(memory: UserMemory, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JavisSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, JavisDivider, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = memory.category.icon,
                contentDescription = null,
                tint = JavisBlue.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(memory.key.replace("_", " ").replaceFirstChar { it.uppercase() },
                    color = JavisOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text(memory.value, color = JavisOnSurface, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete",
                    tint = JavisOnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

private val MemoryCategory.displayName get() = when (this) {
    MemoryCategory.PERSONAL -> "PERSONAL"
    MemoryCategory.PREFERENCE -> "PREFERENCES"
    MemoryCategory.APP_USAGE -> "APP USAGE"
    MemoryCategory.COMMAND -> "COMMANDS"
    MemoryCategory.GENERAL -> "GENERAL"
}

private val MemoryCategory.icon get() = when (this) {
    MemoryCategory.PERSONAL -> Icons.Default.Person
    MemoryCategory.PREFERENCE -> Icons.Default.Favorite
    MemoryCategory.APP_USAGE -> Icons.Default.Apps
    MemoryCategory.COMMAND -> Icons.Default.Terminal
    MemoryCategory.GENERAL -> Icons.Default.Lightbulb
}
