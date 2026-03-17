package com.loopchat.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.loopchat.app.data.models.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val updatedAt: String?,
    val lastMessage: String?,
    val unreadCount: Int = 0
)

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    name = name,
    isGroup = isGroup,
    updatedAt = updatedAt,
    lastMessage = lastMessage
)
