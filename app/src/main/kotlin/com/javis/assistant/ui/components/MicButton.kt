package com.javis.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.javis.assistant.ui.theme.*

@Composable
fun MicButton(
    isListening: Boolean,
    isProcessing: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    val ringAlpha by pulseAnim.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isListening) 0f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Outer ring (pulse when listening)
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(CyanAccent.copy(alpha = ringAlpha))
            )
        }

        // Main button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isListening)
                        Brush.radialGradient(listOf(CyanAccent, BlueAccent))
                    else
                        Brush.radialGradient(listOf(SurfaceMid, SurfaceDark))
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    )
                }
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = CyanAccent,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (isListening) "Stop" else "Speak",
                    tint = if (isListening) DarkBg else CyanAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
