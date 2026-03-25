package com.loopchat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loopchat.app.data.MessageWithSender
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.ui.components.*
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.viewmodels.EnhancedChatViewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex

import com.loopchat.app.ui.viewmodels.VoiceRecorderViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EnhancedChatScreen(
    conversationId: String,
    participantName: String? = null,
    onBackClick: () -> Unit,
    onCallClick: (String, String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToGroupInfo: (String) -> Unit,
    onNavigateToMediaGallery: (String) -> Unit = {},
    chatViewModel: EnhancedChatViewModel = viewModel(),
    voiceViewModel: VoiceRecorderViewModel = viewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = SupabaseClient.currentUserId
    val context = LocalContext.current
    
    // Voice Recorder State
    val voiceState by voiceViewModel.state.collectAsState()
    
    var showMessageMenu by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<MessageWithSender?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    
    // Media viewer state
    var showImageViewer by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var viewerMediaUrl by remember { mutableStateOf("") }
    var viewerSenderName by remember { mutableStateOf<String?>(null) }
    var viewerTimestamp by remember { mutableStateOf<String?>(null) }
    
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
    
    var showPollComposer by remember { mutableStateOf(false) }

    // Start background tasks
    LaunchedEffect(conversationId) {
        chatViewModel.loadMessages(conversationId, context)
        voiceViewModel.state
        // Setup real-time listeners
        com.loopchat.app.data.realtime.SupabaseRealtimeClient.connectAndSubscribe(
            conversationId = conversationId
        )
    }

    // Attachment Menu
    if (showAttachmentMenu) {
        ModalBottomSheet(onDismissRequest = { showAttachmentMenu = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Attachments", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                    AttachmentOption(Icons.Default.Image, "Gallery", Primary) {
                        imagePickerLauncher.launch("image/*")
                        showAttachmentMenu = false
                    }
                    AttachmentOption(Icons.Default.Videocam, "Video", Secondary) {
                        videoPickerLauncher.launch("video/*")
                        showAttachmentMenu = false
                    }
                    AttachmentOption(Icons.Default.InsertDriveFile, "Document", Info) {
                        documentPickerLauncher.launch("*/*")
                        showAttachmentMenu = false
                    }
                    AttachmentOption(Icons.Default.Poll, "Poll", Warning) {
                        showPollComposer = true
                        showAttachmentMenu = false
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showPollComposer) {
        PollComposerBottomSheet(
            onDismiss = { showPollComposer = false },
            onCreatePoll = { question, options, isMultipleChoice, isAnonymous ->
                chatViewModel.sendPoll(question, options, isMultipleChoice, isAnonymous)
                showPollComposer = false
            }
        )
    }
    
    // Start/stop message listening with screen lifecycle
    DisposableEffect(conversationId) {
        onDispose {
            com.loopchat.app.data.realtime.SupabaseRealtimeClient.disconnect()
        }
    }
    
    // Scroll to latest message when new message arrives or chat opens
    val latestMessageId = chatViewModel.messages.lastOrNull()?.id
    LaunchedEffect(latestMessageId) {
        if (latestMessageId != null) {
            // With reverseLayout=true and asReversed(), index 0 = newest message at bottom
            listState.animateScrollToItem(0)
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
    
    val otherUserId = chatViewModel.otherParticipant?.userId ?: chatViewModel.otherParticipant?.id
    val onlineUsers by com.loopchat.app.data.realtime.SupabaseRealtimeClient.onlineUsers.collectAsState(initial = emptySet())
    val isOnline = otherUserId != null && onlineUsers.contains(otherUserId)
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.combinedClickable(
                            onClick = { 
                                val isGroupChat = chatViewModel.currentConversation?.is_group == true
                                if (isGroupChat) {
                                    val groupId = chatViewModel.currentConversation?.group_id
                                    if (groupId != null) {
                                        onNavigateToGroupInfo(groupId)
                                    }
                                } else {
                                    otherUserId?.let { onNavigateToProfile(it) }
                                }
                            }
                        )
                    ) {
                        val isGroupChat = chatViewModel.currentConversation?.is_group == true
                        
                        Box(contentAlignment = Alignment.BottomEnd) {
                            SmallGradientAvatar(
                                initial = otherUserName.firstOrNull()?.toString() ?: "?",
                                imageUrl = otherUserAvatar,
                                size = 42.dp,
                                isGroup = isGroupChat
                            )
                            if (isOnline && !isGroupChat) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Online)
                                        .border(2.dp, Background, CircleShape)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = otherUserName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            val statusText = when {
                                chatViewModel.isLoading -> "Loading..."
                                isGroupChat -> "Group Message"
                                isOnline -> "online"
                                else -> {
                                    val lastSeen = chatViewModel.otherParticipant?.lastSeen
                                    if (lastSeen != null) "Last seen at ${formatTimestamp(lastSeen)}" else "offline"
                                }
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline && !isGroupChat) Online else TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    var lastCallTimestamp by remember { mutableStateOf(0L) }
                    
                    val participantId = chatViewModel.otherParticipant?.userId 
                        ?: chatViewModel.otherParticipant?.id
                    participantId?.let { userId ->
                        IconButton(onClick = { 
                            val now = System.currentTimeMillis()
                            if (now - lastCallTimestamp > 1500L) {
                                lastCallTimestamp = now
                                onCallClick(userId, "audio") 
                            }
                        }) {
                            Icon(Icons.Default.Phone, contentDescription = "Audio Call", tint = TextSecondary)
                        }
                        IconButton(onClick = { 
                            val now = System.currentTimeMillis()
                            if (now - lastCallTimestamp > 1500L) {
                                lastCallTimestamp = now
                                onCallClick(userId, "video") 
                            }
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = TextSecondary)
                        }
                    }
                    
                    var showChatMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showChatMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = TextSecondary)
                    }
                    
                    androidx.compose.material3.DropdownMenu(
                        expanded = showChatMenu,
                        onDismissRequest = { showChatMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("View Profile") },
                            onClick = {
                                showChatMenu = false
                                participantId?.let { onNavigateToProfile(it) }
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Media Gallery") },
                            onClick = {
                                showChatMenu = false
                                onNavigateToMediaGallery(conversationId)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (chatViewModel.isVanishModeEnabled) Background else SurfaceContainer
                ),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            // Premium Chat Wallpaper with subtle doodle-like pattern
            Canvas(modifier = Modifier.fillMaxSize().alpha(0.06f)) {
                val gridStep = 90.dp.toPx()
                var toggle = true
                for (x in 0..size.width.toInt() step gridStep.toInt()) {
                    for (y in 0..size.height.toInt() step gridStep.toInt()) {
                        val offsetX = x.toFloat() + if (toggle) 20f else -10f
                        val offsetY = y.toFloat() + if (!toggle) 15f else -5f
                        
                        if ((x + y) % 3 == 0) {
                            // Draw 'x' or cross
                            drawLine(
                                color = Color.White,
                                start = Offset(offsetX - 8f, offsetY - 8f),
                                end = Offset(offsetX + 8f, offsetY + 8f),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(offsetX - 8f, offsetY + 8f),
                                end = Offset(offsetX + 8f, offsetY - 8f),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        } else if ((x + y) % 2 == 0) {
                            // Draw small hollow circle 'o'
                            drawCircle(
                                color = Color.White,
                                radius = 6.dp.toPx(),
                                center = Offset(offsetX, offsetY),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        } else {
                            // Draw dot
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = Offset(offsetX, offsetY)
                            )
                        }
                        toggle = !toggle
                    }
                }
            }
            
            // Radial Glow for depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.1f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Secondary, Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = 1500f
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.ime)
            ) {
                // Messages list
            if (chatViewModel.isLoading && chatViewModel.messages.isEmpty()) {
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
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(GlassBackground)
                            .border(1.dp, SurfaceLight.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Primary
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "No messages yet",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Send a message to start the conversation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
                ) {
                    items(chatViewModel.messages.asReversed(), key = { it.id }) { message ->
                        val isFromMe = message.senderId == currentUserId
                        
                        // Mark as read when message becomes visible
                        if (!isFromMe && !message.isRead) {
                            LaunchedEffect(message.id) {
                                chatViewModel.markMessageAsRead(message.id)
                            }
                        }
                        
                        EnhancedMessageBubble(
                            message = message,
                            isFromMe = isFromMe,
                            isStarred = message.id in chatViewModel.starredMessageIds,
                            isGroup = chatViewModel.currentConversation?.is_group == true,
                            reactions = chatViewModel.messageReactions[message.id] ?: emptyMap(),
                            currentUserId = currentUserId ?: "",
                            poll = if (message.messageType == "poll") chatViewModel.polls[message.id] else null,
                            onVotePoll = { optionId, isMultipleChoice ->
                                chatViewModel.voteOnPoll(message.id, optionId, isMultipleChoice)
                            },
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
                            },
                            onMediaClick = { url, type, senderName, timestamp ->
                                viewerMediaUrl = url
                                viewerSenderName = senderName
                                viewerTimestamp = timestamp
                                when (type) {
                                    "image" -> showImageViewer = true
                                    "video" -> showVideoPlayer = true
                                    "document" -> openDocument(context, url)
                                }
                            }
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceVariant.copy(alpha = 0.8f),
                                contentColor = TextSecondary
                            ) {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
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
            
            // Typing indicator
            if (chatViewModel.typingUsers.isNotEmpty()) {
                TypingIndicator(userName = otherUserName)
            }
            
            // Error message
            chatViewModel.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = ErrorColor,
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
                chatViewModel.editingMessage?.let { _ ->
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
            

            // Upload progress indicator
            if (chatViewModel.isUploading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary
                )
            }
            
            // Message input or Voice Recorder
            if (chatViewModel.isChatDisabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = chatViewModel.errorMessage ?: "You cannot send messages in this group.",
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (voiceState.isRecording) {
                 Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    color = Color.Transparent,
                ) {
                    VoiceRecordBar(
                        isRecording = true,
                        durationMs = voiceState.durationMs,
                        amplitudes = voiceState.amplitudes,
                        onStartRecording = {},
                        onStopRecording = { cancel ->
                            val file = voiceViewModel.stopRecording(cancel)
                            if (file != null && !cancel) {
                                chatViewModel.sendVoiceMessage(file, voiceState.durationMs, voiceState.amplitudes)
                            }
                        }
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Main Input Pill — surface_container_highest with full roundness
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SurfaceContainerHighest,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                // Camera icon
                                IconButton(onClick = { /* Camera action */ }) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = "Camera",
                                        tint = TextSecondary
                                    )
                                }
                                // Emoji icon
                                IconButton(onClick = { /* Emoji action */ }) {
                                    Icon(
                                        Icons.Default.EmojiEmotions,
                                        contentDescription = "Emoji",
                                        tint = TextSecondary
                                    )
                                }
                                
                                // Text input
                                BasicTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 12.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                    maxLines = 5,
                                    cursorBrush = SolidColor(Primary),
                                    decorationBox = { innerTextField ->
                                        if (messageText.isEmpty() && chatViewModel.selectedMediaUri == null) {
                                            Text(
                                                text = "Type a message...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextMuted
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                
                                // Attachment button (inside pill now)
                                IconButton(
                                    onClick = { showAttachmentMenu = !showAttachmentMenu }
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = "Attach",
                                        tint = TextSecondary,
                                        modifier = Modifier.rotate(-45f)
                                    )
                                }
                            }
                        }
                        // Send / Mic button area with distinct glow
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp)
                        ) {
                            // Pink glow behind the button
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .alpha(0.6f)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(Primary, Color.Transparent),
                                            radius = 80f
                                        )
                                    )
                            )
                            
                            if (messageText.isNotBlank() || chatViewModel.selectedMediaUri != null) {
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
                                        .background(Primary),
                                    enabled = !chatViewModel.isSending
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
                            } else {
                                // Voice Record Idle Button
                                IconButton(
                                    onClick = { },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Primary)
                                        .pointerInput(Unit) {
                                            detectVerticalDragGestures(
                                                onVerticalDrag = { _, dragAmount -> 
                                                    if (dragAmount < -20) {
                                                        // detect upward swipe for locking (not implemented)
                                                    }
                                                },
                                                onDragEnd = { voiceViewModel.stopRecording(false) }
                                            )
                                        }
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Record Voice",
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
    
    // Full-screen image viewer dialog
    if (showImageViewer && viewerMediaUrl.isNotEmpty()) {
        FullScreenImageViewer(
            imageUrl = viewerMediaUrl,
            senderName = viewerSenderName,
            timestamp = viewerTimestamp,
            onDismiss = {
                showImageViewer = false
                viewerMediaUrl = ""
            }
        )
    }
    
    // Full-screen video player dialog
    if (showVideoPlayer && viewerMediaUrl.isNotEmpty()) {
        FullScreenVideoPlayer(
            videoUrl = viewerMediaUrl,
            senderName = viewerSenderName,
            onDismiss = {
                showVideoPlayer = false
                viewerMediaUrl = ""
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedMessageBubble(
    message: MessageWithSender,
    isFromMe: Boolean,
    isStarred: Boolean,
    isGroup: Boolean,
    reactions: Map<String, List<String>>,
    currentUserId: String,
    poll: com.loopchat.app.data.Poll? = null,
    onVotePoll: (optionId: String, isMultipleChoice: Boolean) -> Unit = { _, _ -> },
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
    onReactionClick: (String) -> Unit,
    onAddReaction: () -> Unit,
    onMediaClick: (url: String, type: String, senderName: String?, timestamp: String?) -> Unit = { _, _, _, _ -> }
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
        val isMediaOnly = message.mediaUrl != null && message.content.isBlank() && message.messageType in listOf("image", "video")
        
        val isFromMeColors = listOf(PrimaryContainer, PrimaryFixedDim)
        
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
                        // Asymmetric corners per Stitch design:
                        // Incoming: bottom-left 8dp (points to sender)
                        // Outgoing: bottom-right 8dp (points to sender)
                        RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = if (isFromMe) 24.dp else 8.dp,
                            bottomEnd = if (isFromMe) 8.dp else 24.dp
                        )
                    )
                    .then(
                        if (isFromMe) {
                            Modifier.background(
                                brush = Brush.horizontalGradient(
                                    colors = isFromMeColors
                                )
                            )
                        } else {
                            // Incoming: surface_container (Level 1)
                            Modifier.background(SurfaceContainer)
                        }
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                    .then(
                        if (isMediaOnly) Modifier.padding(2.dp) 
                        else Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 8.dp)
                    )
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
                            val isDocument = message.messageType != "image" && message.messageType != "video"
                            val senderName = message.sender?.fullName ?: message.sender?.username
                            val msgTimestamp = message.createdAt
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isDocument) Modifier.wrapContentHeight() else Modifier.height(180.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Background)
                                    .combinedClickable(
                                        onClick = {
                                            onMediaClick(url, message.messageType, senderName, msgTimestamp)
                                        },
                                        onLongClick = onLongPress
                                    )
                            ) {
                                when (message.messageType) {
                                    "image" -> {
                                        coil.compose.AsyncImage(
                                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                .data(url)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                    "video" -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PlayCircle,
                                                contentDescription = "Play video",
                                                modifier = Modifier.size(64.dp),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    else -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isFromMe) Color.White.copy(alpha = 0.15f) else SurfaceVariant)
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isFromMe) Color.White.copy(alpha = 0.2f) else Primary.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.InsertDriveFile,
                                                    contentDescription = "Document",
                                                    tint = if (isFromMe) Color.White else Primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (message.content.isNotEmpty()) message.content else "Document.pdf",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isFromMe) Color.White else TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Tap to view",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isFromMe) Color.White.copy(alpha = 0.7f) else TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    
                    // Voice Message Render
                if (message.messageType == "audio") {
                    VoiceMessageBubble(
                        duration = "0:34", // Mock duration
                        isPlaying = false,
                        onTogglePlayback = { /* Handle audio */ },
                        isFromMe = isFromMe,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Poll Message Render
                if (message.messageType == "poll") {
                    val question = message.content.substringAfter("📊 Poll: ").trim()
                    
                    if (poll != null) {
                        val optionsData = poll.options ?: poll.poll_options
                        val totalVotes = optionsData.flatMap { it.votes ?: it.poll_votes }.size
                        
                        val realOptions = optionsData.map { opt ->
                            val votesForOpt = opt.votes ?: opt.poll_votes
                            val hasVoted = votesForOpt.any { it.user_id == currentUserId }
                            PollOptionUI(
                                id = opt.id,
                                text = opt.option_text,
                                voteCount = votesForOpt.size,
                                isVotedByMe = hasVoted
                            )
                        }.sortedBy { it.id }
                        
                        PollBubble(
                            question = poll.question,
                            options = realOptions,
                            totalVotes = totalVotes,
                            isMultipleChoice = poll.multiple_answers,
                            isFromMe = isFromMe,
                            onVote = { optionId ->
                                onVotePoll(optionId, poll.multiple_answers)
                            },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    } else {
                        // Show loading state while poll fetches
                        PollBubble(
                            question = question,
                            options = listOf(PollOptionUI("1", "Loading Poll Data...", 0, isVotedByMe = false)),
                            totalVotes = 0,
                            isMultipleChoice = false,
                            isFromMe = isFromMe,
                            onVote = { },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Text payload / Deleted Message Render
                if (message.deletedForEveryone == true || message.content.isNotEmpty()) {
                    // Avoid duplicating the document name if it's already in the media box
                    val isDuplicateDocName = message.messageType != "text" && message.messageType != "image" && message.messageType != "video" && message.messageType != "poll"
                    val showText = !isDuplicateDocName || message.deletedForEveryone == true

                    if (showText) {
                        Text(
                            text = if (message.deletedForEveryone == true) 
                                "This message was deleted" 
                            else message.content,
                            color = if (message.deletedForEveryone == true) TextMuted else TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (message.deletedForEveryone == true) 
                                androidx.compose.ui.text.font.FontStyle.Italic 
                            else androidx.compose.ui.text.font.FontStyle.Normal,
                            modifier = Modifier.padding(top = if (message.mediaUrl != null) 2.dp else 4.dp)
                        )
                    }
                }
                    
                // Edited indicator
                    if (message.editedAt != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EditedIndicator()
                    }
                    
                    // Inner Timestamp and ticks for sent messages, inside the bubble
                    if (isFromMe) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            message.createdAt?.let { timestamp ->
                                Text(
                                    text = formatTimestamp(timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            val messageStatus = when {
                                message.isRead -> com.loopchat.app.ui.components.MessageStatus.READ
                                message.status == "synced" -> com.loopchat.app.ui.components.MessageStatus.SENT
                                else -> com.loopchat.app.ui.components.MessageStatus.SENDING
                            }
                            com.loopchat.app.ui.components.MessageStatusIcon(
                                status = messageStatus,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White.copy(alpha = 0.7f),
                                readTint = Color.White
                            )
                        }
                    }
                }
            }
        }
        
        // Reactions
        Spacer(modifier = Modifier.height(4.dp))
        MessageReactions(
            reactions = reactions,
            currentUserId = currentUserId,
            onReactionClick = onReactionClick,
            onAddReaction = onAddReaction,
            modifier = Modifier.padding(start = if (isFromMe) 0.dp else 8.dp)
        )
        
        // Timestamp and status underneath the bubble for received
        if (!isFromMe) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            ) {
                // Only show sender name in group chats
                if (isGroup) {
                    Text(
                        text = "${message.sender?.fullName ?: message.sender?.username ?: "User"} • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                message.createdAt?.let { timestamp ->
                    Text(
                        text = formatTimestamp(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

// Enhanced time formatter that respects local timezone
private fun formatTimestamp(timestamp: String): String {
    return try {
        // Normalize UTC timestamp string from Supabase
        val normalizedTimestamp = timestamp.replace(" ", "T").let { ts ->
            if (!ts.contains("Z") && !ts.contains("+")) "${ts}Z" else ts
        }
        
        val instant = Instant.parse(normalizedTimestamp)
        
        // Use 12-hour format with AM/PM for in-chat messages
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            
        formatter.format(instant)
    } catch (e: Exception) {
        android.util.Log.e("EnhancedChatScreen", "Error formatting timestamp", e)
        ""
    }
}
