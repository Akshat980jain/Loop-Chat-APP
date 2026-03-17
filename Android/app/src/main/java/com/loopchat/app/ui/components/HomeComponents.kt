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
import coil.compose.AsyncImage
import com.loopchat.app.data.ConversationWithParticipant
import com.loopchat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

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
                    isGroup = conversation.isGroup // Added parameter assuming it exists or can be ignored
                )
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
                                color = TextMuted
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = conversation.lastMessage ?: "No messages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Simple time formatter
private fun formatTime(timestamp: String): String {
    // Try to parse ISO string roughly
    // This is a simplification. Ideally use a proper date formatter.
    return try {
        timestamp.substring(11, 16) // Extract HH:mm
    } catch (e: Exception) {
        ""
    }
}
