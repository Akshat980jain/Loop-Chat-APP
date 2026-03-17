package com.loopchat.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.MessageWithSender
import com.loopchat.app.data.MediaUploadManager
import com.loopchat.app.data.MessagingFeaturesRepository
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.loopchat.app.data.local.entities.toEntity

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
    
    // Media attachment state
    var selectedMediaUri by mutableStateOf<Uri?>(null)
        private set
    var selectedMediaType by mutableStateOf<String?>(null) // "image", "video", "document"
        private set
    var isUploading by mutableStateOf(false)
        private set
    var uploadProgress by mutableStateOf<String?>(null)
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
    
    fun loadMessages(conversationId: String, context: android.content.Context) {
        currentConversationId = conversationId
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            // Initialize and connect Realtime WebSockets
            com.loopchat.app.data.realtime.SupabaseRealtimeClient.initialize(context)
            com.loopchat.app.data.realtime.SupabaseRealtimeClient.connectAndSubscribe(conversationId)
            
            // Load other participant and starred messages
            loadOtherParticipant(conversationId)
            loadStarredMessages()
            
            val db = com.loopchat.app.data.local.LoopChatDatabase.getDatabase(context)
            
            // 1. Observe Single Source of Truth from Room Database
            launch {
                db.messageDao().observeMessages(conversationId).collect { entities ->
                    val userIds = entities.map { it.senderId }.distinct()
                    val profiles = db.userDao().getUsersByIds(userIds)
                    val profileMap = profiles.associateBy { it.id }
                    
                    val messageList = entities.map { entity ->
                        val userEntity = profileMap[entity.senderId]
                        val profile = userEntity?.let {
                            com.loopchat.app.data.models.Profile(
                                id = it.id,
                                userId = it.id,
                                username = it.username,
                                fullName = it.fullName,
                                avatarUrl = it.avatarUrl,
                                isOnline = it.isOnline,
                                lastSeen = it.lastSeen
                            )
                        }
                        
                        MessageWithSender(
                            id = entity.id,
                            content = entity.content,
                            conversationId = entity.conversationId,
                            senderId = entity.senderId,
                            createdAt = entity.createdAt,
                            sender = profile,
                            mediaUrl = entity.mediaUrl,
                            messageType = entity.messageType
                        )
                    }
                    
                    messages = messageList
                    loadReactionsForMessages(messageList.map { it.id })
                    
                    if (otherParticipant == null) {
                        val currentUserId = SupabaseClient.currentUserId
                        val otherSender = messageList.firstOrNull { it.senderId != currentUserId }?.sender
                        if (otherSender != null) {
                            otherParticipant = otherSender
                        }
                    }
                    
                    isLoading = false
                }
            }
            
            // 2. Trigger background sync from REST API to populate DB initially
            launch {
                SupabaseRepository.syncMessages(conversationId, context)
            }
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
    
    fun onMediaSelected(uri: Uri, type: String) {
        selectedMediaUri = uri
        selectedMediaType = type
    }
    
    fun clearSelectedMedia() {
        selectedMediaUri = null
        selectedMediaType = null
        uploadProgress = null
    }
    
    fun sendMessage(content: String, context: android.content.Context? = null) {
        val conversationId = currentConversationId ?: return
        // Allow sending if there's text OR media
        if (content.isBlank() && selectedMediaUri == null) return
        
        viewModelScope.launch {
            isSending = true
            
            // Check if editing
            if (editingMessage != null) {
                context?.let { editMessage(editingMessage!!.id, content, it) }
                editingMessage = null
                return@launch
            }
            
            var mediaUrl: String? = null
            var messageType = "text"
            
            // Upload media if selected
            selectedMediaUri?.let { uri ->
                if (context == null) {
                    isSending = false
                    return@launch
                }
                isUploading = true
                uploadProgress = "Uploading..."
                
                val uploadResult = when (selectedMediaType) {
                    "image" -> {
                        // Try to compress first
                        val compressed = MediaUploadManager.compressImage(context, uri)
                        val uploadUri = compressed.getOrNull() ?: uri
                        MediaUploadManager.uploadImage(context, uploadUri, httpClient)
                    }
                    "video" -> MediaUploadManager.uploadVideo(context, uri, httpClient)
                    else -> MediaUploadManager.uploadDocument(context, uri, httpClient)
                }
                
                uploadResult.onSuccess { result ->
                    mediaUrl = result.url
                    messageType = selectedMediaType ?: "document"
                }.onFailure { e ->
                    errorMessage = "Upload failed: ${e.message}"
                    isSending = false
                    isUploading = false
                    uploadProgress = null
                    return@launch
                }
                
                isUploading = false
                uploadProgress = null
            }
            
            // Auto-generate caption for media-only messages
            val msgContent = content.ifBlank {
                when (messageType) {
                    "image" -> "\uD83D\uDCF7 Photo"
                    "video" -> "\uD83C\uDFA5 Video"
                    "document" -> "\uD83D\uDCC4 Document"
                    else -> content
                }
            }
            
            val result = SupabaseRepository.sendMessage(
                conversationId, msgContent,
                mediaUrl = mediaUrl,
                messageType = messageType
            )
            result.onSuccess { message ->
                context?.let { ctx ->
                    val db = com.loopchat.app.data.local.LoopChatDatabase.getDatabase(ctx)
                    db.messageDao().insertMessage(message.toEntity())
                }
                
                // Clear reply if replying
                replyToMessage = null
            }.onFailure { e ->
                errorMessage = e.message
            }
            
            clearSelectedMedia()
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
    
    private suspend fun editMessage(messageId: String, newContent: String, context: android.content.Context) {
        val result = MessagingFeaturesRepository.editMessage(httpClient, messageId, newContent)
        result.onSuccess {
            // Reload messages to show edited version
            currentConversationId?.let { loadMessages(it, context) }
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
    
    fun deleteMessageForEveryone(messageId: String, context: android.content.Context) {
        viewModelScope.launch {
            val result = MessagingFeaturesRepository.deleteMessageForEveryone(httpClient, messageId)
            result.onSuccess {
                // Reload messages
                currentConversationId?.let { loadMessages(it, context) }
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
        // Refresh is now handled by the background sync in loadMessages or Room observation
    }
    
    override fun onCleared() {
        super.onCleared()
        com.loopchat.app.data.realtime.SupabaseRealtimeClient.disconnect()
        httpClient.close()
    }
}
