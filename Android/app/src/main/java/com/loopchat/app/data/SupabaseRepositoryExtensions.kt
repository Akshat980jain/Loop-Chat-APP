package com.loopchat.app.data

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.loopchat.app.data.models.*

object SupabaseRepositoryExtensions {
    
    private val supabaseUrl = com.loopchat.app.BuildConfig.SUPABASE_URL
    private val supabaseKey = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY
    
    /**
     * Get conversation participants with their profiles
     */
    suspend fun getConversationParticipants(
        httpClient: HttpClient,
        conversationId: String
    ): Result<List<Profile>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            // Get participant user IDs
            val participantsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "user_id")
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!participantsResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch participants"))
            }

            val participants: List<UserIdOnly> = participantsResponse.body()
            val userIds = participants.map { it.user_id }

            // Fetch profiles for these users
            val profiles = mutableListOf<Profile>()
            for (userId in userIds) {
                val profileResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                    parameter("select", "id,user_id,full_name,username,avatar_url,phone,status")
                    parameter("user_id", "eq.$userId")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.pgrst.object+json")
                }

                if (profileResponse.status.isSuccess()) {
                    try {
                        val profile: Profile = profileResponse.body()
                        profiles.add(profile)
                    } catch (e: Exception) {
                        Log.e("SupabaseRepo", "Error parsing profile: ${e.message}")
                    }
                }
            }

            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
