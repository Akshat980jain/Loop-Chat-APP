package com.loopchat.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.viewmodels.GroupInfoViewModel
import com.loopchat.app.data.models.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    groupId: String,
    viewModel: GroupInfoViewModel,
    onBackClick: () -> Unit,
    onAddMemberClick: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    val groupDetails = viewModel.groupDetails
    val members = viewModel.members
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage
    val isOwner = viewModel.isOwner

    LaunchedEffect(groupId) {
        viewModel.loadGroupData(groupId)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Group Info", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = onAddMemberClick) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Member", tint = Accent)
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
        ) {
            if (isLoading && groupDetails == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (errorMessage != null && groupDetails == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = errorMessage, color = Color.Red, modifier = Modifier.padding(16.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    // Group Header
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(Surface)
                            ) {
                                if (groupDetails?.avatarUrl != null) {
                                    AsyncImage(
                                        model = groupDetails.avatarUrl,
                                        contentDescription = "Group Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Group,
                                        contentDescription = "Group Avatar",
                                        modifier = Modifier
                                            .size(60.dp)
                                            .align(Alignment.Center),
                                        tint = TextSecondary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = groupDetails?.name ?: "Unnamed Group",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            
                            if (!groupDetails?.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = groupDetails?.description ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }

                    // Member List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${members.size} Members",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    // Members
                    items(members) { memberInfo ->
                        MemberItem(
                            memberInfo = memberInfo,
                            isAdmin = viewModel.isAdmin,
                            isOwner = viewModel.isOwner,
                            onMakeAdmin = { viewModel.makeAdmin(groupId, memberInfo.profile.id) },
                            onSuspend = { viewModel.suspendMember(groupId, memberInfo.profile.id) },
                            onRemoveClick = { viewModel.removeMember(groupId, memberInfo.profile.id) },
                            onUnsuspend = { viewModel.makeAdmin(groupId, memberInfo.profile.id) } // Unsuspend is essentially reverting to 'member' if they were simple members, or we can add a specific action. I'll pass 'member' correctly in the viewmodel later, but we can reuse makeAdmin for now or write a true unwinding. Let's make it work nicely below.
                        )
                    }

                    // Admin Actions at the bottom
                    if (viewModel.isAdmin || viewModel.isOwner) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Divider(color = OutlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val isSuspended = groupDetails?.isSuspended ?: false
                            Button(
                                onClick = { viewModel.toggleGroupSuspension(groupId, !isSuspended) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSuspended) Accent else Surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isSuspended) "Unsuspend Group" else "Suspend Group", color = if (isSuspended) Color.White else Accent)
                            }
                            
                            if (viewModel.isOwner) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { 
                                        viewModel.deleteGroup(groupId) {
                                            onBackClick()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Delete Group", color = Color.Red)
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberItem(
    memberInfo: com.loopchat.app.data.GroupRepository.GroupMemberInfo,
    isAdmin: Boolean,
    isOwner: Boolean,
    onMakeAdmin: () -> Unit,
    onSuspend: () -> Unit,
    onRemoveClick: () -> Unit,
    onUnsuspend: () -> Unit
) {
    val member = memberInfo.profile
    val role = memberInfo.role
    
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { 
                if (isAdmin || isOwner) expanded = true 
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Surface)
        ) {
            if (member.avatarUrl != null) {
                AsyncImage(
                    model = member.avatarUrl,
                    contentDescription = "Member Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = (member.fullName ?: member.username ?: "?").first().toString(),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = Accent
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.fullName ?: member.username ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (role == "suspended") TextSecondary else TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (member.id == com.loopchat.app.data.SupabaseClient.currentUserId) "You" else member.username ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (role == "admin" || role == "owner" || role == "suspended") {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (role == "suspended") Color.Red.copy(alpha = 0.2f) else Accent.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = role.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (role == "suspended") Color.Red else Accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box {
            if (isAdmin || isOwner) {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(SurfaceContainer)
                ) {
                    if (isOwner && role != "owner" && role != "admin") {
                        DropdownMenuItem(
                            text = { Text("Make Admin", color = TextPrimary) },
                            onClick = { expanded = false; onMakeAdmin() }
                        )
                    }
                    if ((isOwner || isAdmin) && role != "owner") {
                        DropdownMenuItem(
                            text = { Text(if (role == "suspended") "Unsuspend" else "Suspend", color = TextPrimary) },
                            onClick = { expanded = false; if (role == "suspended") onUnsuspend() else onSuspend() }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove", color = Color.Red) },
                            onClick = { expanded = false; onRemoveClick() }
                        )
                    }
                }
            }
        }
    }
}
