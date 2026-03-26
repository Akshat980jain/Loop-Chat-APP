package com.loopchat.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("user_id")
    val userId: String? = null,
    val username: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    @SerialName("last_seen")
    val lastSeen: String? = null,
    @SerialName("is_online")
    val isOnline: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("last_message")
    val lastMessage: String? = null,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    val name: String? = null,
    @SerialName("is_group")
    val isGroup: Boolean = false,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class Message(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("sender_id")
    val senderId: String,
    val content: String,
    @SerialName("message_type")
    val messageType: String = "text",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("media_url")
    val mediaUrl: String? = null,
    // New fields for enhanced messaging
    @SerialName("edited_at")
    val editedAt: String? = null,
    @SerialName("reply_to_message_id")
    val replyToMessageId: String? = null,
    val forwarded: Boolean? = null,
    @SerialName("deleted_for_everyone")
    val deletedForEveryone: Boolean? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    @SerialName("media_duration")
    val mediaDuration: Long? = null,
    @SerialName("waveform_data")
    val waveformData: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null
)

@Serializable
data class ConversationParticipant(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("joined_at")
    val joinedAt: String? = null
)

@Serializable
data class Call(
    val id: String,
    @SerialName("caller_id")
    val callerId: String,
    @SerialName("callee_id")
    val calleeId: String? = null,
    @SerialName("group_id")
    val groupId: String? = null,
    @SerialName("call_type")
    val callType: String,
    val status: String,
    @SerialName("room_url")
    val roomUrl: String? = null,
    @SerialName("caller_token")
    val callerToken: String? = null,
    @SerialName("callee_token")
    val calleeToken: String? = null,
    @SerialName("room_name")
    val roomName: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("started_at")
    val startedAt: String? = null,
    @SerialName("ended_at")
    val endedAt: String? = null
)

@Serializable
data class Story(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("media_url")
    val mediaUrl: String,
    @SerialName("media_type")
    val mediaType: String = "image",
    val caption: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null
)

