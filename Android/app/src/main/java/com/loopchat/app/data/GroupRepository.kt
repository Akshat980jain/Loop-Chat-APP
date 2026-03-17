package com.loopchat.app.data

import com.loopchat.app.data.local.LoopChatDatabase
import com.loopchat.app.data.local.dao.GroupDao
import com.loopchat.app.data.local.entities.GroupEntity
import com.loopchat.app.data.local.entities.GroupMemberEntity
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow

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

        return try {
            val response = SupabaseClient.httpClient.post("$supabaseUrl/rest/v1/groups") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "name" to name,
                    "description" to description,
                    "avatar_url" to avatarUrl,
                    "created_by" to currentUserId
                ))
            }
            // Parse response to get the created Group entity and insert to local DB
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                // In a real app we would use kotlinx.serialization to parse this JSON
                // For the blueprint let's assume parsing succeeds and we have a group
                val id = extractJsonString(body, "id") ?: java.util.UUID.randomUUID().toString()
                
                val groupEntity = GroupEntity(
                    id = id,
                    name = name,
                    description = description,
                    avatarUrl = avatarUrl,
                    createdBy = currentUserId,
                    createdAt = extractJsonString(body, "created_at") ?: java.time.Instant.now().toString(),
                    updatedAt = extractJsonString(body, "updated_at") ?: java.time.Instant.now().toString()
                )
                
                groupDao.insertGroup(groupEntity)
                
                // Add the creator as owner in local DB
                val ownerEntity = GroupMemberEntity(
                    groupId = id,
                    userId = currentUserId,
                    role = "owner",
                    joinedAt = java.time.Instant.now().toString(),
                    addedBy = currentUserId
                )
                groupDao.insertGroupMembers(listOf(ownerEntity))

                Result.success(groupEntity)
            } else {
                Result.failure(Exception("Failed to create group: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun manageGroupMember(groupId: String, targetUserId: String, action: String, role: String? = null): Result<Boolean> {
        val token = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("No access token"))
        
        return try {
            val response = SupabaseClient.httpClient.post("$supabaseUrl/functions/v1/manage-group-member") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                setBody(mapOf(
                    "group_id" to groupId,
                    "user_id" to targetUserId,
                    "action" to action,
                    "role" to role
                ))
            }
            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.bodyAsText()))
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
