package com.loopchat.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.theme.Primary
import com.loopchat.app.ui.theme.SurfaceVariant

@Composable
fun AudioVisualizer(
    amplitudes: List<Int>,
    progress: Float, // 0f to 1f
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    gapWidth: Dp = 2.dp,
    playedColor: Color = Primary,
    unplayedColor: Color = SurfaceVariant
) {
    if (amplitudes.isEmpty()) return

    // Smooth the progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "playbackProgress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(vertical = 4.dp)
    ) {
        val totalBars = size.width / (barWidth.toPx() + gapWidth.toPx())
        val step = (amplitudes.size / totalBars).coerceAtLeast(1f)
        
        // Downsample the amplitudes list to fit the canvas width
        val sampledAmplitudes = mutableListOf<Float>()
        for (i in 0 until totalBars.toInt()) {
            val index = (i * step).toInt().coerceIn(0, amplitudes.lastIndex)
            sampledAmplitudes.add(amplitudes[index].toFloat() / 100f) // Normalize 0..1
        }

        var startX = 0f
        val centerY = size.height / 2f

        sampledAmplitudes.forEachIndexed { index, amp ->
            val barHeight = (amp * size.height).coerceAtLeast(4.dp.toPx()) // Min height 4dp
            
            val isPlayed = (index.toFloat() / sampledAmplitudes.size) <= animatedProgress
            val color = if (isPlayed) playedColor else unplayedColor

            drawRoundRect(
                color = color,
                topLeft = Offset(startX, centerY - (barHeight / 2f)),
                size = Size(barWidth.toPx(), barHeight),
                cornerRadius = CornerRadius(barWidth.toPx() / 2f)
            )
            
            startX += barWidth.toPx() + gapWidth.toPx()
        }
    }
}
