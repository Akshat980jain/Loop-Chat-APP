package com.loopchat.app.data

import android.content.Context
import android.net.Uri
import com.loopchat.app.data.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Repository extension for Phase 1: Core Messaging & Media Features
 * 
 * This file contains all backend functions for:
 * - Message reactions
 * - Message edits
 * - Starred messages
 * - Message deliveries & status
 * - Media messages (images, videos, documents)
 * - Voice messages
 * - Location sharing
 * - Typing indicators
 */
object MessagingFeaturesRepository {
    
    private val supabaseUrl = com.loopchat.app.BuildConfig.SUPABASE_URL
    private val supabaseKey = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY
    
    // ============================================
    // MESSAGE REACTIONS
    // ============================================
    
    /**
     * Add a reaction to a message
     */
    suspend fun addReaction(
        httpClient: HttpClient,
        messageId: String,
        reaction: String
    ): Result<MessageReaction> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/message_reactions") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "message_id" to messageId,
                    "user_id" to userId,
                    "reaction" to reaction
                ))
            }
            
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Failed to add reaction"))
            }
            
            val reactions: List<MessageReaction> = response.body()
            Result.success(reactions.first())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove a reaction from a message
     */
    suspend fun removeReaction(
        httpClient: HttpClient,
        messageId: String,
        reaction: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.delete("$supabaseUrl/rest/v1/message_reactions") {
                parameter("message_id", "eq.$messageId")
                parameter("user_id", "eq.$userId")
                parameter("reaction", "eq.$reaction")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove reaction"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all reactions for a message
     */
    suspend fun getMessageReactions(
        httpClient: HttpClient,
        messageId: String
    ): Result<List<MessageReaction>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/message_reactions") {
                parameter("select", "*")
                parameter("message_id", "eq.$messageId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val reactions: List<MessageReaction> = response.body()
                Result.success(reactions)
            } else {
                Result.failure(Exception("Failed to fetch reactions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MESSAGE EDITS
    // ============================================
    
    /**
     * Edit a message (within 5-minute window)
     */
    suspend fun editMessage(
        httpClient: HttpClient,
        messageId: String,
        newContent: String
    ): Result<Message> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            // First, get the current message to save edit history
            val getMessage = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "id,content,created_at")
                parameter("id", "eq.$messageId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (!getMessage.status.isSuccess()) {
                return Result.failure(Exception("Message not found"))
            }
            
            val currentMessage: Message = getMessage.body()
            
            // Save edit history
            httpClient.post("$supabaseUrl/rest/v1/message_edits") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf(
                    "message_id" to messageId,
                    "previous_content" to currentMessage.content,
                    "edited_by" to userId
                ))
            }
            
            // Update the message
            val response = httpClient.patch("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                parameter("id", "eq.$messageId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "content" to newContent,
                    "edited_at" to "now()"
                ))
            }
            
            if (response.status.isSuccess()) {
                val messages: List<Message> = response.body()
                Result.success(messages.first())
            } else {
                Result.failure(Exception("Failed to edit message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get edit history for a message
     */
    suspend fun getMessageEditHistory(
        httpClient: HttpClient,
        messageId: String
    ): Result<List<MessageEdit>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/message_edits") {
                parameter("select", "*")
                parameter("message_id", "eq.$messageId")
                parameter("order", "edited_at.desc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val edits: List<MessageEdit> = response.body()
                Result.success(edits)
            } else {
                Result.failure(Exception("Failed to fetch edit history"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // STARRED MESSAGES
    // ============================================
    
    /**
     * Star a message
     */
    suspend fun starMessage(
        httpClient: HttpClient,
        messageId: String
    ): Result<StarredMessage> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/starred_messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "message_id" to messageId,
                    "user_id" to userId
                ))
            }
            
            if (response.status.isSuccess()) {
                val starred: List<StarredMessage> = response.body()
                Result.success(starred.first())
            } else {
                Result.failure(Exception("Failed to star message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Unstar a message
     */
    suspend fun unstarMessage(
        httpClient: HttpClient,
        messageId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.delete("$supabaseUrl/rest/v1/starred_messages") {
                parameter("message_id", "eq.$messageId")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unstar message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all starred messages for current user
     */
    suspend fun getStarredMessages(
        httpClient: HttpClient
    ): Result<List<MessageWithSender>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            // Get starred message IDs
            val starredResponse = httpClient.get("$supabaseUrl/rest/v1/starred_messages") {
                parameter("select", "message_id")
                parameter("user_id", "eq.$userId")
                parameter("order", "starred_at.desc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (!starredResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch starred messages"))
            }
            
            val starred: List<StarredMessage> = starredResponse.body()
            if (starred.isEmpty()) {
                return Result.success(emptyList())
            }
            
            // Fetch full message details
            val messageIds = starred.map { it.message_id }.joinToString(",")
            val messagesResponse = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "id,content,conversation_id,sender_id,created_at")
                parameter("id", "in.($messageIds)")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (messagesResponse.status.isSuccess()) {
                val messages: List<Message> = messagesResponse.body()
                // TODO: Fetch sender profiles and convert to MessageWithSender
                Result.success(emptyList()) // Placeholder
            } else {
                Result.failure(Exception("Failed to fetch message details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MESSAGE DELIVERIES & STATUS
    // ============================================
    
    /**
     * Mark message as delivered
     */
    suspend fun markMessageDelivered(
        httpClient: HttpClient,
        messageId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/message_deliveries") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf(
                    "message_id" to messageId,
                    "user_id" to userId,
                    "delivered_at" to "now()"
                ))
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark as delivered"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Mark message as read
     */
    suspend fun markMessageRead(
        httpClient: HttpClient,
        messageId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/message_deliveries") {
                contentType(ContentType.Application.Json)
                parameter("message_id", "eq.$messageId")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf(
                    "read_at" to "now()"
                ))
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark as read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get delivery status for a message
     */
    suspend fun getMessageDeliveryStatus(
        httpClient: HttpClient,
        messageId: String
    ): Result<List<MessageDelivery>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/message_deliveries") {
                parameter("select", "*")
                parameter("message_id", "eq.$messageId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val deliveries: List<MessageDelivery> = response.body()
                Result.success(deliveries)
            } else {
                Result.failure(Exception("Failed to fetch delivery status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MEDIA MESSAGES
    // ============================================
    
    /**
     * Send media message (image, video, document)
     */
    suspend fun sendMediaMessage(
        httpClient: HttpClient,
        context: Context,
        conversationId: String,
        mediaUri: Uri,
        mediaType: String, // 'image', 'video', 'document'
        caption: String? = null,
        viewOnce: Boolean = false
    ): Result<MediaMessage> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val senderId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            // Upload media to Supabase Storage
            val uploadResult = when (mediaType) {
                "image" -> {
                    // Compress image first
                    val compressedUri = MediaUploadManager.compressImage(context, mediaUri)
                        .getOrNull() ?: mediaUri
                    MediaUploadManager.uploadImage(context, compressedUri, httpClient)
                }
                "video" -> MediaUploadManager.uploadVideo(context, mediaUri, httpClient)
                "document" -> MediaUploadManager.uploadDocument(context, mediaUri, httpClient)
                else -> return Result.failure(Exception("Unknown media type"))
            }.getOrElse { return Result.failure(it) }
            
            // Generate thumbnail for images/videos
            val thumbnailUrl = if (mediaType == "image" || mediaType == "video") {
                val thumbUri = if (mediaType == "video") {
                    MediaUploadManager.generateVideoThumbnail(context, mediaUri).getOrNull()
                } else {
                    mediaUri
                }
                
                thumbUri?.let {
                    MediaUploadManager.uploadImage(context, it, httpClient)
                        .getOrNull()?.url
                }
            } else null
            
            // Create message entry
            val messageResponse = httpClient.post("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId,
                    "content" to (caption ?: "📎 ${mediaType.capitalize()}"),
                    "message_type" to mediaType
                ))
            }
            
            if (!messageResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to create message"))
            }
            
            val messages: List<Message> = messageResponse.body()
            val messageId = messages.first().id
            
            // Create media message entry
            val mediaResponse = httpClient.post("$supabaseUrl/rest/v1/media_messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "message_id" to messageId,
                    "media_type" to mediaType,
                    "media_url" to uploadResult.url,
                    "thumbnail_url" to thumbnailUrl,
                    "file_name" to uploadResult.fileName,
                    "file_size" to uploadResult.fileSize,
                    "mime_type" to uploadResult.mimeType,
                    "caption" to caption,
                    "width" to uploadResult.width,
                    "height" to uploadResult.height,
                    "view_once" to viewOnce,
                    "viewed_by" to emptyList<String>()
                ))
            }
            
            if (mediaResponse.status.isSuccess()) {
                val mediaMessages: List<MediaMessage> = mediaResponse.body()
                Result.success(mediaMessages.first())
            } else {
                Result.failure(Exception("Failed to create media message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // VOICE MESSAGES
    // ============================================
    
    /**
     * Send voice message
     */
    suspend fun sendVoiceMessage(
        httpClient: HttpClient,
        context: Context,
        conversationId: String,
        audioUri: Uri,
        duration: Int,
        waveform: List<Float>? = null,
        viewOnce: Boolean = false
    ): Result<VoiceMessage> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val senderId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            // Upload audio to Supabase Storage
            val uploadResult = MediaUploadManager.uploadVoiceMessage(context, audioUri, httpClient)
                .getOrElse { return Result.failure(it) }
            
            // Create message entry
            val messageResponse = httpClient.post("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId,
                    "content" to "🎤 Voice message",
                    "message_type" to "voice"
                ))
            }
            
            if (!messageResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to create message"))
            }
            
            val messages: List<Message> = messageResponse.body()
            val messageId = messages.first().id
            
            // Create voice message entry
            val waveformJson = waveform?.joinToString(",") { it.toString() }
            
            val voiceResponse = httpClient.post("$supabaseUrl/rest/v1/voice_messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "message_id" to messageId,
                    "audio_url" to uploadResult.url,
                    "duration" to duration,
                    "waveform" to waveformJson,
                    "view_once" to viewOnce
                ))
            }
            
            if (voiceResponse.status.isSuccess()) {
                val voiceMessages: List<VoiceMessage> = voiceResponse.body()
                Result.success(voiceMessages.first())
            } else {
                Result.failure(Exception("Failed to create voice message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // LOCATION SHARING
    // ============================================
    
    /**
     * Share location
     */
    suspend fun shareLocation(
        httpClient: HttpClient,
        conversationId: String,
        latitude: Double,
        longitude: Double,
        address: String? = null,
        isLive: Boolean = false,
        liveUntil: String? = null
    ): Result<LocationMessage> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val senderId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            // Create message first
            val messageResponse = httpClient.post("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId,
                    "content" to "📍 Location"
                ))
            }
            
            if (!messageResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to create message"))
            }
            
            val messages: List<Message> = messageResponse.body()
            val messageId = messages.first().id
            
            // Create location entry
            val locationResponse = httpClient.post("$supabaseUrl/rest/v1/location_messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "message_id" to messageId,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "address" to address,
                    "is_live" to isLive,
                    "live_until" to liveUntil
                ))
            }
            
            if (locationResponse.status.isSuccess()) {
                val locations: List<LocationMessage> = locationResponse.body()
                Result.success(locations.first())
            } else {
                Result.failure(Exception("Failed to share location"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // TYPING INDICATORS
    // ============================================
    
    /**
     * Update typing status
     */
    suspend fun updateTypingStatus(
        httpClient: HttpClient,
        conversationId: String,
        isTyping: Boolean
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/typing_indicators") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(mapOf(
                    "conversation_id" to conversationId,
                    "user_id" to userId,
                    "is_typing" to isTyping,
                    "updated_at" to "now()"
                ))
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update typing status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MESSAGE OPERATIONS
    // ============================================
    
    /**
     * Forward message
     */
    suspend fun forwardMessage(
        httpClient: HttpClient,
        messageId: String,
        targetConversationIds: List<String>
    ): Result<List<Message>> {
        if (targetConversationIds.isEmpty()) return Result.success(emptyList())

        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val senderId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            // Get original message full content
            val getMessage = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "content,media_url,message_type")
                parameter("id", "eq.$messageId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (!getMessage.status.isSuccess()) {
                return Result.failure(Exception("Message not found or unauthorized"))
            }
            
            val originalMessage: Map<String, String?> = getMessage.body()
            val content = originalMessage["content"]
            val mediaUrl = originalMessage["media_url"]
            val messageType = originalMessage["message_type"] ?: "text"
            
            // Create bulk insert payload
            val payloads = targetConversationIds.map { conversationId ->
                mapOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId,
                    "content" to (content ?: ""),
                    "media_url" to mediaUrl,
                    "message_type" to messageType,
                    "forwarded" to true
                )
            }
            
            val response = httpClient.post("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(payloads)
            }
            
            if (response.status.isSuccess()) {
                val forwardedMessages: List<Message> = response.body()
                Result.success(forwardedMessages)
            } else {
                Result.failure(Exception("Failed to forward messages: \${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fetch media gallery items (photos/videos, docs, links)
     */
    suspend fun getMediaGallery(
        httpClient: HttpClient,
        conversationId: String,
        type: String // "media", "docs", "links"
    ): Result<List<Message>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "id,content,media_url,message_type,created_at,sender_id")
                parameter("conversation_id", "eq.$conversationId")
                
                when (type) {
                    "media" -> parameter("message_type", "in.(image,video)")
                    "docs" -> parameter("message_type", "eq.document")
                    "links" -> {
                        parameter("message_type", "eq.text")
                        parameter("content", "ilike.*http*")
                    }
                }
                parameter("deleted_for_everyone", "eq.false") // dont fetch deleted ones
                parameter("order", "created_at.desc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val messages: List<Message> = response.body()
                Result.success(messages)
            } else {
                Result.failure(Exception("Failed to fetch gallery media"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete message for everyone
     */
    suspend fun deleteMessageForEveryone(
        httpClient: HttpClient,
        messageId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                parameter("id", "eq.$messageId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf(
                    "deleted_for_everyone" to true,
                    "deleted_at" to "now()",
                    "content" to "This message was deleted"
                ))
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete message for me (local user only)
     */
    suspend fun deleteMessageForMe(
        httpClient: HttpClient,
        messageId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId
                ?: return Result.failure(Exception("No user ID"))
            
            val requestBody = DeletedMessageRequest(
                user_id = userId,
                message_id = messageId
            )
            
            val response = httpClient.post("$supabaseUrl/rest/v1/deleted_messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=minimal")
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                // Check if already deleted (conflict)
                val status = response.status.value
                if (status == 409) {
                    return Result.success(Unit)
                }
                Result.failure(Exception("Failed to delete message for me"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ============================================
// DATA MODELS
// ============================================

@Serializable
data class MessageReaction(
    val id: String,
    val message_id: String,
    val user_id: String,
    val reaction: String,
    val created_at: String
)

@Serializable
data class MessageEdit(
    val id: String,
    val message_id: String,
    val previous_content: String,
    val edited_at: String,
    val edited_by: String
)

@Serializable
data class StarredMessage(
    val id: String,
    val message_id: String,
    val user_id: String,
    val starred_at: String
)

@Serializable
data class MessageDelivery(
    val id: String,
    val message_id: String,
    val user_id: String,
    val delivered_at: String?,
    val read_at: String?
)

@Serializable
data class MediaMessage(
    val id: String,
    val message_id: String,
    val media_type: String,
    val media_url: String,
    val thumbnail_url: String?,
    val file_name: String?,
    val file_size: Long?,
    val mime_type: String?,
    val duration: Int?,
    val caption: String?,
    val width: Int?,
    val height: Int?,
    val view_once: Boolean,
    val viewed_by: List<String>,
    val created_at: String
)

@Serializable
data class VoiceMessage(
    val id: String,
    val message_id: String,
    val audio_url: String,
    val duration: Int,
    val waveform: String?, // JSON string
    val view_once: Boolean,
    val created_at: String
)

@Serializable
data class LocationMessage(
    val id: String,
    val message_id: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val is_live: Boolean,
    val live_until: String?,
    val created_at: String
)

@Serializable
data class DeletedMessageRequest(
    val user_id: String,
    val message_id: String
)
