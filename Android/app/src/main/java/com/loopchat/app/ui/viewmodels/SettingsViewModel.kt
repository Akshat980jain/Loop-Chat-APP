package com.loopchat.app.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import com.loopchat.app.data.BiometricAuthManager
import com.loopchat.app.data.BiometricCredentialStore
import com.loopchat.app.data.PrivacySecurityRepository
import com.loopchat.app.data.PrivacySettings
import com.loopchat.app.data.SecuritySettings
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.PasskeyManager
import com.loopchat.app.data.UserDevice
import com.loopchat.app.data.UserSessionInfo
import kotlinx.coroutines.launch
import android.util.Log

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("loop_chat_security", Context.MODE_PRIVATE)

    // ─── Biometric helpers (SharedPrefs as device-local source of truth) ───────

    /**
     * Biometric settings are DEVICE-LOCAL by nature.
     * SharedPreferences is the primary source of truth.
     * Supabase is a best-effort backup — its failure must NEVER revert the toggle.
     */
    private fun localBiometricLockEnabled(): Boolean =
        prefs.getBoolean("biometric_lock_enabled", false)

    private fun localBiometricLoginEnabled(): Boolean =
        prefs.getBoolean("biometric_login_enabled", false)

    private fun saveLocalBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_lock_enabled", enabled).apply()
        Log.d("SettingsVM", "Local biometric_lock_enabled saved: $enabled")
    }

    private fun saveLocalBiometricLogin(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_login_enabled", enabled).apply()
        Log.d("SettingsVM", "Local biometric_login_enabled saved: $enabled")
    }

    // ─── Observable state ─────────────────────────────────────────────────────

    var privacySettings by mutableStateOf<PrivacySettings>(PrivacySettings())
        private set

    /**
     * Initialise from local prefs so the toggles look correct immediately,
     * before the Supabase network call completes.
     */
    var securitySettings by mutableStateOf(
        SecuritySettings(
            biometric_lock_enabled  = application
                .getSharedPreferences("loop_chat_security", Context.MODE_PRIVATE)
                .getBoolean("biometric_lock_enabled", false),
            biometric_login_enabled = application
                .getSharedPreferences("loop_chat_security", Context.MODE_PRIVATE)
                .getBoolean("biometric_login_enabled", false)
        )
    )
        private set

    var devices by mutableStateOf<List<UserDevice>>(emptyList())
        private set

    var sessions by mutableStateOf<List<UserSessionInfo>>(emptyList())
        private set

    var blockedUsers by mutableStateOf<List<String>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────────────────

    fun loadAllSettings() {
        viewModelScope.launch {
            isLoading = true
            loadPrivacySettings()
            loadSecuritySettings()
            loadDevices()
            loadActiveSessions()
            loadBlockedUsers()
            isLoading = false
        }
    }

    private suspend fun loadPrivacySettings() {
        val result = PrivacySecurityRepository.getPrivacySettings(SupabaseClient.httpClient)
        result.onSuccess { settings ->
            privacySettings = settings
        }
    }

    private suspend fun loadSecuritySettings() {
        val result = PrivacySecurityRepository.getSecuritySettings(SupabaseClient.httpClient)
        result.onSuccess { serverSettings ->
            // Biometric fields are device-local: local prefs WIN over server value.
            // All other fields (2FA etc.) come from server.
            val mergedSettings = serverSettings.copy(
                biometric_lock_enabled  = localBiometricLockEnabled(),
                biometric_login_enabled = localBiometricLoginEnabled()
            )
            securitySettings = mergedSettings
            Log.d("SettingsVM", "Security settings loaded. " +
                "lock=${mergedSettings.biometric_lock_enabled} " +
                "login=${mergedSettings.biometric_login_enabled}")
        }.onFailure { e ->
            Log.w("SettingsVM", "Failed to load security settings from server: ${e.message}")
            // Network failed — keep current state (already seeded from local prefs)
        }
    }

    private suspend fun loadDevices() {
        val result = PrivacySecurityRepository.getUserDevices(SupabaseClient.httpClient)
        result.onSuccess { userDevices ->
            devices = userDevices
        }
    }

    private suspend fun loadActiveSessions() {
        val result = SupabaseClient.getActiveSessions()
        result.onSuccess { activeSessions ->
            sessions = activeSessions
        }.onFailure { e ->
            errorMessage = "Failed to load sessions: ${e.message}"
        }
    }

    private suspend fun loadBlockedUsers() {
        val result = PrivacySecurityRepository.getBlockedUsers(SupabaseClient.httpClient)
        result.onSuccess { blocked ->
            blockedUsers = blocked
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVACY
    // ─────────────────────────────────────────────────────────────────────────

    fun updatePrivacySettings(settings: PrivacySettings) {
        viewModelScope.launch {
            val result = PrivacySecurityRepository.updatePrivacySettings(SupabaseClient.httpClient, settings)
            result.onSuccess { updated ->
                privacySettings = updated
            }.onFailure { e ->
                errorMessage = "Failed to update privacy settings: ${e.message}"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TWO-STEP VERIFICATION
    // ─────────────────────────────────────────────────────────────────────────

    fun enableTwoStep(pin: String, email: String?) {
        viewModelScope.launch {
            val result = PrivacySecurityRepository.enableTwoStepVerification(
                SupabaseClient.httpClient,
                pinHash = pin,
                email = email
            )
            result.onSuccess {
                loadSecuritySettings()
            }.onFailure { e ->
                errorMessage = "Failed to enable 2FA: ${e.message}"
            }
        }
    }

    fun disableTwoStep() {
        viewModelScope.launch {
            val result = PrivacySecurityRepository.disableTwoStepVerification(SupabaseClient.httpClient)
            result.onSuccess {
                loadSecuritySettings()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BIOMETRIC APP LOCK
    // ─────────────────────────────────────────────────────────────────────────

    fun enableBiometricLock(activity: FragmentActivity) {
        val status = BiometricAuthManager.canAuthenticate(activity)
        if (status != BiometricAuthManager.BiometricStatus.AVAILABLE) {
            errorMessage = when (status) {
                BiometricAuthManager.BiometricStatus.NO_HARDWARE ->
                    "Your device doesn't support biometric authentication."
                BiometricAuthManager.BiometricStatus.NOT_ENROLLED ->
                    "Please enroll at least one fingerprint in your device Settings → Biometrics first."
                else ->
                    "Biometric authentication is not available on this device."
            }
            return
        }

        // Show the biometric prompt. Do NOT do an optimistic update before
        // the user actually authenticates — that's what caused the flash.
        BiometricAuthManager.authenticate(
            activity          = activity,
            title             = "Enable App Lock",
            subtitle          = "Confirm your fingerprint to enable App Lock",
            negativeButtonText = "Cancel",
            onSuccess = {
                // 1. Persist locally FIRST — this is the source of truth.
                saveLocalBiometricLock(true)
                // 2. Update UI state.
                securitySettings = securitySettings.copy(biometric_lock_enabled = true)
                Log.d("SettingsVM", "App Lock ENABLED locally")
                // 3. Best-effort server sync (failure does NOT revert the toggle).
                viewModelScope.launch {
                    val result = PrivacySecurityRepository.enableBiometricLock(SupabaseClient.httpClient)
                    result.onFailure { e ->
                        Log.w("SettingsVM", "Server sync for biometric lock failed (non-fatal): ${e.message}")
                        // Do NOT revert — local prefs already have the correct value.
                    }
                }
            },
            onError = { error ->
                Log.d("SettingsVM", "Biometric prompt cancelled/error for lock enable: $error")
                // User cancelled — do nothing, toggle stays as-is.
            }
        )
    }

    fun disableBiometricLock() {
        // 1. Persist locally FIRST.
        saveLocalBiometricLock(false)
        // 2. Update UI.
        securitySettings = securitySettings.copy(biometric_lock_enabled = false)
        Log.d("SettingsVM", "App Lock DISABLED locally")
        // 3. Best-effort server sync.
        viewModelScope.launch {
            val result = PrivacySecurityRepository.disableBiometricLock(SupabaseClient.httpClient)
            result.onFailure { e ->
                Log.w("SettingsVM", "Server sync for biometric lock disable failed (non-fatal): ${e.message}")
                // Do NOT revert.
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BIOMETRIC LOGIN
    // ─────────────────────────────────────────────────────────────────────────

    fun enableBiometricLogin(activity: FragmentActivity) {
        val status = BiometricAuthManager.canAuthenticate(activity)
        if (status != BiometricAuthManager.BiometricStatus.AVAILABLE) {
            errorMessage = when (status) {
                BiometricAuthManager.BiometricStatus.NO_HARDWARE ->
                    "Your device doesn't support biometric authentication."
                BiometricAuthManager.BiometricStatus.NOT_ENROLLED ->
                    "Please enroll at least one fingerprint in your device Settings → Biometrics first."
                else ->
                    "Biometric authentication is not available on this device."
            }
            return
        }

        // Generate a fresh Keystore key for this enrollment
        BiometricAuthManager.generateKey()

        BiometricAuthManager.authenticateForEncryption(
            activity           = activity,
            title              = "Enable Fingerprint Login",
            subtitle           = "Confirm your fingerprint to enable login with fingerprint",
            negativeButtonText = "Cancel",
            onResult = { result ->
                when (result) {
                    is BiometricAuthManager.CryptoAuthResult.Success -> {
                        // 1. Persist preference locally FIRST — this is the source of truth.
                        saveLocalBiometricLogin(true)
                        // 2. Update UI state.
                        securitySettings = securitySettings.copy(biometric_login_enabled = true)
                        Log.d("SettingsVM", "Fingerprint Login ENABLED locally (crypto-bound)")

                        // 3. Store encrypted credentials so the LOGIN SCREEN fingerprint button works.
                        viewModelScope.launch {
                            val refreshToken = SupabaseClient.getRefreshToken(getApplication())
                            val userId       = SupabaseClient.currentUserId
                            val email        = SupabaseClient.currentEmail
                            if (refreshToken != null && userId != null) {
                                BiometricCredentialStore.storeCredentials(
                                    context      = getApplication(),
                                    cipher       = result.cipher,
                                    userId       = userId,
                                    email        = email ?: "",
                                    refreshToken = refreshToken
                                )
                                Log.d("SettingsVM", "Biometric credentials stored (crypto-bound)")
                            } else {
                                Log.w("SettingsVM", "Could not store credentials: refreshToken or userId is null")
                            }

                            // 4. Best-effort server sync (failure does NOT revert the toggle).
                            val serverResult = PrivacySecurityRepository.enableBiometricLogin(SupabaseClient.httpClient)
                            serverResult.onFailure { e ->
                                Log.w("SettingsVM", "Server sync for biometric login failed (non-fatal): ${e.message}")
                            }
                        }
                    }
                    is BiometricAuthManager.CryptoAuthResult.Cancelled -> {
                        Log.d("SettingsVM", "Biometric enrollment cancelled by user")
                        BiometricAuthManager.deleteKey()
                        // User cancelled — toggle stays OFF.
                    }
                    is BiometricAuthManager.CryptoAuthResult.Error -> {
                        Log.d("SettingsVM", "Biometric enrollment error: ${result.message}")
                        BiometricAuthManager.deleteKey()
                        errorMessage = result.message
                    }
                    is BiometricAuthManager.CryptoAuthResult.KeyInvalidated -> {
                        Log.w("SettingsVM", "Key invalidated during enrollment — should not happen")
                        errorMessage = "Biometric setup failed. Please try again."
                    }
                }
            }
        )
    }

    fun disableBiometricLogin() {
        // 1. Clear encrypted credentials AND delete the Keystore key.
        BiometricCredentialStore.clearCredentials(getApplication())
        // 2. Persist preference locally.
        saveLocalBiometricLogin(false)
        // 3. Update UI.
        securitySettings = securitySettings.copy(biometric_login_enabled = false)
        Log.d("SettingsVM", "Fingerprint Login DISABLED — credentials and key cleared")
        // 4. Best-effort server sync.
        viewModelScope.launch {
            val result = PrivacySecurityRepository.disableBiometricLogin(SupabaseClient.httpClient)
            result.onFailure { e ->
                Log.w("SettingsVM", "Server sync for biometric login disable failed (non-fatal): ${e.message}")
                // Do NOT revert.
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECURITY NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    fun toggleSecurityNotifications(enabled: Boolean) {
        securitySettings = securitySettings.copy(security_notifications_enabled = enabled)
        viewModelScope.launch {
            val result = PrivacySecurityRepository.updateSecurityNotifications(SupabaseClient.httpClient, enabled)
            result.onFailure {
                securitySettings = securitySettings.copy(security_notifications_enabled = !enabled)
                errorMessage = "Failed to update notifications setting"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEVICE / SESSION MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            val result = PrivacySecurityRepository.removeDevice(SupabaseClient.httpClient, deviceId)
            result.onSuccess {
                loadDevices()
            }
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            val result = PrivacySecurityRepository.unblockUser(SupabaseClient.httpClient, userId)
            result.onSuccess {
                loadBlockedUsers()
            }
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            isLoading = true
            val result = SupabaseClient.revokeSession(sessionId)
            result.onSuccess {
                loadActiveSessions()
            }.onFailure { e ->
                errorMessage = "Failed to revoke session: ${e.message}"
            }
            isLoading = false
        }
    }

    fun revokeAllOtherSessions() {
        viewModelScope.launch {
            isLoading = true
            val result = SupabaseClient.revokeAllOtherSessions()
            result.onSuccess {
                loadActiveSessions()
            }.onFailure { e ->
                errorMessage = "Failed to revoke sessions: ${e.message}"
            }
            isLoading = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASSKEY MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /** Whether a passkey registration is in progress */
    var passkeyRegistrationInProgress by mutableStateOf(false)
        private set

    /**
     * Register a new Passkey for the current user.
     * This enables biometric login on other devices via Google Account sync.
     */
    fun registerPasskey(activity: FragmentActivity) {
        passkeyRegistrationInProgress = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            val result = PasskeyManager.registerPasskey(
                context = activity,
                httpClient = SupabaseClient.httpClient
            )

            passkeyRegistrationInProgress = false

            when (result) {
                is PasskeyManager.RegistrationResult.Success -> {
                    Log.d("SettingsVM", "Passkey registered successfully")
                    // Show success message
                    successMessage = "Passkey registered successfully! You can now use your fingerprint to log into this account from any device."
                    errorMessage = null
                }
                is PasskeyManager.RegistrationResult.Cancelled -> {
                    Log.d("SettingsVM", "Passkey registration cancelled by user")
                }
                is PasskeyManager.RegistrationResult.Error -> {
                    Log.e("SettingsVM", "Passkey registration failed: ${result.message}")
                    errorMessage = result.message
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun clearError() {
        errorMessage = null
    }

    fun clearMessages() {
        errorMessage = null
        successMessage = null
    }
}
