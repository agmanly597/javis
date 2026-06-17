package com.javis.assistant.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javis.assistant.data.model.Message
import com.javis.assistant.data.model.MessageRole
import com.javis.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun MessageBubble(message: Message, modifier: Modifier = Modifier) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    if (isSystem) return // Don't show system messages in chat

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.linearGradient(listOf(CyanAccent.copy(alpha = 0.2f), BlueAccent.copy(alpha = 0.2f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "JAVIS",
                    tint = CyanAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 18.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .background(
                        if (isUser)
                            Brush.linearGradient(listOf(BlueAccent.copy(0.8f), CyanAccent.copy(0.6f)))
                        else
                            Brush.linearGradient(listOf(SurfaceDark, SurfaceMid))
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isUser) TextPrimary else TextPrimary.copy(alpha = 0.92f),
                        lineHeight = 22.sp
                    )
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val anim = rememberInfiniteTransition(label = "dot_$i")
            val offsetY by anim.animateFloat(
                initialValue = 0f, targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    tween(400, delayMillis = i * 130),
                    RepeatMode.Reverse
                ), label = "dot_anim_$i"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .offset(y = offsetY.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CyanAccent.copy(alpha = 0.7f))
            )
        }
    }
}
