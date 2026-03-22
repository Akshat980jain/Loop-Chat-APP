package com.loopchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loopchat.app.data.CallWithProfile
import com.loopchat.app.data.ContactWithProfile
import com.loopchat.app.data.ConversationWithParticipant
import com.loopchat.app.data.SettingsRepository
import com.loopchat.app.data.models.Profile
import com.loopchat.app.ui.components.GlassCard
import com.loopchat.app.ui.components.SmallGradientAvatar
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.viewmodels.HomeViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.result.contract.ActivityResultContracts
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.StoryWithProfile
import com.loopchat.app.data.SupabaseClient
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.loopchat.app.ui.components.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onConversationClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onCallHistoryClick: (String, String) -> Unit = { _, _ -> },
    onNavigate: (String) -> Unit = {}, // New navigation callback
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    val onlineUsers by com.loopchat.app.data.realtime.SupabaseRealtimeClient.onlineUsers.collectAsState(initial = emptySet())
    
    // Refresh data every time HomeScreen enters composition (e.g. navigating back from a chat)
    LaunchedEffect(Unit) {
        viewModel.loadConversations()
        viewModel.loadContacts()
        viewModel.loadCalls()
    }
    
    // Poll conversations every 10s while the Chats tab is active
    DisposableEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.startPolling()
        }
        onDispose {
            viewModel.stopPolling()
        }
    }
    
    Scaffold(
        containerColor = Background,
        topBar = {
            // Glassmorphic top bar — surface_container_low at 80% opacity
            Surface(
                color = SurfaceContainerLow.copy(alpha = 0.8f),
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "Loop Chat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleMessageSearch() },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .background(SurfaceContainerHighest, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Search, 
                                contentDescription = "Search", 
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.openNewChatDialog() },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .border(1.dp, OutlineVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = "New", 
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onProfileClick) {
                            Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary
                    )
                )
            }
        },
        bottomBar = {
            // Electric Noir bottom nav — surface_container background
            NavigationBar(
                containerColor = SurfaceContainer,
                contentColor = TextSecondary,
                tonalElevation = 0.dp
            ) {
                val tabs = listOf(
                    Triple(Icons.Default.Chat, "Chats", 0),
                    Triple(Icons.Default.Call, "Calls", 1),
                    Triple(Icons.Default.Contacts, "Contacts", 2),
                    Triple(Icons.Default.Settings, "Settings", 3)
                )
                
                tabs.forEach { (icon, label, index) ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                icon, 
                                contentDescription = label,
                                tint = if (selectedTab == index) Primary else TextSecondary
                            ) 
                        },
                        label = { 
                            Text(
                                label,
                                color = if (selectedTab == index) Primary else TextSecondary,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            ) 
                        },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = SurfaceVariant // pill-shaped active indicator
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                // Primary gradient FAB at 135° with atmospheric glow
                FloatingActionButton(
                    onClick = { viewModel.openNewChatDialog() },
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = PrimaryGradientColors,
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", tint = TextPrimary)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Message Search Bar (Overlay)
            if (viewModel.showMessageSearch && selectedTab == 0) {
                Column {
                    MessageSearchBar(
                        query = viewModel.messageSearchQuery,
                        onQueryChange = { viewModel.searchMessages(it) },
                        onSearch = { },
                        onClose = { viewModel.toggleMessageSearch() }
                    )
                    
                    if (viewModel.messageSearchResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Background)
                        ) {
                            items(viewModel.messageSearchResults) { message ->
                                // Display search result item
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .clickable { 
                                            onConversationClick(message.conversationId)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = message.sender?.fullName ?: "User",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Primary
                                        )
                                        Text(
                                            text = message.content ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> ChatsTab(
                        conversations = viewModel.conversations,
                        isLoading = viewModel.isLoadingConversations,
                        errorMessage = viewModel.errorMessage,
                        onConversationClick = onConversationClick,
                        viewModel = viewModel, // Pass VM for actions
                        onlineUsers = onlineUsers
                    )
                    1 -> CallsTab(
                        calls = viewModel.calls,
                        isLoading = viewModel.isLoadingCalls,
                        onContactClick = { contactId, contactName ->
                            onCallHistoryClick(contactId, contactName)
                        }
                    )
                    2 -> ContactsTab(
                        contacts = viewModel.contacts,
                        isLoading = viewModel.isLoadingContacts,
                        onContactClick = { contact ->
                            contact.profile?.id?.let { profileId ->
                                viewModel.createConversation(contact.contactUserId) { conversationId ->
                                    onConversationClick(conversationId)
                                }
                            }
                        }
                    )
                    3 -> SettingsTab(
                        onProfileClick = onProfileClick,
                        onNavigate = onNavigate,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
    
    // Search Dialog
    if (viewModel.showSearchDialog) {
        SearchDialog(
            searchQuery = searchQuery,
            onQueryChange = { 
                searchQuery = it
                viewModel.searchUsers(it)
            },
            searchResults = viewModel.searchResults,
            isSearching = viewModel.isSearching,
            isAddingContact = viewModel.isAddingContact,
            errorMessage = viewModel.errorMessage,
            onDismiss = { 
                viewModel.closeSearchDialog()
                viewModel.clearError()
                searchQuery = ""
            },
            onUserSelect = { profile ->
                // Use userId (auth user ID) for contacts, fallback to id
                val contactUserId = profile.userId ?: profile.id
                viewModel.addContact(contactUserId)
            }
        )
    }
    
    
    // New Chat Dialog (using search)
    if (viewModel.showNewChatDialog) {
        var newChatSearchQuery by remember { mutableStateOf("") }
        
        SearchDialog(
            searchQuery = newChatSearchQuery,
            onQueryChange = { 
                newChatSearchQuery = it
                viewModel.searchUsers(it)
            },
            searchResults = viewModel.searchResults,
            isSearching = viewModel.isSearching,
            isAddingContact = viewModel.isCreatingConversation,
            errorMessage = viewModel.errorMessage,
            onDismiss = { 
                viewModel.closeNewChatDialog()
                viewModel.clearError()
                newChatSearchQuery = ""
            },
            onUserSelect = { profile ->
                val otherUserId = profile.userId ?: profile.id
                viewModel.createConversation(otherUserId) { conversationId ->
                    onConversationClick(conversationId)
                }
            },
            dialogTitle = "New Chat",
            actionText = "Chat"
        )
    }
}


@Composable
fun ChatsTab(
    conversations: List<ConversationWithParticipant>, // Added paremeter
    isLoading: Boolean,
    errorMessage: String?,
    onConversationClick: (String) -> Unit,
    viewModel: HomeViewModel,
    onlineUsers: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Sort conversations: pinned first, then by date
    val sortedConversations = remember(conversations, viewModel.pinnedConversations) {
        conversations
            .filter { !viewModel.isConversationArchived(it.id) }
            .sortedWith(
                compareByDescending<ConversationWithParticipant> { 
                    viewModel.isConversationPinned(it.id) 
                }.thenByDescending { it.updatedAt }
            )
    }

    // Action Menu
    if (viewModel.selectedConversationForActions != null) {
        val convId = viewModel.selectedConversationForActions!!
        ConversationActionMenu(
            isArchived = viewModel.isConversationArchived(convId),
            isPinned = viewModel.isConversationPinned(convId),
            isMuted = viewModel.isConversationMuted(convId),
            onArchive = {
                if (viewModel.isConversationArchived(convId)) {
                    viewModel.unarchiveConversation(convId)
                } else {
                    viewModel.archiveConversation(convId)
                }
                viewModel.selectConversationForActions(null)
            },
            onPin = {
                if (viewModel.isConversationPinned(convId)) {
                    viewModel.unpinConversation(convId)
                } else {
                    viewModel.pinConversation(convId)
                }
                viewModel.selectConversationForActions(null)
            },
            onMute = { 
                // Simple toggle for now, ideal would be to show dialog
                if (viewModel.isConversationMuted(convId)) {
                    viewModel.unmuteConversation(convId)
                } else {
                    viewModel.muteConversation(convId, "8h") 
                }
                viewModel.selectConversationForActions(null)
            },
            onDelete = { 
                // Implement delete later
                viewModel.selectConversationForActions(null)
            },
            onDismiss = { viewModel.selectConversationForActions(null) }
        )
    }

    
    // Stories state
    var stories by remember { mutableStateOf<List<StoryWithProfile>>(emptyList()) }
    var isLoadingStories by remember { mutableStateOf(true) }
    var isUploadingStory by remember { mutableStateOf(false) }
    var selectedStories by remember { mutableStateOf<List<StoryWithProfile>?>(null) }
    
    // Load stories
    LaunchedEffect(Unit) {
        isLoadingStories = true
        SupabaseRepository.getStories().onSuccess {
            stories = it
        }
        isLoadingStories = false
    }
    
    // Story image picker
    var previewStoryUri by remember { mutableStateOf<Uri?>(null) }
    
    // Story image picker
    val storyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            previewStoryUri = selectedUri
        }
    }
    
    if (isLoading && conversations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
    } else if (errorMessage != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = ErrorColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error loading chats",
                style = MaterialTheme.typography.titleMedium,
                color = ErrorColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    } else {
        Column {
            val storyByUser = stories.groupBy { it.story.userId }
            
            // Stories Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // My Story (Add Story button)
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(72.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.sweepGradient(PrimaryGradientColors),
                                    shape = CircleShape
                                )
                                .clickable(enabled = !isUploadingStory) {
                                    storyPickerLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUploadingStory) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Primary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Story",
                                        tint = Primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "My Story",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Groups stories by user
                items(storyByUser.entries.toList()) { entry ->
                    val userId = entry.key
                    val userStories = entry.value
                    val firstStory = userStories.first()
                    val isCurrentUser = userId == SupabaseClient.currentUserId
                    val userName = if (isCurrentUser) "You" else (firstStory.userProfile?.fullName 
                        ?: firstStory.userProfile?.username 
                        ?: "User")
                    
                    val avatarUrl = firstStory.userProfile?.avatarUrl
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clickable { selectedStories = userStories }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(StoryGradientColors),
                                    shape = CircleShape
                                )
                                .padding(3.dp)
                        ) {
                            if (!avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = userName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                SmallGradientAvatar(
                                    initial = userName.firstOrNull()?.toString() ?: "?",
                                    size = 54.dp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = userName.split(" ").firstOrNull() ?: userName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // No-Line Rule: use vertical spacing instead of dividers
            Spacer(modifier = Modifier.height(12.dp))
            
            // Conversations list
            if (sortedConversations.isEmpty() && conversations.isNotEmpty()) {
                 // All archived?
                 Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                 ) {
                     Text("All conversations archived", color = TextSecondary)
                 }
            } else if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Chat,
                        message = "No conversations yet",
                        subtitle = "Start a new chat to get started"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), // tighter spacing, no dividers
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(sortedConversations) { conversation ->
                        val participantId = conversation.participant?.userId ?: conversation.participant?.id
                        val isOnline = participantId != null && onlineUsers.contains(participantId)
                        val unreadCount = if (!conversation.isGroup && isOnline) 1 else 0 // Faked for UI demonstration 

                        ConversationItem(
                            conversation = conversation,
                            isPinned = viewModel.isConversationPinned(conversation.id),
                            isMuted = viewModel.isConversationMuted(conversation.id),
                            isOnline = isOnline,
                            unreadCount = unreadCount,
                            onClick = { onConversationClick(conversation.id) },
                            onLongClick = { viewModel.selectConversationForActions(conversation.id) }
                        )
                    }
                }
            }
        }
    }
    
    // Story Viewer Dialog
    selectedStories?.let { userStories ->
        StoryViewerDialog(
            stories = userStories,
            onDismiss = { selectedStories = null }
        )
    }
    
    // Story Preview Dialog
    previewStoryUri?.let { uri ->
        StoryPreviewDialog(
            imageUri = uri,
            onDismiss = { previewStoryUri = null },
            onSend = { caption ->
                scope.launch {
                    isUploadingStory = true
                    previewStoryUri = null
                    SupabaseRepository.createStory(context, uri, caption).onSuccess {
                        // Refresh stories
                        SupabaseRepository.getStories().onSuccess { newStories ->
                            stories = newStories
                        }
                    }
                    isUploadingStory = false
                }
            }
        )
    }
}

// Story Preview Dialog
@Composable
private fun StoryPreviewDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onSend: (String?) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Image Preview
            AsyncImage(
                model = imageUri,
                contentDescription = "Story Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
            
            // Bottom Section with Caption
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(16.dp)
            ) {
                TextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Add a caption...", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FloatingActionButton(
                        onClick = { onSend(caption.takeIf { it.isNotBlank() }) },
                        containerColor = Primary,
                        contentColor = TextPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Share Story"
                        )
                    }
                }
            }
        }
    }
}

// Story Viewer Dialog
@Composable
private fun StoryViewerDialog(
    stories: List<StoryWithProfile>,
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val currentStory = stories.getOrNull(currentIndex) ?: return
    
    // Auto progress (5 seconds per story)
    LaunchedEffect(currentStory) {
        progress = 0f
        while (progress < 1f) {
            kotlinx.coroutines.delay(50)
            progress += 0.01f // 5 seconds = 100 * 50ms
        }
        // Auto advance
        if (currentIndex < stories.size - 1) {
            currentIndex++
        } else {
            onDismiss()
        }
    }
    
    val userName = currentStory.userProfile?.fullName ?: currentStory.userProfile?.username ?: "User"
    val timeAgo = currentStory.story.createdAt?.let { formatTimeAgo(it) } ?: "Just now"
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Story image
            AsyncImage(
                model = currentStory.story.mediaUrl,
                contentDescription = "Story",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // Touch navigation overlays
            Row(modifier = Modifier.fillMaxSize()) {
                // Left side (Previous)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (currentIndex > 0) {
                                currentIndex--
                                progress = 0f
                            }
                        }
                )
                // Right side (Next)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (currentIndex < stories.size - 1) {
                                currentIndex++
                                progress = 0f
                            } else {
                                onDismiss()
                            }
                        }
                )
            }
            
            // Top section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
                    .padding(16.dp)
            ) {
                // Segmented Progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { index, _ ->
                        val itemProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress
                            else -> 0f
                        }
                        
                        LinearProgressIndicator(
                            progress = itemProgress,
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp)),
                            color = TextPrimary,
                            trackColor = TextPrimary.copy(alpha = 0.3f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // User info row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SmallGradientAvatar(
                        initial = userName.firstOrNull()?.toString() ?: "?",
                        size = 36.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = timeAgo,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextPrimary
                        )
                    }
                }
            }
            
            // Caption at bottom if present
            currentStory.story.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// Helper function for time ago formatting
private fun formatTimeAgo(timestamp: String): String {
    return try {
        val instant = java.time.Instant.parse(timestamp.replace(" ", "T").let { ts ->
            if (!ts.contains("Z") && !ts.contains("+")) "${ts}Z" else ts
        })
        val now = java.time.Instant.now()
        val diff = java.time.Duration.between(instant, now)
        
        when {
            diff.toMinutes() < 1 -> "Just now"
            diff.toMinutes() < 60 -> "${diff.toMinutes()}m ago"
            diff.toHours() < 24 -> "${diff.toHours()}h ago"
            else -> "${diff.toDays()}d ago"
        }
    } catch (e: Exception) {
        "Recently"
    }
}

@Composable
private fun ContactsTab(
    contacts: List<ContactWithProfile>,
    isLoading: Boolean,
    onContactClick: (ContactWithProfile) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
    } else if (contacts.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Contacts,
            message = "No contacts yet",
            subtitle = "Search for users to add contacts"
        )
    } else {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(contacts) { contact ->
                ContactItem(
                    contact = contact,
                    onClick = { onContactClick(contact) }
                )
            }
        }
    }
}

@Composable
private fun CallsTab(
    calls: List<CallWithProfile>,
    isLoading: Boolean,
    onContactClick: (String, String) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
    } else if (calls.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Call,
            message = "No recent calls",
            subtitle = "Your call history will appear here"
        )
    } else {
        // Group calls by the other user's ID
        val groupedCalls = calls.groupBy { 
            if (it.isOutgoing) it.calleeId else it.callerId 
        }
        
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            groupedCalls.forEach { (contactId, contactCalls) ->
                val firstCall = contactCalls.first()
                val displayName = firstCall.otherUserProfile?.fullName 
                    ?: firstCall.otherUserProfile?.username 
                    ?: "Unknown"
                
                item(key = "contact_$contactId") {
                    ContactCallCard(
                        displayName = displayName,
                        avatarUrl = firstCall.otherUserProfile?.avatarUrl,
                        callCount = contactCalls.size,
                        onClick = { onContactClick(contactId, displayName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactCallCard(
    displayName: String,
    avatarUrl: String? = null,
    callCount: Int,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            SmallGradientAvatar(
                initial = displayName.firstOrNull()?.toString() ?: "?",
                imageUrl = avatarUrl,
                size = 52.dp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Name and call count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "$callCount call${if (callCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            // Arrow icon to indicate navigation
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View history",
                tint = TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: ConversationWithParticipant,
    onClick: () -> Unit,
    isPinned: Boolean = false,
    isMuted: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val participant = conversation.participant
    val lastMessage = conversation.lastMessage
    val unreadCount = 0 
    
    val displayName = if (conversation.isGroup) {
        conversation.groupName ?: "Unnamed Group"
    } else {
        participant?.fullName ?: participant?.username ?: "Unknown"
    }

    val displayAvatarUrl = if (conversation.isGroup) {
        conversation.groupAvatarUrl
    } else {
        participant?.avatarUrl
    }
    
    val initialChar = displayName.firstOrNull()?.toString() ?: "?"
    
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
               detectTapGestures(
                   onTap = { onClick() },
                   onLongPress = { onLongClick?.invoke() }
               )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Status
            Box {
                GradientAvatar(
                    initial = initialChar,
                    imageUrl = displayAvatarUrl,
                    size = 54.dp,
                    borderWidth = 2.dp
                )
                
                if (participant?.isOnline == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(androidx.compose.ui.graphics.Color.Black, CircleShape)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Success, CircleShape)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                
                // Smart preview based on message type
                val previewText = when {
                    conversation.lastMessage == null && conversation.lastMessageType == null -> null
                    conversation.lastMessageType == "image" -> "📷 Photo"
                    conversation.lastMessageType == "video" -> "🎥 Video"
                    conversation.lastMessageType == "document" -> "📄 Document"
                    conversation.lastMessageType == "voice" -> "🎤 Voice message"
                    conversation.lastMessageType == "poll" -> "📊 Poll"
                    conversation.lastMessage.isNullOrBlank() -> null
                    else -> conversation.lastMessage
                }

                previewText?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Stats
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                conversation.updatedAt?.let { timestamp ->
                    Text(
                        text = formatTimeAgo(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    if (isMuted) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Muted",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(Primary, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: ContactWithProfile,
    onClick: () -> Unit
) {
    val displayName = contact.nickname 
        ?: contact.profile?.fullName 
        ?: contact.profile?.username 
        ?: "Unknown"
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallGradientAvatar(
                initial = displayName.firstOrNull()?.toString() ?: "?",
                imageUrl = contact.profile?.avatarUrl,
                size = 48.dp
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                contact.profile?.bio?.let { bio ->
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            Icon(
                Icons.Default.Chat,
                contentDescription = "Start chat",
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDialog(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<Profile>,
    isSearching: Boolean,
    isAddingContact: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onUserSelect: (Profile) -> Unit,
    dialogTitle: String = "Search Users",
    actionText: String = "Add"
) {
    AlertDialog(
        onDismissRequest = { if (!isAddingContact) onDismiss() },
        containerColor = Surface,
        title = { Text(dialogTitle, color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search by name or phone", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isAddingContact,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Primary)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = SurfaceVariant
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Error message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = ErrorColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Adding contact loading
                if (isAddingContact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing...", color = TextSecondary)
                    }
                } else if (isSearching) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary)
                    }
                } else if (searchResults.isEmpty() && searchQuery.length >= 2) {
                    Text(
                        "No users found",
                        color = TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(searchResults) { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAddingContact) { onUserSelect(profile) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SmallGradientAvatar(
                                    initial = (profile.fullName?.firstOrNull() ?: "?").toString(),
                                    imageUrl = profile.avatarUrl,
                                    size = 40.dp
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.fullName ?: profile.username ?: "Unknown",
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    profile.phone?.let { phone ->
                                        Text(
                                            text = phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                
                                // Action icon
                                Icon(
                                    if (actionText == "Chat") Icons.Default.Chat else Icons.Default.PersonAdd,
                                    contentDescription = actionText,
                                    tint = Primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isAddingContact) {
                Text("Close", color = Primary)
            }
        }
    )
}

@Composable
private fun NewChatDialog(
    contacts: List<ContactWithProfile>,
    isCreating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onContactSelect: (ContactWithProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        containerColor = Surface,
        title = { Text("New Chat", color = TextPrimary) },
        text = {
            Column {
                // Error message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = ErrorColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Loading state
                if (isCreating) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creating chat...", color = TextSecondary)
                    }
                } else if (contacts.isEmpty()) {
                    Text("No contacts yet. Add contacts first using the search button!", color = TextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(contacts) { contact ->
                            val displayName = contact.nickname 
                                ?: contact.profile?.fullName 
                                ?: "Unknown"
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCreating) { onContactSelect(contact) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SmallGradientAvatar(
                                    initial = displayName.firstOrNull()?.toString() ?: "?",
                                    size = 40.dp
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Text(
                                    text = displayName,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    color = TextPrimary
                                )
                                
                                // Chat icon
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = "Start chat",
                                    tint = Primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Cancel", color = Primary)
            }
        }
    )
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    message: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = TextMuted
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
fun SettingsTab(
    onProfileClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    
    // Collect settings from repository
    val darkModeEnabled by settingsRepository.darkMode.collectAsState(initial = false)
    val messageNotifications by settingsRepository.messageNotifications.collectAsState(initial = true)
    val callNotifications by settingsRepository.callNotifications.collectAsState(initial = true)
    val vibrationEnabled by settingsRepository.vibrationEnabled.collectAsState(initial = true)
    val readReceipts by settingsRepository.readReceipts.collectAsState(initial = true)
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    
    // Load settings from cloud on first composition
    LaunchedEffect(Unit) {
        settingsRepository.loadFromCloud()
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Account Section
        item {
            SettingsSection(title = "Account")
        }
        item {
            SettingsItem(
                icon = Icons.Default.Person,
                title = "Account",
                subtitle = "Profile details",
                onClick = onProfileClick
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.Security,
                title = "Security",
                subtitle = "Two-step verification, device management",
                onClick = { onNavigate("security_settings") }
            )
        }
        
        // Appearance Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSection(title = "Appearance")
        }
        item {
            SettingsToggleItem(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = "Use dark theme",
                checked = darkModeEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch { settingsRepository.setDarkMode(enabled) }
                }
            )
        }
        
        // Notifications Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSection(title = "Notifications")
        }
        item {
            SettingsToggleItem(
                icon = Icons.Default.Message,
                title = "Message Notifications",
                subtitle = "Get notified when you receive messages",
                checked = messageNotifications,
                onCheckedChange = { enabled ->
                    coroutineScope.launch { settingsRepository.setMessageNotifications(enabled) }
                }
            )
        }
        item {
            SettingsToggleItem(
                icon = Icons.Default.Call,
                title = "Call Notifications",
                subtitle = "Get notified for incoming calls",
                checked = callNotifications,
                onCheckedChange = { enabled ->
                    coroutineScope.launch { settingsRepository.setCallNotifications(enabled) }
                }
            )
        }
        item {
            SettingsToggleItem(
                icon = Icons.Default.Vibration,
                title = "Vibration",
                subtitle = "Vibrate on notifications",
                checked = vibrationEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch { settingsRepository.setVibrationEnabled(enabled) }
                }
            )
        }
        
        // Privacy Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSection(title = "Privacy")
        }
        item {
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Privacy Settings",
                subtitle = "Last seen, profile photo, about",
                onClick = { onNavigate("privacy_settings") }
            )
        }
        item {
            SettingsToggleItem(
                icon = Icons.Default.DoneAll,
                title = "Read Receipts",
                subtitle = "Show when you've read messages",
                checked = readReceipts,
                onCheckedChange = { enabled ->
                    coroutineScope.launch { settingsRepository.setReadReceipts(enabled) }
                }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.Block,
                title = "Blocked Users",
                subtitle = "Manage blocked contacts",
                onClick = { onNavigate("blocked_users") }
            )
        }
        
        // Storage Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSection(title = "Storage & Data")
        }
        item {
            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Storage Usage",
                subtitle = "View storage used by app",
                onClick = { /* Implement storage usage screen if needed or just show info */ }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Cache",
                subtitle = "Free up space",
                onClick = { showClearCacheDialog = true }
            )
        }
        
        // About Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSection(title = "About")
        }
        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "App Version",
                subtitle = "1.0.0",
                onClick = { },
                showArrow = false
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.Help,
                title = "Help & Support",
                subtitle = "Get help and FAQs",
                onClick = { }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.Star,
                title = "Rate App",
                subtitle = "Love the app? Rate us!",
                onClick = { }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.Description,
                title = "Terms of Service",
                subtitle = "Read our terms",
                onClick = { }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "How we handle your data",
                onClick = { }
            )
        }
        
        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoggingOut) showLogoutDialog = false },
            containerColor = Surface,
            title = { Text("Logout", color = TextPrimary) },
            text = { 
                if (isLoggingOut) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Logging out...", color = TextSecondary)
                    }
                } else {
                    Text("Are you sure you want to logout?", color = TextSecondary) 
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        coroutineScope.launch {
                            isLoggingOut = true
                            try {
                                SupabaseClient.signOut(context)
                                showLogoutDialog = false
                                onLogout()
                            } catch (e: Exception) {
                                // handle error if needed
                            } finally {
                                isLoggingOut = false
                            }
                        }
                    },
                    enabled = !isLoggingOut
                ) {
                    Text("Logout", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !isLoggingOut
                ) {
                    Text("Cancel", color = Primary)
                }
            }
        )
    }
    
    // Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = Surface,
            title = { Text("Clear Cache", color = TextPrimary) },
            text = { Text("This will clear cached data. Your messages and contacts won't be affected.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { 
                    // TODO: Actually clear cache
                    showClearCacheDialog = false 
                }) {
                    Text("Clear", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tintColor: Color = Primary,
    showArrow: Boolean = true
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = tintColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = tintColor
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            if (showArrow) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SurfaceVariant
                )
            )
        }
    }
}
