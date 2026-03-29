package com.loopchat.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.loopchat.app.data.BiometricAuthManager
import com.loopchat.app.ui.theme.*

/**
 * Full-screen biometric lock overlay.
 * 
 * Shown when the user returns to the app and biometric_lock_enabled is true.
 * The user must scan their fingerprint to proceed. This screen sits on top
 * of the entire app, blocking all interaction until authenticated.
 */
@Composable
fun BiometricLockScreen(
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    // Pulsing animation for the fingerprint icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    // Auto-trigger biometric prompt on first composition
    var hasPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasPrompted && activity != null) {
            hasPrompted = true
            kotlinx.coroutines.delay(300) // small delay to let UI render
            BiometricAuthManager.authenticate(
                activity = activity,
                title = "Unlock Loop Chat",
                subtitle = "Scan your fingerprint to continue",
                negativeButtonText = "Cancel",
                onSuccess = { onUnlocked() },
                onError = { /* Stay on lock screen */ },
                onFallback = { /* Stay on lock screen */ }
            )
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Background,
                        Color(0xFF0A0F1A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Loop Chat",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "App is locked",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // Fingerprint icon with pulsing glow
            Box(contentAlignment = Alignment.Center) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(scale)
                        .alpha(alpha * 0.3f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Primary.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                
                // Main fingerprint button
                FilledIconButton(
                    onClick = {
                        if (activity != null) {
                            BiometricAuthManager.authenticate(
                                activity = activity,
                                title = "Unlock Loop Chat",
                                subtitle = "Scan your fingerprint to continue",
                                negativeButtonText = "Cancel",
                                onSuccess = { onUnlocked() },
                                onError = { /* Stay on lock screen */ },
                                onFallback = { /* Stay on lock screen */ }
                            )
                        }
                    },
                    modifier = Modifier.size(80.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Surface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Unlock with fingerprint",
                        modifier = Modifier.size(40.dp),
                        tint = Primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Tap to unlock with fingerprint",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = ErrorColor)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", fontSize = 14.sp)
            }
        }
    }
}
