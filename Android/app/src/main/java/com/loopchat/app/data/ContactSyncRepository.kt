package com.loopchat.app.data

import android.content.Context
import android.util.Log
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
data class MatchedContact(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val username: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val phone: String? = null
)

/**
 * Handles Contact Sync: matching device phone numbers against registered Supabase users.
 */
object ContactSyncRepository {

    private const val TAG = "ContactSyncRepository"
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) { level = LogLevel.NONE }
    }

    /**
     * Reads the device contact book and matches phone numbers against the `profiles` table.
     * Returns a list of [MatchedContact] for users who are registered.
     */
    suspend fun syncContacts(context: Context): Result<List<MatchedContact>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            // 1. Read device contacts
            val deviceContacts = ContactsManager.getDeviceContacts(context)
            if (deviceContacts.isEmpty()) {
                return Result.success(emptyList())
            }

            // 2. Build a comma-separated list for Supabase "in" filter
            //    PostgREST uses: phone=in.("num1","num2",...)
            val phoneNumbers = deviceContacts.map { it.phoneNumber }
            val inFilter = phoneNumbers.joinToString(",") { "\"$it\"" }

            // 3. Query profiles where phone matches
            val response = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("phone", "in.($inFilter)")
                parameter("select", "id,user_id,username,full_name,avatar_url,phone")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                val matched: List<MatchedContact> = response.body()
                Log.d(TAG, "Synced contacts: ${matched.size} matches found from ${phoneNumbers.size} device contacts")
                Result.success(matched)
            } else {
                val err = response.bodyAsText()
                Log.e(TAG, "Contact sync failed: $err")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncContacts error", e)
            Result.failure(e)
        }
    }
}
