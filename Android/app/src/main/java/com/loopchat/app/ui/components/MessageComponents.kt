package com.loopchat.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopchat.app.ui.theme.*

/**
 * Reaction picker component for messages
 * Shows a horizontal list of emoji reactions
 */
@Composable
fun ReactionPicker(
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val commonReactions = listOf(
        "❤️", "😂", "😮", "😢", "🙏", "👍", "👎", "🔥"
    )
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceContainerHigh,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonReactions.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHighest)
                        .clickable {
                            onReactionSelected(emoji)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp
                    )
                }
            }
        }
    }
}

/**
 * Display reactions on a message
 */
@Composable
fun MessageReactions(
    reactions: Map<String, List<String>>, // emoji -> list of user IDs
    currentUserId: String,
    onReactionClick: (String) -> Unit,
    onAddReaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.entries.forEach { (emoji, userIds) ->
            val hasReacted = currentUserId in userIds
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (hasReacted) Primary.copy(alpha = 0.2f) else SurfaceVariant,
                border = if (hasReacted) androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Primary
                ) else null,
                modifier = Modifier.clickable { onReactionClick(emoji) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = emoji,
                        fontSize = 14.sp
                    )
                    if (userIds.size > 1) {
                        Text(
                            text = userIds.size.toString(),
                            fontSize = 12.sp,
                            color = if (hasReacted) Primary else TextSecondary,
                            fontWeight = if (hasReacted) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        
        // Add reaction button
        Surface(
            shape = CircleShape,
            color = SurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .clickable { onAddReaction() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add reaction",
                    modifier = Modifier.size(16.dp),
                    tint = TextSecondary
                )
            }
        }
    }
}

/**
 * Reply preview component shown when replying to a message
 */
@Composable
fun ReplyPreview(
    replyToMessage: String,
    replyToSender: String,
    onCancelReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(Primary, RoundedCornerShape(2.dp))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = replyToSender,
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = replyToMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
            
            IconButton(onClick = onCancelReply) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = TextSecondary
                )
            }
        }
    }
}

/**
 * Quoted message display in a reply
 */
@Composable
fun QuotedMessage(
    quotedText: String,
    quotedSender: String,
    onQuoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onQuoteClick),
        color = SurfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(Primary, RoundedCornerShape(2.dp))
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column {
                Text(
                    text = quotedSender,
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = quotedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Message action menu (edit, delete, forward, star, etc.)
 */
@Composable
fun MessageActionMenu(
    isOwnMessage: Boolean,
    isStarred: Boolean,
    canEdit: Boolean, // Within 5-minute window
    onReply: () -> Unit,
    onForward: () -> Unit,
    onStar: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerHigh,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // Reply
            MessageMenuItem(
                icon = Icons.Default.Reply,
                text = "Reply",
                onClick = {
                    onReply()
                    onDismiss()
                }
            )
            
            // Forward
            MessageMenuItem(
                icon = Icons.Default.Forward,
                text = "Forward",
                onClick = {
                    onForward()
                    onDismiss()
                }
            )
            
            // Star/Unstar
            MessageMenuItem(
                icon = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                text = if (isStarred) "Unstar" else "Star",
                onClick = {
                    onStar()
                    onDismiss()
                }
            )
            
            // Edit (only for own messages within 5 minutes)
            if (isOwnMessage && canEdit) {
                MessageMenuItem(
                    icon = Icons.Default.Edit,
                    text = "Edit",
                    onClick = {
                        onEdit()
                        onDismiss()
                    }
                )
            }
            
            // Ghost separator
            Spacer(modifier = Modifier.height(4.dp))
            
            // Delete for me
            MessageMenuItem(
                icon = Icons.Default.Delete,
                text = "Delete for me",
                onClick = {
                    onDelete()
                    onDismiss()
                },
                isDestructive = true
            )
            
            // Delete for everyone (only for own messages)
            if (isOwnMessage) {
                MessageMenuItem(
                    icon = Icons.Default.DeleteForever,
                    text = "Delete for everyone",
                    onClick = {
                        onDeleteForEveryone()
                        onDismiss()
                    },
                    isDestructive = true
                )
            }
        }
    }
}

@Composable
private fun MessageMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isDestructive) ErrorColor else TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDestructive) ErrorColor else TextPrimary
        )
    }
}

/**
 * Typing indicator animation
 */
@Composable
fun TypingIndicator(
    userName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$userName is typing",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
        
        // Animated dots
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                var visible by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index * 200L)
                    while (true) {
                        visible = true
                        kotlinx.coroutines.delay(600)
                        visible = false
                        kotlinx.coroutines.delay(600)
                    }
                }
                
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(TextSecondary, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Edited indicator for edited messages
 */
@Composable
fun EditedIndicator(
    modifier: Modifier = Modifier
) {
    Text(
        text = "edited",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        modifier = modifier
    )
}

/**
 * Forwarded indicator for forwarded messages
 */
@Composable
fun ForwardedIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.Forward,
            contentDescription = "Forwarded",
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Forwarded",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

/**
 * Message delivery status icons
 */
@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = TextMuted,
    readTint: androidx.compose.ui.graphics.Color = Primary
) {
    when (status) {
        MessageStatus.SENDING -> {
            CircularProgressIndicator(
                modifier = modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = tint
            )
        }
        MessageStatus.SENT -> {
            Icon(
                Icons.Default.Check,
                contentDescription = "Sent",
                tint = tint,
                modifier = modifier.size(16.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = tint,
                modifier = modifier.size(16.dp)
            )
        }
        MessageStatus.READ -> {
            Icon(
                Icons.Default.DoneAll,
                contentDescription = "Read",
                tint = readTint,
                modifier = modifier.size(16.dp)
            )
        }
    }
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}
