package com.loopchat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loopchat.app.data.MessageWithSender
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.ui.components.*
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.viewmodels.EnhancedChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EnhancedChatScreen(
    conversationId: String,
    participantName: String? = null,
    onBackClick: () -> Unit,
    onCallClick: (String, String) -> Unit,
    chatViewModel: EnhancedChatViewModel = viewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = SupabaseClient.currentUserId
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var showMessageMenu by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<MessageWithSender?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    
    // File picker launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { chatViewModel.onMediaSelected(it, "image") } }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { chatViewModel.onMediaSelected(it, "video") } }
    
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { chatViewModel.onMediaSelected(it, "document") } }
    
    // Load messages on first composition
    LaunchedEffect(conversationId) {
        chatViewModel.loadMessages(conversationId, context)
    }
    
    // Start/stop message listening with screen lifecycle
    DisposableEffect(conversationId) {
        onDispose {
            com.loopchat.app.data.realtime.SupabaseRealtimeClient.disconnect()
        }
    }
    
    // Scroll to bottom when new message arrives
    LaunchedEffect(chatViewModel.messages.size) {
        if (chatViewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatViewModel.messages.size - 1)
        }
    }
    
    // Update typing status when user types
    LaunchedEffect(messageText) {
        if (messageText.isNotEmpty()) {
            chatViewModel.updateTypingStatus(true)
            delay(3000) // Stop typing after 3 seconds of no input
            chatViewModel.updateTypingStatus(false)
        }
    }
    
    val otherUserName = participantName 
        ?: chatViewModel.otherParticipant?.fullName 
        ?: chatViewModel.otherParticipant?.username 
        ?: "Chat"
    
    val otherUserAvatar = chatViewModel.otherParticipant?.avatarUrl
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isGroupChat = chatViewModel.currentConversation?.is_group == true
                        SmallGradientAvatar(
                            initial = otherUserName.firstOrNull()?.toString() ?: "?",
                            imageUrl = otherUserAvatar,
                            size = 42.dp,
                            isGroup = isGroupChat
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
                    
                    // Show starred messages count if any
                    if (chatViewModel.starredMessageIds.isNotEmpty()) {
                        IconButton(onClick = { /* TODO: Navigate to starred messages */ }) {
                            Badge(
                                containerColor = Primary,
                                contentColor = TextPrimary
                            ) {
                                Text(chatViewModel.starredMessageIds.size.toString())
                            }
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
                        EnhancedMessageBubble(
                            message = message,
                            isFromMe = message.senderId == currentUserId,
                            isStarred = message.id in chatViewModel.starredMessageIds,
                            reactions = chatViewModel.messageReactions[message.id] ?: emptyMap(),
                            currentUserId = currentUserId ?: "",
                            onLongPress = {
                                selectedMessageForMenu = message
                                showMessageMenu = true
                            },
                            onSwipeReply = {
                                chatViewModel.prepareReply(message)
                            },
                            onReactionClick = { emoji ->
                                chatViewModel.toggleReaction(message.id, emoji)
                            },
                            onAddReaction = {
                                chatViewModel.showReactionPickerFor(message.id)
                            }
                        )
                    }
                }
            }
            
            // Typing indicator
            if (chatViewModel.typingUsers.isNotEmpty()) {
                TypingIndicator(userName = otherUserName)
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
            
            // Reply preview
            AnimatedVisibility(
                visible = chatViewModel.replyToMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                chatViewModel.replyToMessage?.let { replyMsg ->
                    ReplyPreview(
                        replyToMessage = replyMsg.content,
                        replyToSender = replyMsg.sender?.fullName ?: "User",
                        onCancelReply = { chatViewModel.cancelReply() }
                    )
                }
            }
            
            // Edit preview
            AnimatedVisibility(
                visible = chatViewModel.editingMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                chatViewModel.editingMessage?.let { editMsg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editing",
                                tint = Primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Edit message",
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { chatViewModel.cancelEdit() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel edit",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
            
            // Attachment menu popup
            AnimatedVisibility(
                visible = showAttachmentMenu,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                AttachmentMenu(
                    onImageSelected = { imagePickerLauncher.launch("image/*") },
                    onVideoSelected = { videoPickerLauncher.launch("video/*") },
                    onDocumentSelected = { documentPickerLauncher.launch("*/*") },
                    onLocationSelected = { /* future */ },
                    onCameraSelected = { /* future */ },
                    onDismiss = { showAttachmentMenu = false },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Media preview strip (shown when file is selected before sending)
            chatViewModel.selectedMediaUri?.let { uri ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            when (chatViewModel.selectedMediaType) {
                                "image" -> {
                                    coil.compose.AsyncImage(
                                        model = uri,
                                        contentDescription = "Selected image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                "video" -> Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = "Video",
                                    tint = Secondary,
                                    modifier = Modifier.size(32.dp)
                                )
                                else -> Icon(
                                    Icons.Default.Description,
                                    contentDescription = "Document",
                                    tint = Warning,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = when (chatViewModel.selectedMediaType) {
                                "image" -> "Photo selected"
                                "video" -> "Video selected"
                                else -> "Document selected"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(onClick = { chatViewModel.clearSelectedMedia() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
            
            // Upload progress indicator
            if (chatViewModel.isUploading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary
                )
            }
            
            // Message input
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Attachment button
                    IconButton(
                        onClick = { showAttachmentMenu = !showAttachmentMenu },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach",
                            tint = Primary
                        )
                    }
                    
                    // Text input
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = when {
                                    chatViewModel.editingMessage != null -> "Edit message..."
                                    chatViewModel.replyToMessage != null -> "Reply..."
                                    chatViewModel.selectedMediaUri != null -> "Add a caption..."
                                    else -> "Type a message..."
                                },
                                color = TextMuted
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = SurfaceVariant,
                            focusedContainerColor = SurfaceVariant,
                            unfocusedContainerColor = SurfaceVariant
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 5
                    )
                    
                    // Send button
                    IconButton(
                        onClick = {
                            chatViewModel.sendMessage(messageText, context)
                            messageText = ""
                            showAttachmentMenu = false
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.horizontalGradient(SunsetGradientColors)
                            ),
                        enabled = (messageText.isNotBlank() || chatViewModel.selectedMediaUri != null) && !chatViewModel.isSending
                    ) {
                        if (chatViewModel.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
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
    
    // Message action menu dialog
    if (showMessageMenu && selectedMessageForMenu != null) {
        Dialog(onDismissRequest = { 
            showMessageMenu = false
            selectedMessageForMenu = null
        }) {
            MessageActionMenu(
                isOwnMessage = selectedMessageForMenu!!.senderId == currentUserId,
                isStarred = selectedMessageForMenu!!.id in chatViewModel.starredMessageIds,
                canEdit = true, // TODO: Check 5-minute window
                onReply = {
                    chatViewModel.prepareReply(selectedMessageForMenu!!)
                    showMessageMenu = false
                    selectedMessageForMenu = null
                },
                onForward = {
                    // TODO: Implement forward dialog with conversation selection
                    // For now, just dismiss
                    showMessageMenu = false
                    selectedMessageForMenu = null
                },
                onStar = {
                    chatViewModel.toggleStarMessage(selectedMessageForMenu!!.id)
                    showMessageMenu = false
                    selectedMessageForMenu = null
                },
                onEdit = {
                    chatViewModel.startEditingMessage(selectedMessageForMenu!!)
                    messageText = selectedMessageForMenu!!.content
                    showMessageMenu = false
                    selectedMessageForMenu = null
                },
                onDelete = {
                    chatViewModel.deleteMessageForMe(selectedMessageForMenu!!.id)
                    showMessageMenu = false
                    selectedMessageForMenu = null
                },
                onDeleteForEveryone = {
                    chatViewModel.deleteMessageForEveryone(selectedMessageForMenu!!.id, context)
                    showMessageMenu = false
                    selectedMessageForMenu = null
                },
                onDismiss = {
                    showMessageMenu = false
                    selectedMessageForMenu = null
                }
            )
        }
    }
    
    // Reaction picker dialog
    if (chatViewModel.showReactionPicker && chatViewModel.reactionPickerMessageId != null) {
        Dialog(onDismissRequest = { chatViewModel.hideReactionPicker() }) {
            ReactionPicker(
                onReactionSelected = { emoji ->
                    chatViewModel.addReaction(chatViewModel.reactionPickerMessageId!!, emoji)
                },
                onDismiss = { chatViewModel.hideReactionPicker() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedMessageBubble(
    message: MessageWithSender,
    isFromMe: Boolean,
    isStarred: Boolean,
    reactions: Map<String, List<String>>,
    currentUserId: String,
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
    onReactionClick: (String) -> Unit,
    onAddReaction: () -> Unit
) {
    var swipeOffset by remember { mutableStateOf(0f) }
    
    Column(
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset > 100f) {
                            onSwipeReply()
                        }
                        swipeOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (!isFromMe && dragAmount > 0) {
                            swipeOffset += dragAmount
                        }
                    }
                )
            }
    ) {
        Row(
            horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star indicator
            if (isStarred) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Starred",
                    tint = Warning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            
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
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Forwarded indicator
                    if (message.forwarded == true) {
                        ForwardedIndicator()
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    // Reply preview if replying
                    message.replyToMessageId?.let {
                        // TODO: Fetch and display quoted message
                        QuotedMessage(
                            quotedText = "Original message",
                            quotedSender = "User",
                            onQuoteClick = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Media content (image/video/document)
                    message.mediaUrl?.let { url ->
                        if (message.messageType != "text") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceVariant)
                            ) {
                                when {
                                    message.messageType == "image" -> {
                                        coil.compose.AsyncImage(
                                            model = url,
                                            contentDescription = "Image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                    message.messageType == "video" -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PlayCircle,
                                                contentDescription = "Play video",
                                                modifier = Modifier.size(64.dp),
                                                tint = androidx.compose.ui.graphics.Color.White
                                            )
                                        }
                                    }
                                    else -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = "Document",
                                                modifier = Modifier.size(40.dp),
                                                tint = TextSecondary
                                            )
                                            Text(
                                                text = "Document",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    
                    // Message content
                    Text(
                        text = if (message.deletedForEveryone == true) 
                            "This message was deleted" 
                        else message.content,
                        color = if (message.deletedForEveryone == true) TextMuted else TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (message.deletedForEveryone == true) 
                            androidx.compose.ui.text.font.FontStyle.Italic 
                        else androidx.compose.ui.text.font.FontStyle.Normal
                    )
                    
                    // Edited indicator
                    if (message.editedAt != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EditedIndicator()
                    }
                }
            }
        }
        
        // Reactions
        if (reactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            MessageReactions(
                reactions = reactions,
                currentUserId = currentUserId,
                onReactionClick = onReactionClick,
                modifier = Modifier.padding(start = if (isFromMe) 0.dp else 8.dp)
            )
        }
        
        // Timestamp and status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        ) {
            message.createdAt?.let { timestamp ->
                Text(
                    text = formatTimestamp(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            
            // Message status for own messages
            if (isFromMe) {
                MessageStatusIcon(
                    status = MessageStatus.READ // TODO: Get actual status
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        val normalizedTimestamp = timestamp
            .replace(" ", "T")
            .let { ts ->
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
        val localDateTime = java.time.LocalDateTime.ofInstant(
            instant, 
            java.time.ZoneId.systemDefault()
        )
        
        val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
        localDateTime.format(formatter)
    } catch (e: Exception) {
        ""
    }
}
