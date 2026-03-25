package com.loopchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.loopchat.app.data.MessagingFeaturesRepository
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.ui.theme.*
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ============================================
// ViewModel
// ============================================

@Serializable
data class StarredMessageItem(
    val id: String,
    val content: String? = null,
    @SerialName("message_type") val messageType: String? = "text",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null
)

@Serializable
data class StarredEntry(
    @SerialName("message_id") val messageId: String,
    @SerialName("starred_at") val starredAt: String? = null
)

data class StarredMessageDisplay(
    val id: String,
    val content: String,
    val messageType: String,
    val createdAt: String,
    val senderName: String?,
    val senderAvatarUrl: String?
)

class StarredMessagesViewModel : ViewModel() {
    var starredMessages by mutableStateOf<List<StarredMessageDisplay>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val accessToken = SupabaseClient.getAccessToken() ?: return@launch
                val userId = SupabaseClient.currentUserId ?: return@launch
                val supabaseUrl = com.loopchat.app.BuildConfig.SUPABASE_URL
                val supabaseKey = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY

                // 1. Fetch starred message IDs
                val starredResponse = httpClient.get("$supabaseUrl/rest/v1/starred_messages") {
                    parameter("select", "message_id,starred_at")
                    parameter("user_id", "eq.$userId")
                    parameter("order", "starred_at.desc")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                }

                if (!starredResponse.status.isSuccess()) {
                    errorMessage = "Failed to load starred messages"
                    isLoading = false
                    return@launch
                }

                val entries: List<StarredEntry> = starredResponse.body()
                if (entries.isEmpty()) {
                    starredMessages = emptyList()
                    isLoading = false
                    return@launch
                }

                // 2. Fetch full message details
                val messageIds = entries.map { it.messageId }.joinToString(",")
                val messagesResponse = httpClient.get("$supabaseUrl/rest/v1/messages") {
                    parameter("select", "id,content,message_type,created_at,sender_id,media_url")
                    parameter("id", "in.($messageIds)")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                }

                if (!messagesResponse.status.isSuccess()) {
                    errorMessage = "Failed to load message details"
                    isLoading = false
                    return@launch
                }

                val msgs: List<StarredMessageItem> = messagesResponse.body()

                // 3. Fetch sender profiles
                val senderIds = msgs.mapNotNull { it.senderId }.distinct().joinToString(",")
                val profilesResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                    parameter("select", "id,username,full_name,avatar_url")
                    parameter("id", "in.($senderIds)")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                }

                val profiles: List<com.loopchat.app.data.models.Profile> =
                    if (profilesResponse.status.isSuccess()) profilesResponse.body() else emptyList()
                val profileMap = profiles.associateBy { it.id }

                // 4. Build display items in starred order
                val msgMap = msgs.associateBy { it.id }
                starredMessages = entries.mapNotNull { entry ->
                    val msg = msgMap[entry.messageId] ?: return@mapNotNull null
                    val profile = msg.senderId?.let { profileMap[it] }
                    StarredMessageDisplay(
                        id = msg.id,
                        content = msg.content ?: "📎 Media",
                        messageType = msg.messageType ?: "text",
                        createdAt = msg.createdAt ?: "",
                        senderName = profile?.fullName ?: profile?.username,
                        senderAvatarUrl = profile?.avatarUrl
                    )
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            }
            isLoading = false
        }
    }

    fun unstar(messageId: String) {
        viewModelScope.launch {
            val result = MessagingFeaturesRepository.unstarMessage(httpClient, messageId)
            result.onSuccess {
                starredMessages = starredMessages.filter { it.id != messageId }
            }
        }
    }
}

// ============================================
// Screen
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    onBackClick: () -> Unit,
    onMessageClick: ((conversationId: String) -> Unit)? = null,
    viewModel: StarredMessagesViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Warning, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Starred Messages", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainer)
            )
        }
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            viewModel.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(viewModel.errorMessage!!, color = TextSecondary)
                    }
                }
            }
            viewModel.starredMessages.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.StarBorder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No starred messages", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text("Long press any message to star it", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.starredMessages, key = { it.id }) { msg ->
                        StarredMessageCard(
                            message = msg,
                            onUnstar = { viewModel.unstar(msg.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarredMessageCard(
    message: StarredMessageDisplay,
    onUnstar: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Sender avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                if (message.senderAvatarUrl != null) {
                    AsyncImage(
                        model = message.senderAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = (message.senderName ?: "?").first().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Sender name
                Text(
                    text = message.senderName ?: "Unknown",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Message content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Date
                Text(
                    text = formatStarredDate(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            // Unstar button
            IconButton(onClick = onUnstar, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Star, contentDescription = "Unstar", tint = Warning, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatStarredDate(dateStr: String): String {
    return try {
        val instant = java.time.Instant.parse(dateStr)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
        zoned.format(formatter)
    } catch (e: Exception) {
        dateStr
    }
}
