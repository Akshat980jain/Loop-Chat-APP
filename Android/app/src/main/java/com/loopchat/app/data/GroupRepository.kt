package com.loopchat.app.data

import com.loopchat.app.data.local.LoopChatDatabase
import com.loopchat.app.data.local.dao.GroupDao
import com.loopchat.app.data.local.entities.GroupEntity
import com.loopchat.app.data.local.entities.GroupMemberEntity
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import io.ktor.http.isSuccess
import io.ktor.client.call.body
import com.loopchat.app.data.models.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

class GroupRepository(
    private val database: LoopChatDatabase
) {
    private val groupDao: GroupDao = database.groupDao()
    private val supabaseUrl = com.loopchat.app.BuildConfig.SUPABASE_URL
    private val supabaseKey = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY

    fun observeGroups(): Flow<List<GroupEntity>> = groupDao.observeGroups()

    suspend fun createGroup(name: String, description: String?, avatarUrl: String?): Result<GroupEntity> {
        val currentUserId = SupabaseClient.currentUserId ?: return Result.failure(Exception("Not authenticated"))
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))

        val groupId = java.util.UUID.randomUUID().toString()
        
        return try {
            val response = SupabaseClient.httpClient.post("$supabaseUrl/rest/v1/groups") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                // Do NOT use return=representation — it requires SELECT which RLS may block
                header("Prefer", "return=minimal")
                setBody(mapOf(
                    "id" to groupId,
                    "name" to name,
                    "description" to description,
                    "avatar_url" to avatarUrl,
                    "created_by" to currentUserId
                ))
            }
            if (response.status.isSuccess()) {
                val now = java.time.Instant.now().toString()
                val groupEntity = GroupEntity(
                    id = groupId,
                    name = name,
                    description = description,
                    avatarUrl = avatarUrl,
                    createdBy = currentUserId,
                    createdAt = now,
                    updatedAt = now
                )
                
                groupDao.insertGroup(groupEntity)
                
                // Add the creator as owner in local DB
                val ownerEntity = GroupMemberEntity(
                    groupId = groupId,
                    userId = currentUserId,
                    role = "owner",
                    joinedAt = now,
                    addedBy = currentUserId
                )
                groupDao.insertGroupMembers(listOf(ownerEntity))

                Result.success(groupEntity)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to create group: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun manageGroupMember(groupId: String, targetUserId: String, action: String, role: String? = null): Result<Boolean> {
        val currentUserId = SupabaseClient.currentUserId ?: return Result.failure(Exception("Not authenticated"))
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))
        
        return try {
            if (action == "add" || action == "remove" || action == "update_role") {
                // Call the PostgreSQL RPC instead of Edge Function
                val response = SupabaseClient.httpClient.post("$supabaseUrl/rest/v1/rpc/manage_group_member") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $token")
                    setBody(mapOf(
                        "p_group_id" to groupId,
                        "p_user_id" to targetUserId,
                        "p_role" to (role ?: "member"),
                        "p_action" to action
                    ))
                }
                
                if (response.status.isSuccess()) {
                    if (action == "add") {
                        val now = java.time.Instant.now().toString()
                        val memberEntity = GroupMemberEntity(
                            groupId = groupId,
                            userId = targetUserId,
                            role = role ?: "member",
                            joinedAt = now,
                            addedBy = currentUserId
                        )
                        groupDao.insertGroupMembers(listOf(memberEntity))
                    } else if (action == "remove") {
                        groupDao.deleteGroupMember(groupId, targetUserId)
                    } else if (action == "update_role") {
                        val existing = groupDao.getGroupMember(groupId, targetUserId)
                        if (existing != null) {
                            groupDao.insertGroupMembers(listOf(existing.copy(role = role ?: "member")))
                        }
                    }
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to $action member: ${response.status} - ${response.bodyAsText()}"))
                }
            } else {
                Result.failure(Exception("Unknown action: $action"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(groupId: String): Result<Boolean> {
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))
        
        return try {
            val response = SupabaseClient.httpClient.post("$supabaseUrl/rest/v1/rpc/delete_group") {
                contentType(io.ktor.http.ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                setBody(mapOf("p_group_id" to groupId))
            }
            
            if (response.status.isSuccess()) {
                // Delete from local DB immediately
                groupDao.deleteGroup(groupId)
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete group: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun suspendGroup(groupId: String, suspend: Boolean): Result<Boolean> {
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))
        
        return try {
            val response = SupabaseClient.httpClient.post("$supabaseUrl/rest/v1/rpc/toggle_group_suspension") {
                contentType(io.ktor.http.ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                setBody(mapOf("p_group_id" to groupId, "p_is_suspended" to suspend))
            }
            
            if (response.status.isSuccess()) {
                // Update local DB
                groupDao.updateGroupSuspension(groupId, suspend)
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to toggle group suspension: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConversationIdForGroup(groupId: String): Result<String> {
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))
        
        return try {
            val response = SupabaseClient.httpClient.get("$supabaseUrl/rest/v1/conversations") {
                parameter("select", "id")
                parameter("group_id", "eq.$groupId")
                parameter("is_group", "eq.true")
                parameter("limit", "1")
                contentType(io.ktor.http.ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
            }
            
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                // Parse the JSON array to get the conversation ID
                val idRegex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val match = idRegex.find(body)
                if (match != null) {
                    Result.success(match.groupValues[1])
                } else {
                    Result.failure(Exception("No conversation found for group"))
                }
            } else {
                Result.failure(Exception("Failed to find conversation: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupDetails(groupId: String): Result<GroupEntity> {
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))
        
        return try {
            val response = SupabaseClient.httpClient.get("$supabaseUrl/rest/v1/groups") {
                parameter("select", "*")
                parameter("id", "eq.$groupId")
                parameter("limit", "1")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
            }
            
            if (response.status.isSuccess()) {
                val groupList: List<GroupEntity> = response.body()
                if (groupList.isNotEmpty()) {
                    Result.success(groupList[0])
                } else {
                    Result.failure(Exception("Group not found"))
                }
            } else {
                Result.failure(Exception("Failed to fetch group: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    data class GroupMemberWithProfile(
        @SerialName("group_id")
        val groupId: String,
        @SerialName("user_id")
        val userId: String,
        val role: String,
        val profiles: Profile? = null
    )

    data class GroupMemberInfo(
        val profile: Profile,
        val role: String
    )

    suspend fun getGroupMembers(groupId: String): Result<List<GroupMemberInfo>> {
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))
        
        return try {
            // Fetch group members and join with profiles table
            val response = SupabaseClient.httpClient.get("$supabaseUrl/rest/v1/group_members") {
                // PostgREST requires explicit fkey name because there are multiple foreign keys to profiles.
                // Using an alias "profiles:profiles!" to ensure the key in JSON is "profiles"
                parameter("select", "*,profiles:profiles!group_members_user_id_fkey(*)")
                parameter("group_id", "eq.$groupId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
            }
            
            if (response.status.isSuccess()) {
                val members: List<GroupMemberWithProfile> = response.body()
                val infos = members.mapNotNull { if (it.profiles != null) GroupMemberInfo(it.profiles, it.role) else null }
                Result.success(infos)
            } else {
                Result.failure(Exception("Failed to fetch members: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        // Simple regex fallback parser for demonstration without heavy imports
        val regex = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }
}
