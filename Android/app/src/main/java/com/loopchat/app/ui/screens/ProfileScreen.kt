package com.loopchat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import com.loopchat.app.ui.components.GlassCard
import com.loopchat.app.ui.components.GradientAvatar
import com.loopchat.app.ui.components.GradientButtonLarge
import com.loopchat.app.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onLogout: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Editable fields
    var editedName by remember { mutableStateOf("") }
    var editedUsername by remember { mutableStateOf("") }
    var editedBio by remember { mutableStateOf("") }
    var editedPhone by remember { mutableStateOf("") }
    var editedStatus by remember { mutableStateOf("") }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Image picker launcher for profile picture
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isUploadingPhoto = true
                errorMessage = null
                successMessage = null
                
                val result = SupabaseRepository.uploadProfilePicture(context, selectedUri)
                result.onSuccess { newAvatarUrl ->
                    successMessage = "Profile picture updated!"
                    // Refresh profile to get new avatar URL
                    val refreshResult = SupabaseRepository.getCurrentUserProfile()
                    refreshResult.onSuccess { userProfile ->
                        profile = userProfile
                    }
                }.onFailure { e ->
                    errorMessage = e.message ?: "Failed to upload picture"
                }
                
                isUploadingPhoto = false
            }
        }
    }
    
    // Load profile on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        val result = SupabaseRepository.getCurrentUserProfile()
        result.onSuccess { userProfile ->
            profile = userProfile
            editedName = userProfile.fullName ?: ""
            editedUsername = userProfile.username ?: ""
            editedBio = userProfile.bio ?: ""
            editedPhone = userProfile.phone ?: ""
            editedStatus = "" // Status field if it exists
        }.onFailure { e ->
            errorMessage = e.message
        }
        isLoading = false
    }
    
    val displayName = profile?.fullName ?: profile?.username ?: "User"
    val displayUsername = profile?.username?.let { "@$it" } ?: ""
    val displayBio = profile?.bio ?: "Hey there! I'm using Loop Chat"
    val displayPhone = profile?.phone ?: SupabaseClient.currentPhone ?: "Not set"
    val displayEmail = SupabaseClient.currentEmail ?: "Not set"
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (!isEditing && !isLoading) {
                        IconButton(onClick = { 
                            isEditing = true
                            editedName = profile?.fullName ?: ""
                            editedUsername = profile?.username ?: ""
                            editedBio = profile?.bio ?: ""
                            editedPhone = profile?.phone ?: ""
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Primary)
                        }
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
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture with Camera Overlay
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clickable(enabled = !isUploadingPhoto) {
                                imagePickerLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Avatar image or gradient fallback
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
                        
                        // Camera icon overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Primary)
                                .border(2.dp, Background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploadingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = TextPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change photo",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Messages
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = Error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    successMessage?.let { success ->
                        Text(
                            text = success,
                            color = Success,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (isEditing) {
                        // Edit mode with glass card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Edit Profile",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Primary
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Full Name
                                ProfileTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    label = "Full Name",
                                    icon = Icons.Default.Person
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Username
                                ProfileTextField(
                                    value = editedUsername,
                                    onValueChange = { editedUsername = it.lowercase().replace(" ", "_") },
                                    label = "Username",
                                    icon = Icons.Default.AlternateEmail,
                                    placeholder = "your_username"
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Phone
                                ProfileTextField(
                                    value = editedPhone,
                                    onValueChange = { editedPhone = it },
                                    label = "Phone Number",
                                    icon = Icons.Default.Phone,
                                    placeholder = "+1234567890"
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Bio
                                ProfileTextField(
                                    value = editedBio,
                                    onValueChange = { editedBio = it },
                                    label = "Bio",
                                    icon = Icons.Default.Info,
                                    maxLines = 3,
                                    placeholder = "Tell us about yourself..."
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isEditing = false
                                            editedName = profile?.fullName ?: ""
                                            editedUsername = profile?.username ?: ""
                                            editedBio = profile?.bio ?: ""
                                            editedPhone = profile?.phone ?: ""
                                            errorMessage = null
                                            successMessage = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TextSecondary
                                        )
                                    ) {
                                        Text("Cancel")
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(
                                                brush = Brush.horizontalGradient(SunsetGradientColors),
                                                shape = RoundedCornerShape(24.dp)
                                            )
                                            .clip(RoundedCornerShape(24.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isSaving = true
                                                    errorMessage = null
                                                    successMessage = null
                                                    
                                                    val result = SupabaseRepository.updateProfile(
                                                        fullName = editedName,
                                                        username = editedUsername,
                                                        bio = editedBio,
                                                        phone = editedPhone
                                                    )
                                                    
                                                    result.onSuccess {
                                                        successMessage = "Profile updated successfully!"
                                                        // Refresh profile
                                                        val refreshResult = SupabaseRepository.getCurrentUserProfile()
                                                        refreshResult.onSuccess { userProfile ->
                                                            profile = userProfile
                                                        }
                                                        isEditing = false
                                                    }.onFailure { e ->
                                                        errorMessage = e.message ?: "Failed to update profile"
                                                    }
                                                    
                                                    isSaving = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                                contentColor = TextPrimary
                                            )
                                        ) {
                                            if (isSaving) {
                                                CircularProgressIndicator(
                                                    color = TextPrimary,
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text("Save")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // View mode - Display profile info
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
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Contact Info Card
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
                                
                                ProfileInfoRow(
                                    icon = Icons.Default.Phone,
                                    label = "Phone",
                                    value = displayPhone
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                ProfileInfoRow(
                                    icon = Icons.Default.Email,
                                    label = "Email",
                                    value = displayEmail
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Account Info Card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Account Info",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                ProfileInfoRow(
                                    icon = Icons.Default.Badge,
                                    label = "User ID",
                                    value = "${SupabaseClient.currentUserId?.take(12) ?: "null"}..."
                                )
                                
                                if (!displayUsername.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    ProfileInfoRow(
                                        icon = Icons.Default.AlternateEmail,
                                        label = "Username",
                                        value = profile?.username ?: "Not set"
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Logout button with gradient border
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(
                                width = 2.dp,
                                brush = Brush.horizontalGradient(listOf(Error, Error.copy(alpha = 0.6f))),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    SupabaseClient.signOut(context)
                                    onLogout()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Error.copy(alpha = 0.1f),
                                contentColor = Error
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Logout", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    maxLines: Int = 1,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder, color = TextMuted) }} else null,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = Primary)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = SurfaceVariant,
            focusedLabelColor = Primary,
            cursorColor = Primary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}
