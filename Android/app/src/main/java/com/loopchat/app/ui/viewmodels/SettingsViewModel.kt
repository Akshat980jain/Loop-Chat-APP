package com.loopchat.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.PrivacySecurityRepository
import com.loopchat.app.data.PrivacySettings
import com.loopchat.app.data.SecuritySettings
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.UserDevice
import com.loopchat.app.data.UserSessionInfo
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    
    var privacySettings by mutableStateOf<PrivacySettings>(PrivacySettings())
        private set
        
    var securitySettings by mutableStateOf<SecuritySettings>(SecuritySettings())
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
            securitySettings = settings
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

    fun enableBiometric() {
        viewModelScope.launch {
            val result = PrivacySecurityRepository.enableBiometricLock(SupabaseClient.httpClient)
            result.onSuccess {
                loadSecuritySettings()
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            val result = PrivacySecurityRepository.disableBiometricLock(SupabaseClient.httpClient)
            result.onSuccess {
                loadSecuritySettings()
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
