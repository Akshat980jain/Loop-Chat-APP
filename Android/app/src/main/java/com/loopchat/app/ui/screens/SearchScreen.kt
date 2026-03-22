package com.loopchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.components.SmallGradientAvatar
import com.loopchat.app.ui.theme.*

/**
 * Search Screen — Electric Noir Design
 * Full-screen search with category tabs (Chats, Contacts, Messages)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onChatClick: (String) -> Unit = {},
    onContactClick: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chats", "Contacts", "Messages")

    // Mock search results
    val mockChats = remember {
        listOf(
            SearchResult("1", "Sarah Jenkins", "Can't wait for the design sync tomorrow! ✨", "chat"),
            SearchResult("2", "Marcus Wright", "The assets are uploaded to the drive.", "chat"),
            SearchResult("3", "Elena Rodriguez", "Did you check the new loop animation?", "chat")
        )
    }
    val mockContacts = remember {
        listOf(
            SearchResult("4", "David Chen", "Available", "contact"),
            SearchResult("5", "Chloe Smith", "Busy", "contact"),
            SearchResult("6", "Alex Turner", "At work", "contact")
        )
    }
    val mockMessages = remember {
        listOf(
            SearchResult("7", "Sarah Jenkins", "Check the Figma file I shared yesterday", "message"),
            SearchResult("8", "Marcus Wright", "Meeting at 3pm confirmed", "message")
        )
    }

    val filteredResults = when (selectedTab) {
        0 -> mockChats
        1 -> mockContacts
        2 -> mockMessages
        else -> mockChats
    }.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            Surface(
                color = SurfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Search bar
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = SurfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextSecondary
                                )
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Search...", color = TextMuted)
                                },
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    cursorColor = Primary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                singleLine = true
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = Primary,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        color = if (selectedTab == index) Primary else TextSecondary,
                                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (filteredResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = TextMuted
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No results found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                            Text(
                                "Try a different search term",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                items(filteredResults) { result ->
                    SearchResultItem(
                        result = result,
                        onClick = {
                            when (result.type) {
                                "chat", "message" -> onChatClick(result.id)
                                "contact" -> onContactClick(result.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallGradientAvatar(
                initial = result.name.firstOrNull()?.toString() ?: "?",
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = result.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val icon = when (result.type) {
                "chat" -> Icons.Default.Chat
                "contact" -> Icons.Default.Person
                "message" -> Icons.Default.Message
                else -> Icons.Default.Search
            }

            Icon(
                icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class SearchResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val type: String // "chat", "contact", "message"
)
