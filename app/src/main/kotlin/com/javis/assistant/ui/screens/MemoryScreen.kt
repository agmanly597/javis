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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javis.assistant.data.model.Memory
import com.javis.assistant.data.model.MemoryType
import com.javis.assistant.ui.components.JavisBottomBar
import com.javis.assistant.ui.theme.*
import com.javis.assistant.ui.viewmodel.MemoryViewModel

@Composable
fun MemoryScreen(
    navController: NavController,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = { JavisBottomBar(navController) },
        containerColor = DarkBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Memory", style = MaterialTheme.typography.headlineMedium, color = CyanAccent)
                TextButton(onClick = viewModel::clearAll) {
                    Text("Clear All", color = ErrorRed)
                }
            }

            if (memories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No memories yet", color = TextSecondary)
                        Text("JAVIS learns as you talk", color = TextSecondary.copy(0.6f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(memories) { memory ->
                        MemoryCard(memory = memory, onDelete = { viewModel.deleteMemory(memory) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryCard(memory: Memory, onDelete: () -> Unit) {
    val typeColor = when (memory.type) {
        MemoryType.USER_NAME -> CyanAccent
        MemoryType.PREFERENCE -> BlueAccent
        MemoryType.HABIT -> SuccessGreen
        MemoryType.ROUTINE -> SuccessGreen.copy(0.7f)
        MemoryType.FACT -> TextPrimary.copy(0.7f)
        MemoryType.FAVORITE_APP -> CyanAccent.copy(0.7f)
        MemoryType.COMMAND -> BlueAccent.copy(0.7f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(typeColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                memory.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = typeColor
            )
            Text(memory.value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            if (memory.key != memory.value) {
                Text(memory.key, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, null, tint = TextSecondary.copy(0.5f), modifier = Modifier.size(16.dp))
        }
    }
}
