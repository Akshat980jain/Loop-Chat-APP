package com.loopchat.app.data

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for Phase 2: Privacy, Security & Organization Features
 * 
 * This file contains all backend functions for:
 * - Privacy settings
 * - Blocked users
 * - Archived conversations
 * - Pinned conversations
 * - Muted conversations
 * - Disappearing messages
 * - Security settings
 * - Device management
 * - Message search
 */
object PrivacySecurityRepository {
    
    private val supabaseUrl = com.loopchat.app.BuildConfig.SUPABASE_URL
    private val supabaseKey = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY
    
    // ============================================
    // PRIVACY SETTINGS
    // ============================================
    
    /**
     * Get user's privacy settings
     */
    suspend fun getPrivacySettings(
        httpClient: HttpClient
    ): Result<PrivacySettings> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/privacy_settings") {
                parameter("select", "*")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (response.status.isSuccess()) {
                val settings: PrivacySettings = response.body()
                Result.success(settings)
            } else {
                // Create default settings if not found
                createDefaultPrivacySettings(httpClient)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update privacy settings
     */
    suspend fun updatePrivacySettings(
        httpClient: HttpClient,
        settings: PrivacySettings
    ): Result<PrivacySettings> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/privacy_settings") {
                contentType(ContentType.Application.Json)
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(settings)
            }
            
            if (response.status.isSuccess()) {
                val updated: List<PrivacySettings> = response.body()
                Result.success(updated.first())
            } else {
                Result.failure(Exception("Failed to update privacy settings"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun createDefaultPrivacySettings(httpClient: HttpClient): Result<PrivacySettings> {
        val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
        val userId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))
        
        val response = httpClient.post("$supabaseUrl/rest/v1/privacy_settings") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $accessToken")
            header("Prefer", "return=representation")
            setBody(buildJsonObject { put("user_id", userId) })
        }
        
        if (response.status.isSuccess()) {
            val settings: List<PrivacySettings> = response.body()
            return Result.success(settings.first())
        }
        return Result.failure(Exception("Failed to create privacy settings"))
    }
    
    // ============================================
    // BLOCKED USERS
    // ============================================
    
    /**
     * Block a user
     */
    suspend fun blockUser(
        httpClient: HttpClient,
        blockedUserId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/blocked_users") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject {
                    put("blocker_id", userId)
                    put("blocked_id", blockedUserId)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to block user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Unblock a user
     */
    suspend fun unblockUser(
        httpClient: HttpClient,
        blockedUserId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.delete("$supabaseUrl/rest/v1/blocked_users") {
                parameter("blocker_id", "eq.$userId")
                parameter("blocked_id", "eq.$blockedUserId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unblock user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get list of blocked users
     */
    suspend fun getBlockedUsers(
        httpClient: HttpClient
    ): Result<List<String>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/blocked_users") {
                parameter("select", "blocked_id")
                parameter("blocker_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val blocked: List<BlockedUser> = response.body()
                Result.success(blocked.map { it.blocked_id })
            } else {
                Result.failure(Exception("Failed to fetch blocked users"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // ARCHIVED CONVERSATIONS
    // ============================================
    
    /**
     * Archive a conversation
     */
    suspend fun archiveConversation(
        httpClient: HttpClient,
        conversationId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/archived_conversations") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("conversation_id", conversationId)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to archive conversation"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Unarchive a conversation
     */
    suspend fun unarchiveConversation(
        httpClient: HttpClient,
        conversationId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.delete("$supabaseUrl/rest/v1/archived_conversations") {
                parameter("user_id", "eq.$userId")
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unarchive conversation"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get archived conversations
     */
    suspend fun getArchivedConversations(
        httpClient: HttpClient
    ): Result<List<String>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/archived_conversations") {
                parameter("select", "conversation_id")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val archived: List<ArchivedConversation> = response.body()
                Result.success(archived.map { it.conversation_id })
            } else {
                Result.failure(Exception("Failed to fetch archived conversations"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // PINNED CONVERSATIONS
    // ============================================
    
    /**
     * Pin a conversation
     */
    suspend fun pinConversation(
        httpClient: HttpClient,
        conversationId: String,
        pinOrder: Int = 0
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/pinned_conversations") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("conversation_id", conversationId)
                    put("pin_order", pinOrder)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to pin conversation"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Unpin a conversation
     */
    suspend fun unpinConversation(
        httpClient: HttpClient,
        conversationId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.delete("$supabaseUrl/rest/v1/pinned_conversations") {
                parameter("user_id", "eq.$userId")
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unpin conversation"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get pinned conversations
     */
    suspend fun getPinnedConversations(
        httpClient: HttpClient
    ): Result<List<String>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/pinned_conversations") {
                parameter("select", "conversation_id")
                parameter("user_id", "eq.$userId")
                parameter("order", "pin_order.asc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val pinned: List<PinnedConversation> = response.body()
                Result.success(pinned.map { it.conversation_id })
            } else {
                Result.failure(Exception("Failed to fetch pinned conversations"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MUTED CONVERSATIONS
    // ============================================
    
    /**
     * Mute a conversation
     */
    suspend fun muteConversation(
        httpClient: HttpClient,
        conversationId: String,
        mutedUntil: String? = null // ISO timestamp or null for forever
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/muted_conversations") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("conversation_id", conversationId)
                    put("muted_until", mutedUntil)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mute conversation"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Unmute a conversation
     */
    suspend fun unmuteConversation(
        httpClient: HttpClient,
        conversationId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.delete("$supabaseUrl/rest/v1/muted_conversations") {
                parameter("user_id", "eq.$userId")
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unmute conversation"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get muted conversations
     */
    suspend fun getMutedConversations(
        httpClient: HttpClient
    ): Result<List<String>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/muted_conversations") {
                parameter("select", "conversation_id")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val muted: List<MutedConversation> = response.body()
                Result.success(muted.map { it.conversation_id })
            } else {
                Result.failure(Exception("Failed to fetch muted conversations"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MESSAGE SEARCH
    // ============================================
    
    /**
     * Search messages
     */
    suspend fun searchMessages(
        httpClient: HttpClient,
        query: String,
        conversationId: String? = null
    ): Result<List<MessageWithSender>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/rpc/search_messages") {
                parameter("search_query", query)
                if (conversationId != null) {
                    parameter("conversation_filter", conversationId)
                }
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val messages: List<MessageWithSender> = response.body()
                Result.success(messages)
            } else {
                Result.failure(Exception("Failed to search messages"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // DISAPPEARING MESSAGES
    // ============================================
    
    /**
     * Enable disappearing messages for a conversation
     */
    suspend fun enableDisappearingMessages(
        httpClient: HttpClient,
        conversationId: String,
        durationSeconds: Int // 86400 = 24h, 604800 = 7d, 7776000 = 90d
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/disappearing_message_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(buildJsonObject {
                    put("conversation_id", conversationId)
                    put("enabled", true)
                    put("duration_seconds", durationSeconds)
                    put("enabled_by", userId)
                    put("enabled_at", "now()")
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to enable disappearing messages"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disable disappearing messages for a conversation
     */
    suspend fun disableDisappearingMessages(
        httpClient: HttpClient,
        conversationId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/disappearing_message_settings") {
                contentType(ContentType.Application.Json)
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject {
                    put("enabled", false)
                    put("duration_seconds", null as Int?)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to disable disappearing messages"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get disappearing message settings for a conversation
     */
    suspend fun getDisappearingMessageSettings(
        httpClient: HttpClient,
        conversationId: String
    ): Result<DisappearingMessageSettings?> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/disappearing_message_settings") {
                parameter("select", "*")
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (response.status.isSuccess()) {
                val settings: DisappearingMessageSettings = response.body()
                Result.success(settings)
            } else if (response.status.value == 406) {
                // Not found
                Result.success(null)
            } else {
                Result.failure(Exception("Failed to fetch disappearing message settings"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // SECURITY SETTINGS
    // ============================================
    
    /**
     * Get security settings
     */
    suspend fun getSecuritySettings(
        httpClient: HttpClient
    ): Result<SecuritySettings> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/security_settings") {
                parameter("select", "*")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (response.status.isSuccess()) {
                val settings: SecuritySettings = response.body()
                Result.success(settings)
            } else {
                // Create default settings
                createDefaultSecuritySettings(httpClient)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Enable two-step verification
     */
    suspend fun enableTwoStepVerification(
        httpClient: HttpClient,
        pinHash: String,
        email: String?
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/security_settings") {
                contentType(ContentType.Application.Json)
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject {
                    put("two_step_enabled", true)
                    put("two_step_pin_hash", pinHash)
                    put("two_step_email", email)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to enable two-step verification"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disable two-step verification
     */
    suspend fun disableTwoStepVerification(
        httpClient: HttpClient
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/security_settings") {
                contentType(ContentType.Application.Json)
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject {
                    put("two_step_enabled", false)
                    put("two_step_pin_hash", null as String?)
                    put("two_step_email", null as String?)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to disable two-step verification"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Enable biometric lock
     */
    suspend fun enableBiometricLock(
        httpClient: HttpClient
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            // Use UPSERT (POST with Prefer header) instead of PATCH to ensure row existence
            val response = httpClient.post("$supabaseUrl/rest/v1/security_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("biometric_lock_enabled", true)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val error = response.bodyAsText()
                Log.e("SecurityRepo", "Enable biometric lock failed: $error")
                Result.failure(Exception("Failed to enable biometric lock"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disable biometric lock
     */
    suspend fun disableBiometricLock(
        httpClient: HttpClient
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/security_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("biometric_lock_enabled", false)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to disable biometric lock"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Enable biometric login (distinct from biometric app lock).
     * This allows the user to sign in with fingerprint/face instead of password.
     */
    suspend fun enableBiometricLogin(
        httpClient: HttpClient
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/security_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("biometric_login_enabled", true)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to enable biometric login"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disable biometric login
     */
    suspend fun disableBiometricLogin(
        httpClient: HttpClient
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/security_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("biometric_login_enabled", false)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to disable biometric login"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update security notifications enabled state
     */
    suspend fun updateSecurityNotifications(
        httpClient: HttpClient,
        enabled: Boolean
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/security_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("security_notifications_enabled", enabled)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update security notifications"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun createDefaultSecuritySettings(httpClient: HttpClient): Result<SecuritySettings> {
        val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
        val userId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))
        
        val response = httpClient.post("$supabaseUrl/rest/v1/security_settings") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $accessToken")
            header("Prefer", "return=representation")
            setBody(buildJsonObject { put("user_id", userId) })
        }
        
        if (response.status.isSuccess()) {
            val settings: List<SecuritySettings> = response.body()
            return Result.success(settings.first())
        }
        return Result.failure(Exception("Failed to create security settings"))
    }
    
    // ============================================
    // DEVICE MANAGEMENT
    // ============================================
    
    /**
     * Register a device
     */
    suspend fun registerDevice(
        httpClient: HttpClient,
        deviceName: String,
        deviceType: String,
        deviceToken: String?
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.post("$supabaseUrl/rest/v1/user_devices") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(buildJsonObject {
                    put("user_id", userId)
                    put("device_name", deviceName)
                    put("device_type", deviceType)
                    put("device_token", deviceToken)
                })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to register device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all user devices
     */
    suspend fun getUserDevices(
        httpClient: HttpClient
    ): Result<List<UserDevice>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/user_devices") {
                parameter("select", "*")
                parameter("user_id", "eq.$userId")
                parameter("order", "last_active.desc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val devices: List<UserDevice> = response.body()
                Result.success(devices)
            } else {
                Result.failure(Exception("Failed to fetch devices"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove a device
     */
    suspend fun removeDevice(
        httpClient: HttpClient,
        deviceId: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.delete("$supabaseUrl/rest/v1/user_devices") {
                parameter("id", "eq.$deviceId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update device last active timestamp
     */
    suspend fun updateDeviceActivity(
        httpClient: HttpClient,
        deviceToken: String
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/user_devices") {
                contentType(ContentType.Application.Json)
                parameter("device_token", "eq.$deviceToken")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildJsonObject { put("last_active", "now()") })
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update device activity"))
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
data class PrivacySettings(
    val id: String? = null,
    val user_id: String? = null,
    val last_seen_visibility: String = "everyone",
    val profile_photo_visibility: String = "everyone",
    val about_visibility: String = "everyone",
    val status_visibility: String = "everyone",
    val status_excluded_users: List<String> = emptyList(),
    val read_receipts_enabled: Boolean = true,
    val show_online_status: Boolean = true,
    val who_can_add_to_groups: String = "everyone",
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class BlockedUser(
    val id: String,
    val blocker_id: String,
    val blocked_id: String,
    val blocked_at: String
)

@Serializable
data class ArchivedConversation(
    val id: String,
    val user_id: String,
    val conversation_id: String,
    val archived_at: String
)

@Serializable
data class PinnedConversation(
    val id: String,
    val user_id: String,
    val conversation_id: String,
    val pin_order: Int,
    val pinned_at: String
)

@Serializable
data class MutedConversation(
    val id: String,
    val user_id: String,
    val conversation_id: String,
    val muted_until: String?,
    val muted_at: String
)

@Serializable
data class DisappearingMessageSettings(
    val id: String,
    val conversation_id: String,
    val enabled: Boolean,
    val duration_seconds: Int?,
    val enabled_by: String?,
    val enabled_at: String?
)

@Serializable
data class SecuritySettings(
    val id: String? = null,
    val user_id: String? = null,
    val two_step_enabled: Boolean = false,
    val two_step_pin_hash: String? = null,
    val two_step_email: String? = null,
    val biometric_lock_enabled: Boolean = false,
    val biometric_login_enabled: Boolean = false,
    val security_notifications_enabled: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class UserDevice(
    val id: String,
    val user_id: String,
    val device_name: String,
    val device_type: String,
    val device_token: String?,
    val last_active: String,
    val created_at: String
)
