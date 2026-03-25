package com.loopchat.app.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.GroupRepository
import com.loopchat.app.data.PhoneContact
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import com.loopchat.app.util.PhoneUtils
import kotlinx.coroutines.launch

class AddGroupMemberViewModel(
    application: Application,
    private val repository: GroupRepository
) : AndroidViewModel(application) {

    var deviceContacts by mutableStateOf<List<PhoneContact>>(emptyList())
        private set
        
    var contactProfiles by mutableStateOf<Map<String, Profile>>(emptyMap())
        private set

    var existingMemberPhones by mutableStateOf<Set<String>>(emptySet())
        private set

    var selectedContactIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoadingContacts by mutableStateOf(false)
        private set

    var isAddingByProgress by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isSuccess by mutableStateOf(false)
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

    fun loadContacts(groupId: String) {
        viewModelScope.launch {
            isLoadingContacts = true
            try {
                // 1. Get already present members
                val currentMembersResult = repository.getGroupMembers(groupId)
                val currentMemberPhones = currentMembersResult.getOrNull()?.mapNotNull { it.profile.phone }?.toSet() ?: emptySet()
                existingMemberPhones = currentMemberPhones

                // 2. Get device contacts
                val contacts = com.loopchat.app.data.ContactsManager.getDeviceContacts(getApplication())
                
                // 3. Keep all contacts (do not filter out existing members so they stay visible)
                deviceContacts = contacts

                // 4. Find registered users
                val normalized = deviceContacts.map { PhoneUtils.normalize(it.phoneNumber) }
                val suffixes = normalized.map { if (it.length > 10) it.takeLast(10) else it }
                val phoneNumbers = (normalized + suffixes).distinct()
                if (phoneNumbers.isNotEmpty()) {
                    val result = SupabaseRepository.findUsersByPhoneNumbers(phoneNumbers)
                    result.onSuccess { profiles ->
                        val mapping = mutableMapOf<String, Profile>()
                        profiles.forEach { profile ->
                            deviceContacts.filter { PhoneUtils.areSame(it.phoneNumber, profile.phone ?: "") }.forEach { contact ->
                                mapping[contact.phoneNumber] = profile
                            }
                        }
                        contactProfiles = mapping
                    }
                }
            } catch (e: Exception) {
                error = "Failed to load contacts: ${e.message}"
            }
            isLoadingContacts = false
        }
    }

    fun toggleContactSelection(phoneNumber: String) {
        val profile = contactProfiles[phoneNumber]
        if (profile == null) {
            error = "This contact is not on Loop Chat yet."
            return
        }

        val isAlreadyMember = existingMemberPhones.any { memberPhone -> PhoneUtils.areSame(phoneNumber, memberPhone) }
        if (isAlreadyMember) {
            error = "User is already added in this group."
            return
        }

        val newSelection = selectedContactIds.toMutableSet()
        if (newSelection.contains(profile.id)) {
            newSelection.remove(profile.id)
        } else {
            newSelection.add(profile.id)
        }
        selectedContactIds = newSelection
    }

    fun removeSelectionByProfileId(profileId: String) {
        val newSelection = selectedContactIds.toMutableSet()
        newSelection.remove(profileId)
        selectedContactIds = newSelection
    }

    fun addSelectedMembers(groupId: String) {
        if (selectedContactIds.isEmpty()) return

        viewModelScope.launch {
            isAddingByProgress = true
            var hasFailure = false
            
            selectedContactIds.forEach { contactId ->
                val profile = contactProfiles[contactId]
                if (profile != null) {
                    val result = repository.manageGroupMember(groupId, profile.id, "add")
                    if (result.isFailure) hasFailure = true
                }
            }

            if (hasFailure) {
                error = "Some members could not be added."
            } else {
                isSuccess = true
            }
            isAddingByProgress = false
        }
    }

    fun clearError() {
        error = null
    }
}
