package com.loopchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import com.loopchat.app.ui.components.GlassCard
import com.loopchat.app.ui.components.GradientAvatar
import com.loopchat.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onBackClick: () -> Unit,
    onMessageClick: (String, String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scrollState = rememberScrollState()
    
    LaunchedEffect(userId) {
        isLoading = true
        val result = SupabaseRepository.getProfileById(userId)
        result.onSuccess { userProfile ->
            profile = userProfile
        }.onFailure { e ->
            errorMessage = e.message ?: "Failed to load profile"
        }
        isLoading = false
    }
    
    val displayName = profile?.fullName ?: profile?.username ?: "User"
    val displayUsername = profile?.username?.let { "@$it" } ?: ""
    val displayBio = profile?.bio ?: "Hey there! I'm using Loop Chat"
    val displayPhone = profile?.phone
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Profile INFO", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (errorMessage != null && profile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = errorMessage ?: "Unknown error", color = ErrorColor)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profile?.avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = profile?.avatarUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = 3.dp,
                                        brush = Brush.sweepGradient(AvatarBorderGradient),
                                        shape = CircleShape
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            GradientAvatar(
                                initial = displayName.firstOrNull()?.toString() ?: "?",
                                size = 110.dp,
                                borderWidth = 3.dp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    if (displayUsername.isNotEmpty()) {
                        Text(
                            text = displayUsername,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = displayBio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    
                    // Message action
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(48.dp)
                            .background(
                                brush = Brush.horizontalGradient(PrimaryGradientColors),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clip(RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { 
                                // Route to message logic handled by Navigation 
                                // Ideally passing the contact user ID
                                onMessageClick(userId, displayName)
                            },
                            modifier = Modifier.fillMaxSize(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "Message", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Message", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (!displayPhone.isNullOrEmpty()) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Contact Information",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Phone",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                        Text(
                                            text = displayPhone,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
