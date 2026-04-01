package com.loopchat.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import javax.crypto.Cipher

/**
 * Encrypted credential store for biometric login.
 *
 * ## Security model (crypto-bound biometrics)
 *
 * Unlike a typical EncryptedSharedPreferences approach, this store uses a
 * **biometric-bound Android Keystore key** for the refresh token:
 *
 * - During **enrollment**, the caller passes in a [Cipher] (ENCRYPT mode)
 *   obtained from a successful `BiometricPrompt.CryptoObject`. The refresh
 *   token is encrypted with this cipher, and both the ciphertext and the IV
 *   are stored in SharedPreferences.
 *
 * - During **login**, the caller passes in a [Cipher] (DECRYPT mode)
 *   initialised with the stored IV. Only a successful biometric scan unlocks
 *   the Keystore key, making the cipher usable for decryption.
 *
 * - If device biometric enrollment changes (fingerprints added/removed),
 *   the Keystore key is **permanently invalidated** and the stored credentials
 *   become undecryptable. The user must re-enroll by signing in with their
 *   password.
 *
 * Non-sensitive metadata (user ID, email, enrollment flag) is stored in plain
 * SharedPreferences since they don't grant access to the account.
 */
object BiometricCredentialStore {

    private const val TAG = "BiometricCredStore"
    private const val PREFS_NAME = "biometric_credentials"

    private const val KEY_USER_ID = "bio_user_id"
    private const val KEY_USER_EMAIL = "bio_user_email"
    private const val KEY_ENCRYPTED_TOKEN = "bio_encrypted_token"
    private const val KEY_TOKEN_IV = "bio_token_iv"
    private const val KEY_BIOMETRIC_ENROLLED = "bio_enrolled"

    private fun getPrefs(context: Context): SharedPreferences {
        // Plain SharedPreferences — the refresh token is encrypted by the
        // biometric-bound Keystore key, not by the prefs themselves.
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ========================================================================
    // Store (encrypt)
    // ========================================================================

    /**
     * Store credentials after a successful biometric enrollment.
     *
     * @param cipher The authenticated [Cipher] in ENCRYPT mode, obtained from
     *               the successful `BiometricPrompt.CryptoObject`.
     * @param userId The Supabase user ID.
     * @param email  The user's email address.
     * @param refreshToken The Supabase refresh token to encrypt and store.
     */
    fun storeCredentials(
        context: Context,
        cipher: Cipher,
        userId: String,
        email: String,
        refreshToken: String
    ) {
        try {
            // Encrypt the refresh token with the bio-bound cipher
            val encryptedBytes = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            val encryptedToken = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)

            getPrefs(context).edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_ENCRYPTED_TOKEN, encryptedToken)
                .putString(KEY_TOKEN_IV, ivString)
                .putBoolean(KEY_BIOMETRIC_ENROLLED, true)
                .apply()

            Log.d(TAG, "Biometric credentials stored (crypto-bound) for user: ${email.take(3)}***")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store credentials", e)
        }
    }

    // ========================================================================
    // Retrieve (decrypt)
    // ========================================================================

    /**
     * Retrieve stored credentials for biometric login.
     *
     * @param cipher The authenticated [Cipher] in DECRYPT mode, obtained from
     *               the successful `BiometricPrompt.CryptoObject`.
     * @return Decrypted [BiometricCredentials] or `null` if unavailable.
     */
    fun getStoredCredentials(context: Context, cipher: Cipher): BiometricCredentials? {
        return try {
            val prefs = getPrefs(context)
            val userId = prefs.getString(KEY_USER_ID, null)
            val email = prefs.getString(KEY_USER_EMAIL, null)
            val encryptedToken = prefs.getString(KEY_ENCRYPTED_TOKEN, null)
            val enrolled = prefs.getBoolean(KEY_BIOMETRIC_ENROLLED, false)

            if (userId != null && encryptedToken != null && enrolled) {
                val encryptedBytes = Base64.decode(encryptedToken, Base64.NO_WRAP)
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                val refreshToken = String(decryptedBytes, Charsets.UTF_8)

                BiometricCredentials(
                    userId = userId,
                    email = email ?: "",
                    refreshToken = refreshToken
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt credentials", e)
            null
        }
    }

    // ========================================================================
    // Metadata (no cipher needed)
    // ========================================================================

    /**
     * Check if biometric credentials are enrolled (without reading the token).
     */
    fun hasStoredCredentials(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)
            prefs.getBoolean(KEY_BIOMETRIC_ENROLLED, false) &&
                prefs.getString(KEY_ENCRYPTED_TOKEN, null) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the stored IV needed to initialise the decrypt cipher.
     * Returns `null` if no IV is stored.
     */
    fun getStoredIv(context: Context): ByteArray? {
        return try {
            val ivString = getPrefs(context).getString(KEY_TOKEN_IV, null) ?: return null
            Base64.decode(ivString, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read IV", e)
            null
        }
    }

    /**
     * Clear all stored biometric credentials and delete the Keystore key.
     * Called on:
     * - User disables biometric login
     * - Token decryption failure (forces re-enrollment)
     * - Key invalidation detected
     */
    fun clearCredentials(context: Context) {
        try {
            getPrefs(context).edit().clear().apply()
            BiometricAuthManager.deleteKey()
            Log.d(TAG, "Biometric credentials cleared and Keystore key deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials", e)
        }
    }

    /**
     * Update the stored refresh token after a successful token refresh.
     *
     * This requires re-encryption with a new biometric-bound cipher. Since
     * we can't prompt for biometrics during a background token refresh, we
     * must **clear credentials** and let the user re-enroll on next login.
     *
     * In practice, Supabase refresh tokens are long-lived, so this path is
     * rarely hit. If it is, the user simply sees the password login and can
     * re-enable fingerprint login.
     *
     * NOTE: For a better UX, we keep the old encrypted token as-is and only
     * clear if the old token actually fails to authenticate. This way, most
     * token refreshes are transparent.
     */
    fun markTokenPotentiallyStale(context: Context) {
        Log.d(TAG, "Refresh token may have changed — biometric login still uses original enrolled token")
        // We intentionally do NOT clear here. The stored encrypted refresh token
        // will be used as-is. If Supabase rejects it, performBiometricLogin()
        // in AuthViewModel will clear credentials and force re-enrollment.
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
