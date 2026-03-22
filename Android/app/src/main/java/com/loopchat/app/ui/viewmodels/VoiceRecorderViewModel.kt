package com.loopchat.app.ui.viewmodels

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.SupabaseClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class VoiceRecorderState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0,
    val amplitudes: List<Int> = emptyList(),
    val outputFile: File? = null,
    val isLocked: Boolean = false,
    val isCancelled: Boolean = false
)

class VoiceRecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(VoiceRecorderState())
    val state = _state.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var startTime: Long = 0

    fun startRecording(conversationId: String) {
        val outputFile = File(getApplication<Application>().cacheDir, "voice_note_\${System.currentTimeMillis()}.m4a")
        
        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            
            startTime = System.currentTimeMillis()
            _state.value = VoiceRecorderState(isRecording = true, outputFile = outputFile)
            
            startAmplitudePolling()
            
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to start recording", e)
            _state.value = VoiceRecorderState(isRecording = false)
        }
    }

    private fun startAmplitudePolling() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (_state.value.isRecording) {
                delay(50) // Poll every 50ms
                val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                
                // Normalize amplitude roughly (0-32767 to 0-100)
                val normalizedAmp = (maxAmp / 327f).toInt().coerceIn(0, 100)
                
                val currentList = _state.value.amplitudes.toMutableList()
                currentList.add(normalizedAmp)
                
                // Keep UI list bounded for drawing if necessary, or keep all for backend
                
                _state.value = _state.value.copy(
                    durationMs = System.currentTimeMillis() - startTime,
                    amplitudes = currentList
                )
            }
        }
    }

    fun stopRecording(cancel: Boolean = false): File? {
        val finalFile = _state.value.outputFile
        val finalAmps = _state.value.amplitudes
        val finalDuration = _state.value.durationMs

        try {
            recordingJob?.cancel()
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
             Log.e("VoiceRecorder", "Failed to stop recording cleanly", e)
        } finally {
            mediaRecorder = null
            _state.value = VoiceRecorderState()
        }

        if (cancel && finalFile?.exists() == true) {
            finalFile.delete()
            return null
        }

        // Return the file and the data if we want to immediately upload
        // In a real flow, you might pass `finalAmps` back via an event
        return if (!cancel) finalFile else null
    }
    
    override fun onCleared() {
        super.onCleared()
        stopRecording(cancel = true)
    }
}
