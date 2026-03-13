package com.loopchat.app.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.loopchat.app.BuildConfig
import java.security.MessageDigest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// DataStore extension
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "supabase_session")

/**
 * Supabase authentication client for Android
 * Uses Ktor HTTP client for REST API calls to Supabase Auth
 */
object SupabaseClient {
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            connectTimeout = 30_000
            socketTimeout = 30_000
        }
    }
    
    // DataStore keys
    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    private val USER_PHONE_KEY = stringPreferencesKey("user_phone")
    
    // Cached session state
    var isAuthenticated = false
        private set
    var currentUserId: String? = null
        private set
    var currentEmail: String? = null
        private set
    var currentPhone: String? = null
        private set
    private var accessToken: String? = null
    
    /**
     * Initialize the client and restore session from DataStore
     * Also validates and refreshes the token if needed
     */
    suspend fun initialize(context: Context) {
        val prefs = context.dataStore.data.first()
        accessToken = prefs[ACCESS_TOKEN_KEY]
        val refreshToken = prefs[REFRESH_TOKEN_KEY]
        currentUserId = prefs[USER_ID_KEY]
        currentEmail = prefs[USER_EMAIL_KEY]
        currentPhone = prefs[USER_PHONE_KEY]
        
        // If we have a token, validate it and refresh if needed
        if (accessToken != null) {
            val isValid = validateToken()
            if (!isValid && refreshToken != null) {
                // Try to refresh the token
                val refreshed = refreshSession(context, refreshToken)
                isAuthenticated = refreshed
            } else {
                isAuthenticated = isValid
            }
            
            // Check if session was revoked from another device
            if (isAuthenticated) {
                val revoked = checkSessionRevoked()
                if (revoked) {
                    android.util.Log.w("SupabaseClient", "Session was revoked from another device")
                    signOut(context)
                    isAuthenticated = false
                }
            }
        } else {
            isAuthenticated = false
        }
    }
    
    /**
     * Validate current access token by calling /auth/v1/user
     */
    private suspend fun validateToken(): Boolean {
        return try {
            val response = httpClient.get("$supabaseUrl/auth/v1/user") {
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Refresh the session using refresh token
     */
    private suspend fun refreshSession(context: Context, refreshToken: String): Boolean {
        return try {
            val response = httpClient.post("$supabaseUrl/auth/v1/token?grant_type=refresh_token") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                setBody(mapOf("refresh_token" to refreshToken))
            }
            
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                saveSession(context, authResponse)
                true
            } else {
                // Refresh failed, clear session
                signOut(context)
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String, context: Context): AuthResult {
        return try {
            val response = httpClient.post("$supabaseUrl/auth/v1/token?grant_type=password") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                setBody(EmailPasswordRequest(email, password))
            }
            
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                saveSession(context, authResponse)
                // Track login session
                trackSession(context)
                AuthResult.Success(authResponse.user?.id ?: "")
            } else {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    json.decodeFromString<AuthError>(errorBody).message
                } catch (e: Exception) {
                    "Login failed"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    /**
     * Sign in with phone and password via edge function
     */
    suspend fun signInWithPhone(phone: String, password: String, context: Context): AuthResult {
        return try {
            val response = httpClient.post("$supabaseUrl/functions/v1/login-with-phone") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $supabaseKey")
                setBody(PhonePasswordRequest(phone, password))
            }
            
            if (response.status.isSuccess()) {
                val authResponse: PhoneAuthResponse = response.body()
                if (authResponse.session != null) {
                    saveSession(context, AuthResponse(
                        access_token = authResponse.session.access_token,
                        refresh_token = authResponse.session.refresh_token,
                        user = authResponse.user
                    ))
                    // Track login session
                    trackSession(context)
                    AuthResult.Success(authResponse.user?.id ?: "")
                } else if (authResponse.error != null) {
                    // Check if it's a rate limit error
                    if (authResponse.error.contains("Too many", ignoreCase = true)) {
                        AuthResult.Error(authResponse.error)
                    } else {
                        AuthResult.Error(authResponse.error)
                    }
                } else {
                    AuthResult.Error("Login failed")
                }
            } else {
                // Handle 429 rate limit
                if (response.status.value == 429) {
                    val errorBody = response.bodyAsText()
                    val errorMessage = try {
                        json.decodeFromString<AuthError>(errorBody).message
                    } catch (e: Exception) {
                        "Too many login attempts. Please try again later."
                    }
                    AuthResult.Error(errorMessage)
                } else {
                    val errorBody = response.bodyAsText()
                    val errorMessage = try {
                        json.decodeFromString<AuthError>(errorBody).message
                    } catch (e: Exception) {
                        "Invalid credentials"
                    }
                    AuthResult.Error(errorMessage)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    /**
     * Sign up with email, password, full name, and phone
     */
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        context: Context
    ): AuthResult {
        return try {
            val response = httpClient.post("$supabaseUrl/auth/v1/signup") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                setBody(SignUpRequest(
                    email = email,
                    password = password,
                    data = UserMetadata(
                        full_name = fullName,
                        phone = phone,
                        username = "user_${System.currentTimeMillis()}"
                    )
                ))
            }
            
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                if (authResponse.access_token != null) {
                    saveSession(context, authResponse)
                }
                AuthResult.Success(authResponse.user?.id ?: "")
            } else {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    json.decodeFromString<AuthError>(errorBody).message
                } catch (e: Exception) {
                    "Sign up failed"
                }
                if (errorMessage.contains("already registered", ignoreCase = true)) {
                    AuthResult.Error("This email is already registered. Please sign in instead.")
                } else {
                    AuthResult.Error(errorMessage)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    /**
     * Sign out and clear session
     */
    suspend fun signOut(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
        isAuthenticated = false
        currentUserId = null
        currentEmail = null
        currentPhone = null
        accessToken = null
    }
    
    private suspend fun saveSession(context: Context, response: AuthResponse) {
        context.dataStore.edit { prefs ->
            response.access_token?.let { prefs[ACCESS_TOKEN_KEY] = it }
            response.refresh_token?.let { prefs[REFRESH_TOKEN_KEY] = it }
            response.user?.id?.let { prefs[USER_ID_KEY] = it }
            response.user?.email?.let { prefs[USER_EMAIL_KEY] = it }
            response.user?.phone?.let { prefs[USER_PHONE_KEY] = it }
        }
        
        accessToken = response.access_token
        currentUserId = response.user?.id
        currentEmail = response.user?.email
        currentPhone = response.user?.phone
        isAuthenticated = true
    }
    
    fun getAccessToken(): String? = accessToken
    
    /**
     * SHA-256 hash for token comparison
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Track current login session by calling the track-session Edge Function
     */
    suspend fun trackSession(context: Context) {
        val token = accessToken ?: return
        try {
            val deviceInfo = mapOf(
                "browser" to "Android App",
                "os" to "Android ${Build.VERSION.RELEASE}",
                "device_type" to "mobile",
                "screen_size" to "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}",
                "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            
            httpClient.post("$supabaseUrl/functions/v1/track-session") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                setBody(mapOf("device_info" to deviceInfo))
            }
        } catch (e: Exception) {
            // Non-critical: don't fail the login if session tracking fails
            android.util.Log.e("SupabaseClient", "Error tracking session: ${e.message}")
        }
    }
    
    /**
     * Check if the current session has been revoked from another device
     */
    suspend fun checkSessionRevoked(): Boolean {
        val token = accessToken ?: return false
        return try {
            val tokenHash = sha256(token)
            val userId = currentUserId ?: return false
            
            val response = httpClient.get("$supabaseUrl/rest/v1/user_sessions") {
                parameter("select", "is_revoked")
                parameter("session_token_hash", "eq.$tokenHash")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
            }
            
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                // Parse JSON array — check if any entry has is_revoked=true
                body.contains("\"is_revoked\":true") || body.contains("\"is_revoked\": true")
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Error checking session revocation: ${e.message}")
            false
        }
    }
    
    /**
     * Fetch active sessions for the current user
     */
    suspend fun getActiveSessions(): Result<List<UserSessionInfo>> {
        val token = accessToken ?: return Result.failure(Exception("Not authenticated"))
        val userId = currentUserId ?: return Result.failure(Exception("No user ID"))
        
        return try {
            val currentTokenHash = sha256(token)
            
            val response = httpClient.get("$supabaseUrl/rest/v1/user_sessions") {
                parameter("select", "*")
                parameter("user_id", "eq.$userId")
                parameter("is_revoked", "eq.false")
                parameter("order", "last_active.desc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
            }
            
            if (response.status.isSuccess()) {
                val sessions: List<UserSessionInfo> = response.body()
                // Mark current session
                val marked = sessions.map { session ->
                    session.copy(is_current = session.session_token_hash == currentTokenHash)
                }
                Result.success(marked)
            } else {
                Result.failure(Exception("Failed to fetch sessions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Revoke a specific session
     */
    suspend fun revokeSession(sessionId: String): Result<Unit> {
        val token = accessToken ?: return Result.failure(Exception("Not authenticated"))
        
        return try {
            val response = httpClient.post("$supabaseUrl/functions/v1/revoke-session") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                setBody(mapOf("sessionId" to sessionId))
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to revoke session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Revoke all other sessions
     */
    suspend fun revokeAllOtherSessions(): Result<Unit> {
        val token = accessToken ?: return Result.failure(Exception("Not authenticated"))
        
        return try {
            val response = httpClient.post("$supabaseUrl/functions/v1/revoke-session") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                setBody(mapOf("revokeAllOthers" to true))
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to revoke sessions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update FCM token in user_settings for push notifications
     */
    suspend fun updateFcmToken(token: String) {
        val userId = currentUserId ?: return
        val currentToken = accessToken ?: return
        
        try {
            // First, try to update existing record
            val updateResponse = httpClient.request("$supabaseUrl/rest/v1/user_settings") {
                method = HttpMethod.Patch
                contentType(ContentType.Application.Json)
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $currentToken")
                header("Prefer", "return=minimal")
                setBody(mapOf(
                    "fcm_token" to token,
                    "fcm_token_updated_at" to java.time.Instant.now().toString()
                ))
            }
            
            // If no rows affected (404 or empty result), insert new record
            if (!updateResponse.status.isSuccess()) {
                httpClient.post("$supabaseUrl/rest/v1/user_settings") {
                    contentType(ContentType.Application.Json)
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $currentToken")
                    header("Prefer", "return=minimal")
                    setBody(mapOf(
                        "user_id" to userId,
                        "fcm_token" to token,
                        "fcm_token_updated_at" to java.time.Instant.now().toString()
                    ))
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash - FCM token update is not critical
            android.util.Log.e("SupabaseClient", "Error updating FCM token: ${e.message}")
        }
    }
}

// Request/Response models
@Serializable
data class EmailPasswordRequest(
    val email: String,
    val password: String
)

@Serializable
data class PhonePasswordRequest(
    val phone: String,
    val password: String
)

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val data: UserMetadata? = null
)

@Serializable
data class UserMetadata(
    val full_name: String? = null,
    val phone: String? = null,
    val username: String? = null
)

@Serializable
data class AuthResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val user: AuthUser? = null
)

@Serializable
data class PhoneAuthResponse(
    val session: SessionData? = null,
    val user: AuthUser? = null,
    val error: String? = null
)

@Serializable
data class SessionData(
    val access_token: String,
    val refresh_token: String
)

@Serializable
data class AuthUser(
    val id: String? = null,
    val email: String? = null,
    val phone: String? = null
)

@Serializable
data class AuthError(
    val message: String = "Unknown error",
    val error: String? = null,
    val error_description: String? = null
)

sealed class AuthResult {
    data class Success(val userId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Serializable
data class UserSessionInfo(
    val id: String,
    val user_id: String,
    val session_token_hash: String,
    val device_info: Map<String, String>? = null,
    val ip_address: String? = null,
    val is_revoked: Boolean = false,
    val created_at: String? = null,
    val last_active: String? = null,
    val is_current: Boolean = false
)
