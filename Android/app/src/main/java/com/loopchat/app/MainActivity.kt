package com.loopchat.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.services.CallService
import com.loopchat.app.ui.navigation.LoopChatNavigation
import com.loopchat.app.ui.theme.LoopChatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Call navigation data passed from CallService or notifications
    private val pendingCallNavigation = kotlinx.coroutines.flow.MutableStateFlow<CallNavigationData?>(null)
    
    data class CallNavigationData(
        val callId: String,
        val callerId: String,
        val callerName: String,
        val callType: String,
        val roomUrl: String,
        val calleeToken: String,
        val isIncoming: Boolean
    )
    
    // Permission request launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            uploadFcmToken()
        } else {
            Log.w(TAG, "Notification permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Check for call navigation from intent
        handleIntent(intent)
        
        // Request notification permission for Android 13+
        requestNotificationPermission()
        
        setContent {
            val callNavData by pendingCallNavigation.collectAsState()
            
            LoopChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoopChatNavigation(
                        initialCallData = callNavData,
                        onCallDataConsumed = {
                            pendingCallNavigation.value = null
                        }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    /**
     * Handle intents from CallService for navigating to call screen
     */
    private fun handleIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        
        if (navigateTo == "call") {
            val callId = intent.getStringExtra(CallService.EXTRA_CALL_ID) ?: ""
            val callerId = intent.getStringExtra(CallService.EXTRA_CALLER_ID) ?: ""
            val callerName = intent.getStringExtra(CallService.EXTRA_CALLER_NAME) ?: "Unknown"
            val callType = intent.getStringExtra(CallService.EXTRA_CALL_TYPE) ?: "audio"
            val roomUrl = intent.getStringExtra(CallService.EXTRA_ROOM_URL) ?: ""
            val calleeToken = intent.getStringExtra(CallService.EXTRA_CALLEE_TOKEN) ?: ""
            val isIncoming = intent.getBooleanExtra("is_incoming", false)
            
            Log.d(TAG, "Navigating to call: callId=$callId, from=$callerName, type=$callType")
            
            pendingCallNavigation.value = CallNavigationData(
                callId = callId,
                callerId = callerId,
                callerName = callerName,
                callType = callType,
                roomUrl = roomUrl,
                calleeToken = calleeToken,
                isIncoming = isIncoming
            )
        }
    }
    
    /**
     * Request notification permission on Android 13+
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                    uploadFcmToken()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Could show explanation dialog here
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // No runtime permission needed for Android < 13
            uploadFcmToken()
        }
    }
    
    /**
     * Upload FCM token to Supabase for push notifications
     */
    private fun uploadFcmToken() {
        if (!SupabaseClient.isAuthenticated) {
            Log.d(TAG, "User not authenticated, skipping FCM token upload")
            return
        }
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to get FCM token", task.exception)
                return@addOnCompleteListener
            }
            
            val token = task.result
            Log.d(TAG, "Uploading FCM token: ${token.take(20)}...")
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SupabaseClient.updateFcmToken(token)
                    Log.d(TAG, "FCM token uploaded successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading FCM token: ${e.message}")
                }
            }
        }
    }
}
