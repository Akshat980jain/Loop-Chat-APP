package com.loopchat.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.loopchat.app.data.models.Profile

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val isOnline: Boolean,
    val lastSeen: String?
)

fun Profile.toEntity() = UserEntity(
    id = id,
    username = username,
    fullName = fullName,
    avatarUrl = avatarUrl,
    isOnline = isOnline,
    lastSeen = lastSeen
)
