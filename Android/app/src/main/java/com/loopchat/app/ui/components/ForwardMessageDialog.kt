package com.loopchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.loopchat.app.ui.theme.*

data class ForwardTarget(
    val conversationId: String,
    val name: String,
    val avatarUrl: String?,
    val isGroup: Boolean
)

/**
 * Forward Message Dialog — Electric Noir Design
 * Allows the user to select one or more conversations to forward a message to.
 */
@Composable
fun ForwardMessageDialog(
    conversations: List<ForwardTarget>,
    onForward: (selectedConversationIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered = if (searchQuery.isBlank()) conversations
    else conversations.filter { it.name.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainer,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Forward Message",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                // Search bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search conversations...", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                }
                                inner()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedIds.isNotEmpty()) {
                    Text(
                        "${selectedIds.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Conversation list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered) { target ->
                        val isSelected = target.conversationId in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Primary.copy(alpha = 0.15f) else SurfaceVariant)
                                .clickable {
                                    selectedIds = if (isSelected) selectedIds - target.conversationId
                                    else selectedIds + target.conversationId
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (target.avatarUrl != null) {
                                    AsyncImage(
                                        model = target.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = target.name.firstOrNull()?.toString() ?: "?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = target.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (target.isGroup) {
                                    Text("Group", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }

                            // Checkbox indicator
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selectedIds = if (isSelected) selectedIds - target.conversationId
                                    else selectedIds + target.conversationId
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Primary,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onForward(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Forward${if (selectedIds.size > 1) " (${selectedIds.size})" else ""}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
