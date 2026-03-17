package com.loopchat.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.loopchat.app.IncomingCallActivity
import com.loopchat.app.MainActivity
import com.loopchat.app.R
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.IncomingCallManager
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.loopchat.app.BuildConfig

/**
 * Foreground service for handling VoIP calls.
 * 
 * This service keeps running even when the app is in background,
 * ensuring the call stays connected and the user can interact with it
 * through the notification.
 */
class CallService : Service() {
    
    companion object {
        private const val TAG = "CallService"
        const val CHANNEL_ID = "call_service"
        const val NOTIFICATION_ID = 2001
        
        // Actions
        const val ACTION_INCOMING_CALL = "com.loopchat.app.INCOMING_CALL"
        const val ACTION_ACCEPT_CALL = "com.loopchat.app.ACCEPT_CALL"
        const val ACTION_REJECT_CALL = "com.loopchat.app.REJECT_CALL"
        const val ACTION_END_CALL = "com.loopchat.app.END_CALL"
        const val ACTION_ONGOING_CALL = "com.loopchat.app.ONGOING_CALL"
        
        // Extras
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALLER_ID = "caller_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_ROOM_URL = "room_url"
        const val EXTRA_CALLEE_TOKEN = "callee_token"
        
        // Current call state
        var currentCallId: String? = null
        var isCallActive = false
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallService created")
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_INCOMING_CALL -> handleIncomingCall(intent)
            ACTION_ACCEPT_CALL -> handleAcceptCall(intent)
            ACTION_REJECT_CALL -> handleRejectCall(intent)
            ACTION_END_CALL -> handleEndCall()
            ACTION_ONGOING_CALL -> handleOngoingCall(intent)
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallService destroyed")
        stopRingtone()
        releaseWakeLock()
        isCallActive = false
        currentCallId = null
    }
    
    /**
     * Handle incoming call - show notification and play ringtone
     */
    private fun handleIncomingCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: return
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "audio"
        val roomUrl = intent.getStringExtra(EXTRA_ROOM_URL) ?: ""
        
        currentCallId = callId
        
        Log.d(TAG, "Handling incoming call from $callerName")
        
        // Start as foreground with incoming call notification
        val notification = createIncomingCallNotification(callId, callerId, callerName, callType, roomUrl)
        startForeground(NOTIFICATION_ID, notification)
        
        // Start ringtone and vibration
        startRingtone()
    }
    
    /**
     * Handle accept call action
     */
    private fun handleAcceptCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: currentCallId ?: return
        val callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: ""
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "audio"
        val roomUrl = intent.getStringExtra(EXTRA_ROOM_URL) ?: ""
        val calleeToken = intent.getStringExtra(EXTRA_CALLEE_TOKEN) ?: ""
        
        Log.d(TAG, "Accepting call: $callId")
        
        stopRingtone()
        IncomingCallManager.markCallAsAcceptedByService()
        isCallActive = true
        
        // Update call status in database
        CoroutineScope(Dispatchers.IO).launch {
            acceptCallInDatabase(callId)
        }
        
        // Update notification to show ongoing call
        val notification = createOngoingCallNotification(
            callId = callId,
            callerId = callerId,
            callerName = callerName,
            callType = callType,
            roomUrl = roomUrl,
            calleeToken = calleeToken
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        // Launch main activity with call screen
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "call")
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_ROOM_URL, roomUrl)
            putExtra(EXTRA_CALLEE_TOKEN, calleeToken)
            putExtra("is_incoming", true)
        }
        startActivity(mainIntent)
    }
    
    /**
     * Handle reject call action
     */
    private fun handleRejectCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: currentCallId ?: return
        
        Log.d(TAG, "Rejecting call: $callId")
        
        stopRingtone()
        IncomingCallManager.clearIncomingCall()
        
        // Update call status in database
        CoroutineScope(Dispatchers.IO).launch {
            rejectCallInDatabase(callId)
        }
        
        stopSelf()
    }
    
    /**
     * Handle end call action
     */
    private fun handleEndCall() {
        Log.d(TAG, "Ending call")
        
        stopRingtone()
        isCallActive = false
        
        // Update call status in database if we have call ID
        currentCallId?.let { callId ->
            CoroutineScope(Dispatchers.IO).launch {
                endCallInDatabase(callId)
            }
        }
        
        stopSelf()
    }
    
    /**
     * Handle ongoing call - update notification for active call
     */
    private fun handleOngoingCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: currentCallId ?: ""
        val callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: ""
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "audio"
        val roomUrl = intent.getStringExtra(EXTRA_ROOM_URL) ?: ""
        val calleeToken = intent.getStringExtra(EXTRA_CALLEE_TOKEN) ?: ""
        
        isCallActive = true
        
        val notification = createOngoingCallNotification(
            callId = callId,
            callerId = callerId,
            callerName = callerName,
            callType = callType,
            roomUrl = roomUrl,
            calleeToken = calleeToken
        )
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * Create notification for incoming call with accept/reject actions
     */
    private fun createIncomingCallNotification(
        callId: String,
        callerId: String,
        callerName: String,
        callType: String,
        roomUrl: String
    ): Notification {
        // Full-screen intent for incoming call
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_ROOM_URL, roomUrl)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Accept action
        val acceptIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_ACCEPT_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_ROOM_URL, roomUrl)
        }
        val acceptPendingIntent = PendingIntent.getService(
            this, 1, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Reject action
        val rejectIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_REJECT_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val rejectPendingIntent = PendingIntent.getService(
            this, 2, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val callTypeText = if (callType == "video") "Video Call" else "Voice Call"
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.canUseFullScreenIntent()) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
            } else {
                // Fallback if permission is denied: set as a regular content intent
                builder.setContentIntent(fullScreenPendingIntent)
            }
        } else {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }
        
        return builder.build()
    }
    
    /**
     * Create notification for ongoing call with end call action
     */
    private fun createOngoingCallNotification(
        callId: String,
        callerId: String,
        callerName: String,
        callType: String,
        roomUrl: String,
        calleeToken: String
    ): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "call")
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_ROOM_URL, roomUrl)
            putExtra(EXTRA_CALLEE_TOKEN, calleeToken)
            putExtra("is_incoming", true)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val endIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_END_CALL
        }
        val endPendingIntent = PendingIntent.getService(
            this, 3, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val callTypeText = if (callType == "video") "Video call" else "Voice call"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$callTypeText in progress")
            .setContentText("Tap to return to call with $callerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", endPendingIntent)
            .build()
    }
    
    /**
     * Create notification channel for call service
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for active calls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Start ringtone and vibration
     */
    private fun startRingtone() {
        try {
            // Vibration
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            
            vibrator?.let {
                val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                it.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
            
            // Ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@CallService, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
            
            Log.d(TAG, "Ringtone started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ringtone: ${e.message}")
        }
    }
    
    /**
     * Stop ringtone and vibration
     */
    private fun stopRingtone() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            vibrator?.cancel()
            vibrator = null
            
            Log.d(TAG, "Ringtone stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.message}")
        }
    }
    
    /**
     * Acquire wake lock to keep CPU running during call
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LoopChat:CallWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}")
        }
    }
    
    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
    }
    
    /**
     * Accept call in Supabase database
     */
    private suspend fun acceptCallInDatabase(callId: String) {
        val accessToken = SupabaseClient.getAccessToken() ?: return
        
        try {
            val response = httpClient.request("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                method = HttpMethod.Patch
                contentType(ContentType.Application.Json)
                parameter("id", "eq.$callId")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=minimal")
                setBody(mapOf(
                    "status" to "accepted",
                    "started_at" to java.time.Instant.now().toString()
                ))
            }
            if (response.status.isSuccess()) {
                Log.d(TAG, "Call accepted in database successfully")
            } else {
                Log.e(TAG, "Failed to accept call in DB: ${response.status} - ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting call in database: ${e.message}")
        }
    }
    
    /**
     * Reject call in Supabase database
     */
    private suspend fun rejectCallInDatabase(callId: String) {
        val accessToken = SupabaseClient.getAccessToken() ?: return
        
        try {
            val response = httpClient.request("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                method = HttpMethod.Patch
                contentType(ContentType.Application.Json)
                parameter("id", "eq.$callId")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=minimal")
                setBody(mapOf(
                    "status" to "rejected",
                    "ended_at" to java.time.Instant.now().toString()
                ))
            }
            if (response.status.isSuccess()) {
                Log.d(TAG, "Call rejected in database successfully")
            } else {
                Log.e(TAG, "Failed to reject call in DB: ${response.status} - ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting call in database: ${e.message}")
        }
    }
    
    /**
     * End call in Supabase database
     */
    private suspend fun endCallInDatabase(callId: String) {
        val accessToken = SupabaseClient.getAccessToken() ?: return
        
        try {
            val response = httpClient.request("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                method = HttpMethod.Patch
                contentType(ContentType.Application.Json)
                parameter("id", "eq.$callId")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=minimal")
                setBody(mapOf(
                    "status" to "ended",
                    "ended_at" to java.time.Instant.now().toString()
                ))
            }
            if (response.status.isSuccess()) {
                Log.d(TAG, "Call ended in database successfully")
            } else {
                Log.e(TAG, "Failed to end call in DB: ${response.status} - ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call in database: ${e.message}")
        }
    }
}
