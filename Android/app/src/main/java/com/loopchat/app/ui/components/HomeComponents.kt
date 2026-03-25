package com.loopchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loopchat.app.data.ConversationWithParticipant
import com.loopchat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = SurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationWithParticipant,
    isPinned: Boolean,
    isMuted: Boolean,
    isOnline: Boolean = false,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        // Determine display properties based on whether it's a group
        val displayName = if (conversation.isGroup) {
            conversation.groupName ?: "Unnamed Group"
        } else {
            conversation.participant?.fullName ?: conversation.participant?.username ?: "Unknown User"
        }

        val displayAvatarUrl = if (conversation.isGroup) {
            conversation.groupAvatarUrl
        } else {
            conversation.participant?.avatarUrl
        }

        val initialChar = displayName.firstOrNull()?.toString() ?: "?"

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box {
                if (!displayAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = displayAvatarUrl,
                        contentDescription = displayName,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    SmallGradientAvatar(
                        initial = initialChar,
                        size = 56.dp,
                        isGroup = conversation.isGroup
                    )
                }
                
                if (!conversation.isGroup && isOnline) {
                    PresenceIndicator(
                        isOnline = true,
                        size = 14.dp,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Time and icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isMuted) {
                            Icon(
                                Icons.Default.VolumeOff,
                                contentDescription = "Muted",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        
                        if (isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = TextSecondary, // Slightly more visible for pin
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        
                        conversation.updatedAt?.let { ts ->
                            Text(
                                text = formatTime(ts), // Helper function needed or simple text
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unreadCount > 0) Primary else TextMuted,
                                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Smart preview based on message type
                    val previewText = when {
                        conversation.lastMessage == null && conversation.lastMessageType == null -> "No messages"
                        conversation.lastMessageType == "image" -> "📷 Photo"
                        conversation.lastMessageType == "video" -> "🎥 Video"
                        conversation.lastMessageType == "document" -> "📄 Document"
                        conversation.lastMessageType == "voice" -> "🎤 Voice message"
                        conversation.lastMessageType == "poll" -> "📊 Poll"
                        conversation.lastMessage.isNullOrBlank() -> "No messages"
                        else -> conversation.lastMessage
                    }

                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unreadCount > 0) TextPrimary else TextSecondary,
                        fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(24.dp)
                                .background(Primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// Simple time formatter that respects local timezone
private fun formatTime(timestamp: String): String {
    return try {
        // Normalize UTC timestamp string from Supabase
        val normalizedTimestamp = timestamp.replace(" ", "T").let { ts ->
            if (!ts.contains("Z") && !ts.contains("+")) "${ts}Z" else ts
        }
        
        val instant = Instant.parse(normalizedTimestamp)
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            
        formatter.format(instant)
    } catch (e: Exception) {
        // Fallback to rough extraction if parsing fails
        try {
            if (timestamp.length >= 16) timestamp.substring(11, 16) else ""
        } catch (e2: Exception) {
            ""
        }
    }
}
