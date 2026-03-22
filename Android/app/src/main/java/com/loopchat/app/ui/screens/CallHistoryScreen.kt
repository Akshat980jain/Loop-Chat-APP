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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loopchat.app.data.CallWithProfile
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import com.loopchat.app.ui.components.GlassCard
import com.loopchat.app.ui.components.SmallGradientAvatar
import com.loopchat.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    contactUserId: String,
    contactName: String,
    onBackClick: () -> Unit,
    onMessageClick: (String) -> Unit,
    onCallClick: (String, String) -> Unit
) {
    var calls by remember { mutableStateOf<List<CallWithProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var contactProfile by remember { mutableStateOf<Profile?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Fetch call history for this contact (paginated — loads all pages)
    LaunchedEffect(contactUserId) {
        isLoading = true
        
        // Get contact profile
        val profileResult = SupabaseRepository.getProfileById(contactUserId)
        profileResult.onSuccess { profile ->
            contactProfile = profile
        }
        
        // Paginate through ALL calls and filter for this contact
        val allCalls = mutableListOf<CallWithProfile>()
        var offset = 0
        val pageSize = 50
        var hasMore = true
        
        while (hasMore) {
            val callsResult = SupabaseRepository.getCallHistory(offset = offset, limit = pageSize)
            callsResult.onSuccess { page ->
                val filtered = page.filter { call ->
                    (call.isOutgoing && call.calleeId == contactUserId) ||
                    (!call.isOutgoing && call.callerId == contactUserId)
                }
                allCalls.addAll(filtered)
                offset += page.size
                hasMore = page.size >= pageSize
            }.onFailure {
                hasMore = false
            }
        }
        
        calls = allCalls
        isLoading = false
    }
    
    val displayName = contactProfile?.fullName ?: contactProfile?.username ?: contactName
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        displayName, 
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Contact card with action buttons
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large avatar
                    SmallGradientAvatar(
                        initial = displayName.firstOrNull()?.toString() ?: "?",
                        size = 80.dp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    Text(
                        text = "${calls.size} call${if (calls.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Message button
                        ActionButton(
                            icon = Icons.Default.Message,
                            label = "Message",
                            onClick = { 
                                coroutineScope.launch {
                                    // Create or get conversation and navigate
                                    val result = SupabaseRepository.createOrGetConversation(contactUserId)
                                    result.onSuccess { conversationId ->
                                        onMessageClick(conversationId)
                                    }
                                }
                            }
                        )
                        
                        // Voice call button
                        ActionButton(
                            icon = Icons.Default.Call,
                            label = "Voice",
                            onClick = { onCallClick(contactUserId, "audio") }
                        )
                        
                        // Video call button
                        ActionButton(
                            icon = Icons.Default.Videocam,
                            label = "Video",
                            onClick = { onCallClick(contactUserId, "video") }
                        )
                    }
                }
            }
            
            // Call history header
            Text(
                text = "Call History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Call history list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (calls.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No call history",
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(calls) { call ->
                        CallHistoryItem(call = call)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(PrimaryGradientColors)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun CallHistoryItem(call: CallWithProfile) {
    // Format full date and time
    val dateTimeText = call.createdAt?.let { 
        try {
            val instant = java.time.Instant.parse(it)
            val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm"))
        } catch (e: Exception) { "" }
    } ?: ""
    
    // Call status icon and color
    val (statusIcon, statusColor, statusText) = when {
        call.status == "missed" && !call.isOutgoing -> Triple(Icons.Default.CallMissed, ErrorColor, "Missed")
        call.status == "rejected" -> Triple(Icons.Default.CallEnd, ErrorColor, "Rejected")
        call.isOutgoing -> Triple(Icons.Default.CallMade, Online, "Outgoing")
        else -> Triple(Icons.Default.CallReceived, Online, "Incoming")
    }
    
    val callTypeText = if (call.callType == "video") "Video" else "Voice"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = statusColor
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Call details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$statusText $callTypeText call",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = dateTimeText,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
    
    Divider(
        color = SurfaceVariant,
        thickness = 0.5.dp
    )
}
