package com.loopchat.app.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.loopchat.app.ui.theme.Primary
import com.loopchat.app.ui.theme.TextSecondary

private const val TAG = "DailyCallWebView"

// WebRTC resource constants for camera and audio
private val WEBRTC_RESOURCES = arrayOf(
    PermissionRequest.RESOURCE_VIDEO_CAPTURE,
    PermissionRequest.RESOURCE_AUDIO_CAPTURE
)

/**
 * Callback interface for Daily.co call events
 */
interface DailyCallCallback {
    fun onJoined()
    fun onParticipantJoined(participantId: String)
    fun onParticipantLeft(participantId: String)
    fun onCallEnded()
    fun onError(error: String)
}

/**
 * A WebView-based component that loads a Daily.co room for real-time audio/video calls.
 * This approach leverages Daily.co's JavaScript API which handles WebRTC connections automatically.
 * 
 * WhatsApp-like flow:
 * 1. Room is created with meeting tokens via edge function
 * 2. Both caller and callee join using their respective tokens
 * 3. Daily.co handles all WebRTC complexity (STUN/TURN, ICE, etc.)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DailyCallWebView(
    roomUrl: String,
    userName: String,
    isVideoCall: Boolean,
    meetingToken: String? = null,  // Optional meeting token for authenticated access
    onJoined: (() -> Unit)? = null,
    onParticipantJoined: ((String) -> Unit)? = null,
    onParticipantLeft: ((String) -> Unit)? = null,
    onCallEnded: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    onConsole: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var connectionStatus by remember { mutableStateOf("Connecting...") }
    var hasJoined by remember { mutableStateOf(false) }
    var hasRemoteParticipant by remember { mutableStateOf(false) }
    
    // Build the full URL with token if provided
    val fullRoomUrl = remember(roomUrl, meetingToken) {
        if (!meetingToken.isNullOrEmpty()) {
            if (roomUrl.contains("?")) {
                "$roomUrl&t=$meetingToken"
            } else {
                "$roomUrl?t=$meetingToken"
            }
        } else {
            roomUrl
        }
    }
    
    Log.d(TAG, "========================================")
    Log.d(TAG, "Loading Daily.co room: $roomUrl")
    Log.d(TAG, "Has meeting token: ${!meetingToken.isNullOrEmpty()}")
    Log.d(TAG, "Full URL being loaded: $fullRoomUrl")
    Log.d(TAG, "Is video call: $isVideoCall")
    Log.d(TAG, "========================================")
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    Log.d(TAG, "Creating WebView for Daily.co room: $fullRoomUrl")
                    
                    // Configure WebView settings for video calls
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        
                        // CRITICAL: Enable mixed content mode for WebRTC camera access
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // Enable hardware acceleration
                        setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                        
                        // Additional settings for WebRTC
                        databaseEnabled = true
                        setGeolocationEnabled(true)
                        
                        // User agent to ensure proper WebRTC support
                        userAgentString = userAgentString + " LoopChat/1.0"
                    }
                    
                    // Add JavaScript interface for communication
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onJoinedMeeting() {
                            Log.d(TAG, "JS Bridge: User joined meeting")
                            hasJoined = true
                            connectionStatus = "Connected"
                            onJoined?.invoke()
                        }
                        
                        @JavascriptInterface
                        fun onParticipantJoined(participantId: String) {
                            Log.d(TAG, "JS Bridge: Participant joined - $participantId")
                            hasRemoteParticipant = true
                            onParticipantJoined?.invoke(participantId)
                        }
                        
                        @JavascriptInterface
                        fun onParticipantLeft(participantId: String) {
                            Log.d(TAG, "JS Bridge: Participant left - $participantId")
                            onParticipantLeft?.invoke(participantId)
                        }
                        
                        @JavascriptInterface
                        fun onMeetingEnded() {
                            Log.d(TAG, "JS Bridge: Meeting ended")
                            onCallEnded?.invoke()
                        }
                        
                        @JavascriptInterface
                        fun onError(error: String) {
                            Log.e(TAG, "JS Bridge: Error - $error")
                            connectionStatus = "Error: $error"
                            onError?.invoke(error)
                        }
                    }, "AndroidBridge")
                    
                    // Handle permission requests for camera/microphone - CRITICAL for WebRTC
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            Log.d(TAG, "WebView permission request received: ${request?.resources?.joinToString()}")
                            
                            request?.let { req ->
                                // Filter for WebRTC resources (camera and microphone)
                                val grantedResources = req.resources.filter { resource ->
                                    resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                                    resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                    resource == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                                }.toTypedArray()
                                
                                if (grantedResources.isNotEmpty()) {
                                    // Grant permissions on UI thread
                                    post {
                                        try {
                                            req.grant(grantedResources)
                                            Log.d(TAG, "WebView permissions GRANTED: ${grantedResources.joinToString()}")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error granting WebView permissions: ${e.message}", e)
                                            // Try granting all requested resources as fallback
                                            try {
                                                req.grant(req.resources)
                                                Log.d(TAG, "Fallback: Granted all permissions")
                                            } catch (e2: Exception) {
                                                Log.e(TAG, "Fallback also failed: ${e2.message}")
                                            }
                                        }
                                    }
                                } else {
                                    Log.w(TAG, "No WebRTC resources in request, denying")
                                    post { req.deny() }
                                }
                            }
                        }
                        
                        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                            Log.d(TAG, "Permission request canceled: ${request?.resources?.joinToString()}")
                        }
                        
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                val message = "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                                if (it.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                                    Log.e(TAG, "WebView Console Error: $message")
                                    onConsole?.invoke("ERR: ${it.message()}")
                                } else {
                                    Log.d(TAG, "WebView Console: $message")
                                    // Optional: onConsole?.invoke(it.message()) for verbose logging
                                }
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                    
                    // Handle page loading
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "Page loaded: $url")
                            isLoading = false
                            connectionStatus = "Room loaded, waiting to join..."
                            
                            // Inject JavaScript to monitor Daily.co events
                            val jsMonitor = """
                                (function() {
                                    console.log('=== DAILY.CO WEBVIEW DEBUG ===');
                                    console.log('Page URL: ' + window.location.href);
                                    console.log('Document ready state: ' + document.readyState);
                                    
                                    // Log all available Daily objects
                                    console.log('window.DailyIframe exists: ' + (typeof window.DailyIframe !== 'undefined'));
                                    console.log('window.callFrame exists: ' + (typeof window.callFrame !== 'undefined'));
                                    console.log('window.Daily exists: ' + (typeof window.Daily !== 'undefined'));
                                    
                                    // Check for any video/audio elements
                                    var videos = document.getElementsByTagName('video');
                                    var audios = document.getElementsByTagName('audio');
                                    console.log('Video elements found: ' + videos.length);
                                    console.log('Audio elements found: ' + audios.length);
                                    
                                    // Try to hook into Daily.co events if available
                                    if (typeof window.callFrame !== 'undefined') {
                                        console.log('Daily callFrame is available! Hooking events...');
                                        
                                        window.callFrame.on('joined-meeting', function() {
                                            console.log('EVENT: joined-meeting');
                                            if (typeof AndroidBridge !== 'undefined') {
                                                AndroidBridge.onJoinedMeeting();
                                            }
                                        });
                                        
                                        window.callFrame.on('participant-joined', function(event) {
                                            console.log('EVENT: participant-joined', JSON.stringify(event));
                                            if (typeof AndroidBridge !== 'undefined') {
                                                AndroidBridge.onParticipantJoined(event.participant.user_id || 'unknown');
                                            }
                                        });
                                        
                                        window.callFrame.on('participant-left', function(event) {
                                            console.log('EVENT: participant-left', JSON.stringify(event));
                                            if (typeof AndroidBridge !== 'undefined') {
                                                AndroidBridge.onParticipantLeft(event.participant.user_id || 'unknown');
                                            }
                                        });
                                        
                                        window.callFrame.on('left-meeting', function() {
                                            console.log('EVENT: left-meeting');
                                            if (typeof AndroidBridge !== 'undefined') {
                                                AndroidBridge.onMeetingEnded();
                                            }
                                        });
                                        
                                        window.callFrame.on('error', function(event) {
                                            console.log('EVENT: error', JSON.stringify(event));
                                            if (typeof AndroidBridge !== 'undefined') {
                                                AndroidBridge.onError(event.errorMsg || 'Unknown error');
                                            }
                                        });
                                        
                                        // Get current participants
                                        try {
                                            var participants = window.callFrame.participants();
                                            console.log('Current participants: ' + JSON.stringify(participants));
                                        } catch(e) {
                                            console.log('Could not get participants: ' + e.message);
                                        }
                                    } else {
                                        console.log('Daily callFrame NOT available - prebuilt UI is handling everything');
                                    }
                                    
                                    // Fallback: Consider joined after page load
                                    setTimeout(function() {
                                        console.log('=== TIMEOUT CHECK (2s) ===');
                                        console.log('Video elements: ' + document.getElementsByTagName('video').length);
                                        console.log('Audio elements: ' + document.getElementsByTagName('audio').length);
                                        if (typeof AndroidBridge !== 'undefined') {
                                            AndroidBridge.onJoinedMeeting();
                                        }
                                    }, 2000);
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(jsMonitor, null)
                        }
                        
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Allow Daily.co URLs
                            val url = request?.url?.toString() ?: return false
                            return if (url.contains("daily.co")) {
                                false // Let WebView handle it
                            } else {
                                Log.d(TAG, "Blocking external URL: $url")
                                true // Block other URLs
                            }
                        }
                        
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            Log.e(TAG, "WebView error: $errorCode - $description at $failingUrl")
                            connectionStatus = "Error: $description"
                            onError?.invoke(description ?: "Unknown error")
                        }
                    }
                    
                    // Load the Daily.co room URL with token
                    loadUrl(fullRoomUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                // Can update WebView here if needed
            }
        )
        
        // Show loading indicator and status
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Primary)
                Text(
                    text = connectionStatus,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
