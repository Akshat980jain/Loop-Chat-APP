package com.loopchat.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val createdBy: String?,
    val createdAt: String,
    val updatedAt: String
)
