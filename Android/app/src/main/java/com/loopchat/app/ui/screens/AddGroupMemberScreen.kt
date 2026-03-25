package com.loopchat.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.viewmodels.AddGroupMemberViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddGroupMemberScreen(
    groupId: String,
    viewModel: AddGroupMemberViewModel,
    onBackClick: () -> Unit,
    onSuccess: () -> Unit
) {
    val contactsPermissionState = rememberPermissionState(android.Manifest.permission.READ_CONTACTS)
    val isLoading = viewModel.isLoadingContacts
    val isAdding = viewModel.isAddingByProgress
    val isSuccess = viewModel.isSuccess
    val error = viewModel.error

    LaunchedEffect(contactsPermissionState.status.isGranted) {
        if (contactsPermissionState.status.isGranted) {
            viewModel.loadContacts(groupId)
        }
    }

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            onSuccess()
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Add Members", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (viewModel.selectedContactIds.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.addSelectedMembers(groupId) },
                            enabled = !isAdding
                        ) {
                            if (isAdding) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                            } else {
                                Text("Add", color = Accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Search by name or number", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = OutlineVariant,
                    cursorColor = Accent,
                    focusedLabelColor = Accent,
                    unfocusedLabelColor = TextSecondary
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selected Members Row
            if (viewModel.selectedContactIds.isNotEmpty()) {
                val selectedProfiles = viewModel.contactProfiles.values.filter { viewModel.selectedContactIds.contains(it.id) }.distinctBy { it.id }
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(selectedProfiles) { profile ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(64.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Surface)
                            ) {
                                if (profile.avatarUrl != null) {
                                    AsyncImage(
                                        model = profile.avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = (profile.fullName ?: profile.username ?: "?").first().toString(),
                                        modifier = Modifier.align(Alignment.Center),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Accent
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                        .clickable { 
                                            viewModel.removeSelectionByProfileId(profile.id)
                                        }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(10.dp).align(Alignment.Center))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profile.fullName ?: profile.username ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            if (!contactsPermissionState.status.isGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Contacts permission required", color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { contactsPermissionState.launchPermissionRequest() }) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (viewModel.deviceContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No new contacts to add", color = TextSecondary)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.filteredContacts) { contact ->
                        val profile = viewModel.contactProfiles[contact.phoneNumber]
                        val isRegistered = profile != null
                        val isSelected = profile != null && viewModel.selectedContactIds.contains(profile.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { 
                                    if (isRegistered) {
                                        viewModel.toggleContactSelection(contact.phoneNumber) 
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Surface)
                            ) {
                                Text(
                                    text = contact.name.first().toString(),
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isRegistered) Accent else TextMuted
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isRegistered) TextPrimary else TextMuted
                                )
                                val secondaryText = if (isRegistered) {
                                    if (!profile?.username.isNullOrBlank()) "${contact.phoneNumber} (@${profile!!.username})" else contact.phoneNumber
                                } else {
                                    "Invite to join"
                                }
                                Text(
                                    text = secondaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isRegistered) TextSecondary else Accent
                                )
                            }

                            if (isRegistered) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleContactSelection(contact.phoneNumber) },
                                    colors = CheckboxDefaults.colors(checkedColor = Accent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(error) {
        if (error != null) {
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
}
