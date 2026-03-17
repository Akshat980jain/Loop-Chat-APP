package com.loopchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopchat.app.ui.theme.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/**
 * Premium Gradient Button with Sunset Vibes colors
 */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradientColors: List<Color> = SunsetGradientColors,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (enabled) gradientColors else gradientColors.map { it.copy(alpha = 0.5f) }
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextSecondary
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        content()
    }
}

/**
 * Large Gradient Button for primary actions
 */
@Composable
fun GradientButtonLarge(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (enabled && !isLoading) SunsetGradientColors 
                            else SunsetGradientColors.map { it.copy(alpha = 0.5f) }
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            enabled = enabled && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = TextPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Glassmorphism Card with subtle blur and border
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Primary.copy(alpha = 0.3f),
                        Secondary.copy(alpha = 0.2f)
                    )
                ),
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = GlassBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        content()
    }
}

/**
 * Avatar with gradient border ring
 */
@Composable
fun GradientAvatar(
    initial: String,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    borderWidth: Dp = 2.dp,
    showOnlineIndicator: Boolean = false,
    isOnline: Boolean = true
) {
    Box(
        modifier = modifier.size(size + borderWidth * 2),
        contentAlignment = Alignment.Center
    ) {
        // Gradient border ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.sweepGradient(
                        colors = AvatarBorderGradient
                    ),
                    shape = CircleShape
                )
        )
        
        // Inner avatar
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initial.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Online indicator
        if (showOnlineIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .border(2.dp, Background, CircleShape)
                    .clip(CircleShape)
                    .background(if (isOnline) Online else Offline)
            )
        }
    }
}

/**
 * Small Avatar for lists
 */
@Composable
fun SmallGradientAvatar(
    initial: String,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isGroup: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = 2.dp,
                brush = Brush.sweepGradient(AvatarBorderGradient),
                shape = CircleShape
            )
            .padding(2.dp)
            .clip(CircleShape)
            .background(Surface),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Glowing icon with sunset colors
 */
@Composable
fun GlowingLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(size)
                .blur(20.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.6f),
                            Secondary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Logo container
        Surface(
            modifier = Modifier.size(size * 0.85f),
            shape = RoundedCornerShape(20.dp),
            color = Surface.copy(alpha = 0.9f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(SunsetGradientColors),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L",
                    fontSize = (size.value * 0.5f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }
    }
}

/**
 * Premium styled text field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextMuted) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = SurfaceVariant,
            focusedLabelColor = Primary,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Primary,
            focusedLeadingIconColor = Primary,
            unfocusedLeadingIconColor = TextSecondary
        )
    )
}
