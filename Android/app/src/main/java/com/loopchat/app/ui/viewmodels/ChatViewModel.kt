package com.loopchat.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.MessageWithSender
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
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
    
    private var currentConversationId: String? = null
    
    fun loadMessages(conversationId: String) {
        currentConversationId = conversationId
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            // Load other participant first
            loadOtherParticipant(conversationId)
            
            val result = SupabaseRepository.getMessages(conversationId)
            result.onSuccess { messageList ->
                messages = messageList
                
                // If we didn't get participant from conversation, try from messages
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
    
    var currentConversation: com.loopchat.app.data.ConversationBasic? by mutableStateOf(null)
        private set

    private suspend fun loadOtherParticipant(conversationId: String) {
        val currentUserId = SupabaseClient.currentUserId ?: return
        
        // Load the conversation basic details first
        val convResult = SupabaseRepository.getConversation(conversationId)
        if (convResult.isSuccess) {
            val conv = convResult.getOrNull()
            currentConversation = conv
            
            if (conv?.is_group == true) {
                // Synthesize a Profile for the group so the UI uses its name and avatar
                otherParticipant = Profile(
                    id = conv.id,
                    fullName = conv.name ?: "Unnamed Group",
                    avatarUrl = conv.avatar_url,
                    username = "Group"
                )
                return // Skip loading individual participant
            }
        }
        
        // If not a group, get other participant from conversation_participants table
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
    
    fun sendMessage(content: String) {
        val conversationId = currentConversationId ?: return
        if (content.isBlank()) return
        
        viewModelScope.launch {
            isSending = true
            
            val result = SupabaseRepository.sendMessage(conversationId, content)
            result.onSuccess { message ->
                // Add the new message to the list
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
            }.onFailure { e ->
                errorMessage = e.message
            }
            
            isSending = false
        }
    }
    
    fun updateOtherParticipant(profile: Profile?) {
        otherParticipant = profile
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    fun refresh() {
        currentConversationId?.let { loadMessages(it) }
    }
}
