package com.loopchat.app.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Repository for Phase 3: Interactive Chat Features
 * 
 * Handles Backend APIs for:
 * - Polls (creating, fetching, voting)
 * - Vanish Mode (enabling disappear configuration)
 */
object InteractiveChatRepository {

    private val supabaseUrl = com.loopchat.app.BuildConfig.SUPABASE_URL
    private val supabaseKey = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY

    // ============================================
    // POLLS
    // ============================================

    /**
     * Create a new poll message
     */
    suspend fun createPoll(
        httpClient: HttpClient,
        conversationId: String,
        question: String,
        options: List<String>,
        multipleAnswers: Boolean = false
    ): Result<Poll> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val senderId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))

            // 1. Create message first
            val messageResponse = httpClient.post("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(buildJsonObject {
                    put("conversation_id", conversationId)
                    put("sender_id", senderId)
                    put("content", "📊 Poll: $question")
                    put("message_type", "poll")
                })
            }

            if (!messageResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to create message for poll"))
            }

            val messages: List<com.loopchat.app.data.models.Message> = messageResponse.body()
            val messageId = messages.first().id

            // 2. Create the poll
            val pollResponse = httpClient.post("$supabaseUrl/rest/v1/polls") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(buildJsonObject {
                    put("message_id", messageId)
                    put("question", question)
                    put("multiple_answers", multipleAnswers)
                })
            }

            if (!pollResponse.status.isSuccess()) {
                // Should potentially rollback message here
                return Result.failure(Exception("Failed to create poll entry"))
            }

            val polls: List<Poll> = pollResponse.body()
            val savedPoll = polls.first()

            // 3. Create the options
            val optionsPayload = buildJsonArray {
                options.forEachIndexed { index, text ->
                    add(buildJsonObject {
                        put("poll_id", savedPoll.id)
                        put("option_text", text)
                        put("order_index", index)
                    })
                }
            }

            val optionsResponse = httpClient.post("$supabaseUrl/rest/v1/poll_options") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(optionsPayload)
            }

            if (!optionsResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to create poll options"))
            }

            val savedOptions: List<PollOption> = optionsResponse.body()

            Result.success(savedPoll.copy(options = savedOptions))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit or toggle a vote
     */
    suspend fun voteOnPollOption(
        httpClient: HttpClient,
        optionId: String,
        pollId: String,
        multipleAnswers: Boolean
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))

            // Check if user already voted for this
            val checkResponse = httpClient.get("$supabaseUrl/rest/v1/poll_votes") {
                parameter("option_id", "eq.$optionId")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            val existingVotes: List<PollVote> = if (checkResponse.status.isSuccess()) checkResponse.body() else emptyList()

            if (existingVotes.isNotEmpty()) {
                // Toggle off
                httpClient.delete("$supabaseUrl/rest/v1/poll_votes") {
                    parameter("id", "eq.${existingVotes.first().id}")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                }
            } else {
                // If not multiple choice, remove other votes first
                if (!multipleAnswers) {
                    // First get all options for this poll
                    val optionsResponse = httpClient.get("$supabaseUrl/rest/v1/poll_options") {
                        parameter("select", "id")
                        parameter("poll_id", "eq.$pollId")
                        header("apikey", supabaseKey)
                        header("Authorization", "Bearer $accessToken")
                    }
                    if (optionsResponse.status.isSuccess()) {
                        val ids: List<Map<String, String>> = optionsResponse.body()
                        val optionIdsString = ids.map { it["id"] }.joinToString(",")
                        
                        // Delete previous votes
                        httpClient.delete("$supabaseUrl/rest/v1/poll_votes") {
                            parameter("option_id", "in.($optionIdsString)")
                            parameter("user_id", "eq.$userId")
                            header("apikey", supabaseKey)
                            header("Authorization", "Bearer $accessToken")
                        }
                    }
                }

                // Vote!
                httpClient.post("$supabaseUrl/rest/v1/poll_votes") {
                    contentType(ContentType.Application.Json)
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                    setBody(buildJsonObject {
                        put("option_id", optionId)
                        put("user_id", userId)
                    })
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Use this to fetch fully nested poll structures
     */
    suspend fun getPollsForMessages(
        httpClient: HttpClient,
        messageIds: List<String>
    ): Result<List<Poll>> {
        if (messageIds.isEmpty()) return Result.success(emptyList())

        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))

            val idsParam = messageIds.joinToString(",")
            val response = httpClient.get("$supabaseUrl/rest/v1/polls") {
                // Fetch poll and nested options and votes
                parameter("select", "*, poll_options(*, poll_votes(*))")
                parameter("message_id", "in.($idsParam)")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                val polls: List<Poll> = response.body()
                Result.success(polls)
            } else {
                Result.failure(Exception("Failed to fetch polls"))
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
data class Poll(
    val id: String,
    val message_id: String,
    val question: String,
    val multiple_answers: Boolean,
    val created_at: String,
    // When using Supabase nested selects `poll_options(*, poll_votes(*))`
    val poll_options: List<PollOption> = emptyList(),
    // Fallback manual hydration mapping
    val options: List<PollOption>? = null
)

@Serializable
data class PollOption(
    val id: String,
    val poll_id: String,
    val option_text: String,
    val order_index: Int,
    val created_at: String,
    // Nesting
    val poll_votes: List<PollVote> = emptyList(),
    // Fallback
    val votes: List<PollVote>? = null
)

@Serializable
data class PollVote(
    val id: String,
    val option_id: String,
    val user_id: String,
    val created_at: String
)
