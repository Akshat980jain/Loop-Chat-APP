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
        const val CHANNEL_ID_MESSAGES = "messages"
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
        
        // Check message type
        val messageType = data["type"]
        when (messageType) {
            "incoming_call" -> handleIncomingCall(data)
            "new_message" -> handleNewMessage(data)
            else -> Log.d(TAG, "Unknown message type: $messageType")
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
        
        // Instead of starting the foreground service directly (which crashes on Android 12+ if in background),
        // we build the incoming call notification right here and post it to NotificationManager with a Full-Screen Intent.
        // Once the user interacts with the incoming call screen (Accept/Decline), the CallService will be properly started.
        
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
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
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Accept and Reject intents (routes directly to CallService)
        val acceptIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_ACCEPT_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_CALLER_ID, callerId)
            putExtra(CallService.EXTRA_CALLER_NAME, callerName)
            putExtra(CallService.EXTRA_CALL_TYPE, callTypeValue)
            putExtra(CallService.EXTRA_ROOM_URL, roomUrl)
            putExtra(CallService.EXTRA_CALLEE_TOKEN, calleeToken)
        }
        val acceptPendingIntent = PendingIntent.getForegroundService(
            this, 1, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val rejectIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_REJECT_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
        }
        val rejectPendingIntent = PendingIntent.getForegroundService(
            this, 2, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val callTypeText = if (callTypeValue == "video") "Video Call" else "Voice Call"
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming $callTypeText")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", rejectPendingIntent)
            
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
        // Handle Android 14 full-screen intent permission requirement
        if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
            if (notificationManager.canUseFullScreenIntent()) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
            } else {
                builder.setContentIntent(fullScreenPendingIntent)
            }
        } else {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }
        
        // Also try to start CallService as foreground service for ringtone
        // This is safe from FCM onMessageReceived on most devices
        try {
            val startRingtoneIntent = Intent(this, CallService::class.java).apply {
                action = CallService.ACTION_INCOMING_CALL
                putExtra(CallService.EXTRA_CALL_ID, callId)
                putExtra(CallService.EXTRA_CALLER_ID, callerId)
                putExtra(CallService.EXTRA_CALLER_NAME, callerName)
                putExtra(CallService.EXTRA_CALL_TYPE, callTypeValue)
                putExtra(CallService.EXTRA_ROOM_URL, roomUrl)
                putExtra(CallService.EXTRA_CALLEE_TOKEN, calleeToken)
            }
            startForegroundService(startRingtoneIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start CallService from FCM: ${e.message}")
            // Not fatal - the notification + full-screen intent will still work
        }
        
        notificationManager.notify(NOTIFICATION_ID_INCOMING_CALL, builder.build())
    }
    
    /**
     * Handle a new message push notification.
     */
    private fun handleNewMessage(data: Map<String, String>) {
        val senderName = data["sender_name"] ?: "Someone"
        val messagePreview = data["message_preview"] ?: "New message"
        val conversationId = data["conversation_id"] ?: ""
        val senderId = data["sender_id"] ?: ""
        
        Log.d(TAG, "New message from $senderName: $messagePreview")
        
        // Don't show notification if the app is in the foreground and the user is viewing this conversation
        // (The in-app realtime listener already handles this case)
        
        // Build the notification
        val intent = Intent(this, com.loopchat.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
            putExtra("conversation_id", conversationId)
            putExtra("sender_id", senderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, conversationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use conversationId hashCode as notification ID so each chat gets its own notification
        notificationManager.notify(conversationId.hashCode(), notification)
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
            
            // Channel for incoming calls
            val callChannel = NotificationChannel(
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
            
            // Channel for standard messages
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Chat Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new chat messages"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(messageChannel)
            Log.d(TAG, "Notification channels created: $CHANNEL_ID_CALLS, $CHANNEL_ID_MESSAGES")
        }
    }
}
