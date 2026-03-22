package com.loopchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loopchat.app.data.PrivacySettings
import com.loopchat.app.ui.theme.*

/**
 * Privacy Settings Screen
 */
@Composable
fun PrivacySettingsScreen(
    settings: PrivacySettings,
    onUpdateSettings: (PrivacySettings) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentSettings by remember { mutableStateOf(settings) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Header
        Surface(
            color = Surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                }
                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Last Seen
            item {
                PrivacySettingItem(
                    icon = Icons.Default.Schedule,
                    title = "Last Seen",
                    subtitle = "Who can see when you were last online",
                    currentValue = currentSettings.last_seen_visibility,
                    options = listOf("everyone", "contacts", "nobody"),
                    onValueChange = { value ->
                        currentSettings = currentSettings.copy(last_seen_visibility = value)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
            
            // Profile Photo
            item {
                PrivacySettingItem(
                    icon = Icons.Default.AccountCircle,
                    title = "Profile Photo",
                    subtitle = "Who can see your profile photo",
                    currentValue = currentSettings.profile_photo_visibility,
                    options = listOf("everyone", "contacts", "nobody"),
                    onValueChange = { value ->
                        currentSettings = currentSettings.copy(profile_photo_visibility = value)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
            
            // About
            item {
                PrivacySettingItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Who can see your about info",
                    currentValue = currentSettings.about_visibility,
                    options = listOf("everyone", "contacts", "nobody"),
                    onValueChange = { value ->
                        currentSettings = currentSettings.copy(about_visibility = value)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
            
            // Read Receipts
            item {
                SwitchSettingItem(
                    icon = Icons.Default.DoneAll,
                    title = "Read Receipts",
                    subtitle = "If turned off, you won't send or receive read receipts",
                    checked = currentSettings.read_receipts_enabled,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(read_receipts_enabled = checked)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
            
            // Online Status
            item {
                SwitchSettingItem(
                    icon = Icons.Default.Circle,
                    title = "Online Status",
                    subtitle = "Show when you're online",
                    checked = currentSettings.show_online_status,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(show_online_status = checked)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
            
            // Groups
            item {
                PrivacySettingItem(
                    icon = Icons.Default.Group,
                    title = "Groups",
                    subtitle = "Who can add you to groups",
                    currentValue = currentSettings.who_can_add_to_groups,
                    options = listOf("everyone", "contacts"),
                    onValueChange = { value ->
                        currentSettings = currentSettings.copy(who_can_add_to_groups = value)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
        }
    }
}

@Composable
private fun PrivacySettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    currentValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showDialog = true },
        color = Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Text(
                text = currentValue.capitalize(),
                style = MaterialTheme.typography.bodyMedium,
                color = Primary
            )
        }
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(option)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option == currentValue,
                                onClick = {
                                    onValueChange(option)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.capitalize(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
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
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary
                )
            )
        }
    }
}

/**
 * Blocked Users Screen
 */
@Composable
fun BlockedUsersScreen(
    blockedUsers: List<String>,
    onUnblockUser: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Header
        Surface(
            color = Surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                }
                Text(
                    text = "Blocked Users",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
        
        if (blockedUsers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No blocked users",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(blockedUsers) { userId ->
                    BlockedUserItem(
                        userId = userId,
                        onUnblock = { onUnblockUser(userId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedUserItem(
    userId: String,
    onUnblock: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = TextSecondary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = userId,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(onClick = onUnblock) {
                Text("Unblock", color = Primary)
            }
        }
    }
}

/**
 * Message Search Bar
 */
@Composable
fun MessageSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Close", tint = TextPrimary)
            }
            
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search messages...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "Clear", tint = TextSecondary)
                }
            }
            
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, "Search", tint = Primary)
            }
        }
    }
}

/**
 * Conversation Action Menu (Archive, Pin, Mute, etc.)
 */
@Composable
fun ConversationActionMenu(
    isArchived: Boolean,
    isPinned: Boolean,
    isMuted: Boolean,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            MenuActionItem(
                icon = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                text = if (isArchived) "Unarchive" else "Archive",
                onClick = {
                    onArchive()
                    onDismiss()
                }
            )
            
            MenuActionItem(
                icon = if (isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                text = if (isPinned) "Unpin" else "Pin",
                onClick = {
                    onPin()
                    onDismiss()
                }
            )
            
            MenuActionItem(
                icon = if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                text = if (isMuted) "Unmute" else "Mute",
                onClick = {
                    onMute()
                    onDismiss()
                }
            )
            
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            MenuActionItem(
                icon = Icons.Default.Delete,
                text = "Delete Chat",
                textColor = ErrorColor,
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun MenuActionItem(
    icon: ImageVector,
    text: String,
    textColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = textColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}

/**
 * Mute Duration Dialog
 */
@Composable
fun MuteDurationDialog(
    onDurationSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute notifications") },
        text = {
            Column {
                MuteDurationOption("8 hours", "8h") { onDurationSelected(it) }
                MuteDurationOption("1 week", "7d") { onDurationSelected(it) }
                MuteDurationOption("Always", null) { onDurationSelected(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MuteDurationOption(
    label: String,
    duration: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(duration) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

// Helper extension
private fun String.capitalize() = this.replaceFirstChar { 
    if (it.isLowerCase()) it.titlecase() else it.toString() 
}
