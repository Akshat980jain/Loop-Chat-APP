package com.loopchat.app.ui.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.CallWithProfile
import com.loopchat.app.data.ContactWithProfile
import com.loopchat.app.data.ConversationWithParticipant
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    
    private var pollingJob: Job? = null
    
    var conversations by mutableStateOf<List<ConversationWithParticipant>>(emptyList())
        private set
    
    var contacts by mutableStateOf<List<ContactWithProfile>>(emptyList())
        private set
    
    var calls by mutableStateOf<List<CallWithProfile>>(emptyList())
        private set
    
    var searchResults by mutableStateOf<List<Profile>>(emptyList())
        private set
    
    var isLoadingConversations by mutableStateOf(true)
        private set
    
    var isLoadingContacts by mutableStateOf(true)
        private set
    
    var isLoadingCalls by mutableStateOf(true)
        private set
    
    var isSearching by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var showSearchDialog by mutableStateOf(false)
        private set
    
    var showNewChatDialog by mutableStateOf(false)
        private set
    
    var isAddingContact by mutableStateOf(false)
        private set
    
    var isCreatingConversation by mutableStateOf(false)
        private set
    
    // Phase 2: Privacy & Organization state
    var archivedConversations by mutableStateOf<List<String>>(emptyList())
        private set
    
    var pinnedConversations by mutableStateOf<List<String>>(emptyList())
        private set
    
    var mutedConversations by mutableStateOf<List<String>>(emptyList())
        private set
    
    var blockedUsers by mutableStateOf<List<String>>(emptyList())
        private set
    
    var messageSearchQuery by mutableStateOf("")
        private set
    
    var messageSearchResults by mutableStateOf<List<com.loopchat.app.data.MessageWithSender>>(emptyList())
        private set
    
    var showMessageSearch by mutableStateOf(false)
        private set
    
    var selectedConversationForActions by mutableStateOf<String?>(null)
        private set
    
    fun loadConversations() {
        val userId = SupabaseClient.currentUserId
        
        if (userId == null) {
            errorMessage = "Not logged in - User ID is null"
            isLoadingConversations = false
            return
        }
        
        viewModelScope.launch {
            isLoadingConversations = true
            errorMessage = null
            
            try {
                val result = SupabaseRepository.getConversations(userId)
                result.onSuccess { convs ->
                    conversations = convs.sortedByDescending { it.updatedAt }
                    // Empty conversations list is normal for new accounts - no error needed
                }.onFailure { e ->
                    errorMessage = e.message ?: "Unknown error"
                }
            } catch (e: Exception) {
                errorMessage = "Exception: ${e.message}"
            }
            
            isLoadingConversations = false
        }
    }
    
    fun loadContacts() {
        val userId = SupabaseClient.currentUserId ?: return
        
        viewModelScope.launch {
            isLoadingContacts = true
            
            val result = SupabaseRepository.getContacts(userId)
            result.onSuccess { contactList ->
                contacts = contactList
            }.onFailure { e ->
                errorMessage = e.message
            }
            
            isLoadingContacts = false
        }
    }
    
    fun loadCalls() {
        viewModelScope.launch {
            isLoadingCalls = true
            
            val result = SupabaseRepository.getCallHistory()
            result.onSuccess { callList ->
                calls = callList
            }.onFailure { e ->
                // Don't set error for calls tab
            }
            
            isLoadingCalls = false
        }
    }
    
    fun searchUsers(query: String) {
        if (query.length < 2) {
            searchResults = emptyList()
            return
        }
        
        viewModelScope.launch {
            isSearching = true
            errorMessage = null
            
            val result = SupabaseRepository.searchUsers(query)
            result.onSuccess { profiles ->
                searchResults = profiles
            }.onFailure { e ->
                searchResults = emptyList()
                errorMessage = "Search failed: ${e.message}"
            }
            
            isSearching = false
        }
    }
    
    fun createConversation(otherUserId: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            isCreatingConversation = true
            errorMessage = null
            
            val result = SupabaseRepository.createOrGetConversation(otherUserId)
            result.onSuccess { conversationId ->
                showNewChatDialog = false
                loadConversations() // Refresh list
                onSuccess(conversationId)
            }.onFailure { e ->
                errorMessage = "Failed to create chat: ${e.message}"
            }
            
            isCreatingConversation = false
        }
    }
    
    fun addContact(contactUserId: String) {
        viewModelScope.launch {
            isAddingContact = true
            errorMessage = null
            
            val result = SupabaseRepository.addContact(contactUserId)
            result.onSuccess {
                loadContacts() // Refresh list
                showSearchDialog = false
                errorMessage = null
            }.onFailure { e ->
                errorMessage = "Failed to add contact: ${e.message}"
            }
            
            isAddingContact = false
        }
    }
    
    fun openSearchDialog() {
        showSearchDialog = true
        searchResults = emptyList()
    }
    
    fun closeSearchDialog() {
        showSearchDialog = false
        searchResults = emptyList()
    }
    
    fun openNewChatDialog() {
        showNewChatDialog = true
    }
    
    fun closeNewChatDialog() {
        showNewChatDialog = false
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    fun refresh() {
        loadConversations()
        loadContacts()
        loadCalls()
        loadPhase2Data()
    }
    
    /**
     * Start periodic polling of conversations every 10 seconds.
     * Called when Chats tab is visible.
     */
    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(10_000L) // 10 seconds
                loadConversations()
            }
        }
    }
    
    /**
     * Stop polling when Chats tab is no longer visible.
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    // ============================================
    // PHASE 2: PRIVACY & ORGANIZATION FUNCTIONS
    // ============================================
    
    fun loadPhase2Data() {
        viewModelScope.launch {
            loadArchivedConversations()
            loadPinnedConversations()
            loadMutedConversations()
            loadBlockedUsers()
        }
    }
    
    private suspend fun loadArchivedConversations() {
        val result = com.loopchat.app.data.PrivacySecurityRepository.getArchivedConversations(
            SupabaseClient.httpClient
        )
        result.onSuccess { archived ->
            archivedConversations = archived
        }
    }
    
    private suspend fun loadPinnedConversations() {
        val result = com.loopchat.app.data.PrivacySecurityRepository.getPinnedConversations(
            SupabaseClient.httpClient
        )
        result.onSuccess { pinned ->
            pinnedConversations = pinned
        }
    }
    
    private suspend fun loadMutedConversations() {
        val result = com.loopchat.app.data.PrivacySecurityRepository.getMutedConversations(
            SupabaseClient.httpClient
        )
        result.onSuccess { muted ->
            mutedConversations = muted
        }
    }
    
    private suspend fun loadBlockedUsers() {
        val result = com.loopchat.app.data.PrivacySecurityRepository.getBlockedUsers(
            SupabaseClient.httpClient
        )
        result.onSuccess { blocked ->
            blockedUsers = blocked
        }
    }
    
    fun archiveConversation(conversationId: String) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.PrivacySecurityRepository.archiveConversation(
                SupabaseClient.httpClient,
                conversationId
            )
            result.onSuccess {
                loadArchivedConversations()
                loadConversations()
            }
        }
    }
    
    fun unarchiveConversation(conversationId: String) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.PrivacySecurityRepository.unarchiveConversation(
                SupabaseClient.httpClient,
                conversationId
            )
            result.onSuccess {
                loadArchivedConversations()
                loadConversations()
            }
        }
    }
    
    fun pinConversation(conversationId: String) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.PrivacySecurityRepository.pinConversation(
                SupabaseClient.httpClient,
                conversationId,
                pinnedConversations.size
            )
            result.onSuccess {
                loadPinnedConversations()
            }
        }
    }
    
    fun unpinConversation(conversationId: String) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.PrivacySecurityRepository.unpinConversation(
                SupabaseClient.httpClient,
                conversationId
            )
            result.onSuccess {
                loadPinnedConversations()
            }
        }
    }
    
    fun muteConversation(conversationId: String, duration: String?) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.PrivacySecurityRepository.muteConversation(
                SupabaseClient.httpClient,
                conversationId,
                duration
            )
            result.onSuccess {
                loadMutedConversations()
            }
        }
    }
    
    fun unmuteConversation(conversationId: String) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.PrivacySecurityRepository.unmuteConversation(
                SupabaseClient.httpClient,
                conversationId
            )
            result.onSuccess {
                loadMutedConversations()
            }
        }
    }
    
    fun searchMessages(query: String) {
        messageSearchQuery = query
        if (query.isEmpty()) {
            messageSearchResults = emptyList()
            return
        }
        
        viewModelScope.launch {
            val result = com.loopchat.app.data.PrivacySecurityRepository.searchMessages(
                SupabaseClient.httpClient,
                query
            )
            result.onSuccess { results ->
                messageSearchResults = results
            }
        }
    }
    
    fun toggleMessageSearch() {
        showMessageSearch = !showMessageSearch
        if (!showMessageSearch) {
            messageSearchQuery = ""
            messageSearchResults = emptyList()
        }
    }
    
    fun selectConversationForActions(conversationId: String?) {
        selectedConversationForActions = conversationId
    }
    
    fun isConversationArchived(conversationId: String): Boolean {
        return archivedConversations.contains(conversationId)
    }
    
    fun isConversationPinned(conversationId: String): Boolean {
        return pinnedConversations.contains(conversationId)
    }
    
    fun isConversationMuted(conversationId: String): Boolean {
        return mutedConversations.contains(conversationId)
    }
}
