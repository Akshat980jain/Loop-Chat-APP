package com.loopchat.app.data.realtime

import android.content.Context
import android.util.Log
import com.loopchat.app.BuildConfig
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.local.LoopChatDatabase
import com.loopchat.app.data.models.Message
import com.loopchat.app.data.local.entities.*
import io.ktor.client.*
import io.ktor.client.request.header
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

object SupabaseRealtimeClient {
    private const val TAG = "SupabaseRealtime"
    private val supabaseUrl = BuildConfig.SUPABASE_URL.replace("https://", "wss://").replace("http://", "ws://")
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 30_000 // 30 seconds HTTP ping
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private var activeConversationId: String? = null
    private var dbContext: Context? = null

    fun getActiveConversationId(): String? = activeConversationId

    fun initialize(context: Context) {
        dbContext = context.applicationContext
    }

    suspend fun connectGlobal() {
        if (_isConnected.value) return
        disconnect()

        scope.launch {
            while (isActive) {
                val accessToken = SupabaseClient.getAccessToken()
                val currentUserId = SupabaseClient.currentUserId
                if (accessToken == null || currentUserId == null) {
                    Log.w(TAG, "Cannot connect: user not authenticated. Retrying in 5s...")
                    delay(5000)
                    continue
                }

                try {
                    val wsUrl = "$supabaseUrl/realtime/v1/websocket?apikey=$anonKey&token=$accessToken&vsn=1.0.0"
                    Log.d(TAG, "Connecting to global WebSocket: $wsUrl")
                    
                    client.webSocket(
                        urlString = wsUrl,
                        request = {
                            header("Authorization", "Bearer $accessToken")
                            header("apikey", anonKey)
                        }
                    ) {
                        session = this
                        _isConnected.value = true
                        Log.d(TAG, "Global WebSocket Connected")

                        // 1. Send Join Payload for the global messages channel
                        val globalJoinPayload = """
                            {
                              "topic": "realtime:public:messages",
                              "event": "phx_join",
                              "payload": {
                                "config": {
                                  "broadcast": { "self": false, "ack": false },
                                  "presence": { "key": "$currentUserId" },
                                  "postgres_changes": [
                                    {
                                      "event": "INSERT",
                                      "schema": "public",
                                      "table": "messages"
                                    }
                                  ]
                                },
                                "access_token": "$accessToken"
                              },
                              "ref": "join_global"
                            }
                        """.trimIndent()
                        send(Frame.Text(globalJoinPayload))

                        // Re-join the active conversation channel if the socket reconnected
                        activeConversationId?.let { convId ->
                            Log.d(TAG, "Re-joining active conversation channel on connect: $convId")
                            val freshToken = SupabaseClient.getAccessToken()
                            if (freshToken != null) {
                                val joinPayload = buildJoinPayload(convId, freshToken, currentUserId)
                                send(Frame.Text(joinPayload))
                            }
                        }

                        // 2. Start Heartbeat
                        startHeartbeat()

                        // 3. Listen to incoming messages
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleIncomingMessage(text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket connection error", e)
                } finally {
                    _isConnected.value = false
                    session = null
                    heartbeatJob?.cancel()
                    Log.d(TAG, "Global WebSocket Disconnected")
                }
                
                // Wait 5 seconds before attempting to reconnect
                delay(5000)
            }
        }
    }

    /**
     * Join a specific conversation's channel to listen to typing indicators and track presence.
     * Does NOT listen to Postgres insert events since the global channel handles all database inserts.
     */
    suspend fun joinConversation(conversationId: String) {
        activeConversationId = conversationId
        val accessToken = SupabaseClient.getAccessToken() ?: return
        val currentUserId = SupabaseClient.currentUserId ?: ""

        if (!_isConnected.value) {
            Log.w(TAG, "WebSocket is not connected. Cannot join conversation channel yet.")
            return
        }

        try {
            val joinPayload = """
                {
                  "topic": "realtime:public:messages:conversation_id=eq.$conversationId",
                  "event": "phx_join",
                  "payload": {
                    "config": {
                      "broadcast": { "self": false, "ack": false },
                      "presence": { "key": "$currentUserId" },
                      "postgres_changes": []
                    },
                    "access_token": "$accessToken"
                  },
                  "ref": "join_$conversationId"
                }
            """.trimIndent()
            session?.send(Frame.Text(joinPayload))

            // Small delay to ensure joined state before tracking presence
            delay(200)

            val trackPayload = """
                {
                  "topic": "realtime:public:messages:conversation_id=eq.$conversationId",
                  "event": "presence",
                  "payload": {
                    "type": "track",
                    "payload": {}
                  },
                  "ref": "track_$conversationId"
                }
            """.trimIndent()
            session?.send(Frame.Text(trackPayload))
            Log.d(TAG, "Joined conversation channel: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join conversation channel: $conversationId", e)
        }
    }

    /**
     * Leave a specific conversation's channel.
     */
    suspend fun leaveConversation(conversationId: String) {
        if (activeConversationId == conversationId) {
            activeConversationId = null
        }
        try {
            val leavePayload = """
                {
                  "topic": "realtime:public:messages:conversation_id=eq.$conversationId",
                  "event": "phx_leave",
                  "payload": {},
                  "ref": "leave_$conversationId"
                }
            """.trimIndent()
            session?.send(Frame.Text(leavePayload))
            Log.d(TAG, "Left conversation channel: $conversationId")
            _typingUsers.value = emptySet()
            _onlineUsers.value = emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave conversation channel: $conversationId", e)
        }
    }

    fun disconnect() {
        scope.launch {
            heartbeatJob?.cancel()
            session?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            session = null
            _isConnected.value = false
            activeConversationId = null
        }
    }

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers = _onlineUsers.asStateFlow()

    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
    val typingUsers = _typingUsers.asStateFlow()

    fun sendTypingEvent(isTyping: Boolean) {
        val conversationId = activeConversationId ?: return
        val userId = SupabaseClient.currentUserId ?: return
        scope.launch {
            try {
                val payload = """
                    {
                      "topic": "realtime:public:messages:conversation_id=eq.$conversationId",
                      "event": "broadcast",
                      "payload": {
                        "type": "broadcast",
                        "event": "typing",
                        "payload": {
                            "user_id": "$userId",
                            "is_typing": $isTyping
                        }
                      },
                      "ref": "typing"
                    }
                """.trimIndent()
                session?.send(Frame.Text(payload))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send typing event", e)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000) // 30 seconds Phoenix heartbeat
                try {
                    val heartbeat = """
                        {
                            "topic": "phoenix",
                            "event": "heartbeat",
                            "payload": {},
                            "ref": "heartbeat"
                        }
                    """.trimIndent()
                    session?.send(Frame.Text(heartbeat))
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
            }
        }
    }

    private fun buildJoinPayload(conversationId: String, token: String, userId: String): String {
        return """
            {
              "topic": "realtime:public:messages:conversation_id=eq.$conversationId",
              "event": "phx_join",
              "payload": {
                "config": {
                  "broadcast": { "self": false, "ack": false },
                  "presence": { "key": "$userId" },
                  "postgres_changes": [
                    {
                      "event": "INSERT",
                      "schema": "public",
                      "table": "messages",
                      "filter": "conversation_id=eq.$conversationId"
                    }
                  ]
                },
                "access_token": "$token"
              },
              "ref": "join_$conversationId"
            }
        """.trimIndent()
    }

    private suspend fun handleIncomingMessage(rawJson: String) {
        Log.d(TAG, "Incoming RT Message: $rawJson")
        try {
            val element = json.parseToJsonElement(rawJson).jsonObject
            val event = element["event"]?.jsonPrimitive?.content ?: return
            
            if (event == "presence_state") {
                val payload = element["payload"]?.jsonObject ?: return
                val users = payload.keys
                _onlineUsers.value = users
            } else if (event == "presence_diff") {
                val payload = element["payload"]?.jsonObject ?: return
                val joins = payload["joins"]?.jsonObject?.keys ?: emptySet()
                val leaves = payload["leaves"]?.jsonObject?.keys ?: emptySet()
                _onlineUsers.value = (_onlineUsers.value + joins) - leaves
            } else if (event == "broadcast") {
                val payload = element["payload"]?.jsonObject ?: return
                val bEvent = payload["event"]?.jsonPrimitive?.content
                if (bEvent == "typing") {
                    val innerPayload = payload["payload"]?.jsonObject ?: return
                    val userId = innerPayload["user_id"]?.jsonPrimitive?.content ?: return
                    val isTyping = innerPayload["is_typing"]?.jsonPrimitive?.boolean ?: false
                    
                    if (isTyping) {
                        _typingUsers.value = _typingUsers.value + userId
                    } else {
                        _typingUsers.value = _typingUsers.value - userId
                    }
                }
            } else if (event == "postgres_changes") {
                val payload = element["payload"]?.jsonObject ?: return
                val type = (payload["type"] ?: payload["event"])?.jsonPrimitive?.content ?: return
                
                if (type == "INSERT") {
                    val record = payload["data"]?.jsonObject?.get("record") 
                        ?: payload["record"] 
                        ?: return
                    
                    val message = json.decodeFromJsonElement<Message>(record)
                    Log.d(TAG, "New RT Message: \${message.id}")
                    
                    // Inject directly into Room DB Single Source of Truth
                    dbContext?.let { ctx ->
                        val db = LoopChatDatabase.getDatabase(ctx)
                        
                        // We run this in a coroutine to avoid blocking the WebSocket listener thread
                        scope.launch {
                            try {
                                // Cache message
                                db.messageDao().insertMessage(message.toEntity())
                                db.conversationDao().incrementUnreadCount(message.conversationId)
                                
                                // Fetch and cache sender profile if missing
                                val senderExists = db.userDao().getUserById(message.senderId) != null
                                if (!senderExists) {
                                    val accessToken = SupabaseClient.getAccessToken()
                                    if (accessToken != null) {
                                        val profile = com.loopchat.app.data.SupabaseRepository.getCachedProfile(message.senderId, accessToken)
                                        profile?.let { p ->
                                            db.userDao().insertUser(p.toEntity())
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to inject real-time message or sender profile", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RT message", e)
        }
    }
}
