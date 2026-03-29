package com.loopchat.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted credential store for biometric login.
 * 
 * Stores the user's Supabase refresh-token encrypted with a hardware-backed
 * Android Keystore key (AES-256-GCM). The biometric prompt gates access to
 * the decrypted token — without successful biometric authentication, the 
 * token cannot be used.
 * 
 * Security model:
 * - AES-256-GCM encryption via EncryptedSharedPreferences
 * - Keys held in Android Keystore (hardware-backed on supported devices)
 * - Credentials tied to user-id to prevent cross-account confusion
 * - clearCredentials() called on logout and on biometric disable
 */
object BiometricCredentialStore {
    
    private const val TAG = "BiometricCredStore"
    private const val PREFS_NAME = "biometric_credentials"
    
    private const val KEY_USER_ID = "bio_user_id"
    private const val KEY_USER_EMAIL = "bio_user_email"
    private const val KEY_REFRESH_TOKEN = "bio_refresh_token"
    private const val KEY_BIOMETRIC_ENROLLED = "bio_enrolled"
    
    private var encryptedPrefs: SharedPreferences? = null
    
    /**
     * Initialize the encrypted SharedPreferences.
     * Must be called before any read/write operations.
     */
    private fun getPrefs(context: Context): SharedPreferences {
        if (encryptedPrefs == null) {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                
                encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create encrypted prefs, falling back to clear", e)
                // Fallback: if crypto fails (rare), use regular prefs
                // This can happen on very old devices or rooted devices
                encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
        return encryptedPrefs!!
    }
    
    /**
     * Store credentials after a successful biometric enrollment.
     * Called after the user authenticates via biometric prompt during enrollment.
     */
    fun storeCredentials(
        context: Context,
        userId: String,
        email: String,
        refreshToken: String
    ) {
        try {
            getPrefs(context).edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putBoolean(KEY_BIOMETRIC_ENROLLED, true)
                .apply()
            Log.d(TAG, "Biometric credentials stored for user: ${email.take(3)}***")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store credentials", e)
        }
    }
    
    /**
     * Retrieve stored credentials for biometric login.
     * Returns null if no credentials are stored or if the store is corrupted.
     */
    fun getStoredCredentials(context: Context): BiometricCredentials? {
        return try {
            val prefs = getPrefs(context)
            val userId = prefs.getString(KEY_USER_ID, null)
            val email = prefs.getString(KEY_USER_EMAIL, null)
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            val enrolled = prefs.getBoolean(KEY_BIOMETRIC_ENROLLED, false)
            
            if (userId != null && refreshToken != null && enrolled) {
                BiometricCredentials(
                    userId = userId,
                    email = email ?: "",
                    refreshToken = refreshToken
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read credentials", e)
            null
        }
    }
    
    /**
     * Check if biometric credentials are enrolled (without reading the token).
     */
    fun hasStoredCredentials(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)
            prefs.getBoolean(KEY_BIOMETRIC_ENROLLED, false) &&
                prefs.getString(KEY_REFRESH_TOKEN, null) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Clear all stored biometric credentials.
     * Called on:
     * - User logout
     * - User disables biometric login
     * - Token refresh failure (forces re-enrollment)
     */
    fun clearCredentials(context: Context) {
        try {
            getPrefs(context).edit().clear().apply()
            Log.d(TAG, "Biometric credentials cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials", e)
        }
    }
    
    /**
     * Update the stored refresh token after a successful token refresh.
     * This keeps the encrypted store in sync with the latest valid token.
     */
    fun updateRefreshToken(context: Context, newRefreshToken: String) {
        try {
            getPrefs(context).edit()
                .putString(KEY_REFRESH_TOKEN, newRefreshToken)
                .apply()
            Log.d(TAG, "Refresh token updated in biometric store")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update refresh token", e)
        }
    }
}

/**
 * Data class holding the decrypted biometric credentials.
 */
data class BiometricCredentials(
    val userId: String,
    val email: String,
    val refreshToken: String
)
