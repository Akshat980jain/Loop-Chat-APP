package com.loopchat.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.components.SmallGradientAvatar
import com.loopchat.app.ui.theme.*

/**
 * Blocked Contacts Screen — Electric Noir Design
 * List of blocked users with unblock action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedContactsScreen(
    onBackClick: () -> Unit
) {
    // Mock blocked contacts
    val blockedContacts = remember {
        mutableStateListOf(
            BlockedContact("1", "John Doe", "Blocked 2 days ago"),
            BlockedContact("2", "Spam Account", "Blocked 1 week ago"),
            BlockedContact("3", "Unknown User", "Blocked 2 weeks ago")
        )
    }

    var showUnblockDialog by remember { mutableStateOf<BlockedContact?>(null) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Blocked Contacts",
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
        if (blockedContacts.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No blocked contacts",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Contacts you block will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    // Info card
                    Surface(
                        color = SurfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Blocked contacts can't send you messages or call you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                items(blockedContacts) { contact ->
                    BlockedContactCard(
                        contact = contact,
                        onUnblock = { showUnblockDialog = contact }
                    )
                }
            }
        }
    }

    // Unblock confirmation dialog
    showUnblockDialog?.let { contact ->
        AlertDialog(
            onDismissRequest = { showUnblockDialog = null },
            containerColor = SurfaceContainer,
            title = {
                Text("Unblock ${contact.name}?", color = TextPrimary)
            },
            text = {
                Text(
                    "This person will be able to message and call you again.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    blockedContacts.remove(contact)
                    showUnblockDialog = null
                }) {
                    Text("Unblock", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun BlockedContactCard(
    contact: BlockedContact,
    onUnblock: () -> Unit
) {
    Surface(
        color = SurfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallGradientAvatar(
                initial = contact.name.firstOrNull()?.toString() ?: "?",
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = contact.blockedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Unblock button
            Surface(
                onClick = onUnblock,
                color = ErrorColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Unblock",
                    style = MaterialTheme.typography.labelMedium,
                    color = ErrorColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private data class BlockedContact(
    val id: String,
    val name: String,
    val blockedDate: String
)
