package com.loopchat.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Handles Android system-level audio routing during a VoIP call.
 * Essential for WebRTC to configure the microphone and speakerphone correctly.
 */
object CallAudioManager {
    private const val TAG = "CallAudioManager"
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // Store original mode to restore later
    private var originalMode: Int = AudioManager.MODE_NORMAL
    private var originalSpeakerphoneState: Boolean = false

    fun initialize(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun startCall() {
        Log.d(TAG, "Starting call audio routing")
        audioManager?.let { am ->
            originalMode = am.mode
            originalSpeakerphoneState = am.isSpeakerphoneOn

            // Request audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                    }
                    .build()
                
                audioFocusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }

            // Set mode to VoIP communication - applies echo cancellation
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        Log.d(TAG, "Setting speakerphone to: $on")
        audioManager?.isSpeakerphoneOn = on
    }

    fun stopCall() {
        Log.d(TAG, "Stopping call audio routing")
        audioManager?.let { am ->
            // Restore previous states
            am.mode = originalMode
            am.isSpeakerphoneOn = originalSpeakerphoneState

            // Abandon audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }
}
