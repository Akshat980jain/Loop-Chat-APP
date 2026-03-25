package com.loopchat.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.GroupRepository
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.local.entities.GroupEntity
import com.loopchat.app.data.models.Profile
import kotlinx.coroutines.launch

class GroupInfoViewModel(private val repository: GroupRepository) : ViewModel() {

    var groupDetails by mutableStateOf<GroupEntity?>(null)
        private set

    var members by mutableStateOf<List<GroupRepository.GroupMemberInfo>>(emptyList())
        private set

    var isAdmin by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isOwner by mutableStateOf(false)
        private set

    fun loadGroupData(groupId: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            // Load group details
            val detailsResult = repository.getGroupDetails(groupId)
            detailsResult.onSuccess { details ->
                groupDetails = details
                isOwner = details.createdBy == SupabaseClient.currentUserId
            }.onFailure { e ->
                errorMessage = "Failed to load group details: ${e.message}"
            }

            // Load members
            val membersResult = repository.getGroupMembers(groupId)
            membersResult.onSuccess { memberList ->
                members = memberList
                val currentAuthId = SupabaseClient.currentUserId
                val currentUserRole = memberList.find { it.profile.userId == currentAuthId || it.profile.id == currentAuthId }?.role
                isAdmin = currentUserRole == "admin" || currentUserRole == "owner"
                
                // Fallback for owner verification if createdBy is empty or not matching auth.uid() directly
                if (currentUserRole == "owner") isOwner = true
            }.onFailure { e ->
                if (errorMessage == null) {
                    errorMessage = "Failed to load members: ${e.message}"
                }
            }
            
            isLoading = false
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            val result = repository.manageGroupMember(groupId, userId, "remove")
            result.onSuccess {
                loadGroupData(groupId)
            }.onFailure { e ->
                errorMessage = "Failed to remove member: ${e.message}"
            }
        }
    }

    fun makeAdmin(groupId: String, userId: String) {
        changeMemberRole(groupId, userId, "admin")
    }

    fun suspendMember(groupId: String, userId: String) {
        changeMemberRole(groupId, userId, "suspended")
    }

    private fun changeMemberRole(groupId: String, userId: String, newRole: String) {
        viewModelScope.launch {
            val result = repository.manageGroupMember(groupId, userId, "update_role", newRole)
            result.onSuccess {
                loadGroupData(groupId)
            }.onFailure { e ->
                errorMessage = "Failed to update member role: ${e.message}"
            }
        }
    }

    fun deleteGroup(groupId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            val result = repository.deleteGroup(groupId)
            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                errorMessage = "Failed to delete group: ${e.message}"
            }
            isLoading = false
        }
    }

    fun toggleGroupSuspension(groupId: String, isSuspended: Boolean) {
        viewModelScope.launch {
            isLoading = true
            val result = repository.suspendGroup(groupId, isSuspended)
            result.onSuccess {
                loadGroupData(groupId)
            }.onFailure { e ->
                errorMessage = "Failed to suspend group: ${e.message}"
            }
            isLoading = false
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
