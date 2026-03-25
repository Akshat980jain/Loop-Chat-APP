package com.loopchat.app.data

import android.util.Log
import com.loopchat.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScheduledMessage(
    val id: String? = null,
    @SerialName("sender_id") val senderId: String,
    @SerialName("conversation_id") val conversationId: String,
    val content: String,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * Repository for Message Scheduling.
 * Allows users to queue messages for future delivery via a pg_cron job.
 */
object ScheduledMessagesRepository {

    private const val TAG = "ScheduledMessagesRepo"
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) { level = LogLevel.NONE }
    }

    /**
     * Schedule a message for future delivery.
     */
    suspend fun scheduleMessage(
        conversationId: String,
        content: String,
        scheduledAt: String, // ISO 8601 timestamp
        messageType: String = "text",
        mediaUrl: String? = null
    ): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId
                ?: return Result.failure(Exception("No user ID"))

            val payload = ScheduledMessage(
                senderId = userId,
                conversationId = conversationId,
                content = content,
                messageType = messageType,
                mediaUrl = mediaUrl,
                scheduledAt = scheduledAt
            )

            val response = httpClient.post("$supabaseUrl/rest/v1/scheduled_messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(payload)
            }

            if (response.status.isSuccess()) {
                Log.d(TAG, "Message scheduled for $scheduledAt")
                Result.success(Unit)
            } else {
                val err = response.bodyAsText()
                Log.e(TAG, "Schedule failed: $err")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "scheduleMessage error", e)
            Result.failure(e)
        }
    }

    /**
     * Get all pending scheduled messages for the current user.
     */
    suspend fun getPendingMessages(): Result<List<ScheduledMessage>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId
                ?: return Result.failure(Exception("No user ID"))

            val response = httpClient.get("$supabaseUrl/rest/v1/scheduled_messages") {
                parameter("sender_id", "eq.$userId")
                parameter("status", "eq.pending")
                parameter("order", "scheduled_at.asc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                val messages: List<ScheduledMessage> = response.body()
                Result.success(messages)
            } else {
                Result.failure(Exception(response.bodyAsText()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancel a scheduled message (set status to 'cancelled').
     */
    suspend fun cancelScheduledMessage(messageId: String): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = httpClient.patch("$supabaseUrl/rest/v1/scheduled_messages") {
                contentType(ContentType.Application.Json)
                parameter("id", "eq.$messageId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf("status" to "cancelled"))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.bodyAsText()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
