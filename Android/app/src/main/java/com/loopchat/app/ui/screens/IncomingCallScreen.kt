package com.loopchat.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.components.GradientAvatar
import com.loopchat.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IncomingCallScreen(
    callerName: String,
    callType: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var isAccepting by remember { mutableStateOf(false) }
    var isRejecting by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(30) } // 30 second timeout
    
    // Pulsating animation for the avatar ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    // Check if call is still ringing and implement 30-second timeout
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0 && !isAccepting && !isRejecting) {
            delay(1000)
            remainingSeconds--
            
            // Also check if call is still ringing every 3 seconds
            // (Caller should handle actual ringing state via a separate effect or flow)
        }
        
        // Auto-reject if not accepted within 30 seconds
        if (remainingSeconds == 0 && !isAccepting && !isRejecting) {
            isRejecting = true
            onReject()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Decorative gradient orbs in background
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
                .blur(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Success.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .blur(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // Call info section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulsating avatar ring
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsating ring
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Success.copy(alpha = pulseAlpha))
                    )
                    
                    // Middle ring
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(Surface)
                    )
                    
                    // Avatar
                    GradientAvatar(
                        initial = callerName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        size = 120.dp,
                        borderWidth = 3.dp
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Caller name
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Call type indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Incoming ${if (callType == "video") "Video" else "Voice"} Call",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Success
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Reject button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Error),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (!isRejecting && !isAccepting) {
                                    isRejecting = true
                                    onReject()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            enabled = !isRejecting && !isAccepting
                        ) {
                            if (isRejecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = TextPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Reject",
                                    modifier = Modifier.size(32.dp),
                                    tint = TextPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Decline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                
                // Accept button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Success),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (!isAccepting && !isRejecting) {
                                    isAccepting = true
                                    onAccept()
                                    // Depending on whether it navigates away or fails, the host might need to compose again 
                                    // or reset the state, but usually navigation destroys this screen.
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            enabled = !isAccepting && !isRejecting
                        ) {
                            if (isAccepting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = TextPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Accept",
                                    modifier = Modifier.size(32.dp),
                                    tint = TextPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Accept",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
