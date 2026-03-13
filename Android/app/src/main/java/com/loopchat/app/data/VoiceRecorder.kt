package com.loopchat.app.data

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Voice message recorder
 * Handles recording, playback, and waveform generation
 */
class VoiceRecorder(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var startTime = 0L
    
    /**
     * Start recording voice message
     */
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                return@withContext Result.failure(Exception("Already recording"))
            }
            
            // Create temp file
            val fileName = "voice_${UUID.randomUUID()}.m4a"
            recordingFile = File(context.cacheDir, fileName)
            
            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(recordingFile!!.absolutePath)
                
                prepare()
                start()
            }
            
            isRecording = true
            startTime = System.currentTimeMillis()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to start recording", e)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Stop recording and return file URI
     */
    suspend fun stopRecording(): Result<VoiceRecordingResult> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.failure(Exception("Not recording"))
            }
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            val file = recordingFile ?: return@withContext Result.failure(Exception("No recording file"))
            
            isRecording = false
            
            Result.success(
                VoiceRecordingResult(
                    uri = Uri.fromFile(file),
                    duration = duration,
                    waveform = generateWaveform(file)
                )
            )
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to stop recording", e)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Cancel recording and delete file
     */
    fun cancelRecording() {
        cleanup()
    }
    
    /**
     * Get current recording duration in seconds
     */
    fun getCurrentDuration(): Int {
        return if (isRecording) {
            ((System.currentTimeMillis() - startTime) / 1000).toInt()
        } else {
            0
        }
    }
    
    /**
     * Generate waveform data from audio file
     */
    private fun generateWaveform(file: File): List<Float> {
        // Simple waveform generation
        // In production, you'd use proper audio analysis
        val waveform = mutableListOf<Float>()
        val samples = 50 // Number of waveform bars
        
        try {
            val fileSize = file.length()
            val chunkSize = fileSize / samples
            
            file.inputStream().use { input ->
                repeat(samples) {
                    val bytes = ByteArray(chunkSize.toInt())
                    input.read(bytes)
                    
                    // Calculate amplitude (simplified)
                    val amplitude = bytes.map { it.toInt().toFloat() }
                        .average()
                        .toFloat()
                        .coerceIn(0f, 1f)
                    
                    waveform.add(amplitude)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to generate waveform", e)
            // Return default waveform
            repeat(samples) { waveform.add(0.5f) }
        }
        
        return waveform
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                if (isRecording) {
                    stop()
                }
                release()
            }
            mediaRecorder = null
            
            recordingFile?.delete()
            recordingFile = null
            
            isRecording = false
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Cleanup error", e)
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        cleanup()
    }
}

/**
 * Voice recording result
 */
data class VoiceRecordingResult(
    val uri: Uri,
    val duration: Int,
    val waveform: List<Float>
)

/**
 * Voice message player
 */
class VoicePlayer(private val context: Context) {
    
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var isPlaying = false
    private var currentPosition = 0
    private var duration = 0
    
    /**
     * Play voice message
     */
    suspend fun play(url: String, onProgress: (Int) -> Unit, onComplete: () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stop()
            
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(url)
                prepare()
                 this@VoicePlayer.duration = this.duration
                
                setOnCompletionListener {
                    this@VoicePlayer.isPlaying = false
                    this@VoicePlayer.currentPosition = 0
                    onComplete()
                }
                
                start()
                this@VoicePlayer.isPlaying = true
            }
            
            // Update progress
            while (isPlaying && mediaPlayer != null) {
                currentPosition = mediaPlayer?.currentPosition ?: 0
                onProgress(currentPosition)
                kotlinx.coroutines.delay(100)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Playback error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
    }
    
    /**
     * Resume playback
     */
    fun resume() {
        mediaPlayer?.start()
        isPlaying = true
    }
    
    /**
     * Stop playback
     */
    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        isPlaying = false
        currentPosition = 0
    }
    
    /**
     * Seek to position
     */
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        currentPosition = position
    }
    
    /**
     * Set playback speed
     */
    fun setSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed) ?: return
        }
    }
    
    /**
     * Get duration
     */
    fun getDuration(): Int = duration
    
    /**
     * Get current position
     */
    fun getCurrentPosition(): Int = currentPosition
    
    /**
     * Check if playing
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * Release resources
     */
    fun release() {
        stop()
    }
}
