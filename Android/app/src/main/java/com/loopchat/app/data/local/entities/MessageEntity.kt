package com.loopchat.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.loopchat.app.data.models.Message

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("createdAt")
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val messageType: String,
    val mediaUrl: String?,
    val createdAt: String?,
    val isRead: Boolean,
    val status: String = "synced" // "pending", "synced", "failed"
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    messageType = messageType,
    mediaUrl = mediaUrl,
    createdAt = createdAt,
    isRead = isRead,
    status = "synced"
)
