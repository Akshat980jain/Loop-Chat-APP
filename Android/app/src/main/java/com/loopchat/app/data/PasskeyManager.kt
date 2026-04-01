package com.loopchat.app.data

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Manages Passkey (WebAuthn) registration and login via Android CredentialManager.
 *
 * Passkeys are synced across devices via the user's Google Account,
 * allowing biometric login on any device the user owns.
 *
 * Flow:
 * 1. Registration: App → Edge Function (get options) → CredentialManager (create) → Edge Function (verify)
 * 2. Login:        App → Edge Function (get options) → CredentialManager (get)    → Edge Function (verify) → Session
 */
object PasskeyManager {

    private const val TAG = "PasskeyManager"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ============================================
    // REGISTRATION (Enrollment)
    // ============================================

    sealed class RegistrationResult {
        object Success : RegistrationResult()
        data class Error(val message: String) : RegistrationResult()
        object Cancelled : RegistrationResult()
    }

    /**
     * Register a new Passkey for the currently logged-in user.
     *
     * Must be called while the user is already authenticated (has a valid access token).
     */
    suspend fun registerPasskey(
        context: Context,
        httpClient: HttpClient
    ): RegistrationResult {
        try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return RegistrationResult.Error("Not authenticated")
            val supabaseUrl = SupabaseClient.supabaseUrl
            val supabaseKey = SupabaseClient.supabaseKey

            // Step 1: Get registration options from the server
            Log.d(TAG, "Requesting registration options...")
            val optionsResponse = httpClient.post("$supabaseUrl/functions/v1/passkey-register-options") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody("{}")  // Empty body, user identified by JWT
            }

            if (!optionsResponse.status.isSuccess()) {
                val error = optionsResponse.bodyAsText()
                Log.e(TAG, "Failed to get registration options: $error")
                return RegistrationResult.Error("Failed to start passkey registration")
            }

            val optionsJson = optionsResponse.bodyAsText()
            Log.d(TAG, "Received registration options")

            // Step 2: Use CredentialManager to create the passkey
            val credentialManager = CredentialManager.create(context)
            val createRequest = CreatePublicKeyCredentialRequest(
                requestJson = optionsJson
            )

            val createResult = try {
                credentialManager.createCredential(
                    context = context as android.app.Activity,
                    request = createRequest
                )
            } catch (e: CreateCredentialCancellationException) {
                Log.d(TAG, "Passkey creation cancelled by user")
                return RegistrationResult.Cancelled
            } catch (e: CreateCredentialException) {
                Log.e(TAG, "Passkey creation failed: ${e.message}")
                return RegistrationResult.Error("Passkey creation failed: ${e.message}")
            }

            // Step 3: Extract the credential data
            val credential = createResult as? PublicKeyCredential
                ?: return RegistrationResult.Error("Unexpected credential type")

            val responseJson = credential.authenticationResponseJson
            Log.d(TAG, "Credential created, sending to server for verification...")

            // Parse the response to extract fields for verification
            val parsedResponse = json.decodeFromString<JsonObject>(responseJson)
            val response = parsedResponse["response"] as? JsonObject
                ?: return RegistrationResult.Error("Invalid credential response")

            val credentialId = parsedResponse["id"]?.jsonPrimitive?.content
                ?: return RegistrationResult.Error("Missing credential ID")
            val clientDataJSON = response["clientDataJSON"]?.jsonPrimitive?.content ?: ""
            val attestationObject = response["attestationObject"]?.jsonPrimitive?.content ?: ""
            val publicKey = response["publicKey"]?.jsonPrimitive?.content ?: ""
            val transportsArray = response["transports"]  // May be null

            // Step 4: Send to server for verification and storage
            val verifyResponse = httpClient.post("$supabaseUrl/functions/v1/passkey-register-verify") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(buildString {
                    append("{")
                    append("\"credentialId\":\"$credentialId\",")
                    append("\"publicKey\":\"$publicKey\",")
                    append("\"clientDataJSON\":\"$clientDataJSON\",")
                    append("\"attestationObject\":\"$attestationObject\",")
                    append("\"transports\":${transportsArray ?: "[]"},")
                    append("\"deviceName\":\"${Build.MANUFACTURER} ${Build.MODEL}\"")
                    append("}")
                })
            }

            if (!verifyResponse.status.isSuccess()) {
                val error = verifyResponse.bodyAsText()
                Log.e(TAG, "Server verification failed: $error")
                return RegistrationResult.Error("Passkey verification failed on server")
            }

            Log.d(TAG, "Passkey registered successfully!")
            return RegistrationResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Passkey registration error: ${e.message}", e)
            return RegistrationResult.Error(e.message ?: "Unknown error during passkey registration")
        }
    }

    // ============================================
    // LOGIN (Authentication)
    // ============================================

    sealed class LoginResult {
        data class Success(
            val accessToken: String,
            val refreshToken: String,
            val userId: String?,
            val email: String?,
            val phone: String?
        ) : LoginResult()
        data class Error(val message: String) : LoginResult()
        object Cancelled : LoginResult()
        object NoPasskeys : LoginResult()
    }

    /**
     * Log in using a stored Passkey.
     *
     * Called from the login screen BEFORE the user is authenticated.
     * The email/phone is needed to look up their registered passkeys on the server.
     */
    suspend fun loginWithPasskey(
        context: Context,
        httpClient: HttpClient,
        email: String? = null,
        phone: String? = null
    ): LoginResult {
        try {
            val supabaseUrl = SupabaseClient.supabaseUrl
            val supabaseKey = SupabaseClient.supabaseKey

            // Step 1: Get authentication options from the server
            Log.d(TAG, "Requesting login options...")
            val optionsResponse = httpClient.post("$supabaseUrl/functions/v1/passkey-login-options") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $supabaseKey")  // Anonymous auth for login
                setBody(buildString {
                    append("{")
                    if (email != null) append("\"email\":\"$email\"")
                    else if (phone != null) append("\"phone\":\"$phone\"")
                    append("}")
                })
            }

            if (!optionsResponse.status.isSuccess()) {
                val errorBody = optionsResponse.bodyAsText()
                Log.e(TAG, "Failed to get login options: $errorBody")
                if (optionsResponse.status.value == 404) {
                    return LoginResult.NoPasskeys
                }
                return LoginResult.Error("Failed to start passkey login")
            }

            val optionsJson = optionsResponse.bodyAsText()
            Log.d(TAG, "Received login options")

            // Step 2: Use CredentialManager to get the passkey
            val credentialManager = CredentialManager.create(context)
            val getRequest = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(requestJson = optionsJson))
            )

            val getResult = try {
                credentialManager.getCredential(
                    context = context as android.app.Activity,
                    request = getRequest
                )
            } catch (e: GetCredentialCancellationException) {
                Log.d(TAG, "Passkey login cancelled by user")
                return LoginResult.Cancelled
            } catch (e: NoCredentialException) {
                Log.d(TAG, "No passkey found on this device")
                return LoginResult.NoPasskeys
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Passkey get failed: ${e.message}")
                return LoginResult.Error("Passkey login failed: ${e.message}")
            }

            // Step 3: Extract the credential data
            val credential = getResult.credential as? PublicKeyCredential
                ?: return LoginResult.Error("Unexpected credential type")

            val responseJson = credential.authenticationResponseJson
            Log.d(TAG, "Credential retrieved, sending to server for verification...")

            // Parse the response
            val parsedResponse = json.decodeFromString<JsonObject>(responseJson)
            val response = parsedResponse["response"] as? JsonObject
                ?: return LoginResult.Error("Invalid credential response")

            val credentialId = parsedResponse["id"]?.jsonPrimitive?.content
                ?: return LoginResult.Error("Missing credential ID")
            val clientDataJSON = response["clientDataJSON"]?.jsonPrimitive?.content ?: ""
            val authenticatorData = response["authenticatorData"]?.jsonPrimitive?.content ?: ""
            val signature = response["signature"]?.jsonPrimitive?.content ?: ""
            val userHandle = response["userHandle"]?.jsonPrimitive?.content ?: ""

            // Step 4: Send to server for verification
            val verifyResponse = httpClient.post("$supabaseUrl/functions/v1/passkey-login-verify") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $supabaseKey")  // Anonymous
                setBody(buildString {
                    append("{")
                    append("\"credentialId\":\"$credentialId\",")
                    append("\"clientDataJSON\":\"$clientDataJSON\",")
                    append("\"authenticatorData\":\"$authenticatorData\",")
                    append("\"signature\":\"$signature\",")
                    append("\"userHandle\":\"$userHandle\"")
                    append("}")
                })
            }

            if (!verifyResponse.status.isSuccess()) {
                val error = verifyResponse.bodyAsText()
                Log.e(TAG, "Server login verification failed: $error")
                return LoginResult.Error("Passkey login verification failed")
            }

            // Step 5: Parse the session tokens from the response
            val loginResponse = json.decodeFromString<PasskeyLoginResponse>(
                verifyResponse.bodyAsText()
            )

            if (loginResponse.success != true || loginResponse.access_token == null) {
                return LoginResult.Error("Login verification succeeded but no session was created")
            }

            Log.d(TAG, "Passkey login successful!")
            return LoginResult.Success(
                accessToken = loginResponse.access_token,
                refreshToken = loginResponse.refresh_token ?: "",
                userId = loginResponse.user?.id,
                email = loginResponse.user?.email,
                phone = loginResponse.user?.phone
            )

        } catch (e: Exception) {
            Log.e(TAG, "Passkey login error: ${e.message}", e)
            return LoginResult.Error(e.message ?: "Unknown error during passkey login")
        }
    }

    // ============================================
    // RESPONSE MODELS
    // ============================================

    @Serializable
    private data class PasskeyLoginResponse(
        val success: Boolean? = null,
        val access_token: String? = null,
        val refresh_token: String? = null,
        val user: PasskeyUser? = null,
        val error: String? = null
    )

    @Serializable
    private data class PasskeyUser(
        val id: String? = null,
        val email: String? = null,
        val phone: String? = null
    )
}
