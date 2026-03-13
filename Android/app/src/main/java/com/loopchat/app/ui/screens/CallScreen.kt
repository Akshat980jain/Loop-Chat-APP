package com.loopchat.app.ui.screens

import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    var isMuted by remember { mutableStateOf(false) }
    var isVideoOff by remember { mutableStateOf(callType == "audio") }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var callDuration by remember { mutableIntStateOf(0) }
    var callStatus by remember { mutableStateOf(if (isIncoming) "Connecting..." else "Initiating...") }
    var isConnected by remember { mutableStateOf(false) }
    var callId by remember { mutableStateOf<String?>(initialCallId) }
    var callEnded by remember { mutableStateOf(false) }
    var roomUrl by remember { mutableStateOf<String?>(initialRoomUrl) } // Dynamic Daily.co room URL
    var meetingToken by remember { mutableStateOf<String?>(initialCalleeToken) } // Meeting token for authenticated access
    var roomName by remember { mutableStateOf<String?>(null) } // Room name for token requests
    var isRinging by remember { mutableStateOf(false) } // Track if call is ringing
    var callTimeoutReached by remember { mutableStateOf(false) } // Track if timeout occurred
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Camera state
    var isFrontCamera by remember { mutableStateOf(true) }
    
    // Daily.co call state
    val dailyIsJoined by DailyCallManager.isJoined.collectAsState()
    val dailyHasRemote by DailyCallManager.hasRemoteParticipant.collectAsState()
    val dailyCallEnded by DailyCallManager.callEnded.collectAsState()
    val dailyError by DailyCallManager.error.collectAsState()
    
    // Initialize Daily.co and CallSoundManager when screen loads
    LaunchedEffect(Unit) {
        DailyCallManager.initialize(context)
        CallSoundManager.initialize(context)
    }
    
    // Join the native Daily.co call when connected (enables camera/mic via native SDK)
    LaunchedEffect(isConnected, roomUrl) {
        if (isConnected && roomUrl != null) {
            Log.d(TAG, "Joining native Daily.co call: $roomUrl, isVideo=${callType == "video"}")
            DailyCallManager.joinCall(
                roomUrl = roomUrl!!,
                meetingToken = meetingToken,
                isVideoCall = callType == "video"
            )
        }
    }
    
    // Cleanup sounds and Daily.co connection when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            CallSoundManager.stopAllSounds()
            // Full cleanup resets ALL state so next call starts fresh
            DailyCallManager.cleanup()
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
    
    // Fetch callee/caller profile from database
    var otherProfile by remember { mutableStateOf<Profile?>(null) }
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
                    var createdCallerToken: String? = null
                    var createdCalleeToken: String? = null
                    var createdRoomName: String? = generatedRoomName
                    var edgeFunctionWorked = false
                    
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
                            throw Exception("Edge function returned ${roomResponse.status}: $errorBody")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "=== EDGE FUNCTION FAILED ===")
                        Log.e(TAG, "Error: ${e.message}")
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
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating call: ${e.message}")
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
                        parameter("select", "id,room_url,callee_token")
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
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching room URL: ${e.message}")
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
                    parameter("select", "id,status")
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
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not verify call status: ${e.message}, proceeding anyway")
            }
            
            callStatus = "Connected"
            isConnected = true
            Log.d(TAG, "=== CALLEE CONNECTED ===")
            Log.d(TAG, "Final room URL: $roomUrl")
            Log.d(TAG, "Final meeting token present: ${!meetingToken.isNullOrEmpty()}")
            return@LaunchedEffect
        }
        
        // For outgoing calls, poll until callee accepts or rejects
        val currentCallId = callId ?: return@LaunchedEffect
        
        while (!isConnected && !callEnded) {
            delay(2000) // Poll every 2 seconds
            
            try {
                val response = httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/calls") {
                    parameter("select", "id,status,started_at")
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
                            CallSoundManager.stopAllSounds()
                            isRinging = false
                            callStatus = "Connecting..."
                            // Use the room URL we created earlier
                            Log.d(TAG, "Caller connecting to room: $roomUrl")
                            delay(500)
                            callStatus = "Connected"
                            isConnected = true
                            Log.d(TAG, "Call accepted, joined Daily room!")
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
                            callStatus = "Ringing..."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling call status: ${e.message}")
            }
        }
    }
    
    // Duration timer - only runs when connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }
    
    // Handle end call - update status in database
    val handleEndCall: () -> Unit = {
        // Leave Daily.co room first
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
        // Decorative gradient orbs in background
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
                .blur(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.25f),
                            androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-50).dp, y = 50.dp)
                .blur(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Secondary.copy(alpha = 0.2f),
                            androidx.compose.ui.graphics.Color.Transparent
                        )
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
                // DailyCallManager handles the actual WebRTC video/audio — just show avatar UI
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background),
                    contentAlignment = Alignment.Center
                ) {
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
                
                // Call duration overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface.copy(alpha = 0.8f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Success)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedDuration,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
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
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.weight(0.7f))
            }
        }
        
        // Call controls (always visible at bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute button
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    onClick = { 
                        isMuted = !isMuted
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
                            isVideoOff = !isVideoOff
                            DailyCallManager.toggleVideo()
                        }
                    )
                }
                
                // Speaker button
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    isActive = isSpeakerOn,
                    onClick = { 
                        isSpeakerOn = !isSpeakerOn
                        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                        audioManager.isSpeakerphoneOn = isSpeakerOn
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // End call button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Error),
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
                .size(56.dp)
                .then(
                    if (isActive) {
                        Modifier.border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(AvatarBorderGradient),
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(
                    if (isActive) Surface else SurfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Primary else TextSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

