package com.loopchat.app.data

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
import com.loopchat.app.data.crypto.CryptoManager

@Serializable
data class UserPublicKey(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("public_key") val publicKey: String,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * Repository for E2EE Key Exchange.
 * Uploads the local device's RSA public key to the server, and fetches
 * the recipient's public key for encrypting messages.
 */
object KeyExchangeRepository {

    private const val TAG = "KeyExchangeRepository"
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) { level = LogLevel.NONE }
    }

    /**
     * Uploads the current device's RSA public key to Supabase.
     * Uses UPSERT so re-registrations just update the existing row.
     */
    suspend fun uploadPublicKey(deviceId: String): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId
                ?: return Result.failure(Exception("No user ID"))
            val publicKeyBase64 = CryptoManager.getMyPublicKeyBase64()
                ?: return Result.failure(Exception("Public key not available"))

            val payload = UserPublicKey(
                userId = userId,
                deviceId = deviceId,
                publicKey = publicKeyBase64
            )

            val response = httpClient.post("$supabaseUrl/rest/v1/public_keys") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates") // UPSERT
                setBody(payload)
            }

            if (response.status.isSuccess()) {
                Log.d(TAG, "Public key uploaded for device $deviceId")
                Result.success(Unit)
            } else {
                val err = response.bodyAsText()
                Log.e(TAG, "Upload public key failed: $err")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadPublicKey error", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches the public key(s) for a given user.
     * Returns all device keys so the sender can encrypt for every device.
     */
    suspend fun getPublicKeys(targetUserId: String): Result<List<UserPublicKey>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = httpClient.get("$supabaseUrl/rest/v1/public_keys") {
                parameter("user_id", "eq.$targetUserId")
                parameter("select", "id,user_id,device_id,public_key,created_at")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                val keys: List<UserPublicKey> = response.body()
                Result.success(keys)
            } else {
                Result.failure(Exception(response.bodyAsText()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
