package com.loopchat.app.data.realtime

import android.content.Context
import android.util.Log
import com.loopchat.app.BuildConfig
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.local.LoopChatDatabase
import com.loopchat.app.data.models.Message
import com.loopchat.app.data.local.entities.toEntity
import io.ktor.client.*
import io.ktor.client.engine.android.*
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

    private val client = HttpClient(Android) {
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

    fun initialize(context: Context) {
        dbContext = context.applicationContext
    }

    suspend fun connectAndSubscribe(conversationId: String) {
        if (activeConversationId == conversationId && _isConnected.value) return
        disconnect()

        activeConversationId = conversationId
        val accessToken = SupabaseClient.getAccessToken() ?: return

        scope.launch {
            try {
                // Supabase Realtime requires api key and vsn
                val wsUrl = "$supabaseUrl/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
                
                client.webSocket(wsUrl) {
                    session = this
                    _isConnected.value = true
                    Log.d(TAG, "WebSocket Connected")

                    // 1. Send Join Payload
                    val currentUserId = SupabaseClient.currentUserId ?: ""
                    val joinPayload = buildJoinPayload(conversationId, accessToken, currentUserId)
                    send(Frame.Text(joinPayload))

                    // 2. Start Heartbeat
                    startHeartbeat()

                    // 3. Listen to incoming messages
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleIncomingMessage(text)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "WebSocket Read Error", e)
                    } finally {
                        _isConnected.value = false
                        session = null
                        heartbeatJob?.cancel()
                        Log.d(TAG, "WebSocket Disconnected")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebSocket Connection Failed", e)
                _isConnected.value = false
                delay(5000) // Exponential backoff in production
                connectAndSubscribe(conversationId)
            }
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
                  "broadcast": { "self": false },
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
            } else if (event == "postgres_changes") {
                val payload = element["payload"]?.jsonObject ?: return
                val type = payload["type"]?.jsonPrimitive?.content ?: return
                
                if (type == "INSERT") {
                    val record = payload["record"] ?: return
                    val message = json.decodeFromJsonElement<Message>(record)
                    Log.d(TAG, "New RT Message: \${message.id}")
                    
                    // Inject directly into Room DB Single Source of Truth
                    dbContext?.let { ctx ->
                        val db = LoopChatDatabase.getDatabase(ctx)
                        db.messageDao().insertMessage(message.toEntity())
                        db.conversationDao().incrementUnreadCount(message.conversationId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RT message", e)
        }
    }
}
