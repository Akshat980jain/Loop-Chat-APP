package com.loopchat.app.data

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.loopchat.app.BuildConfig
import com.loopchat.app.data.models.Call
import com.loopchat.app.data.models.Profile
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/**
 * Singleton that listens for incoming calls via polling Supabase
 * (Supabase Realtime requires WebSocket which is complex on Android, so we poll)
 */
object IncomingCallManager {
    private const val TAG = "IncomingCallManager"
    private const val POLL_INTERVAL_MS = 2000L // Poll every 2 seconds
    
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    // State for incoming call
    private val _incomingCall = MutableStateFlow<IncomingCallData?>(null)
    val incomingCall: StateFlow<IncomingCallData?> = _incomingCall
    
    // State for accepted call (preserved until CallScreen reads it)
    private var _lastAcceptedCall: IncomingCallData? = null
    
    // Job for polling
    private var pollingJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    
    data class IncomingCallData(
        val call: Call,
        val callerProfile: Profile?
    )
    
    /**
     * Start listening for incoming calls
     */
    fun startListening(context: Context) {
        if (pollingJob?.isActive == true) {
            Log.d(TAG, "Already listening for incoming calls")
            return
        }
        
        Log.d(TAG, "Starting incoming call listener")
        
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    checkForIncomingCalls(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking for incoming calls: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop listening for incoming calls
     */
    fun stopListening() {
        Log.d(TAG, "Stopping incoming call listener")
        pollingJob?.cancel()
        pollingJob = null
        stopRingtone()
    }
    
    /**
     * Check for incoming calls from the database
     */
    private suspend fun checkForIncomingCalls(context: Context) {
        val accessToken = SupabaseClient.getAccessToken() ?: return
        val currentUserId = SupabaseClient.currentUserId ?: return
        
        // Don't check if we already have an incoming call
        if (_incomingCall.value != null) return
        
        try {
            val response = httpClient.get("$supabaseUrl/rest/v1/calls") {
                parameter("select", "id,caller_id,callee_id,call_type,status,room_url,callee_token,caller_token,room_name,created_at")
                parameter("callee_id", "eq.$currentUserId")
                parameter("status", "eq.ringing")
                parameter("order", "created_at.desc")
                parameter("limit", "1")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val calls: List<Call> = response.body()
                
                if (calls.isNotEmpty()) {
                    val call = calls.first()
                    Log.d(TAG, "Incoming call detected from ${call.callerId}")
                    
                    // Fetch caller profile
                    val callerProfile = fetchCallerProfile(call.callerId, accessToken)
                    
                    // Set the incoming call state
                    _incomingCall.value = IncomingCallData(call, callerProfile)
                    
                    // Start ringtone and vibration
                    startRingtone(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching calls: ${e.message}")
        }
    }
    
    /**
     * Fetch the caller's profile
     */
    private suspend fun fetchCallerProfile(callerId: String, accessToken: String): Profile? {
        return try {
            val response = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("select", "id,user_id,full_name,username,avatar_url")
                parameter("user_id", "eq.$callerId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (response.status.isSuccess()) {
                response.body()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching caller profile: ${e.message}")
            null
        }
    }
    
    /**
     * Accept the incoming call
     */
    suspend fun acceptCall(): Call? {
        val callData = _incomingCall.value ?: return null
        val accessToken = SupabaseClient.getAccessToken() ?: return null
        
        try {
            stopRingtone()
            
            // Update call status to accepted
            val response = httpClient.request("$supabaseUrl/rest/v1/calls") {
                method = HttpMethod.Patch
                contentType(ContentType.Application.Json)
                parameter("id", "eq.${callData.call.id}")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf(
                    "status" to "accepted",
                    "started_at" to java.time.Instant.now().toString()
                ))
            }
            
            if (response.status.isSuccess()) {
                Log.d(TAG, "Call accepted successfully, room URL: ${callData.call.roomUrl}")
                // Store the accepted call data BEFORE clearing incoming call
                _lastAcceptedCall = callData
                val acceptedCall = callData.call
                _incomingCall.value = null
                return acceptedCall
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Failed to accept call: ${response.status} - $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting call: ${e.message}")
            e.printStackTrace()
        }
        
        return null
    }
    
    /**
     * Get the last accepted call data (for CallScreen to read room URL)
     */
    fun getLastAcceptedCall(): IncomingCallData? {
        return _lastAcceptedCall
    }
    
    /**
     * Clear the last accepted call (called by CallScreen after reading the data)
     */
    fun clearAcceptedCall() {
        Log.d(TAG, "Clearing accepted call data")
        _lastAcceptedCall = null
    }
    
    /**
     * Reject the incoming call
     */
    suspend fun rejectCall() {
        val callData = _incomingCall.value ?: return
        val accessToken = SupabaseClient.getAccessToken() ?: return
        
        try {
            stopRingtone()
            
            // Update call status to rejected
            val response = httpClient.request("$supabaseUrl/rest/v1/calls") {
                method = HttpMethod.Patch
                contentType(ContentType.Application.Json)
                parameter("id", "eq.${callData.call.id}")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf(
                    "status" to "rejected",
                    "ended_at" to java.time.Instant.now().toString()
                ))
            }
            
            if (response.status.isSuccess()) {
                Log.d(TAG, "Call rejected successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting call: ${e.message}")
        } finally {
            _incomingCall.value = null
        }
    }
    
    /**
     * Clear the incoming call (e.g., when caller cancels)
     */
    fun clearIncomingCall() {
        stopRingtone()
        _incomingCall.value = null
    }
    
    /**
     * Start playing ringtone and vibrating
     */
    private fun startRingtone(context: Context) {
        try {
            // Start vibration
            vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                    it.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(longArrayOf(0, 1000, 500, 1000, 500), 0)
                }
            }
            
            // Start ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, ringtoneUri)
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
     * Check if there's an active incoming call
     */
    suspend fun refreshCallStatus() {
        val callData = _incomingCall.value ?: return
        val accessToken = SupabaseClient.getAccessToken() ?: return
        
        try {
            val response = httpClient.get("$supabaseUrl/rest/v1/calls") {
                parameter("select", "id,status")
                parameter("id", "eq.${callData.call.id}")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (response.status.isSuccess()) {
                val call: Call = response.body()
                // If caller cancelled or call ended, clear the incoming call
                if (call.status != "ringing") {
                    Log.d(TAG, "Call no longer ringing, status: ${call.status}")
                    clearIncomingCall()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing call status: ${e.message}")
        }
    }
}
