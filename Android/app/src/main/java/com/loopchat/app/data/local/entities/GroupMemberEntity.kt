package com.loopchat.app.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "userId"]
)
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val role: String, // 'owner', 'admin', 'member'
    val joinedAt: String,
    val addedBy: String?
)
