package com.loopchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.theme.ErrorColor
import com.loopchat.app.ui.theme.Primary
import com.loopchat.app.ui.theme.SurfaceVariant

@Composable
fun VoiceRecordBar(
    isRecording: Boolean,
    durationMs: Long,
    amplitudes: List<Int>,
    onStartRecording: () -> Unit,
    onStopRecording: (cancel: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLocked by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            // Recording Active State
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Blinking red dot
                val alpha by animateFloatAsState(
                    targetValue = if ((durationMs / 500) % 2 == 0L) 1f else 0.3f,
                    label = "blink"
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ErrorColor)
                        .alpha(alpha)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Timer
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Live Visualizer (simplified version of AudioVisualizer for preview)
                Box(modifier = Modifier.weight(1f).height(24.dp)) {
                    // For the preview, we just show a slice of the end
                    val recentAmps = amplitudes.takeLast(20)
                    AudioVisualizer(
                         amplitudes = recentAmps,
                         progress = 1f, // draw all
                         barWidth = 2.dp,
                         gapWidth = 1.dp
                    )
                }

                if (isLocked) {
                    IconButton(onClick = { onStopRecording(true) }) {
                        Icon(Icons.Default.Delete, "Cancel", tint = ErrorColor)
                    }
                } else {
                     Text("Slide up to lock", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            // Send/Stop button area
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { 
                    isLocked = false
                    onStopRecording(false) 
                }) {
                    Icon(Icons.Default.Send, "Send Voice Note", tint = Color.White)
                }
            }
        } else {
            // Idle State (Mic Button) - usually this is part of the ChatScreen input row
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Primary)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { _ -> onStartRecording() },
                            onDragEnd = { 
                                if (!isLocked) {
                                     onStopRecording(false)
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount < -50f && !isLocked) {
                                    isLocked = true
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, "Record Voice Note", tint = Color.White)
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
