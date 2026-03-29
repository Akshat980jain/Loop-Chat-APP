package com.loopchat.app.data

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Manager class that wraps AndroidX BiometricPrompt API.
 * 
 * Provides a clean interface for:
 * - Checking biometric hardware availability
 * - Showing the system biometric authentication prompt
 * - Handling authentication results via callbacks
 * 
 * Uses BIOMETRIC_STRONG authenticators only (fingerprint, face, iris)
 * for maximum security.
 */
object BiometricAuthManager {
    
    private const val TAG = "BiometricAuthManager"
    
    /**
     * Status of biometric availability on the device.
     */
    enum class BiometricStatus {
        /** Biometric hardware is available and at least one biometric is enrolled */
        AVAILABLE,
        /** Device has no biometric hardware */
        NO_HARDWARE,
        /** Device has biometric hardware but no biometrics are enrolled */
        NOT_ENROLLED,
        /** Biometric authentication is not available (security update needed, etc.) */
        NOT_AVAILABLE
    }
    
    /**
     * Check if the device supports biometric authentication
     * and whether the user has enrolled at least one biometric.
     */
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
    
    /**
     * Show the system biometric authentication prompt.
     * 
     * @param activity The FragmentActivity required by BiometricPrompt
     * @param title The title shown in the biometric dialog
     * @param subtitle The subtitle shown in the biometric dialog
     * @param description Optional description text
     * @param negativeButtonText Text for the cancel/fallback button
     * @param onSuccess Called when authentication succeeds
     * @param onError Called when authentication fails with an error message
     * @param onFallback Called when the user taps the negative (fallback) button
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
                        // User tapped the "Use Password" button
                        onFallback()
                    }
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> {
                        // User dismissed the dialog
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
                // Don't call onError here — the system will show its own retry UI
                // onAuthenticationError will be called if the user exceeds max attempts
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
