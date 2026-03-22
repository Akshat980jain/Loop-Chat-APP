package com.loopchat.app.ui.screens

import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.loopchat.app.BuildConfig
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Call
import com.loopchat.app.data.models.Profile
import com.loopchat.app.ui.components.GradientAvatar
import com.loopchat.app.ui.theme.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.loopchat.app.ui.components.CameraPreview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.Manifest
import androidx.compose.foundation.shape.RoundedCornerShape
import com.loopchat.app.data.DailyCallManager
import com.loopchat.app.data.IncomingCallManager
import com.loopchat.app.data.CallSoundManager
import androidx.compose.ui.platform.LocalContext
import com.loopchat.app.ui.components.DailyVideoView
import co.daily.view.VideoView
import androidx.camera.lifecycle.ProcessCameraProvider

private const val TAG = "CallScreen"
private const val CALL_TIMEOUT_MS = 30000L // 30 seconds timeout for unanswered calls

// Response models for the daily-room edge function
private val edgeFunctionJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class DailyRoomResponse(
    val success: Boolean = false,
    val room: DailyRoom? = null,
    val callerToken: String? = null,
    val calleeToken: String? = null,
    val error: String? = null
)

@Serializable
private data class DailyRoom(
    val name: String? = null,
    val url: String? = null
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(
    calleeId: String,
    callType: String,
    calleeName: String? = null,
    isIncoming: Boolean = false, // true if this is a received call
    initialCallId: String? = null,
    initialRoomUrl: String? = null,
    initialCalleeToken: String? = null,
    onEndCall: () -> Unit
) {
    // Mute and video state — read from DailyCallManager as single source of truth
    val isMuted by DailyCallManager.isMuted.collectAsState()
    val dailyVideoEnabled by DailyCallManager.isVideoEnabled.collectAsState()
    val isVideoOff = !dailyVideoEnabled || callType == "audio"
    var isSpeakerOn by remember { mutableStateOf(true) }
    var callDuration by remember { mutableIntStateOf(0) }
    var callStatus by remember { mutableStateOf(if (isIncoming) "Connecting..." else "Initiating...") }
    var isConnected by remember { mutableStateOf(false) }
    var readyToJoin by remember { mutableStateOf(false) }
    var hasInitiatedJoin by remember { mutableStateOf(false) }
    var callId by remember { mutableStateOf<String?>(initialCallId) }
    var callEnded by remember { mutableStateOf(false) }
    var roomUrl by remember { mutableStateOf<String?>(initialRoomUrl) } // Dynamic Daily.co room URL
    var meetingToken by remember { mutableStateOf<String?>(initialCalleeToken) } // Meeting token for authenticated access
    var isRinging by remember { mutableStateOf(false) } // Track if call is ringing
    var callTimeoutReached by remember { mutableStateOf(false) } // Track if timeout occurred
    var errorLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val addErrorLog = { log: String ->
        errorLogs = errorLogs + "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}: $log"
    }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Camera state
    var isFrontCamera by remember { mutableStateOf(true) }
    
    // Daily.co call state
    val dailyHasRemote by DailyCallManager.hasRemoteParticipant.collectAsState()
    val dailyLocalParticipant by DailyCallManager.localParticipant.collectAsState()
    val dailyRemoteParticipants by DailyCallManager.remoteParticipants.collectAsState()
    val dailyCallEnded by DailyCallManager.callEnded.collectAsState()
    val dailyError by DailyCallManager.error.collectAsState()
    
    // Add daily error to logs if it changes
    LaunchedEffect(dailyError) {
        dailyError?.let { 
            addErrorLog("Daily.co SDK Error: $it") 
            callStatus = "Error connecting media"
        }
    }
    
    // Sync UI with Daily.co native state
    val dailyCallState by DailyCallManager.callState.collectAsState()
    LaunchedEffect(dailyCallState) {
        when (dailyCallState) {
            co.daily.model.CallState.joining -> callStatus = "Connecting to media..."
            co.daily.model.CallState.joined -> {
                callStatus = "Connected"
                isConnected = true
            }
            else -> {}
        }
    }
    
    // Initialize Daily.co and CallSoundManager when screen loads
    LaunchedEffect(Unit) {
        DailyCallManager.initialize(context)
        CallSoundManager.initialize(context)
    }
    
    
    // (Removed automatic native join here to let the polling/incoming logic handle connecting strictly when accepted)
    
    // Cleanup sounds and Daily.co connection when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            CallSoundManager.stopAllSounds()
            // Full cleanup resets ALL state so next call starts fresh
            DailyCallManager.cleanup()
            // Restore audio routing to normal
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        }
    }
    
    // JSON and HTTP client for API calls
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    val httpClient = remember {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
    
    // 30-second timeout for outgoing calls
    LaunchedEffect(isRinging, isConnected, callEnded) {
        if (isRinging && !isConnected && !callEnded && !isIncoming) {
            // Start timeout timer
            delay(CALL_TIMEOUT_MS)
            
            // If still ringing after 30 seconds, timeout
            if (isRinging && !isConnected && !callEnded) {
                callTimeoutReached = true
                callEnded = true
                callStatus = "No Answer"
                
                // Play no answer/busy tone
                CallSoundManager.stopAllSounds()
                CallSoundManager.playBusyTone()
                
                // Update call status in database
                val accessToken = SupabaseClient.getAccessToken()
                val currentCallId = callId
                if (accessToken != null && currentCallId != null) {
                    try {
                        httpClient.request("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                            method = HttpMethod.Patch
                            contentType(ContentType.Application.Json)
                            parameter("id", "eq.$currentCallId")
                            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                            header("Authorization", "Bearer $accessToken")
                            setBody(mapOf(
                                "status" to "missed",
                                "ended_at" to java.time.Instant.now().toString()
                            ))
                        }
                        Log.d(TAG, "Call marked as missed due to timeout")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating call status: ${e.message}")
                    }
                }
                
                delay(3000) // Let busy tone play for a bit
                CallSoundManager.stopAllSounds()
                onEndCall()
            }
        }
    }
    
    // Handle Daily.co call ended (remote hangup or network disconnect)
    LaunchedEffect(dailyCallEnded) {
        if (dailyCallEnded && isConnected) {
            Log.d(TAG, "Daily.co native call ended - ending call screen")
            callStatus = "Call ended"
            CallSoundManager.playEndCallTone()
            delay(1500)
            onEndCall()
        }
    }
    
    // Permission handling for video calls
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )
    
    // Request permissions immediately when call screen loads
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.allPermissionsGranted) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
    }
    
    // Join the Daily room only when both ready (DB confirmed accepted) AND permissions granted
    LaunchedEffect(readyToJoin, cameraPermissionState.allPermissionsGranted) {
        if (readyToJoin && cameraPermissionState.allPermissionsGranted && !hasInitiatedJoin && roomUrl != null) {
            hasInitiatedJoin = true
            // Force-release CameraX so Daily.co SDK can access the camera hardware
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
                Log.d(TAG, "CameraX released before Daily.co join")
            } catch (e: Exception) {
                Log.w(TAG, "CameraX release failed: ${e.message}")
            }
            delay(300) // Brief delay for hardware handoff
            DailyCallManager.initialize(context)
            Log.d(TAG, "Permissions granted and ready. Joining Daily room: $roomUrl")
            DailyCallManager.joinCall(
                roomUrl = roomUrl!!,
                meetingToken = meetingToken,
                isVideoCall = callType == "video"
            )
        }
    }
    
    // Fetch profiles from database
    var otherProfile by remember { mutableStateOf<Profile?>(null) }
    var ownProfile by remember { mutableStateOf<Profile?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }
    
    // Load other user's profile
    LaunchedEffect(calleeId) {
        if (calleeId.isNotEmpty()) {
            isLoadingProfile = true
            val result = SupabaseRepository.getProfileById(calleeId)
            result.onSuccess { profile ->
                otherProfile = profile
            }
            isLoadingProfile = false
        }
    }
    
    // Load own profile to send correct caller name in push notifications
    val currentUserId = SupabaseClient.currentUserId
    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            val result = SupabaseRepository.getProfileById(currentUserId)
            result.onSuccess { profile ->
                ownProfile = profile
            }
        }
    }
    
    // Determine display name
    val displayName = calleeName 
        ?: otherProfile?.fullName 
        ?: otherProfile?.username 
        ?: if (isLoadingProfile) "Loading..." else "Unknown"
    
    val displayInitial = displayName.firstOrNull()?.toString()?.uppercase() ?: "?"
    
    // Create call record in Supabase (for outgoing calls only)
    LaunchedEffect(Unit) {
        if (!isIncoming) {
            val accessToken = SupabaseClient.getAccessToken()
            val currentUserId = SupabaseClient.currentUserId
            
            Log.d(TAG, "=== OUTGOING CALL INITIALIZATION ===")
            Log.d(TAG, "Current User ID: $currentUserId")
            Log.d(TAG, "Callee ID: $calleeId")
            Log.d(TAG, "Call Type: $callType")
            Log.d(TAG, "Access Token present: ${accessToken != null}")
            
            if (accessToken != null && currentUserId != null) {
            try {
                    callStatus = "Creating room..."
                    
                    // Generate a unique room name based on caller + callee + timestamp
                    // This ensures both users can join the same room
                    val userIds = listOf(currentUserId, calleeId).sorted()
                    val roomTimestamp = System.currentTimeMillis()
                    val generatedRoomName = "loop-${userIds[0].take(8)}-${userIds[1].take(8)}-$roomTimestamp"
                    
                    Log.d(TAG, "=== STEP 1: Creating Daily.co Room ===")
                    Log.d(TAG, "Generated room name: $generatedRoomName")
                    
                    var createdRoomUrl: String
                    var createdCallerToken: String?
                    var createdCalleeToken: String?
                    var createdRoomName: String?
                    var edgeFunctionWorked: Boolean
                    
                    // Try the edge function first (requires DAILY_API_KEY configured in Supabase)
                    try {
                        val roomResponse = httpClient.post("${BuildConfig.SUPABASE_URL}/functions/v1/daily-room") {
                            contentType(ContentType.Application.Json)
                            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                            header("Authorization", "Bearer $accessToken")
                            setBody(mapOf(
                                "action" to "create",
                                "roomName" to generatedRoomName,
                                "callerId" to currentUserId,
                                "calleeId" to calleeId,
                                "callType" to callType
                            ))
                        }
                        
                        Log.d(TAG, "Room creation response status: ${roomResponse.status}")
                        
                        if (roomResponse.status.isSuccess()) {
                            val roomData = roomResponse.bodyAsText()
                            Log.d(TAG, "=== ROOM CREATION SUCCESS ===")
                            Log.d(TAG, "Full response: $roomData")
                            
                            // Parse response using proper JSON deserialization
                            val parsed = edgeFunctionJson.decodeFromString<DailyRoomResponse>(roomData)
                            
                            if (parsed.success && !parsed.room?.url.isNullOrEmpty()) {
                                createdRoomUrl = parsed.room!!.url!!
                                edgeFunctionWorked = true
                                Log.d(TAG, "Room URL: $createdRoomUrl")
                                
                                createdCallerToken = parsed.callerToken
                                meetingToken = createdCallerToken
                                Log.d(TAG, "Caller Token received: ${createdCallerToken != null}")
                                
                                createdCalleeToken = parsed.calleeToken
                                Log.d(TAG, "Callee Token received: ${createdCalleeToken != null}")
                                
                                createdRoomName = parsed.room.name
                                Log.d(TAG, "Room Name: $createdRoomName")
                            } else {
                                throw Exception(parsed.error ?: "No URL in response")
                            }
                        } else {
                            val errorBody = roomResponse.bodyAsText()
                            Log.e(TAG, "Edge function failed: ${roomResponse.status} - $errorBody")
                            addErrorLog("Edge function failed: ${roomResponse.status} - $errorBody")
                            throw Exception("Edge function returned ${roomResponse.status}: $errorBody")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "=== EDGE FUNCTION FAILED ===")
                        Log.e(TAG, "Error: ${e.message}")
                        addErrorLog("Edge function exception: ${e.message}")
                        // Don't use static fallback — show error instead
                        callStatus = "Failed to create room: ${e.message?.take(80)}"
                        return@LaunchedEffect
                    }
                    
                    roomUrl = createdRoomUrl
                    Log.d(TAG, "=== FINAL ROOM URL: $createdRoomUrl ===")
                    Log.d(TAG, "Edge function worked: $edgeFunctionWorked")
                    
                    callStatus = "Creating call..."
                    
                    // Step 2: Create call record with the room URL and tokens
                    val response = httpClient.post("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                        contentType(ContentType.Application.Json)
                        header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        header("Authorization", "Bearer $accessToken")
                        header("Prefer", "return=representation")
                        // Include all columns including tokens for secure authentication
                        setBody(mapOf(
                            "caller_id" to currentUserId,
                            "callee_id" to calleeId,
                            "call_type" to callType,
                            "status" to "ringing",
                            "room_url" to createdRoomUrl,
                            "caller_token" to (createdCallerToken ?: ""),
                            "callee_token" to (createdCalleeToken ?: ""),
                            "room_name" to (createdRoomName ?: "")
                        ))
                    }
                    
                    if (response.status.isSuccess()) {
                        val calls: List<Call> = response.body()
                        if (calls.isNotEmpty()) {
                            callId = calls.first().id
                            callStatus = "Ringing..."
                            isRinging = true
                            // Start ringback tone so caller knows it's ringing
                            CallSoundManager.playRingbackTone()
                            Log.d(TAG, "Call created with ID: ${callId}, room: $createdRoomUrl")
                            
                            // Send FCM push notification to callee so they get notified even if app is closed
                            try {
                                val pushResponse = httpClient.post("${BuildConfig.SUPABASE_URL}/functions/v1/send-call-notification") {
                                    contentType(ContentType.Application.Json)
                                    header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                                    header("Authorization", "Bearer $accessToken")
                                    setBody(mapOf(
                                        "callId" to callId,
                                        "callerId" to currentUserId,
                                        "calleeId" to calleeId,
                                        "callerName" to (ownProfile?.fullName ?: ownProfile?.username ?: "Loop User"),
                                        "callType" to callType,
                                        "roomUrl" to createdRoomUrl,
                                        "calleeToken" to (createdCalleeToken ?: "")
                                    ))
                                }
                                Log.d(TAG, "FCM push notification result: ${pushResponse.status}")
                                if (!pushResponse.status.isSuccess()) {
                                    val pushError = pushResponse.bodyAsText()
                                    Log.w(TAG, "FCM push failed: $pushError")
                                    addErrorLog("FCM push: $pushError")
                                }
                            } catch (pushEx: Exception) {
                                // Non-critical: call still works via in-app polling
                                Log.w(TAG, "FCM push notification failed: ${pushEx.message}")
                                addErrorLog("FCM push exception: ${pushEx.message}")
                            }
                        }
                    } else {
                        val errorBody = response.bodyAsText()
                        Log.e(TAG, "Failed to create call: ${response.status} - $errorBody")
                        callStatus = if (errorBody.contains("column", ignoreCase = true) || 
                                        errorBody.contains("does not exist", ignoreCase = true)) {
                            "Database schema error - run fix_all_calling_issues.sql"
                        } else if (response.status.value == 403) {
                            "Permission denied - check RLS policies"
                        } else {
                            "Failed to create call (${response.status.value})"
                        }
                        addErrorLog("Failed to create call request: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating call: ${e.message}")
                    addErrorLog("Exception creating call: ${e.message}")
                    callStatus = "Connection error"
                }
            } else {
                callStatus = "Not authenticated"
            }
        } else {
            // For incoming calls, we're already connected since we accepted
            callStatus = "Connecting..."
        }
    }
    
    // Poll for call status changes
    LaunchedEffect(callId, isIncoming) {
        val accessToken = SupabaseClient.getAccessToken() ?: return@LaunchedEffect
        
        // For incoming calls, fetch room URL from call record and connect
        if (isIncoming) {
            Log.d(TAG, "=== INCOMING CALL FLOW ===")
            delay(500) // Brief delay for UI feedback
            callStatus = "Getting room..."
            
            // Use the preserved accepted call data or the initial properties passed from the intent
            val acceptedCallData = IncomingCallManager.getLastAcceptedCall()
            var fetchedRoomUrl = acceptedCallData?.call?.roomUrl ?: roomUrl
            var fetchedCalleeToken = acceptedCallData?.call?.calleeToken ?: meetingToken
            val acceptedCallId = acceptedCallData?.call?.id ?: callId
            
            Log.d(TAG, "Accepted call data present: ${acceptedCallData != null} or using initial parameters")
            Log.d(TAG, "Call ID: $acceptedCallId")
            Log.d(TAG, "Room URL from cache: $fetchedRoomUrl")
            Log.d(TAG, "Callee token from cache: ${fetchedCalleeToken?.take(20)}...")
            
            // Set token from cached data immediately if available
            if (!fetchedCalleeToken.isNullOrEmpty()) {
                meetingToken = fetchedCalleeToken
                Log.d(TAG, "Set meeting token from cached call data")
            }
            
            // If room URL not in cached data, fetch from database directly
            if (fetchedRoomUrl.isNullOrEmpty() && acceptedCallId != null) {
                Log.d(TAG, "=== FETCHING FROM DATABASE ===")
                try {
                    Log.d(TAG, "Fetching room URL and callee token from database for call: $acceptedCallId")
                    val response = httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                        parameter("select", "*")
                        parameter("id", "eq.$acceptedCallId")
                        header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        header("Authorization", "Bearer $accessToken")
                        header("Accept", "application/vnd.pgrst.object+json")
                    }
                    Log.d(TAG, "Database response status: ${response.status}")
                    if (response.status.isSuccess()) {
                        val call: Call = response.body()
                        fetchedRoomUrl = call.roomUrl
                        fetchedCalleeToken = call.calleeToken
                        
                        Log.d(TAG, "DB room URL: $fetchedRoomUrl")
                        Log.d(TAG, "DB callee token: ${fetchedCalleeToken?.take(20)}...")
                        
                        if (!fetchedCalleeToken.isNullOrEmpty()) {
                            meetingToken = fetchedCalleeToken
                            Log.d(TAG, "Set meeting token from DB")
                        }
                    } else {
                        Log.e(TAG, "Database fetch failed: ${response.status}")
                        addErrorLog("Failed to fetch room from DB. Code: ${response.status}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching room URL: ${e.message}")
                    addErrorLog("Exception fetching room from DB: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Clear the accepted call data now that we've read it
            IncomingCallManager.clearAcceptedCall()
            
            if (!fetchedRoomUrl.isNullOrEmpty()) {
                roomUrl = fetchedRoomUrl
                Log.d(TAG, "=== CALLEE JOINING ROOM ===")
                Log.d(TAG, "Room URL: $fetchedRoomUrl")
                Log.d(TAG, "Has meeting token: ${!meetingToken.isNullOrEmpty()}")
            } else {
                Log.e(TAG, "=== NO ROOM URL FOUND - CANNOT JOIN ===")
                callStatus = "No room URL found — call cannot connect"
                return@LaunchedEffect
            }
            
            // Verify that the acceptance was actually recorded in the DB
            callStatus = "Verifying connection..."
            try {
                val verifyResponse = httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                    parameter("select", "*")
                    parameter("id", "eq.${acceptedCallId ?: callId}")
                    header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.pgrst.object+json")
                }
                if (verifyResponse.status.isSuccess()) {
                    val verifiedCall: Call = verifyResponse.body()
                    if (verifiedCall.status == "accepted") {
                        Log.d(TAG, "=== DB CONFIRMED: Call accepted ===")
                    } else {
                        Log.w(TAG, "Call status in DB: ${verifiedCall.status}, proceeding anyway")
                        addErrorLog("Warning: DB status is '${verifiedCall.status}', not 'accepted'")
                    }
                } else {
                    val errorBody = verifyResponse.bodyAsText()
                    Log.e(TAG, "Verify response failed: ${verifyResponse.status} - $errorBody")
                    addErrorLog("Verification failed: ${verifyResponse.status} - $errorBody")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not verify call status: ${e.message}, proceeding anyway")
                addErrorLog("Status verification error: ${e.message}")
            }
            
            // === READY TO JOIN DAILY.CO ROOM ===
            callStatus = "Joining room..."
            Log.d(TAG, "=== CALLEE READY TO JOIN DAILY.CO ROOM ===")
            Log.d(TAG, "Room URL: $roomUrl")
            Log.d(TAG, "Meeting token present: ${!meetingToken.isNullOrEmpty()}")
            
            readyToJoin = true
            // isConnected will be set to true by the dailyCallState observer once joined
            Log.d(TAG, "=== CALLEE WAITING FOR PERMISSIONS TO CONNECT ===")
            return@LaunchedEffect
        }
        
        // For outgoing calls, poll until callee accepts or rejects
        val currentCallId = callId ?: return@LaunchedEffect
        
        while (!isConnected && !callEnded) {
            delay(2000) // Poll every 2 seconds
            
            try {
                val response = httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                    parameter("select", "*")
                    parameter("id", "eq.$currentCallId")
                    header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.pgrst.object+json")
                }
                
                if (response.status.isSuccess()) {
                    val call: Call = response.body()
                    
                    when (call.status) {
                        "accepted" -> {
                            // Stop ringback tone
                            Log.d(TAG, "Call accepted in DB! Connecting...")
                            CallSoundManager.stopAllSounds()
                            isRinging = false
                            callStatus = "Connecting..."
                            
                            // === READY TO JOIN DAILY.CO ROOM ===
                            Log.d(TAG, "=== CALLER READY TO JOIN DAILY.CO ROOM ===")
                            Log.d(TAG, "Room URL: $roomUrl")
                            Log.d(TAG, "Meeting token present: ${!meetingToken.isNullOrEmpty()}")
                            
                            delay(500)
                            callStatus = "Joining room..."
                            readyToJoin = true
                            // isConnected will be set to true by the dailyCallState observer once joined
                            Log.d(TAG, "Caller waiting for permissions to connect to Daily room!")
                        }
                        "rejected" -> {
                            CallSoundManager.stopAllSounds()
                            isRinging = false
                            callStatus = "Call declined"
                            callEnded = true
                            Log.d(TAG, "Call rejected - playing verbal feedback")
                            // Play verbal feedback: "Please call again later"
                            CallSoundManager.playCallRejectedFeedback()
                            delay(2500) // Wait for TTS to complete
                            onEndCall()
                        }
                        "ended", "cancelled", "missed" -> {
                            CallSoundManager.stopAllSounds()
                            isRinging = false
                            callStatus = "Call ended"
                            callEnded = true
                            Log.d(TAG, "Call ended")
                            CallSoundManager.playEndCallTone()
                            delay(1500)
                            onEndCall()
                        }
                        "ringing" -> {
                            // Still ringing, continue polling
                            // callStatus = "Ringing..."
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to poll call status. Code: ${response.status}")
                    addErrorLog("Polling call status failed: ${response.status}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling call status: ${e.message}")
                addErrorLog("Exception polling call status: ${e.message}")
            }
        }
    }
    
    // Duration timer - only runs when connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            // Set audio mode to communication so speaker toggle works
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            // Actually activate speaker since isSpeakerOn defaults to true
            if (isSpeakerOn) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val devices = audioManager.availableCommunicationDevices
                    val speaker = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    speaker?.let { audioManager.setCommunicationDevice(it) }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            }
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }
    
    val handleEndCall: () -> Unit = {
        // Restore audio routing before ending
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        
        // Leave Daily.co room
        DailyCallManager.leaveCall()
        
        coroutineScope.launch {
            val accessToken = SupabaseClient.getAccessToken()
            val currentCallId = callId
            
            if (accessToken != null && currentCallId != null) {
                try {
                    httpClient.request("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                        method = HttpMethod.Patch
                        contentType(ContentType.Application.Json)
                        parameter("id", "eq.$currentCallId")
                        header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        header("Authorization", "Bearer $accessToken")
                        setBody(mapOf(
                            "status" to "ended",
                            "ended_at" to java.time.Instant.now().toString()
                        ))
                    }
                    Log.d(TAG, "Call ended in database")
                } catch (e: Exception) {
                    Log.e(TAG, "Error ending call: ${e.message}")
                }
            }
            onEndCall()
        }
    }
    
    // Format duration as MM:SS
    val formattedDuration = remember(callDuration) {
        val minutes = callDuration / 60
        val seconds = callDuration % 60
        String.format("%02d:%02d", minutes, seconds)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Decorative gradient orbs for "Sunset" feel
        Box(
            modifier = Modifier
                .size(600.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-200).dp)
                .blur(150.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Primary.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100.dp), y = 150.dp)
                .blur(140.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Secondary.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )
        
        // When connected, show connected call UI (DailyCallManager handles all WebRTC audio/video)
        if (isConnected && cameraPermissionState.allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp) // Leave space for controls at bottom
            ) {
                val remoteParticipant = dailyRemoteParticipants.values.firstOrNull()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background),
                    contentAlignment = Alignment.Center
                ) {
                if (callType == "video") {
                        // Remote Participant Video (Full Screen)
                        if (remoteParticipant != null) {
                            val remoteVideoTrack = remoteParticipant.media?.camera?.track
                            val remoteCameraState = remoteParticipant.media?.camera?.state
                            
                            Log.d(TAG, "Remote participant: id=${remoteParticipant.id}, cameraState=$remoteCameraState, hasTrack=${remoteVideoTrack != null}")
                            
                            if (remoteCameraState == co.daily.model.MediaState.playable && remoteVideoTrack != null) {
                                key(remoteParticipant.id.toString()) {
                                    DailyVideoView(
                                        videoTrack = remoteVideoTrack,
                                        modifier = Modifier.fillMaxSize(),
                                        scaleMode = VideoView.VideoScaleMode.FILL
                                    )
                                }
                            } else {
                                // Remote connected but video off or track not ready
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GradientAvatar(initial = displayInitial, size = 120.dp, borderWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (remoteCameraState == co.daily.model.MediaState.off) 
                                            "$displayName (Camera Off)" 
                                        else 
                                            "$displayName (Connecting video...)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else if (dailyHasRemote) {
                            // Remote connected but no participant object yet
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                GradientAvatar(initial = displayInitial, size = 120.dp, borderWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "$displayName (Camera Off)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            // Waiting for remote
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                GradientAvatar(initial = displayInitial, size = 120.dp, borderWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Waiting for other user...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Local Participant Video (Picture-in-Picture) — larger and more visible
                        val localVideoTrack = dailyLocalParticipant?.media?.camera?.track
                        val localCameraState = dailyLocalParticipant?.media?.camera?.state
                        
                        if (dailyLocalParticipant != null && !isVideoOff && localCameraState == co.daily.model.MediaState.playable && localVideoTrack != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 16.dp, end = 16.dp)
                                    .size(140.dp, 200.dp)
                                    .shadow(12.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(2.dp, SurfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                                    .clickable { /* Tap to swap views in future */ }
                            ) {
                                key("local") {
                                    DailyVideoView(
                                        videoTrack = localVideoTrack,
                                        modifier = Modifier.fillMaxSize(),
                                        scaleMode = VideoView.VideoScaleMode.FILL
                                    )
                                }
                            }
                        }
                    } else {
                        // Audio Call UI
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GradientAvatar(
                                initial = displayInitial,
                                size = 120.dp,
                                borderWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (dailyHasRemote) "In Call" else "Waiting for other user...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (dailyHasRemote) Success else TextSecondary
                            )
                        }
                    }
                }
                
                // Premium Call Header (Glass)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .border(0.5.dp, SurfaceLight.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                ) {
                    // Blurry background layer
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(GlassBackground)
                            .blur(20.dp)
                    )
                    
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Success)
                                .shadow(8.dp, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = formattedDuration,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else if (isConnected && !cameraPermissionState.allPermissionsGranted) {
            // Connected but permissions not granted - show permission request UI
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Camera",
                    modifier = Modifier.size(64.dp),
                    tint = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Please grant camera and microphone permissions to continue the video call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { cameraPermissionState.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Grant Permissions")
                }
            }
        } else {
            // Not connected yet - show ringing UI
            // For video calls, show camera preview behind the ringing info
            if (callType == "video" && cameraPermissionState.allPermissionsGranted) {
                // Full-screen camera preview as background
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isFrontCamera = isFrontCamera,
                    isEnabled = !isVideoOff
                )
            }
            
            // Overlay caller info on top
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(0.3f))
                
                GradientAvatar(
                    initial = displayInitial,
                    size = 120.dp,
                    borderWidth = 3.dp
                )
            
                Spacer(modifier = Modifier.height(24.dp))
                
                // Name from database
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                // Call type indicator
                Text(
                    text = if (callType == "video") "Video Call" else "Audio Call",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Call status
                Text(
                    text = callStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                if (dailyError != null && roomUrl != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (cameraPermissionState.allPermissionsGranted) {
                                DailyCallManager.joinCall(
                                    roomUrl = roomUrl!!,
                                    meetingToken = meetingToken,
                                    isVideoCall = callType == "video"
                                )
                            } else {
                                cameraPermissionState.launchMultiplePermissionRequest()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("Retry Connection")
                    }
                }
                
                Spacer(modifier = Modifier.weight(0.7f))
            }
        }
        
        // Call controls (always visible at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            // Glass backdrop for controls
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(GlassBackground)
                    .blur(20.dp)
                    .border(1.dp, SurfaceLight.copy(alpha = 0.2f), RoundedCornerShape(40.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute button
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    onClick = { 
                        DailyCallManager.toggleMute()
                    }
                )
                
                // Video toggle (only for video calls)
                if (callType == "video") {
                    CallControlButton(
                        icon = if (isVideoOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        label = if (isVideoOff) "Video On" else "Video Off",
                        isActive = isVideoOff,
                        onClick = { 
                            DailyCallManager.toggleVideo()
                        }
                    )
                }
                
                // End call button (Prominent)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(72.dp)
                        .shadow(16.dp, CircleShape, spotColor = ErrorColor)
                        .clip(CircleShape)
                        .background(ErrorColor),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { handleEndCall() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(32.dp),
                            tint = TextPrimary
                        )
                    }
                }

                // Speaker button
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    isActive = isSpeakerOn,
                    onClick = { 
                        isSpeakerOn = !isSpeakerOn
                        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            if (isSpeakerOn) {
                                val devices = audioManager.availableCommunicationDevices
                                val speakerDevice = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                                if (speakerDevice != null) {
                                    audioManager.setCommunicationDevice(speakerDevice)
                                }
                            } else {
                                audioManager.clearCommunicationDevice()
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = isSpeakerOn
                        }
                    }
                )
                
                // Camera switch (only for video calls)
                if (callType == "video" && !isVideoOff) {
                    CallControlButton(
                        icon = Icons.Default.Refresh,
                        label = if (isFrontCamera) "Rear" else "Front",
                        isActive = !isFrontCamera,
                        onClick = { 
                            isFrontCamera = !isFrontCamera
                            DailyCallManager.switchCamera()
                        }
                    )
                }
            }
        }
        
        // Error Logs Overlay
        if (errorLogs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 64.dp)
                    .padding(bottom = 120.dp), // keep above call controls
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connection Logs",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { errorLogs = emptyList() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Logs",
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    errorLogs.forEach { log ->
                        Text(
                            text = log,
                            color = Color(0xFFFF8A80), // Light red for errors
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isActive) Primary.copy(alpha = 0.2f) else GlassBackground)
                .border(
                    width = 1.dp,
                    brush = if (isActive) Brush.linearGradient(PrimaryGradientColors) else Brush.linearGradient(listOf(SurfaceLight.copy(alpha = 0.3f), SurfaceLight.copy(alpha = 0.1f))),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Primary else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Primary else Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
    }
}

