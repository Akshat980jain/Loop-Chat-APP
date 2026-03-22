package com.loopchat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopchat.app.ui.theme.*
import kotlin.random.Random

@Composable
fun VoiceMessageBubble(
    duration: String,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isFromMe) Color.White else TextPrimary
    val activeBrush = if (isFromMe) {
        androidx.compose.ui.graphics.SolidColor(Color.White)
    } else {
        androidx.compose.ui.graphics.Brush.linearGradient(PrimaryGradientColors)
    }
    
    // Generate static random heights for waveform if not provided
    val waveformData = remember { List(40) { kotlin.random.Random.nextFloat() * 0.8f + 0.2f } }
    
    Row(
        modifier = modifier.width(260.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isFromMe) Color.White.copy(alpha = 0.2f) else Primary)
                .clickable { onTogglePlayback() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Waveform
        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .padding(vertical = 4.dp)
        ) {
            WaveformVisualizer(
                waveformData = waveformData,
                progress = if (isPlaying) 0.4f else 0.1f, // Mock progress
                activeBrush = activeBrush,
                inactiveColor = if (isFromMe) Color.White.copy(alpha = 0.3f) else TextMuted.copy(alpha = 0.5f)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Duration
        Text(
            text = duration,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            fontSize = 11.sp
        )
    }
}

@Composable
fun WaveformVisualizer(
    waveformData: List<Float>,
    progress: Float,
    activeBrush: androidx.compose.ui.graphics.Brush,
    inactiveColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gap = 2.dp.toPx()
        val totalBars = waveformData.size
        val availableWidth = size.width
        val calculatedBarWidth = (availableWidth - (totalBars - 1) * gap) / totalBars
        
        waveformData.forEachIndexed { index, heightFactor ->
            val barHeight = size.height * heightFactor
            val x = index * (calculatedBarWidth + gap)
            val y = (size.height - barHeight) / 2
            
            val isPassed = (index.toFloat() / totalBars) <= progress
            
            if (isPassed) {
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(x, y),
                    size = Size(calculatedBarWidth, barHeight),
                    cornerRadius = CornerRadius(calculatedBarWidth / 2, calculatedBarWidth / 2)
                )
            } else {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(x, y),
                    size = Size(calculatedBarWidth, barHeight),
                    cornerRadius = CornerRadius(calculatedBarWidth / 2, calculatedBarWidth / 2)
                )
            }
        }
    }
}
