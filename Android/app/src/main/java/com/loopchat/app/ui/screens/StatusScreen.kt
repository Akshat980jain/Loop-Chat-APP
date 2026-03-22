package com.loopchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.components.SmallGradientAvatar
import com.loopchat.app.ui.theme.*

/**
 * Status Screen — Electric Noir Design
 * My Status + friend statuses with gradient rings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onBackClick: () -> Unit
) {
    // Mock status data
    val mockStatuses = remember {
        listOf(
            StatusItem("1", "Sarah Jenkins", "2 hours ago", true),
            StatusItem("2", "Marcus Wright", "4 hours ago", true),
            StatusItem("3", "Elena Rodriguez", "Yesterday", false),
            StatusItem("4", "David Chen", "Yesterday", false),
            StatusItem("5", "Chloe Smith", "2 days ago", false)
        )
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Status",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceContainer
                )
            )
        },
        floatingActionButton = {
            // Primary gradient FAB for new status
            FloatingActionButton(
                onClick = { /* Add status */ },
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = PrimaryGradientColors,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(
                                Float.POSITIVE_INFINITY,
                                Float.POSITIVE_INFINITY
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Add Status", tint = TextPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // My Status Section
            item {
                Text(
                    "My Status",
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            item {
                MyStatusCard()
            }

            // Recent Updates
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Recent Updates",
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }

            // Unseen statuses
            val unseenStatuses = mockStatuses.filter { it.hasUnseen }
            if (unseenStatuses.isNotEmpty()) {
                items(unseenStatuses) { status ->
                    StatusListItem(status = status, isSeen = false)
                }
            }

            // Viewed updates
            val seenStatuses = mockStatuses.filter { !it.hasUnseen }
            if (seenStatuses.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Viewed",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                items(seenStatuses) { status ->
                    StatusListItem(status = status, isSeen = true)
                }
            }
        }
    }
}

@Composable
private fun MyStatusCard() {
    Surface(
        color = SurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add status avatar with "+" overlay
            Box(
                contentAlignment = Alignment.BottomEnd
            ) {
                SmallGradientAvatar(
                    initial = "M",
                    size = 52.dp
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .border(2.dp, Background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    "My Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    "Tap to add status update",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatusListItem(
    status: StatusItem,
    isSeen: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* View status */ },
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with gradient ring (unseen) or muted ring (seen)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(
                        width = 2.dp,
                        brush = if (!isSeen)
                            Brush.sweepGradient(StoryGradientColors)
                        else
                            Brush.sweepGradient(listOf(TextMuted, TextMuted)),
                        shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    status.userName.first().toString().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (!isSeen) Primary else TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = status.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

private data class StatusItem(
    val id: String,
    val userName: String,
    val time: String,
    val hasUnseen: Boolean
)
