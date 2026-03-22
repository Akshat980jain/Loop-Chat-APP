package com.loopchat.app.data

import android.content.Context
import android.util.Log
import co.daily.CallClient
import co.daily.CallClientListener
import co.daily.model.CallState
import co.daily.model.MediaState
import co.daily.model.Participant
import co.daily.model.ParticipantId
import co.daily.model.MeetingToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Native manager for Daily.co WebRTC connections.
 * 
 * Replaces the fragile WebView approach with `co.daily:client:0.18.0`.
 * Handles the actual audio/video track logic via Android system hardware.
 */
object DailyCallManager : CallClientListener {
    private const val TAG = "DailyCallManager"
    
    // The core native SDK client instance
    var callClient: CallClient? = null
        private set

    // StateFlows for Jetpack Compose UI
    private val _callState = MutableStateFlow(CallState.initialized)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _localParticipant = MutableStateFlow<Participant?>(null)
    val localParticipant: StateFlow<Participant?> = _localParticipant.asStateFlow()

    private val _remoteParticipants = MutableStateFlow<Map<ParticipantId, Participant>>(emptyMap())
    val remoteParticipants: StateFlow<Map<ParticipantId, Participant>> = _remoteParticipants.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _hasRemoteParticipant = MutableStateFlow(false)
    val hasRemoteParticipant: StateFlow<Boolean> = _hasRemoteParticipant.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _callEnded = MutableStateFlow(false)
    val callEnded: StateFlow<Boolean> = _callEnded.asStateFlow()

    // Whether local user has joined the room
    private val _isJoined = MutableStateFlow(false)
    val isJoined: StateFlow<Boolean> = _isJoined.asStateFlow()
    
    // Store the intended call type to enable AFTER joining
    private var intendedInitialVideoState = true

    /**
     * Initialize the native call client (should be done before joining)
     */
    fun initialize(context: Context) {
        if (callClient == null) {
            Log.d(TAG, "Initializing Daily native CallClient")
            callClient = CallClient(context.applicationContext)
            callClient?.addListener(this)
        }
    }

    /**
     * Join a Daily.co call room using WebRTC
     */
    fun joinCall(roomUrl: String, meetingToken: String? = null, isVideoCall: Boolean = true) {
        Log.d(TAG, "Joining room: $roomUrl, isVideoCall=$isVideoCall")
        resetState()
        
        intendedInitialVideoState = isVideoCall
        // Initially set state to disconnected/off values while joining
        _callState.value = CallState.joining
        _isVideoEnabled.value = false
        _isMuted.value = true
        
        // The Daily.co native SDK join method is asynchronous
        callClient?.apply {
            // STEP 1: Explicitly disable inputs PRE-JOIN so tracks don't initialize prematurely and throw "Not all tracks were ready"
            setInputsEnabled(camera = false, microphone = false)
            
            // STEP 2: Join the room
            join(roomUrl, meetingToken?.let { MeetingToken(it) }) { result ->
                result.error?.let { err -> 
                    Log.e(TAG, "Join error: $err")
                    _error.value = err.toString() 
                }
            }
        } ?: Log.e(TAG, "Cannot join: CallClient is null. Did you initialize?")
    }

    /**
     * Leave the current call and tear down WebRTC connection.
     * Only sets callEnded — does NOT reset isJoined so the callEnded handler can check it.
     */
    fun leaveCall() {
        Log.d(TAG, "Leaving call natively")
        val wasJoined = _isJoined.value
        callClient?.leave()
        // Don't reset everything — keep isJoined state so callEnded handlers can check it
        _callEnded.value = true
        Log.d(TAG, "Call ended (wasJoined=$wasJoined)")
    }
    
    /**
     * Full cleanup — call this only when destroying the call client entirely
     */
    fun cleanup() {
        Log.d(TAG, "Full cleanup of DailyCallManager")
        callClient?.leave()
        resetState()
    }

    /**
     * Toggle microphone (mute/unmute)
     */
    fun toggleMute() {
        val newState = !_isMuted.value
        _isMuted.value = newState
        Log.d(TAG, "Setting microphone enabled to: ${!newState}")
        callClient?.setInputsEnabled(microphone = !newState)
    }

    /**
     * Toggle camera (on/off)
     */
    fun toggleVideo() {
        val newState = !_isVideoEnabled.value
        _isVideoEnabled.value = newState
        Log.d(TAG, "Setting camera enabled to: $newState")
        callClient?.setInputsEnabled(camera = newState)
    }

    /**
     * Switch between front and rear camera
     */
    fun switchCamera() {
        Log.d(TAG, "Switching camera facing mode")
        callClient?.availableDevices()?.let { availableDevices ->
            // Filter for camera devices
            val cameraDevices = availableDevices.camera
            if (cameraDevices.isNotEmpty() && cameraDevices.size > 1) {
                // Determine current camera device ID. If null, use the first one.
                val currentDeviceId = callClient?.inputs()?.camera?.settings?.deviceId
                
                // Find a device that is different from the current one
                val nextDevice = cameraDevices.firstOrNull { device -> device.deviceId != currentDeviceId }
                    ?: cameraDevices.first()

                Log.d(TAG, "Switching camera to device: ${nextDevice.deviceId}")
                setCameraDevice(nextDevice.deviceId)
            } else {
                Log.d(TAG, "Only one or zero camera devices available, cannot switch")
            }
        } ?: Log.e(TAG, "Cannot get available devices: callClient is null")
    }

    /**
     * Select the front-facing camera device explicitly.
     * Should be called after join to ensure user sees themselves, not the back camera.
     */
    fun selectFrontCamera() {
        callClient?.availableDevices()?.let { availableDevices ->
            val cameraDevices = availableDevices.camera
            Log.d(TAG, "Available cameras: ${cameraDevices.map { "${it.deviceId}" }}")

            val frontCamera = cameraDevices.find {
                it.deviceId.contains("front", ignoreCase = true) ||
                it.deviceId.contains("user", ignoreCase = true) ||
                it.deviceId.contains("1")
            }
            if (frontCamera != null) {
                Log.d(TAG, "Selecting front camera: ${frontCamera.deviceId}")
                setCameraDevice(frontCamera.deviceId)
            } else {
                Log.w(TAG, "No front camera found in: ${cameraDevices.map { it.deviceId }}")
            }
        } ?: Log.e(TAG, "Cannot get available devices")
    }

    /**
     * Helper: set camera to a specific device ID
     */
    private fun setCameraDevice(deviceId: String) {
        val cameraUpdate = co.daily.settings.CameraInputSettingsUpdate(
            settings = co.daily.settings.VideoMediaTrackSettingsUpdate(
                deviceId = co.daily.settings.Device(deviceId)
            )
        )
        callClient?.updateInputs(co.daily.settings.InputSettingsUpdate(camera = cameraUpdate))
    }

    /**
     * Cleanup and destroy SDK resources
     */
    fun release() {
        Log.d(TAG, "Releasing CallClient")
        callClient?.removeListener(this)
        callClient?.release()
        callClient = null
        resetState()
    }

    private fun resetState() {
        _isMuted.value = false
        _isVideoEnabled.value = true
        _hasRemoteParticipant.value = false
        _error.value = null
        _callEnded.value = false
        _localParticipant.value = null
        _remoteParticipants.value = emptyMap()
        _isJoined.value = false
    }

    private fun updateParticipants() {
        val client = callClient ?: return
        val participants = client.participants()
        
        _localParticipant.value = participants.local
        val allParticipants = participants.all
        val remoteOnes = allParticipants.filter { it.key != participants.local?.id }
        _remoteParticipants.value = remoteOnes
        _hasRemoteParticipant.value = remoteOnes.isNotEmpty()
        
        // Sync our local mute/camera states with the actual hardware state
        participants.local?.let { local ->
            val camState = local.media?.camera?.state
            val micState = local.media?.microphone?.state
            val hasTrack = local.media?.camera?.track != null
            Log.d(TAG, "updateParticipants: local camera=$camState, hasTrack=$hasTrack, mic=$micState")
            _isMuted.value = micState == MediaState.off
            _isVideoEnabled.value = camState != MediaState.off
        }
        
        // Log remote participants
        remoteOnes.forEach { (id, p) ->
            Log.d(TAG, "updateParticipants: remote id=$id, camera=${p.media?.camera?.state}, hasTrack=${p.media?.camera?.track != null}")
        }
    }

    // --- CallClientListener Overrides ---

    override fun onCallStateUpdated(state: CallState) {
        Log.d(TAG, "Daily call state updated: $state")
        _callState.value = state
        
        when (state) {
            CallState.joined -> {
                Log.d(TAG, "Successfully joined the WebRTC meeting. Enabling intended tracks now.")
                _isJoined.value = true
                
                // STEP 3: Enable the intended inputs AFTER join is completed
                Log.d(TAG, "Enabling camera=$intendedInitialVideoState, microphone=true")
                callClient?.setInputsEnabled(camera = intendedInitialVideoState, microphone = true)
                _isVideoEnabled.value = intendedInitialVideoState
                _isMuted.value = false

                // STEP 4: Select front camera by default for video calls
                if (intendedInitialVideoState) {
                    selectFrontCamera()
                }
                
                updateParticipants()
                
                // The camera hardware takes time to initialize after setInputsEnabled.
                // Poll participant state to catch when the local camera becomes playable.
                if (intendedInitialVideoState) {
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        repeat(10) { attempt ->
                            kotlinx.coroutines.delay(500)
                            updateParticipants()
                            val localCamState = _localParticipant.value?.media?.camera?.state
                            Log.d(TAG, "Camera init poll #${attempt + 1}: localCameraState=$localCamState")
                            if (localCamState == MediaState.playable) {
                                Log.d(TAG, "Local camera is now playable!")
                                return@launch
                            }
                            // If still not playable after 2 seconds, try enabling again and selecting front camera again
                            if (attempt == 3 || attempt == 7) {
                                Log.d(TAG, "Camera not playable, re-enabling camera input and retrying front camera selection")
                                callClient?.setInputsEnabled(camera = true, microphone = true)
                                selectFrontCamera()
                            }
                        }
                        Log.w(TAG, "Camera did not become playable after 5 seconds of polling")
                    }
                }
            }
            CallState.left -> {
                Log.d(TAG, "Left the WebRTC meeting")
                _callEnded.value = true
            }
            else -> {}
        }
    }

    override fun onParticipantJoined(participant: Participant) {
        Log.d(TAG, "Participant joined: ${participant.id}")
        updateParticipants()
    }

    override fun onParticipantUpdated(participant: Participant) {
        Log.d(TAG, "Participant updated: ${participant.id}, cameraState=${participant.media?.camera?.state}, hasVideoTrack=${participant.media?.camera?.track != null}")
        updateParticipants()
    }

    override fun onParticipantLeft(participant: Participant, reason: co.daily.model.ParticipantLeftReason) {
        Log.d(TAG, "Participant left: ${participant.id}. Reason: $reason")
        updateParticipants()
    }

    override fun onInputsUpdated(inputSettings: co.daily.settings.InputSettings) {
        Log.d(TAG, "Inputs updated - camera: ${inputSettings.camera}, microphone: ${inputSettings.microphone}")
        // Re-read participant state whenever camera/mic inputs change
        updateParticipants()
    }

    override fun onError(message: String) {
        Log.e(TAG, "Daily.co SDK Error: $message")
        _error.value = message
    }
}
