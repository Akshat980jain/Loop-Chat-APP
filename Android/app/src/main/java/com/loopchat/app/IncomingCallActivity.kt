package com.loopchat.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loopchat.app.services.CallService
import com.loopchat.app.ui.components.GradientAvatar
import com.loopchat.app.ui.theme.*
import kotlinx.coroutines.delay
import com.loopchat.app.ui.components.CameraPreview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.Manifest

/**
 * Full-screen Activity for incoming calls.
 * Shows on lock screen with accept/reject buttons.
 * 
 * This activity is launched when:
 * 1. FCM push notification is received for incoming call
 * 2. User taps on incoming call notification
 */
class IncomingCallActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "IncomingCallActivity"
    }
    
    private var callId: String? = null
    private var callerId: String? = null
    private var callerName: String? = null
    private var callType: String? = null
    private var roomUrl: String? = null
    private var calleeToken: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "IncomingCallActivity onCreate")
        
        // Extract call data from intent
        callId = intent.getStringExtra(CallService.EXTRA_CALL_ID)
        callerId = intent.getStringExtra(CallService.EXTRA_CALLER_ID)
        callerName = intent.getStringExtra(CallService.EXTRA_CALLER_NAME) ?: "Unknown"
        callType = intent.getStringExtra(CallService.EXTRA_CALL_TYPE) ?: "audio"
        roomUrl = intent.getStringExtra(CallService.EXTRA_ROOM_URL)
        calleeToken = intent.getStringExtra(CallService.EXTRA_CALLEE_TOKEN)
        
        Log.d(TAG, "Incoming call from: $callerName, type: $callType")
        
        // Start the call service to ensure ringtone plays (safe from foreground Activity)
        startCallServiceForRingtone()
        
        // Configure window for lock screen display
        setupWindowFlags()
        
        setContent {
            LoopChatTheme {
                IncomingCallScreen(
                    callerName = callerName ?: "Unknown",
                    callType = callType ?: "audio",
                    onAccept = { acceptCall() },
                    onReject = { rejectCall() }
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update with new intent data
        callId = intent.getStringExtra(CallService.EXTRA_CALL_ID) ?: callId
        callerId = intent.getStringExtra(CallService.EXTRA_CALLER_ID) ?: callerId
        callerName = intent.getStringExtra(CallService.EXTRA_CALLER_NAME) ?: callerName
        callType = intent.getStringExtra(CallService.EXTRA_CALL_TYPE) ?: callType
        roomUrl = intent.getStringExtra(CallService.EXTRA_ROOM_URL) ?: roomUrl
        calleeToken = intent.getStringExtra(CallService.EXTRA_CALLEE_TOKEN) ?: calleeToken
    }
    
    /**
     * Setup window flags to show on lock screen and turn screen on
     */
    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        
        // Keep screen on during incoming call
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    /**
     * Accept the incoming call
     */
    private fun acceptCall() {
        Log.d(TAG, "Accept call tapped")
        
        // Send accept action to CallService
        val serviceIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_ACCEPT_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_CALLER_ID, callerId)
            putExtra(CallService.EXTRA_CALLER_NAME, callerName)
            putExtra(CallService.EXTRA_CALL_TYPE, callType)
            putExtra(CallService.EXTRA_ROOM_URL, roomUrl)
            putExtra(CallService.EXTRA_CALLEE_TOKEN, calleeToken)
        }
        try {
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service for accept: ${e.message}")
        }
        
        // Close this activity - CallService will launch MainActivity with call screen
        finish()
    }
    
    /**
     * Reject the incoming call
     */
    private fun rejectCall() {
        Log.d(TAG, "Reject call tapped")
        
        // Send reject action to CallService
        val serviceIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_REJECT_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
        }
        try {
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service for reject: ${e.message}")
        }
        
        finish()
    }
    
    /**
     * Start the call service to handle ringtone and foreground notification
     */
    private fun startCallServiceForRingtone() {
        val intent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_INCOMING_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_CALLER_ID, callerId)
            putExtra(CallService.EXTRA_CALLER_NAME, callerName)
            putExtra(CallService.EXTRA_CALL_TYPE, callType)
            putExtra(CallService.EXTRA_ROOM_URL, roomUrl)
            putExtra(CallService.EXTRA_CALLEE_TOKEN, calleeToken)
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service for ringtone: ${e.message}")
        }
    }

    override fun onBackPressed() {
        // Don't allow back button to dismiss incoming call
        // User must explicitly accept or reject
    }
}

/**
 * Composable UI for incoming call screen
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun IncomingCallScreen(
    callerName: String,
    callType: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var animatedScale by remember { mutableFloatStateOf(1f) }
    
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )
    
    // Pulsing animation for avatar
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.allPermissionsGranted) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
        while (true) {
            animatedScale = 1.1f
            delay(500)
            animatedScale = 1f
            delay(500)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        if (callType == "video" && cameraPermissionState.allPermissionsGranted) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                isFrontCamera = true,
                isEnabled = true
            )
        } else {
            // Decorative gradient orbs for audio calls or when permissions not granted
            Box(
                modifier = Modifier
                    .size(500.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-150).dp)
                    .blur(150.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-100).dp, y = 100.dp)
                    .blur(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Secondary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        // Semi-transparent overlay to make text readable over the camera
        if (callType == "video") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Caller info section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with pulsing effect
                Box(
                    modifier = Modifier
                        .size((140 * animatedScale).dp)
                ) {
                    GradientAvatar(
                        initial = callerName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        size = 140.dp,
                        borderWidth = 4.dp
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Caller name
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Call type
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (callType == "video") "Video Call" else "Voice Call",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // "Incoming call" text
                Text(
                    text = "Incoming call...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            
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
                            .background(ErrorColor),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = onReject,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
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
                            onClick = onAccept,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
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

/**
 * Simple theme wrapper for the incoming call activity
 */
@Composable
fun LoopChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Primary,
            secondary = Secondary,
            background = Background,
            surface = Surface,
            error = ErrorColor
        ),
        typography = Typography,
        content = content
    )
}
