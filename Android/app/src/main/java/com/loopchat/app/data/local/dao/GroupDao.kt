package com.loopchat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.loopchat.app.data.local.entities.GroupEntity
import com.loopchat.app.data.local.entities.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY updatedAt DESC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observeGroupById(groupId: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)
    
    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    // Group Members
    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberEntity>>
    
    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND userId = :userId LIMIT 1")
    suspend fun getGroupMember(groupId: String, userId: String): GroupMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun deleteGroupMember(groupId: String, userId: String)
    
    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllMembersForGroup(groupId: String)

    @Transaction
    suspend fun updateGroupWithMembers(group: GroupEntity, members: List<GroupMemberEntity>) {
        insertGroup(group)
        // Optionally clear old members first if doing full sync
        // deleteAllMembersForGroup(group.id)
        insertGroupMembers(members)
    }
}
