package com.loopchat.app.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.GroupRepository
import com.loopchat.app.data.local.LoopChatDatabase
import kotlinx.coroutines.launch

class GroupCreationViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: GroupRepository
    
    init {
        val db = LoopChatDatabase.getDatabase(application)
        repository = GroupRepository(db)
    }

    var groupName by mutableStateOf("")
        private set
        
    var groupDescription by mutableStateOf("")
        private set

    // In a real app, this would be a list of User objects selected from contacts
    var selectedContactIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set
        
    var isSuccess by mutableStateOf(false)
        private set

    fun updateGroupName(name: String) {
        groupName = name
    }
    
    fun updateGroupDescription(description: String) {
        groupDescription = description
    }

    fun toggleContactSelection(userId: String) {
        val newSelection = selectedContactIds.toMutableSet()
        if (newSelection.contains(userId)) {
            newSelection.remove(userId)
        } else {
            newSelection.add(userId)
        }
        selectedContactIds = newSelection
    }

    fun clearError() {
        errorMessage = null
    }

    fun createGroup() {
        if (groupName.isBlank()) {
            errorMessage = "Group name is required"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            val result = repository.createGroup(
                name = groupName,
                description = groupDescription.ifBlank { null },
                avatarUrl = null // Handle avatar upload later
            )
            
            result.onSuccess { group ->
                // Now add the selected members to the group
                var allMembersAdded = true
                for (userId in selectedContactIds) {
                    val memberResult = repository.manageGroupMember(
                        groupId = group.id,
                        targetUserId = userId,
                        action = "add",
                        role = "member"
                    )
                    if (memberResult.isFailure) {
                        allMembersAdded = false
                        errorMessage = "Group created but failed to add some members."
                    }
                }
                
                if (allMembersAdded) {
                    isSuccess = true
                }
            }
            result.onFailure {
                errorMessage = it.message ?: "Failed to create group"
            }
            
            isLoading = false
        }
    }
}
