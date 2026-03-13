package com.loopchat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging

class LoopChatApplication : Application() {
    
    companion object {
        private const val TAG = "LoopChatApplication"
        
        // Notification channel IDs
        const val CHANNEL_INCOMING_CALLS = "incoming_calls"
        const val CHANNEL_CALL_SERVICE = "call_service"
        const val CHANNEL_MESSAGES = "messages"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channels
        createNotificationChannels()
        
        // Initialize Firebase and get FCM token
        initializeFirebase()
        
        Log.d(TAG, "LoopChatApplication initialized")
    }
    
    /**
     * Create notification channels for Android 8.0+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Incoming calls channel - highest priority with ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            val incomingCallsChannel = NotificationChannel(
                CHANNEL_INCOMING_CALLS,
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
            notificationManager.createNotificationChannel(incomingCallsChannel)
            
            // Call service channel - for ongoing calls
            val callServiceChannel = NotificationChannel(
                CHANNEL_CALL_SERVICE,
                "Active Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for ongoing calls"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
                setSound(null, null) // No sound for ongoing call notification
            }
            notificationManager.createNotificationChannel(callServiceChannel)
            
            // Messages channel - for chat notifications
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new messages"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(messagesChannel)
            
            Log.d(TAG, "Notification channels created")
        }
    }
    
    /**
     * Initialize Firebase Cloud Messaging
     */
    private fun initializeFirebase() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Failed to get FCM token", task.exception)
                    return@addOnCompleteListener
                }
                
                // Get new FCM registration token
                val token = task.result
                Log.d(TAG, "FCM Token: ${token.take(20)}...")
                
                // Token will be uploaded to server when user logs in
                // and onNewToken will be called if it changes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}")
        }
    }
}
