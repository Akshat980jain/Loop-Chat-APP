package com.loopchat.app.ui.components

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraPreview"

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = true,
    isEnabled: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    
    // Initialize camera provider
    LaunchedEffect(Unit) {
        cameraProvider = context.getCameraProvider()
    }
    
    // Explicitly release camera hardware when preview is removed from the composition
    DisposableEffect(cameraProvider) {
        onDispose {
            cameraProvider?.unbindAll()
            Log.d(TAG, "CameraPreview disposed. Hardware lock released.")
        }
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isEnabled && cameraProvider != null) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    startCamera(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        cameraProvider = cameraProvider!!,
                        previewView = previewView,
                        isFrontCamera = isFrontCamera
                    )
                }
            )
        } else {
            Text(
                text = if (isEnabled) "Starting camera..." else "Camera off",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    isFrontCamera: Boolean
) {
    try {
        // Unbind all use cases before binding
        cameraProvider.unbindAll()
        
        // Camera selector
        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // Preview use case
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Bind to lifecycle
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview
        )
        
        Log.d(TAG, "Camera started successfully")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start camera: ${e.message}")
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                {
                    continuation.resume(future.get())
                },
                ContextCompat.getMainExecutor(this)
            )
        }
    }
