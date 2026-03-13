package com.loopchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loopchat.app.data.MessageWithSender
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.ui.components.SmallGradientAvatar
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.viewmodels.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    participantName: String? = null,
    onBackClick: () -> Unit,
    onCallClick: (String, String) -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = SupabaseClient.currentUserId
    
    // Load messages on first composition
    LaunchedEffect(conversationId) {
        chatViewModel.loadMessages(conversationId)
    }
    
    // Scroll to bottom when new message arrives
    LaunchedEffect(chatViewModel.messages.size) {
        if (chatViewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatViewModel.messages.size - 1)
        }
    }
    
    val otherUserName = participantName 
        ?: chatViewModel.otherParticipant?.fullName 
        ?: chatViewModel.otherParticipant?.username 
        ?: chatViewModel.otherParticipant?.username 
        ?: "Chat"
    
    val otherUserAvatar = chatViewModel.otherParticipant?.avatarUrl
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Gradient Avatar
                        SmallGradientAvatar(
                            initial = otherUserName.firstOrNull()?.toString() ?: "?",
                            imageUrl = otherUserAvatar,
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = otherUserName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (chatViewModel.isLoading) Warning else Online)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (chatViewModel.isLoading) "Loading..." else "Online",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    // Use userId (auth user ID) for calls, not profile id
                    val otherUserId = chatViewModel.otherParticipant?.userId 
                        ?: chatViewModel.otherParticipant?.id
                    otherUserId?.let { userId ->
                        IconButton(onClick = { onCallClick(otherUserId, "audio") }) {
                            Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = Primary)
                        }
                        IconButton(onClick = { onCallClick(otherUserId, "video") }) {
                            Icon(Icons.Default.VideoCall, contentDescription = "Video Call", tint = Secondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages list
            if (chatViewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (chatViewModel.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No messages yet",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Start the conversation!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(chatViewModel.messages) { message ->
                        MessageBubble(
                            message = message,
                            isFromMe = message.senderId == currentUserId
                        )
                    }
                }
            }
            
            // Error message
            chatViewModel.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Message input with premium styling
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Attach */ }) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach",
                            tint = TextSecondary
                        )
                    }
                    
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SurfaceVariant,
                            focusedBorderColor = Primary,
                            cursorColor = Primary
                        ),
                        maxLines = 4
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Gradient Send Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                brush = if (messageText.isNotBlank()) 
                                    Brush.linearGradient(SunsetGradientColors)
                                else 
                                    Brush.linearGradient(SunsetGradientColors.map { it.copy(alpha = 0.5f) })
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank() && !chatViewModel.isSending) {
                                    chatViewModel.sendMessage(messageText)
                                    messageText = ""
                                }
                            },
                            enabled = messageText.isNotBlank() && !chatViewModel.isSending
                        ) {
                            if (chatViewModel.isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = TextPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageWithSender,
    isFromMe: Boolean
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isFromMe) 18.dp else 4.dp,
                            bottomEnd = if (isFromMe) 4.dp else 18.dp
                        )
                    )
                    .then(
                        if (isFromMe) {
                            Modifier.background(
                                brush = Brush.horizontalGradient(SunsetGradientColors)
                            )
                        } else {
                            Modifier.background(MessageReceived)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Timestamp
            message.createdAt?.let { timestamp ->
                Text(
                    text = formatTimestamp(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        // Parse ISO 8601 timestamp from database (usually in UTC)
        // Handle various formats: "2026-01-09T10:30:00Z", "2026-01-09T10:30:00+00:00", "2026-01-09 10:30:00"
        val normalizedTimestamp = timestamp
            .replace(" ", "T")  // Replace space with T for ISO format
            .let { ts ->
                // Add Z if no timezone indicator present
                val tIndex = ts.indexOf("T")
                val hasTimezoneOffset = ts.contains("Z") || ts.contains("+") || 
                    (tIndex >= 0 && ts.substring(tIndex + 1).contains("-"))
                if (!hasTimezoneOffset) {
                    "${ts}Z"
                } else {
                    ts
                }
            }
        
        val instant = java.time.Instant.parse(normalizedTimestamp)
        
        // Convert to device's local timezone
        val localDateTime = java.time.LocalDateTime.ofInstant(
            instant, 
            java.time.ZoneId.systemDefault()
        )
        
        // Format as 12-hour time with AM/PM
        val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
        localDateTime.format(formatter)
    } catch (e: Exception) {
        // Fallback: try parsing with OffsetDateTime for timestamps with offset
        try {
            val offsetDateTime = java.time.OffsetDateTime.parse(timestamp.replace(" ", "T"))
            val localDateTime = offsetDateTime.atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime()
            val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
            localDateTime.format(formatter)
        } catch (e2: Exception) {
            // Last resort: extract time and show as-is (but this may be incorrect timezone)
            try {
                val timePart = timestamp.substringAfter("T").substringBefore(".")
                    .substringBefore("+").substringBefore("Z")
                val parts = timePart.split(":")
                if (parts.size >= 2) {
                    val hour = parts[0].toIntOrNull() ?: 0
                    val minute = parts[1]
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                    String.format("%02d:%s %s", hour12, minute, ampm)
                } else ""
            } catch (e3: Exception) {
                ""
            }
        }
    }
}
