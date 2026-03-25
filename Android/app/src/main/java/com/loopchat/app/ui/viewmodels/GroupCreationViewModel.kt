package com.loopchat.app.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.GroupRepository
import com.loopchat.app.data.PhoneContact
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
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

    // ALL device contacts (from the phone's address book)
    var deviceContacts by mutableStateOf<List<PhoneContact>>(emptyList())
        private set
        
    // Mapping of phone contact ID to Supabase Profile (only for those on Loop Chat)
    var contactProfiles by mutableStateOf<Map<String, Profile>>(emptyMap())
        private set

    var isLoadingContacts by mutableStateOf(true)
        private set

    // Selected contacts by their device contact ID
    var selectedContactIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set
        
    var isSuccess by mutableStateOf(false)
        private set
    
    var createdConversationId by mutableStateOf<String?>(null)
        private set
        
    var searchQuery by mutableStateOf("")
        private set

    val filteredContacts: List<PhoneContact>
        get() = if (searchQuery.isBlank()) {
            deviceContacts
        } else {
            deviceContacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery)
            }
        }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun updateGroupName(name: String) {
        groupName = name
    }
    
    fun updateGroupDescription(description: String) {
        groupDescription = description
    }

    fun loadContacts() {
        viewModelScope.launch {
            isLoadingContacts = true
            try {
                // Get ALL contacts from the device phonebook
                val contacts = com.loopchat.app.data.ContactsManager.getDeviceContacts(getApplication())
                deviceContacts = contacts
                
                val normalized = contacts.map { com.loopchat.app.util.PhoneUtils.normalize(it.phoneNumber) }
                val suffixes = normalized.map { if (it.length > 10) it.takeLast(10) else it }
                val phoneNumbers = (normalized + suffixes).distinct()
                if (phoneNumbers.isNotEmpty()) {
                    // Find registered users matching these phone numbers
                    val result = SupabaseRepository.findUsersByPhoneNumbers(phoneNumbers)
                    result.onSuccess { profiles ->
                        val mapping = mutableMapOf<String, Profile>()
                        profiles.forEach { profile ->
                            // Find which device contact(s) match this profile phone
                            contacts.filter { com.loopchat.app.util.PhoneUtils.areSame(it.phoneNumber, profile.phone ?: "") }.forEach { contact ->
                                mapping[contact.id] = profile
                            }
                        }
                        contactProfiles = mapping
                    }
                }
            } catch (e: SecurityException) {
                errorMessage = "Permission to read contacts was denied."
            } catch (e: Exception) {
                errorMessage = "Failed to access device contacts."
                e.printStackTrace()
            }
            isLoadingContacts = false
        }
    }

    fun toggleContactSelection(contactId: String) {
        // Only allow selection if we have a Supabase profile for this contact
        if (!contactProfiles.containsKey(contactId)) {
            errorMessage = "This contact is not on Loop Chat yet."
            return
        }
        
        val newSelection = selectedContactIds.toMutableSet()
        if (newSelection.contains(contactId)) {
            newSelection.remove(contactId)
        } else {
            newSelection.add(contactId)
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
        if (selectedContactIds.isEmpty()) {
            errorMessage = "Select at least one member"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            val result = repository.createGroup(
                name = groupName,
                description = groupDescription.ifBlank { null },
                avatarUrl = null
            )
            
            result.onSuccess { group ->
                // Now add the selected members to the group
                var allMembersAdded = true
                for (contactId in selectedContactIds) {
                    val profileId = contactProfiles[contactId]?.id ?: continue
                    val memberResult = repository.manageGroupMember(
                        groupId = group.id,
                        targetUserId = profileId,
                        action = "add",
                        role = "member"
                    )
                    if (memberResult.isFailure) {
                        allMembersAdded = false
                        errorMessage = "Group created but failed to add some members: ${memberResult.exceptionOrNull()?.message}"
                    }
                }
                
                if (allMembersAdded) {
                    // Look up the conversation ID for the new group to enable navigation
                    val convResult = repository.getConversationIdForGroup(group.id)
                    convResult.onSuccess { convId ->
                        createdConversationId = convId
                    }.onFailure {
                        // Even if lookup fails, still mark success so user isn't stuck
                        android.util.Log.w("GroupCreationVM", "Could not get conversation ID: ${it.message}")
                    }
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
