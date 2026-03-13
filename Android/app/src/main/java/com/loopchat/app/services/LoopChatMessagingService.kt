package com.loopchat.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loopchat.app.IncomingCallActivity
import com.loopchat.app.R
import com.loopchat.app.data.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging Service for handling VoIP push notifications.
 * 
 * This service receives FCM data messages when someone calls the user,
 * even when the app is closed or in the background.
 */
class LoopChatMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "LoopChatFCM"
        const val CHANNEL_ID_CALLS = "incoming_calls"
        const val NOTIFICATION_ID_INCOMING_CALL = 1001
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    /**
     * Called when a new FCM token is generated.
     * We need to send this token to the backend so it can send us push notifications.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: ${token.take(20)}...")
        
        // Upload token to Supabase
        CoroutineScope(Dispatchers.IO).launch {
            try {
                uploadTokenToServer(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload FCM token: ${e.message}")
            }
        }
    }
    
    /**
     * Called when a message is received from FCM.
     * For VoIP calls, we receive data-only messages that wake the device.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "FCM message received from: ${message.from}")
        Log.d(TAG, "Message data: ${message.data}")
        
        val data = message.data
        
        // Check if this is an incoming call notification
        val callType = data["type"]
        if (callType == "incoming_call") {
            handleIncomingCall(data)
        } else {
            // Handle other notification types
            Log.d(TAG, "Unknown message type: $callType")
        }
    }
    
    /**
     * Handle an incoming call push notification.
     */
    private fun handleIncomingCall(data: Map<String, String>) {
        val callId = data["call_id"] ?: return
        val callerId = data["caller_id"] ?: return
        val callerName = data["caller_name"] ?: "Unknown"
        val callTypeValue = data["call_type"] ?: "audio"
        val roomUrl = data["room_url"] ?: ""
        val calleeToken = data["callee_token"] ?: ""
        
        Log.d(TAG, "Incoming call: id=$callId, from=$callerName, type=$callTypeValue")
        
        // Start the CallService foreground service
        val serviceIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_INCOMING_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_CALLER_ID, callerId)
            putExtra(CallService.EXTRA_CALLER_NAME, callerName)
            putExtra(CallService.EXTRA_CALL_TYPE, callTypeValue)
            putExtra(CallService.EXTRA_ROOM_URL, roomUrl)
            putExtra(CallService.EXTRA_CALLEE_TOKEN, calleeToken)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // Also launch the full-screen incoming call activity
        val callIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_CALLER_ID, callerId)
            putExtra(CallService.EXTRA_CALLER_NAME, callerName)
            putExtra(CallService.EXTRA_CALL_TYPE, callTypeValue)
            putExtra(CallService.EXTRA_ROOM_URL, roomUrl)
            putExtra(CallService.EXTRA_CALLEE_TOKEN, calleeToken)
        }
        startActivity(callIntent)
    }
    
    /**
     * Upload FCM token to Supabase for this user.
     */
    private suspend fun uploadTokenToServer(token: String) {
        val accessToken = SupabaseClient.getAccessToken()
        val userId = SupabaseClient.currentUserId
        
        if (accessToken == null || userId == null) {
            Log.w(TAG, "Cannot upload FCM token: user not logged in")
            return
        }
        
        // Store token in user_settings table
        try {
            SupabaseClient.updateFcmToken(token)
            Log.d(TAG, "FCM token uploaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading FCM token: ${e.message}")
        }
    }
    
    /**
     * Create notification channel for incoming calls.
     * Must be done before showing any notifications on Android 8+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            val channel = NotificationChannel(
                CHANNEL_ID_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming voice and video calls"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID_CALLS")
        }
    }
}
