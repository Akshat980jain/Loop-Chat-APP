package com.loopchat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.theme.Background
import com.loopchat.app.ui.theme.Offline
import com.loopchat.app.ui.theme.Online

/**
 * Reusable animated presence indicator dot.
 */
@Composable
fun PresenceIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    showBorder: Boolean = true
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isOnline) Online else Offline,
        animationSpec = tween(durationMillis = 300),
        label = "presenceColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (showBorder) Modifier.border(2.dp, Background, CircleShape)
                else Modifier
            )
            .background(animatedColor)
    )
}
