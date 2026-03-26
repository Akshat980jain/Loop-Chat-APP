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
import io.ktor.client.call.body
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.*
import io.ktor.client.request.*
import io.ktor.http.*
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
        
    var polls by mutableStateOf<Map<String, com.loopchat.app.data.Poll>>(emptyMap())
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

    var isVanishModeEnabled by mutableStateOf(false)
        private set

    var isChatDisabled by mutableStateOf(false)
        private set
        
    fun toggleVanishMode() {
        isVanishModeEnabled = !isVanishModeEnabled
    }
    
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
            if (messages.isEmpty()) {
                isLoading = true
            }
            errorMessage = null
            
            // Initialize and connect Realtime WebSockets
            com.loopchat.app.data.realtime.SupabaseRealtimeClient.initialize(context)
            com.loopchat.app.data.realtime.SupabaseRealtimeClient.connectAndSubscribe(conversationId)
            
            launch {
                com.loopchat.app.data.realtime.SupabaseRealtimeClient.typingUsers.collect { users ->
                    typingUsers = users
                }
            }
            
            // Load other participant, starred messages, and moderation status
            loadOtherParticipant(conversationId)
            loadStarredMessages()
            loadModerationStatus(conversationId)
            
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
                        
                        // Decrypt payload if it was sent by someone else
                        val currentUserId = SupabaseClient.currentUserId
                        val displayContent = if (entity.senderId != currentUserId && !entity.content.startsWith("\uD83D\uDCCA Poll:")) {
                            com.loopchat.app.data.crypto.CryptoManager.decryptMessage(entity.content) ?: entity.content
                        } else {
                            entity.content
                        }
                        
                        MessageWithSender(
                            id = entity.id,
                            content = displayContent,
                            conversationId = entity.conversationId,
                            senderId = entity.senderId,
                            createdAt = entity.createdAt,
                            sender = profile,
                            mediaUrl = entity.mediaUrl,
                            messageType = entity.messageType,
                            isRead = entity.isRead,
                            status = entity.status
                        )
                    }
                    
                    messages = messageList
                    loadReactionsForMessages(messageList.map { it.id })
                    
                    val pollMessageIds = messageList.filter { it.messageType == "poll" }.map { it.id }
                    if (pollMessageIds.isNotEmpty()) {
                        loadPollsForMessages(pollMessageIds)
                    }
                    
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
                    fullName = conv.groups?.name ?: "Unnamed Group",
                    avatarUrl = conv.groups?.avatar_url,
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
            
            val currentUserId = SupabaseClient.currentUserId ?: run {
                isSending = false
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
            val displayContent = content.ifBlank {
                when (messageType) {
                    "image" -> "\uD83D\uDCF7 Photo"
                    "video" -> "\uD83C\uDFA5 Video"
                    "document" -> "\uD83D\uDCC4 Document"
                    else -> content
                }
            }
            
            // Optimistic UI: show the message immediately in the chat
            val tempId = "temp_${System.currentTimeMillis()}"
            val optimisticMessage = MessageWithSender(
                id = tempId,
                content = displayContent,
                conversationId = conversationId,
                senderId = currentUserId,
                createdAt = java.time.Instant.now().toString(),
                sender = otherParticipant?.let { null } ?: null, // sender is "me", no profile needed for own messages
                mediaUrl = mediaUrl,
                messageType = messageType,
                isRead = false,
                status = "pending"
            )
            messages = messages + optimisticMessage
            
            // Now encrypt for sending (keep displayContent visible locally)
            var msgContent = displayContent
            
            // E2EE Implementation
            // For 1-on-1 chats, we encrypt the message content. Group chats are unencrypted in this simplified model.
            if (currentConversation?.is_group == false && otherParticipant != null) {
                try {
                    val keyResult = SupabaseRepository.getPublicKey(otherParticipant!!.id)
                    keyResult.getOrNull()?.let { pubKeyBase64 ->
                        val recipientKey = com.loopchat.app.data.crypto.CryptoManager.parsePublicKey(pubKeyBase64)
                        if (recipientKey != null) {
                            val cipherText = com.loopchat.app.data.crypto.CryptoManager.encryptMessage(msgContent, recipientKey)
                            if (cipherText != null) {
                                msgContent = cipherText
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EnhancedChatViewModel", "E2EE encryption failed, sending plaintext", e)
                }
            }
            
            // Vanish Mode expiration timestamp (24 hours from now)
            val expiresAtOffset = if (isVanishModeEnabled) {
                java.time.Instant.now().plusSeconds(24 * 60 * 60).toString()
            } else null
            
            val result = SupabaseRepository.sendMessage(
                conversationId, msgContent,
                mediaUrl = mediaUrl,
                messageType = messageType,
                expiresAt = expiresAtOffset
            )
            result.onSuccess { message ->
                // Remove the optimistic message (Room observer will add the real one)
                messages = messages.filterNot { it.id == tempId }
                
                context?.let { ctx ->
                    val db = com.loopchat.app.data.local.LoopChatDatabase.getDatabase(ctx)
                    db.messageDao().insertMessage(message.toEntity())
                }
                
                // Clear reply if replying
                replyToMessage = null
                
                // Send push notification to all participants (non-blocking)
                viewModelScope.launch {
                    try {
                        val accessToken = SupabaseClient.getAccessToken() ?: return@launch
                        // Get sender's display name
                        val myProfile = SupabaseRepository.getProfileById(currentUserId).getOrNull()
                        val senderName = myProfile?.fullName ?: myProfile?.username ?: SupabaseClient.currentEmail ?: "Someone"
                        
                        // receiverId is optional now, the backend uses conversationId to notify all participants
                        val receiverId = otherParticipant?.id ?: ""
                        
                        httpClient.post("${com.loopchat.app.BuildConfig.SUPABASE_URL}/functions/v1/send-message-notification") {
                            contentType(ContentType.Application.Json)
                            header("apikey", com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY)
                            header("Authorization", "Bearer $accessToken")
                            setBody(mapOf(
                                "senderId" to currentUserId,
                                "receiverId" to receiverId,
                                "senderName" to senderName,
                                "messageContent" to displayContent,
                                "messageType" to messageType,
                                "conversationId" to conversationId
                            ))
                        }
                    } catch (e: Exception) {
                        // Non-critical — don't break the message flow
                        android.util.Log.w("EnhancedChatViewModel", "Push notification failed: ${e.message}")
                    }
                }
            }.onFailure { e ->
                // Remove optimistic message on failure
                messages = messages.filterNot { it.id == tempId }
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
    
    var forwardTargets by mutableStateOf<List<com.loopchat.app.ui.components.ForwardTarget>>(emptyList())
        private set
    
    fun loadConversationsForForward() {
        viewModelScope.launch {
            try {
                val accessToken = SupabaseClient.getAccessToken() ?: return@launch
                val userId = SupabaseClient.currentUserId ?: return@launch
                val supabaseUrl = com.loopchat.app.BuildConfig.SUPABASE_URL
                val supabaseKey = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY
                
                // Get conversation IDs for current user
                val participantResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                    parameter("select", "conversation_id")
                    parameter("user_id", "eq.$userId")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                }
                
                if (participantResponse.status.isSuccess()) {
                    val participants: List<Map<String, String>> = participantResponse.body()
                    val convIds = participants.mapNotNull { it["conversation_id"] }
                    
                    if (convIds.isNotEmpty()) {
                        val idsParam = convIds.joinToString(",")
                        val convsResponse = httpClient.get("$supabaseUrl/rest/v1/conversations") {
                            parameter("select", "id,name,is_group,avatar_url")
                            parameter("id", "in.($idsParam)")
                            header("apikey", supabaseKey)
                            header("Authorization", "Bearer $accessToken")
                        }
                        
                        if (convsResponse.status.isSuccess()) {
                            val convs: List<com.loopchat.app.data.models.Conversation> = convsResponse.body()
                            forwardTargets = convs
                                .filter { it.id != currentConversationId }
                                .map { conv ->
                                    com.loopchat.app.ui.components.ForwardTarget(
                                        conversationId = conv.id,
                                        name = conv.name ?: "Chat",
                                        avatarUrl = conv.avatarUrl,
                                        isGroup = conv.isGroup
                                    )
                                }
                        }
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load conversations: ${e.message}"
            }
        }
    }
    
    fun forwardMessage(messageId: String, targetConversationIds: List<String>) {
        viewModelScope.launch {
            val result = MessagingFeaturesRepository.forwardMessage(httpClient, messageId, targetConversationIds)
            result.onSuccess {
                errorMessage = null
            }
            result.onFailure {
                errorMessage = "Failed to forward: ${it.message}"
            }
        }
    }
    
    // ============================================
    // VOICE MESSAGES
    // ============================================

    fun sendVoiceMessage(file: java.io.File, durationMs: Long, amplitudes: List<Int>) {
        val cid = currentConversationId ?: return
        if (isSending) return
        
        isSending = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                // 1. Upload to Storage
                val fileName = "\${java.util.UUID.randomUUID()}.m4a"
                val bucket = "voice_messages"
                val accessToken = com.loopchat.app.data.SupabaseClient.getAccessToken() 
                    ?: throw Exception("Not authenticated")
                
                // Read bytes
                val fileBytes = file.readBytes()
                
                // Standard Ktor upload for Supabase Storage
                val uploadResponse = httpClient.post("\${com.loopchat.app.BuildConfig.SUPABASE_URL}/storage/v1/object/$bucket/$fileName") {
                    header("Authorization", "Bearer $accessToken")
                    contentType(io.ktor.http.ContentType.Audio.MP4)
                    setBody(fileBytes)
                }
                
                if (!uploadResponse.status.isSuccess()) {
                    throw Exception("Failed to upload voice recording: \${uploadResponse.status}")
                }
                
                // Get public URL
                val publicUrl = "\${com.loopchat.app.BuildConfig.SUPABASE_URL}/storage/v1/object/public/$bucket/$fileName"
                
                // 2. Prepare Message Payload
                val amplitudesJson = Json.encodeToString(amplitudes)
                
                val currentUserId = com.loopchat.app.data.SupabaseClient.currentUserId ?: throw Exception("No user ID")
                
                val requestPayload = mapOf(
                    "conversation_id" to cid,
                    "sender_id" to currentUserId,
                    "content" to "Voice Message", // Fallback text
                    "message_type" to "voice",
                    "media_url" to publicUrl,
                    "media_duration" to durationMs,
                    "waveform_data" to kotlinx.serialization.json.Json.parseToJsonElement(amplitudesJson)
                )
                
                val response = httpClient.post("\${com.loopchat.app.BuildConfig.SUPABASE_URL}/rest/v1/messages") {
                    header("Authorization", "Bearer $accessToken")
                    header("apikey", com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY)
                    header("Prefer", "return=representation")
                    contentType(io.ktor.http.ContentType.Application.Json)
                    setBody(requestPayload)
                }
                
                if (response.status.isSuccess()) {
                    // Success, cleanup local file
                    file.delete()
                    // Realtime will catch the new message and update the UI
                } else {
                    errorMessage = "Failed to send voice message: \${response.status}"
                }
                
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to process voice message"
                e.printStackTrace()
            } finally {
                isSending = false
            }
        }
    }

    // ============================================
    // POLLS
    // ============================================

    fun sendPoll(question: String, options: List<String>, isMultipleChoice: Boolean, isAnonymous: Boolean) {
        val cid = currentConversationId ?: return
        if (isSending) return
        
        isSending = true
        errorMessage = null
        
        viewModelScope.launch {
            val result = com.loopchat.app.data.InteractiveChatRepository.createPoll(
                httpClient,
                cid,
                question,
                options,
                isMultipleChoice
            )
            result.onFailure { e ->
                errorMessage = "Failed to create poll: ${e.message}"
            }
            isSending = false
        }
    }

    fun voteOnPoll(pollId: String, optionId: String, isMultipleChoice: Boolean) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.InteractiveChatRepository.voteOnPollOption(
                httpClient,
                optionId,
                pollId,
                isMultipleChoice
            )
            result.onSuccess {
                // Reload polls to update vote counts
                val poll = polls.values.find { it.id == pollId }
                poll?.let { p ->
                    loadPollsForMessages(listOf(p.message_id))
                }
            }
            result.onFailure { e ->
                errorMessage = "Failed to submit vote: ${e.message}"
            }
        }
    }
    
    private fun loadPollsForMessages(messageIds: List<String>) {
        viewModelScope.launch {
            val result = com.loopchat.app.data.InteractiveChatRepository.getPollsForMessages(
                httpClient,
                messageIds
            )
            result.onSuccess { loadedPolls ->
                val newMap = polls.toMutableMap()
                loadedPolls.forEach { poll ->
                    newMap[poll.message_id] = poll
                }
                polls = newMap
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
        if (currentConversationId == null) return
        
        // Broadcast typing via WebSocket instead of REST DB insertion
        com.loopchat.app.data.realtime.SupabaseRealtimeClient.sendTypingEvent(isTyping)
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

    private suspend fun loadModerationStatus(conversationId: String) {
        val token = SupabaseClient.getAccessToken() ?: return
        try {
            val convResponse = httpClient.get("${SupabaseClient.supabaseUrl}/rest/v1/conversations") {
                parameter("id", "eq.$conversationId")
                parameter("select", "group_id,is_group")
                header("apikey", SupabaseClient.supabaseKey)
                header("Authorization", "Bearer $token")
            }
            if (!convResponse.status.isSuccess()) return
            val convs: kotlinx.serialization.json.JsonArray = convResponse.body()
            val conv = convs.firstOrNull() as? kotlinx.serialization.json.JsonObject ?: return
            
            val isGroup = conv["is_group"]?.toString()?.toBoolean() ?: false
            if (isGroup) {
                val groupIdRaw = conv["group_id"]?.toString()
                if (groupIdRaw == null || groupIdRaw == "null") return
                val groupId = groupIdRaw.replace("\"", "")
                
                // Check group suspension
                val grpResp = httpClient.get("${SupabaseClient.supabaseUrl}/rest/v1/groups") {
                    parameter("id", "eq.$groupId")
                    parameter("select", "is_suspended")
                    header("apikey", SupabaseClient.supabaseKey)
                    header("Authorization", "Bearer $token")
                }
                var groupSuspended = false
                if (grpResp.status.isSuccess()) {
                    val ge: kotlinx.serialization.json.JsonArray = grpResp.body()
                    val gf = ge.firstOrNull() as? kotlinx.serialization.json.JsonObject
                    groupSuspended = gf?.get("is_suspended")?.toString()?.toBoolean() ?: false
                }
                
                // Check user suspension
                val profResp = httpClient.get("${SupabaseClient.supabaseUrl}/rest/v1/profiles") {
                    parameter("user_id", "eq.${SupabaseClient.currentUserId}")
                    parameter("select", "id")
                    header("apikey", SupabaseClient.supabaseKey)
                    header("Authorization", "Bearer $token")
                }
                var userSuspended = false
                if (profResp.status.isSuccess()) {
                    val pe: kotlinx.serialization.json.JsonArray = profResp.body()
                    val pf = pe.firstOrNull() as? kotlinx.serialization.json.JsonObject
                    val pId = pf?.get("id")?.toString()?.replace("\"", "")
                    if (pId != null && pId != "null") {
                        val memResp = httpClient.get("${SupabaseClient.supabaseUrl}/rest/v1/group_members") {
                            parameter("group_id", "eq.$groupId")
                            parameter("user_id", "eq.$pId")
                            parameter("select", "role")
                            header("apikey", SupabaseClient.supabaseKey)
                            header("Authorization", "Bearer $token")
                        }
                        if (memResp.status.isSuccess()) {
                            val me: kotlinx.serialization.json.JsonArray = memResp.body()
                            val mf = me.firstOrNull() as? kotlinx.serialization.json.JsonObject
                            val role = mf?.get("role")?.toString()?.replace("\"", "")
                            userSuspended = (role == "suspended")
                        }
                    }
                }
                
                isChatDisabled = groupSuspended || userSuspended
            }
        } catch (e: Exception) {
            // Silently catch so we don't break message loading entirely
        }
    }
}
