package com.loopchat.app.data

import com.loopchat.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
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
import kotlinx.serialization.json.JsonObject

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val data: JsonObject? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String
)

class NotificationsRepository {

    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) { level = LogLevel.NONE }
    }

    suspend fun fetchNotifications(): Result<List<AppNotification>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("No user ID"))

            val response = httpClient.get("$supabaseUrl/rest/v1/notifications") {
                parameter("user_id", "eq.$userId")
                parameter("order", "created_at.desc")
                parameter("limit", "50")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
                // Fallback to array mode since we expect a list
            }

            // Using standard GET request for PostgREST list returns array directly if we don't specify Accept=object
            val listResponse = httpClient.get("$supabaseUrl/rest/v1/notifications") {
                parameter("user_id", "eq.$userId")
                parameter("order", "created_at.desc")
                parameter("limit", "50")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (listResponse.status.isSuccess()) {
                val notifications: List<AppNotification> = listResponse.body()
                Result.success(notifications)
            } else {
                Result.failure(Exception("Failed to load notifications: \${listResponse.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Auth error"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/notifications") {
                contentType(ContentType.Application.Json)
                parameter("id", "eq.$notificationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf("is_read" to true))
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllAsRead(): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Auth error"))
            val userId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))
            
            val response = httpClient.patch("$supabaseUrl/rest/v1/notifications") {
                contentType(ContentType.Application.Json)
                parameter("user_id", "eq.$userId")
                parameter("is_read", "eq.false")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf("is_read" to true))
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark all read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
