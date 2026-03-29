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
import com.loopchat.app.data.PrivacySecurityRepository
import com.loopchat.app.data.PrivacySettings
import com.loopchat.app.data.SecuritySettings
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.UserDevice
import com.loopchat.app.data.UserSessionInfo
import kotlinx.coroutines.launch
import android.util.Log

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("loop_chat_security", Context.MODE_PRIVATE)
    
    var privacySettings by mutableStateOf<PrivacySettings>(PrivacySettings())
        private set
        
    var securitySettings by mutableStateOf<SecuritySettings>(
        SecuritySettings(biometric_lock_enabled = application.getSharedPreferences("loop_chat_security", Context.MODE_PRIVATE).getBoolean("biometric_lock_enabled", false))
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
        result.onSuccess { settings ->
            // Update UI state
            securitySettings = settings
            
            // Sync local preference to match server (Source of Truth)
            prefs.edit().putBoolean("biometric_lock_enabled", settings.biometric_lock_enabled).apply()
        }.onFailure {
            // Network failed — use local prefs as fallback
            val localLockEnabled = prefs.getBoolean("biometric_lock_enabled", false)
            securitySettings = securitySettings.copy(
                biometric_lock_enabled = localLockEnabled
            )
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

    fun enableTwoStep(pin: String, email: String?) {
        viewModelScope.launch {
            // In a real app, hash the PIN here
            val result = PrivacySecurityRepository.enableTwoStepVerification(
                SupabaseClient.httpClient, 
                pinHash = pin, // TODO: Hash this
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

    fun clearError() {
        errorMessage = null
    }

    fun enableBiometricLock(activity: FragmentActivity) {
        val status = BiometricAuthManager.canAuthenticate(activity)
        if (status != BiometricAuthManager.BiometricStatus.AVAILABLE) {
            errorMessage = when (status) {
                BiometricAuthManager.BiometricStatus.NO_HARDWARE -> "Your device doesn't support biometric authentication."
                BiometricAuthManager.BiometricStatus.NOT_ENROLLED -> "Please enroll at least one biometric (fingerprint or face) in your device settings first."
                else -> "Biometric authentication is not available on this device."
            }
            return
        }

        val previousState = securitySettings.biometric_lock_enabled
        // Optimistic UI update: flip switch immediately
        securitySettings = securitySettings.copy(biometric_lock_enabled = true)
        
        viewModelScope.launch {
            BiometricAuthManager.authenticate(
                activity = activity,
                title = "Enable Biometric Lock",
                subtitle = "Scan your fingerprint to lock the app",
                onSuccess = {
                    prefs.edit().putBoolean("biometric_lock_enabled", true).apply()
                    viewModelScope.launch {
                        isLoading = true
                        val result = PrivacySecurityRepository.enableBiometricLock(SupabaseClient.httpClient)
                        result.onFailure { e ->
                            // Revert on server failure
                            securitySettings = securitySettings.copy(biometric_lock_enabled = false)
                            prefs.edit().putBoolean("biometric_lock_enabled", false).apply()
                            errorMessage = "Failed to sync security settings: ${e.message}"
                        }
                        isLoading = false
                    }
                },
                onError = { error ->
                    // Revert UI on biometric cancel/error
                    securitySettings = securitySettings.copy(biometric_lock_enabled = previousState)
                    if (error != "User canceled") {
                        errorMessage = "Biometric verification failed: $error"
                    }
                    Log.e("SettingsViewModel", "Biometric error: $error")
                }
            )
        }
    }

    fun disableBiometricLock() {
        // Optimistic update — toggle OFF immediately
        securitySettings = securitySettings.copy(biometric_lock_enabled = false)
        prefs.edit().putBoolean("biometric_lock_enabled", false).apply()
        viewModelScope.launch {
            isLoading = true
            val result = PrivacySecurityRepository.disableBiometricLock(SupabaseClient.httpClient)
            result.onSuccess {
                loadSecuritySettings()
            }.onFailure { e ->
                // Revert
                securitySettings = securitySettings.copy(biometric_lock_enabled = true)
                prefs.edit().putBoolean("biometric_lock_enabled", true).apply()
                errorMessage = "Failed to disable biometric lock: ${e.message}"
            }
            isLoading = false
        }
    }

    fun enableBiometricLogin(activity: FragmentActivity) {
        val status = BiometricAuthManager.canAuthenticate(activity)
        if (status != BiometricAuthManager.BiometricStatus.AVAILABLE) {
            errorMessage = "Biometric authentication is not available on this device."
            return
        }

        val previousState = securitySettings.biometric_login_enabled
        // Optimistic UI update
        securitySettings = securitySettings.copy(biometric_login_enabled = true)

        viewModelScope.launch {
            BiometricAuthManager.authenticate(
                activity = activity,
                title = "Enable Fingerprint Login",
                subtitle = "Scan your fingerprint to link your account",
                onSuccess = {
                    viewModelScope.launch {
                        isLoading = true
                        val result = PrivacySecurityRepository.enableBiometricLogin(SupabaseClient.httpClient)
                        result.onFailure { e ->
                            // Revert
                            securitySettings = securitySettings.copy(biometric_login_enabled = false)
                            errorMessage = "Failed to enable biometric login: ${e.message}"
                        }
                        isLoading = false
                    }
                },
                onError = { error ->
                    // Revert
                    securitySettings = securitySettings.copy(biometric_login_enabled = previousState)
                    if (error != "User canceled") {
                        errorMessage = "Biometric verification failed: $error"
                    }
                }
            )
        }
    }

    fun disableBiometricLogin() {
        // Optimistic update
        securitySettings = securitySettings.copy(biometric_login_enabled = false)
        viewModelScope.launch {
            isLoading = true
            val result = PrivacySecurityRepository.disableBiometricLogin(SupabaseClient.httpClient)
            result.onSuccess {
                loadSecuritySettings()
            }.onFailure { e ->
                // Revert
                securitySettings = securitySettings.copy(biometric_login_enabled = true)
                errorMessage = "Failed to disable biometric login: ${e.message}"
            }
            isLoading = false
        }
    }

    fun toggleSecurityNotifications(enabled: Boolean) {
        // Optimistic update
        securitySettings = securitySettings.copy(security_notifications_enabled = enabled)
        viewModelScope.launch {
            val result = PrivacySecurityRepository.updateSecurityNotifications(SupabaseClient.httpClient, enabled)
            result.onFailure {
                // Revert on error
                securitySettings = securitySettings.copy(security_notifications_enabled = !enabled)
                errorMessage = "Failed to update notifications setting"
            }
        }
    }

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
}
