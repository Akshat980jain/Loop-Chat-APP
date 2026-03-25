package com.loopchat.app.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopchat.app.data.UserSessionInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(
    sessions: List<UserSessionInfo>,
    isLoading: Boolean,
    onRevokeSession: (String) -> Unit,
    onRevokeAllOthers: () -> Unit,
    onBackClick: () -> Unit
) {
    var sessionToRevoke by remember { mutableStateOf<String?>(null) }
    var showRevokeAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Sessions", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E2E))
            )
        },
        containerColor = Color(0xFF1E1E2E)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && sessions.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF00FF88)
                )
            } else if (sessions.isEmpty()) {
                Text(
                    "No active sessions found.",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Currently active on these devices",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val currentSession = sessions.find { it.is_current }
                    val otherSessions = sessions.filter { !it.is_current }

                    if (currentSession != null) {
                        item {
                            SessionItem(
                                session = currentSession,
                                onRevoke = null // Cannot revoke current session here
                            )
                        }
                    }

                    if (otherSessions.isNotEmpty()) {
                        item {
                            Divider(color = Color(0xFF2A2A3C), modifier = Modifier.padding(vertical = 16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Other devices",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { showRevokeAllConfirm = true }) {
                                    Text("Sign out all", color = Color(0xFFFF4444))
                                }
                            }
                        }

                        items(otherSessions) { session ->
                            SessionItem(
                                session = session,
                                onRevoke = { sessionToRevoke = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (sessionToRevoke != null) {
        AlertDialog(
            onDismissRequest = { sessionToRevoke = null },
            title = { Text("Sign out device?") },
            text = { Text("You will be signed out of this device immediately.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRevokeSession(sessionToRevoke!!)
                        sessionToRevoke = null
                    }
                ) {
                    Text("Sign Out", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRevoke = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF2A2A3C),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }

    if (showRevokeAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeAllConfirm = false },
            title = { Text("Sign out all other devices?") },
            text = { Text("You will be signed out of all other devices immediately.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRevokeAllOthers()
                        showRevokeAllConfirm = false
                    }
                ) {
                    Text("Sign Out All", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeAllConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF2A2A3C),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}

@Composable
fun SessionItem(
    session: UserSessionInfo,
    onRevoke: ((String) -> Unit)?
) {
    val deviceInfo = session.device_info ?: emptyMap()
    val isMobile = deviceInfo["device_type"] == "mobile"
    val icon: ImageVector = if (isMobile) Icons.Default.PhoneAndroid else Icons.Default.Computer
    
    val browser = deviceInfo["browser"] ?: "Unknown Browser"
    val os = deviceInfo["os"] ?: "Unknown OS"
    val model = deviceInfo["device_model"]
    val title = if (model != null) "$model ($browser)" else "$os • $browser"
    
    val currentLabel = if (session.is_current) "Current session • " else ""
    val location = session.ip_address?.let { maskIpAddress(it) } ?: "Unknown location"
    
    val lastActiveFormatted = session.last_active?.let { formatIsoString(it) } ?: "Just now"
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3C)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF3A3A4C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "$currentLabel$location",
                    color = if (session.is_current) Color(0xFF00FF88) else Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = lastActiveFormatted,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            if (onRevoke != null) {
                IconButton(onClick = { onRevoke(session.id) }) {
                    Icon(Icons.Default.Logout, contentDescription = "Revoke session", tint = Color(0xFFFF4444))
                }
            }
        }
    }
}

private fun maskIpAddress(ip: String): String {
    if (ip.contains(".")) {
        val parts = ip.split(".")
        if (parts.size == 4) return "${parts[0]}.${parts[1]}.*.*"
    } else if (ip.contains(":")) {
        val parts = ip.split(":")
        if (parts.size >= 4) return "${parts[0]}:${parts[1]}:*:*"
    }
    return "Unknown IP"
}

// Helper to format ISO strings from Supabase sessions
private fun formatIsoString(isoString: String): String {
    return try {
        // Normalize UTC timestamp string
        val normalizedTimestamp = isoString.replace(" ", "T").let { ts ->
            if (!ts.contains("Z") && !ts.contains("+")) "${ts}Z" else ts
        }
        
        val instant = Instant.parse(normalizedTimestamp)
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            
        formatter.format(instant)
    } catch (e: Exception) {
        isoString
    }
}
