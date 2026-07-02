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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// DataStore extension
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "supabase_session")

/**
 * Supabase authentication client for Android
 * Uses Ktor HTTP client for REST API calls to Supabase Auth
 */
object SupabaseClient {
    val supabaseUrl = BuildConfig.SUPABASE_URL
    val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            connectTimeout = 8_000  // Reduced: fail fast instead of hanging
            socketTimeout = 8_000
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
    private val initMutex = Mutex()
    @Volatile
    private var isInitialized = false
    
    /**
     * Initialize the client and restore session from DataStore
     * Also validates and refreshes the token if needed
     */
    /**
     * @param skipRevocationCheck Pass true when calling from a background service (e.g. CallService,
     * FCM handler) to prevent the revocation check from calling signOut() while the user is
     * actively using the app — which would race with the UI and cause a spurious logout.
     */
    suspend fun initialize(context: Context, skipRevocationCheck: Boolean = false) {
        initMutex.withLock {
            if (isInitialized) return
            
            val prefs = context.dataStore.data.first()
            accessToken = prefs[ACCESS_TOKEN_KEY]
            val refreshToken = prefs[REFRESH_TOKEN_KEY]
            currentUserId = prefs[USER_ID_KEY]
            currentEmail = prefs[USER_EMAIL_KEY]
            currentPhone = prefs[USER_PHONE_KEY]
            
            // If we have a token, validate it and refresh if needed
            if (accessToken != null) {
                // Enforce a hard 10-second cap so a slow/unreachable server never blocks app startup.
                val isValid = try {
                    kotlinx.coroutines.withTimeout(10_000L) { validateToken() }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.w("SupabaseClient", "Token validation timed out — treating as valid to unblock startup")
                    true // Optimistically keep session; next real request will fail if truly expired
                }
                if (!isValid && refreshToken != null) {
                    // Try to refresh the token
                    val refreshed = try {
                        kotlinx.coroutines.withTimeout(10_000L) { refreshSession(context, refreshToken) }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        android.util.Log.w("SupabaseClient", "Token refresh timed out — keeping session active")
                        true // Treat timeout as valid to prevent offline logouts
                    }
                    isAuthenticated = refreshed
                } else {
                    isAuthenticated = isValid
                }
                
                // PERFORMANCE: checkSessionRevoked is non-critical — run it in background
                // so it never blocks the loading screen. Auth is already confirmed above.
                // IMPORTANT: Skip from background service contexts to prevent logout races.
                if (isAuthenticated && !skipRevocationCheck) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val revoked = checkSessionRevoked()
                            if (revoked) {
                                android.util.Log.w("SupabaseClient", "Session was revoked from another device")
                                signOut(context)
                                isAuthenticated = false
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("SupabaseClient", "Session revocation check failed (non-critical): ${e.message}")
                        }
                    }
                }
            } else {
                isAuthenticated = false
            }
            
            isInitialized = true
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
            if (response.status.isSuccess()) {
                true
            } else {
                // Keep session if it's not a definitive 400/401/403 authentication error
                val code = response.status.value
                code != 400 && code != 401 && code != 403
            }
        } catch (e: Exception) {
            // Assume token is valid during network/socket exceptions to prevent offline logouts
            android.util.Log.w("SupabaseClient", "validateToken exception: ${e.message} - assuming valid for offline resilience")
            true
        }
    }
    
    /**
     * Refresh the session using refresh token
     */
    /**
     * Refresh the session using refresh token.
     * Made public to support biometric login flow.
     */
    suspend fun refreshSession(context: Context, refreshToken: String): Boolean {
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
                // Clear the session only if the refresh token is explicitly invalid/expired (400, 401, 403)
                val code = response.status.value
                if (code == 400 || code == 401 || code == 403) {
                    android.util.Log.e("SupabaseClient", "Refresh token invalid ($code) - signing out")
                    signOut(context)
                    false
                } else {
                    // For server errors or other statuses, assume temporary and keep session active
                    true
                }
            }
        } catch (e: Exception) {
            // Keep session active during connectivity/timeout issues
            android.util.Log.w("SupabaseClient", "refreshSession exception: ${e.message} - assuming active for offline resilience")
            true
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
                    json.decodeFromString<AuthError>(errorBody).errorMessage
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
     * Send OTP to phone number
     */
    suspend fun sendPhoneOtp(phone: String): AuthResult {
        return try {
            val response = httpClient.post("$supabaseUrl/auth/v1/otp") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                setBody(SendOtpRequest(phone = phone))
            }
            
            if (response.status.isSuccess()) {
                AuthResult.Success("OTP sent")
            } else {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    json.decodeFromString<AuthError>(errorBody).errorMessage
                } catch (e: Exception) {
                    "Failed to send OTP"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    /**
     * Verify OTP for phone number
     */
    suspend fun verifyPhoneOtp(phone: String, token: String, context: Context): AuthResult {
        return try {
            val response = httpClient.post("$supabaseUrl/auth/v1/verify") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                setBody(VerifyOtpRequest(phone = phone, token = token))
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
                    json.decodeFromString<AuthError>(errorBody).errorMessage
                } catch (e: Exception) {
                    "Invalid verification code"
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
                        json.decodeFromString<AuthError>(errorBody).errorMessage
                    } catch (e: Exception) {
                        "Too many login attempts. Please try again later."
                    }
                    AuthResult.Error(errorMessage)
                } else {
                    val errorBody = response.bodyAsText()
                    val errorMessage = try {
                        json.decodeFromString<AuthError>(errorBody).errorMessage
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
                    json.decodeFromString<AuthError>(errorBody).errorMessage
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
     * Sign out and clear session.
     * NOTE: Does NOT reset isInitialized — we keep it true so that background services
     * calling initialize() after signOut (e.g. FCM/CallService) do not re-run initialization
     * and accidentally overwrite the intentional logout state.
     * The app process restarts naturally on next user launch.
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
        // NOTE: isInitialized intentionally NOT reset here. See doc above.
    }
    
    /**
     * Force-reset initialization state. Call ONLY from explicit logout flows that
     * need to clear state and then immediately re-initialize (e.g., account switching).
     * Do NOT call from background services.
     */
    fun resetForReauth() {
        isInitialized = false
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
     * Save a session returned by the passkey-login-verify Edge Function.
     * This is similar to saveSession but accepts raw values instead of an AuthResponse.
     */
    suspend fun savePasskeySession(
        context: Context,
        accessToken: String,
        refreshToken: String,
        userId: String?,
        email: String?,
        phone: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
            userId?.let { prefs[USER_ID_KEY] = it }
            email?.let { prefs[USER_EMAIL_KEY] = it }
            phone?.let { prefs[USER_PHONE_KEY] = it }
        }
        
        this.accessToken = accessToken
        currentUserId = userId
        currentEmail = email
        currentPhone = phone
        isAuthenticated = true
    }
    
    /**
     * Get the cached refresh token for biometric enrollment.
     * Reads from DataStore.
     */
    suspend fun getRefreshToken(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[REFRESH_TOKEN_KEY]
    }
    
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
        
        // Also upload E2EE Public Key upon valid session initialization
        uploadPublicKey()
    }
    
    /**
     * Upload the user's RSA Public Key to Supabase for E2EE
     */
    private suspend fun uploadPublicKey() {
        val token = accessToken ?: return
        val userId = currentUserId ?: return
        try {
            val base64PublicKey = com.loopchat.app.data.crypto.CryptoManager.getMyPublicKeyBase64() ?: return
            
            val payload = mapOf(
                "user_id" to userId,
                "public_key" to base64PublicKey,
                "updated_at" to "now()"
            )

            // Upsert mechanism: On conflict update public_key
            httpClient.post("$supabaseUrl/rest/v1/user_public_keys") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "resolution=merge-duplicates")
                setBody(payload)
            }
            android.util.Log.d("SupabaseClient", "Public key uploaded successfully")
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Error uploading public key: ${e.message}")
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
            // Use single POST request with Prefer: resolution=merge-duplicates to perform a true upsert.
            // This ensures the record is created if it does not exist, and updated if it does.
            httpClient.post("$supabaseUrl/rest/v1/user_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $currentToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(mapOf(
                    "user_id" to userId,
                    "fcm_token" to token,
                    "fcm_token_updated_at" to java.time.Instant.now().toString()
                ))
            }
            android.util.Log.d("SupabaseClient", "FCM token upserted successfully")
        } catch (e: Exception) {
            // Log error but don't crash - FCM token update is not critical
            android.util.Log.e("SupabaseClient", "Error updating FCM token: ${e.message}")
        }
    }

    /**
     * Send password recovery email via Supabase Auth (/recover)
     */
    suspend fun resetPasswordForEmail(email: String): AuthResult {
        return try {
            val response = httpClient.post("$supabaseUrl/auth/v1/recover") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                setBody(RecoverRequest(email))
            }
            
            if (response.status.isSuccess()) {
                AuthResult.Success("Reset email sent")
            } else {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    json.decodeFromString<AuthError>(errorBody).errorMessage
                } catch (e: Exception) {
                    "Failed to send reset email"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * Invoke send-otp Edge Function for password reset
     */
    suspend fun sendPasswordResetOtp(phone: String): Result<EdgeFunctionResponse> {
        return try {
            val response = httpClient.post("$supabaseUrl/functions/v1/send-otp") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $supabaseKey")
                setBody(SendOtpEdgeRequest(phone))
            }
            
            if (response.status.isSuccess()) {
                val body = response.body<EdgeFunctionResponse>()
                Result.success(body)
            } else {
                val errorBody = response.bodyAsText()
                val errorMsg = try {
                    json.decodeFromString<EdgeFunctionResponse>(errorBody).error ?: "Failed to send OTP"
                } catch (e: Exception) {
                    "Failed to send OTP"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Invoke verify-otp Edge Function for password reset
     */
    suspend fun verifyPasswordResetOtp(phone: String, otp: String, newPassword: String): Result<EdgeFunctionResponse> {
        return try {
            val response = httpClient.post("$supabaseUrl/functions/v1/verify-otp") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $supabaseKey")
                setBody(VerifyOtpEdgeRequest(phone, otp, newPassword))
            }
            
            if (response.status.isSuccess()) {
                val body = response.body<EdgeFunctionResponse>()
                Result.success(body)
            } else {
                val errorBody = response.bodyAsText()
                val errorMsg = try {
                    json.decodeFromString<EdgeFunctionResponse>(errorBody).error ?: "Failed to reset password"
                } catch (e: Exception) {
                    "Failed to reset password"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
    val message: String? = null,
    val msg: String? = null,
    val error: String? = null,
    val error_description: String? = null
) {
    val errorMessage: String
        get() = msg ?: error_description ?: message ?: error ?: "Unknown error"
}

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

@Serializable
data class SendOtpRequest(
    val phone: String,
    val create_user: Boolean = true
)

@Serializable
data class VerifyOtpRequest(
    val type: String = "sms",
    val phone: String,
    val token: String
)

@Serializable
data class RecoverRequest(
    val email: String
)

@Serializable
data class SendOtpEdgeRequest(
    val phone: String
)

@Serializable
data class VerifyOtpEdgeRequest(
    val phone: String,
    val otp: String,
    val newPassword: String
)

@Serializable
data class EdgeFunctionResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val otp: String? = null
)
