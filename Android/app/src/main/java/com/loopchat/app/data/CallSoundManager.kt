package com.loopchat.app.data

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Manager for call-related audio feedback (busy tone, end call tone, etc.)
 * Uses Android's ToneGenerator for system tones without requiring audio files.
 * Also uses TextToSpeech for verbal feedback messages.
 */
object CallSoundManager {
    private const val TAG = "CallSoundManager"
    
    private var toneGenerator: ToneGenerator? = null
    private var toneJob: Job? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var appContext: Context? = null
    
    /**
     * Initialize the tone generator and TTS
     */
    fun initialize(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
        }
        
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                Log.d(TAG, "ToneGenerator initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator: ${e.message}")
        }
        
        // Initialize TTS if context is available
        appContext?.let { ctx ->
            if (textToSpeech == null) {
                textToSpeech = TextToSpeech(ctx) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = textToSpeech?.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "TTS language not supported")
                        } else {
                            isTtsInitialized = true
                            Log.d(TAG, "TTS initialized successfully")
                        }
                    } else {
                        Log.e(TAG, "TTS initialization failed with status: $status")
                    }
                }
            }
        }
    }
    
    /**
     * Play a busy/no-answer tone pattern
     * Plays a repeating short busy tone for 3 seconds
     */
    fun playBusyTone() {
        stopAllSounds()
        
        toneJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                initialize()
                // Play busy tone pattern: beep-beep-beep (3 short tones)
                repeat(6) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_BUSY, 300)
                    delay(500)
                }
                Log.d(TAG, "Busy tone completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing busy tone: ${e.message}")
            }
        }
    }
    
    /**
     * Play a call ended tone
     * Single short tone indicating the call has disconnected
     */
    fun playEndCallTone() {
        stopAllSounds()
        
        try {
            initialize()
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
            Log.d(TAG, "End call tone played")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing end call tone: ${e.message}")
        }
    }
    
    /**
     * Play a connection failed tone
     * Three descending tones indicating call failed
     */
    fun playConnectionFailedTone() {
        stopAllSounds()
        
        toneJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                initialize()
                // Play three short error tones
                repeat(3) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 200)
                    delay(300)
                }
                Log.d(TAG, "Connection failed tone completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing connection failed tone: ${e.message}")
            }
        }
    }
    
    /**
     * Play a ringback tone (what caller hears while waiting for answer)
     * This plays a repeating ring pattern
     */
    fun playRingbackTone() {
        stopAllSounds()
        
        toneJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                initialize()
                while (isActive) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
                    delay(3000) // Ring pattern: 1 second ring, 2 second pause
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing ringback tone: ${e.message}")
            }
        }
    }
    
    /**
     * Stop all currently playing sounds
     */
    fun stopAllSounds() {
        try {
            toneJob?.cancel()
            toneJob = null
            toneGenerator?.stopTone()
            textToSpeech?.stop()
            Log.d(TAG, "All sounds stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sounds: ${e.message}")
        }
    }
    
    /**
     * Speak a verbal message using TTS
     * @param message The message to speak
     */
    fun speakMessage(message: String) {
        try {
            if (isTtsInitialized && textToSpeech != null) {
                textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "call_message")
                Log.d(TAG, "Speaking message: $message")
            } else {
                Log.w(TAG, "TTS not initialized, cannot speak message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking message: ${e.message}")
        }
    }
    
    /**
     * Play call rejected feedback - plays end call tone and speaks "Please call again later"
     */
    fun playCallRejectedFeedback() {
        toneJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // First, play end call tone
                initialize()
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
                delay(300)
                
                // Then speak the verbal message
                if (isTtsInitialized) {
                    withContext(Dispatchers.Main) {
                        textToSpeech?.speak(
                            "Please call again later",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "call_rejected"
                        )
                    }
                    Log.d(TAG, "Speaking: Please call again later")
                } else {
                    // Fallback to busy tone if TTS not available
                    delay(200)
                    repeat(3) {
                        toneGenerator?.startTone(ToneGenerator.TONE_SUP_BUSY, 300)
                        delay(500)
                    }
                }
                Log.d(TAG, "Call rejected feedback completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing call rejected feedback: ${e.message}")
            }
        }
    }
    
    /**
     * Release resources when done
     */
    fun release() {
        stopAllSounds()
        try {
            toneGenerator?.release()
            toneGenerator = null
            textToSpeech?.shutdown()
            textToSpeech = null
            isTtsInitialized = false
            appContext = null
            Log.d(TAG, "Resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}")
        }
    }
}
