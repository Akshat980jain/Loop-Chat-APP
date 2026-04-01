package com.loopchat.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manager class that wraps AndroidX BiometricPrompt API with
 * **app-scoped cryptographic key binding**.
 *
 * ## How it works
 *
 * Instead of a simple "yes/no" biometric prompt, this manager creates an
 * AES-256-GCM key in the Android Keystore that is:
 *
 * 1. **Bound to biometric authentication** (`setUserAuthenticationRequired(true)`)
 *    — the key can only be used after a successful biometric scan.
 *
 * 2. **Invalidated when biometric enrollment changes**
 *    (`setInvalidatedByBiometricEnrollment(true)`) — if the user adds or
 *    removes a fingerprint in the device Settings, the key is permanently
 *    destroyed, forcing the user to re-enroll in Loop Chat with their password.
 *
 * This gives Loop Chat its **own** fingerprint identity: even though we use
 * the OS biometric sensor, the cryptographic key is scoped to the fingerprints
 * that were enrolled on the device at the time the user set up biometric login
 * in Loop Chat. A new fingerprint added later cannot decrypt the stored
 * credentials.
 *
 * ## Two authentication modes
 *
 * - **Crypto-based** (`authenticateWithCrypto`): Used for Fingerprint Login.
 *   Returns a usable [Cipher] that the caller uses to encrypt/decrypt the
 *   stored Supabase refresh token. This is the high-security path.
 *
 * - **Simple** (`authenticate`): Used for App Lock. No crypto involved — just
 *   a confirmation that the user can scan their finger. Acceptable here because
 *   App Lock is a UI gate, not a credential gate.
 */
object BiometricAuthManager {

    private const val TAG = "BiometricAuthManager"

    /** Keystore alias for the biometric-login AES key. */
    private const val KEY_ALIAS = "loopchat_biometric_login_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // ========================================================================
    // Biometric availability
    // ========================================================================

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        NOT_ENROLLED,
        NOT_AVAILABLE
    }

    fun canAuthenticate(activity: FragmentActivity): BiometricStatus {
        val biometricManager = BiometricManager.from(activity)

        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(TAG, "Biometric authentication is available")
                BiometricStatus.AVAILABLE
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d(TAG, "No biometric hardware found")
                BiometricStatus.NO_HARDWARE
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d(TAG, "Biometric hardware unavailable")
                BiometricStatus.NOT_AVAILABLE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(TAG, "No biometrics enrolled on device")
                BiometricStatus.NOT_ENROLLED
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.d(TAG, "Security update required for biometrics")
                BiometricStatus.NOT_AVAILABLE
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.d(TAG, "Biometric authentication unsupported")
                BiometricStatus.NOT_AVAILABLE
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Log.d(TAG, "Biometric status unknown")
                BiometricStatus.NOT_AVAILABLE
            }
            else -> {
                Log.d(TAG, "Unknown biometric status code")
                BiometricStatus.NOT_AVAILABLE
            }
        }
    }

    // ========================================================================
    // Keystore key management
    // ========================================================================

    /**
     * Generate (or regenerate) an AES-256-GCM key in the Android Keystore.
     *
     * The key has two critical properties:
     * - **userAuthenticationRequired**: the Cipher can only be init'd after
     *   a successful biometric scan via BiometricPrompt.
     * - **invalidatedByBiometricEnrollment**: if the user adds/removes a
     *   fingerprint on the device, the key is permanently destroyed.
     */
    fun generateKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
            Log.d(TAG, "Biometric Keystore key generated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Keystore key", e)
        }
    }

    /**
     * Delete the Keystore key (called on biometric-login disable or credential clear).
     */
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                Log.d(TAG, "Biometric Keystore key deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Keystore key", e)
        }
    }

    /**
     * Check whether the Keystore key exists and is still valid.
     *
     * Returns `false` if:
     * - The key was never generated
     * - The key was permanently invalidated (biometric enrollment changed)
     */
    fun isKeyValid(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) return false

            // Attempt to initialise a Cipher — this will throw
            // KeyPermanentlyInvalidatedException if biometrics changed.
            val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Keystore key invalidated — biometric enrollment changed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key validity", e)
            false
        }
    }

    // ========================================================================
    // Crypto-based authentication (for Fingerprint Login)
    // ========================================================================

    /**
     * Result of a crypto-based biometric authentication.
     */
    sealed class CryptoAuthResult {
        /** Authentication succeeded. The [cipher] is ready for encrypt/decrypt. */
        data class Success(val cipher: Cipher) : CryptoAuthResult()
        /** The user cancelled or dismissed the prompt. */
        object Cancelled : CryptoAuthResult()
        /** An error occurred (message provided). */
        data class Error(val message: String) : CryptoAuthResult()
        /** The Keystore key was invalidated (biometric enrollment changed on device). */
        object KeyInvalidated : CryptoAuthResult()
    }

    /**
     * Show the biometric prompt with a **CryptoObject** for encryption.
     *
     * Used during **enrollment**: the returned [Cipher] (in ENCRYPT mode) is
     * used to encrypt the Supabase refresh token before storing it.
     *
     * @param onResult Callback with the [CryptoAuthResult].
     */
    fun authenticateForEncryption(
        activity: FragmentActivity,
        title: String = "Enable Fingerprint Login",
        subtitle: String = "Verify your fingerprint",
        negativeButtonText: String = "Cancel",
        onResult: (CryptoAuthResult) -> Unit
    ) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKey()
            }

            val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)

            showCryptoPrompt(activity, cipher, title, subtitle, negativeButtonText, onResult)
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Key invalidated during encrypt init", e)
            deleteKey()
            onResult(CryptoAuthResult.KeyInvalidated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise cipher for encryption", e)
            onResult(CryptoAuthResult.Error("Failed to start biometric authentication: ${e.message}"))
        }
    }

    /**
     * Show the biometric prompt with a **CryptoObject** for decryption.
     *
     * Used during **login**: the returned [Cipher] (in DECRYPT mode) is
     * used to decrypt the stored Supabase refresh token.
     *
     * @param iv The initialisation vector that was stored alongside the
     *           encrypted token during enrollment.
     * @param onResult Callback with the [CryptoAuthResult].
     */
    fun authenticateForDecryption(
        activity: FragmentActivity,
        iv: ByteArray,
        title: String = "Loop Chat",
        subtitle: String = "Use your fingerprint to sign in",
        negativeButtonText: String = "Use Password",
        onResult: (CryptoAuthResult) -> Unit
    ) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.w(TAG, "No key found for decryption")
                onResult(CryptoAuthResult.KeyInvalidated)
                return
            }

            val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            showCryptoPrompt(activity, cipher, title, subtitle, negativeButtonText, onResult)
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Key invalidated during decrypt init", e)
            deleteKey()
            onResult(CryptoAuthResult.KeyInvalidated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise cipher for decryption", e)
            onResult(CryptoAuthResult.Error("Failed to start biometric authentication: ${e.message}"))
        }
    }

    /**
     * Internal: show the BiometricPrompt with a CryptoObject.
     */
    private fun showCryptoPrompt(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onResult: (CryptoAuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher != null) {
                    Log.d(TAG, "Crypto biometric authentication succeeded")
                    onResult(CryptoAuthResult.Success(authenticatedCipher))
                } else {
                    Log.e(TAG, "CryptoObject cipher is null after auth success")
                    onResult(CryptoAuthResult.Error("Biometric authentication error"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Crypto biometric auth error: $errorCode - $errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onResult(CryptoAuthResult.Cancelled)
                    }
                    BiometricPrompt.ERROR_LOCKOUT -> {
                        onResult(CryptoAuthResult.Error("Too many attempts. Try again later."))
                    }
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onResult(CryptoAuthResult.Error("Biometric locked. Use password to unlock."))
                    }
                    else -> {
                        onResult(CryptoAuthResult.Error(errString.toString()))
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Crypto biometric auth failed (bad biometric)")
                // System shows its own retry UI
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show crypto biometric prompt", e)
            onResult(CryptoAuthResult.Error("Failed to start biometric authentication"))
        }
    }

    // ========================================================================
    // Simple (non-crypto) authentication (for App Lock)
    // ========================================================================

    /**
     * Show the system biometric authentication prompt (no crypto).
     *
     * Used for **App Lock** — a UI gate that blocks access to the app.
     * Does not involve credential encryption, so a simple callback is fine.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Login",
        subtitle: String = "Use your fingerprint to sign in",
        description: String? = null,
        negativeButtonText: String = "Use Password",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Biometric authentication succeeded")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Biometric authentication error: $errorCode - $errString")

                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        onFallback()
                    }
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onFallback()
                    }
                    BiometricPrompt.ERROR_LOCKOUT -> {
                        onError("Too many attempts. Try again later.")
                    }
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onError("Biometric locked. Use password to unlock.")
                    }
                    else -> {
                        onError(errString.toString())
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Biometric authentication failed (bad biometric)")
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (description != null) {
            promptInfoBuilder.setDescription(description)
        }

        try {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show biometric prompt", e)
            onError("Failed to start biometric authentication")
        }
    }
}
