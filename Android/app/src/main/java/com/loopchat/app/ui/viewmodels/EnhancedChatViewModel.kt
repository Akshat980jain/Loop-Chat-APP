package com.loopchat.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.MessageWithSender
import com.loopchat.app.data.MessagingFeaturesRepository
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import kotlinx.coroutines.launch
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class EnhancedChatViewModel : ViewModel() {
    
    var messages by mutableStateOf<List<MessageWithSender>>(emptyList())
        private set
    
    var otherParticipant by mutableStateOf<Profile?>(null)
        private set
    
    var isLoading by mutableStateOf(true)
        private set
    
    var isSending by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    // New features state
    var replyToMessage by mutableStateOf<MessageWithSender?>(null)
        private set
    
    var editingMessage by mutableStateOf<MessageWithSender?>(null)
        private set
    
    var selectedMessages by mutableStateOf<Set<String>>(emptySet())
        private set
    
    var isSelectionMode by mutableStateOf(false)
        private set
    
    var starredMessageIds by mutableStateOf<Set<String>>(emptySet())
        private set
    
    var messageReactions by mutableStateOf<Map<String, Map<String, List<String>>>>(emptyMap())
        private set // messageId -> (emoji -> list of userIds)
    
    var typingUsers by mutableStateOf<Set<String>>(emptySet())
        private set
    
    var showReactionPicker by mutableStateOf(false)
        private set
    
    var reactionPickerMessageId by mutableStateOf<String?>(null)
        private set
    
    private var currentConversationId: String? = null
    
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    fun loadMessages(conversationId: String) {
        currentConversationId = conversationId
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            // Load other participant
            loadOtherParticipant(conversationId)
            
            // Load messages
            val result = SupabaseRepository.getMessages(conversationId)
            result.onSuccess { messageList ->
                messages = messageList
                
                // Load starred messages
                loadStarredMessages()
                
                // Load reactions for all messages
                loadReactionsForMessages(messageList.map { it.id })
                
                if (otherParticipant == null) {
                    val currentUserId = SupabaseClient.currentUserId
                    val otherSender = messageList.firstOrNull { it.senderId != currentUserId }?.sender
                    if (otherSender != null) {
                        otherParticipant = otherSender
                    }
                }
            }.onFailure { e ->
                errorMessage = e.message
            }
            
            isLoading = false
        }
    }
    
    private suspend fun loadOtherParticipant(conversationId: String) {
        val currentUserId = SupabaseClient.currentUserId ?: return
        
        val result = SupabaseRepository.getConversationParticipants(conversationId)
        result.onSuccess { participants ->
            val otherParticipantProfile = participants.firstOrNull { 
                it.userId != currentUserId 
            }
            if (otherParticipantProfile != null) {
                otherParticipant = otherParticipantProfile
            }
        }
    }
    
    private suspend fun loadStarredMessages() {
        val result = MessagingFeaturesRepository.getStarredMessages(httpClient)
        result.onSuccess { starred ->
            starredMessageIds = starred.map { it.id }.toSet()
        }
    }
    
    private suspend fun loadReactionsForMessages(messageIds: List<String>) {
        val reactionsMap = mutableMapOf<String, Map<String, List<String>>>()
        
        messageIds.forEach { messageId ->
            val result = MessagingFeaturesRepository.getMessageReactions(httpClient, messageId)
            result.onSuccess { reactions ->
                // Group by emoji
                val grouped = reactions.groupBy { it.reaction }
                    .mapValues { (_, reactionList) -> reactionList.map { it.user_id } }
                reactionsMap[messageId] = grouped
            }
        }
        
        messageReactions = reactionsMap
    }
    
    fun sendMessage(content: String) {
        val conversationId = currentConversationId ?: return
        if (content.isBlank()) return
        
        viewModelScope.launch {
            isSending = true
            
            // Check if editing
            if (editingMessage != null) {
                editMessage(editingMessage!!.id, content)
                editingMessage = null
                return@launch
            }
            
            val result = SupabaseRepository.sendMessage(conversationId, content)
            result.onSuccess { message ->
                val currentProfile = Profile(
                    id = SupabaseClient.currentUserId ?: "",
                    fullName = "You"
                )
                val newMessage = MessageWithSender(
                    id = message.id,
                    content = message.content,
                    conversationId = message.conversationId,
                    senderId = message.senderId,
                    createdAt = message.createdAt,
                    sender = currentProfile
                )
                messages = messages + newMessage
                
                // Clear reply if replying
                replyToMessage = null
            }.onFailure { e ->
                errorMessage = e.message
            }
            
            isSending = false
        }
    }
    
    // ============================================
    // MESSAGE REACTIONS
    // ============================================
    
    fun showReactionPickerFor(messageId: String) {
        reactionPickerMessageId = messageId
        showReactionPicker = true
    }
    
    fun hideReactionPicker() {
        showReactionPicker = false
        reactionPickerMessageId = null
    }
    
    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            val result = MessagingFeaturesRepository.addReaction(httpClient, messageId, emoji)
            result.onSuccess {
                // Reload reactions for this message
                loadReactionsForMessages(listOf(messageId))
            }
        }
    }
    
    fun removeReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            val result = MessagingFeaturesRepository.removeReaction(httpClient, messageId, emoji)
            result.onSuccess {
                // Reload reactions for this message
                loadReactionsForMessages(listOf(messageId))
            }
        }
    }
    
    fun toggleReaction(messageId: String, emoji: String) {
        val currentUserId = SupabaseClient.currentUserId ?: return
        val messageReactionsList = messageReactions[messageId]?.get(emoji) ?: emptyList()
        
        if (currentUserId in messageReactionsList) {
            removeReaction(messageId, emoji)
        } else {
            addReaction(messageId, emoji)
        }
    }
    
    // ============================================
    // REPLY TO MESSAGE
    // ============================================
    
    fun prepareReply(message: MessageWithSender) {
        replyToMessage = message
        editingMessage = null // Cancel edit if replying
    }
    
    fun cancelReply() {
        replyToMessage = null
    }
    
    // ============================================
    // EDIT MESSAGE
    // ============================================
    
    fun startEditingMessage(message: MessageWithSender) {
        editingMessage = message
        replyToMessage = null // Cancel reply if editing
    }
    
    fun cancelEdit() {
        editingMessage = null
    }
    
    private suspend fun editMessage(messageId: String, newContent: String) {
        val result = MessagingFeaturesRepository.editMessage(httpClient, messageId, newContent)
        result.onSuccess {
            // Reload messages to show edited version
            currentConversationId?.let { loadMessages(it) }
        }.onFailure { e ->
            errorMessage = "Failed to edit: ${e.message}"
        }
        isSending = false
    }
    
    // ============================================
    // STAR MESSAGES
    // ============================================
    
    fun toggleStarMessage(messageId: String) {
        viewModelScope.launch {
            if (messageId in starredMessageIds) {
                val result = MessagingFeaturesRepository.unstarMessage(httpClient, messageId)
                result.onSuccess {
                    starredMessageIds = starredMessageIds - messageId
                }
            } else {
                val result = MessagingFeaturesRepository.starMessage(httpClient, messageId)
                result.onSuccess {
                    starredMessageIds = starredMessageIds + messageId
                }
            }
        }
    }
    
    // ============================================
    // FORWARD MESSAGES
    // ============================================
    
    fun forwardMessages(messageIds: List<String>, targetConversationIds: List<String>) {
        viewModelScope.launch {
            messageIds.forEach { messageId ->
                val result = MessagingFeaturesRepository.forwardMessage(
                    httpClient,
                    messageId,
                    targetConversationIds
                )
                result.onFailure { e ->
                    errorMessage = "Failed to forward: ${e.message}"
                }
            }
            exitSelectionMode()
        }
    }
    
    // ============================================
    // DELETE MESSAGES
    // ============================================
    
    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            val result = MessagingFeaturesRepository.deleteMessageForEveryone(httpClient, messageId)
            result.onSuccess {
                // Reload messages
                currentConversationId?.let { loadMessages(it) }
            }.onFailure { e ->
                errorMessage = "Failed to delete: ${e.message}"
            }
        }
    }
    
    fun deleteMessageForMe(messageId: String) {
        viewModelScope.launch {
            val result = MessagingFeaturesRepository.deleteMessageForMe(httpClient, messageId)
            result.onSuccess {
                // Remove message from local list
                messages = messages.filterNot { it.id == messageId }
            }.onFailure { e ->
                errorMessage = "Failed to delete: ${e.message}"
            }
        }
    }
    
    // ============================================
    // SELECTION MODE (for multi-select)
    // ============================================
    
    fun enterSelectionMode(messageId: String) {
        isSelectionMode = true
        selectedMessages = setOf(messageId)
    }
    
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedMessages = emptySet()
    }
    
    fun toggleMessageSelection(messageId: String) {
        selectedMessages = if (messageId in selectedMessages) {
            selectedMessages - messageId
        } else {
            selectedMessages + messageId
        }
        
        // Exit selection mode if no messages selected
        if (selectedMessages.isEmpty()) {
            isSelectionMode = false
        }
    }
    
    // ============================================
    // TYPING INDICATORS
    // ============================================
    
    fun updateTypingStatus(isTyping: Boolean) {
        val conversationId = currentConversationId ?: return
        
        viewModelScope.launch {
            MessagingFeaturesRepository.updateTypingStatus(httpClient, conversationId, isTyping)
        }
    }
    
    // ============================================
    // MESSAGE DELIVERY STATUS
    // ============================================
    
    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            MessagingFeaturesRepository.markMessageRead(httpClient, messageId)
        }
    }
    
    fun markMessageAsDelivered(messageId: String) {
        viewModelScope.launch {
            MessagingFeaturesRepository.markMessageDelivered(httpClient, messageId)
        }
    }
    
    // ============================================
    // UTILITY
    // ============================================
    
    fun updateOtherParticipant(profile: Profile?) {
        otherParticipant = profile
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    fun refresh() {
        currentConversationId?.let { loadMessages(it) }
    }
    
    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
