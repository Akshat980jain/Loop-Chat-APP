package com.loopchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.components.GlassCard
import com.loopchat.app.ui.theme.*

/**
 * Notifications Settings Screen — Electric Noir Design
 * Notification preference categories: Messages, Groups, Calls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit
) {
    // Local state for toggles
    var messageNotifications by remember { mutableStateOf(true) }
    var messagePreview by remember { mutableStateOf(true) }
    var messageSound by remember { mutableStateOf(true) }
    var messageVibrate by remember { mutableStateOf(true) }

    var groupNotifications by remember { mutableStateOf(true) }
    var groupPreview by remember { mutableStateOf(false) }

    var callNotifications by remember { mutableStateOf(true) }
    var callRingtone by remember { mutableStateOf(true) }
    var callVibrate by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notifications",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Messages Section
            item {
                SectionHeader("Messages")
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.Message,
                    title = "Message Notifications",
                    subtitle = "Get notified for new messages",
                    checked = messageNotifications,
                    onCheckedChange = { messageNotifications = it }
                )
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.Visibility,
                    title = "Message Preview",
                    subtitle = "Show message content in notification",
                    checked = messagePreview,
                    onCheckedChange = { messagePreview = it }
                )
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.MusicNote,
                    title = "Sound",
                    subtitle = "Play sound for messages",
                    checked = messageSound,
                    onCheckedChange = { messageSound = it }
                )
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.Vibration,
                    title = "Vibrate",
                    subtitle = "Vibrate on message",
                    checked = messageVibrate,
                    onCheckedChange = { messageVibrate = it }
                )
            }

            // Groups Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Groups")
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.Groups,
                    title = "Group Notifications",
                    subtitle = "Get notified for group messages",
                    checked = groupNotifications,
                    onCheckedChange = { groupNotifications = it }
                )
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.Preview,
                    title = "Group Preview",
                    subtitle = "Show group message preview",
                    checked = groupPreview,
                    onCheckedChange = { groupPreview = it }
                )
            }

            // Calls Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Calls")
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.Call,
                    title = "Call Notifications",
                    subtitle = "Get notified for incoming calls",
                    checked = callNotifications,
                    onCheckedChange = { callNotifications = it }
                )
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.RingVolume,
                    title = "Ringtone",
                    subtitle = "Play ringtone for calls",
                    checked = callRingtone,
                    onCheckedChange = { callRingtone = it }
                )
            }
            item {
                NotificationToggleCard(
                    icon = Icons.Default.Vibration,
                    title = "Call Vibrate",
                    subtitle = "Vibrate on incoming call",
                    checked = callVibrate,
                    onCheckedChange = { callVibrate = it }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun NotificationToggleCard(
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
