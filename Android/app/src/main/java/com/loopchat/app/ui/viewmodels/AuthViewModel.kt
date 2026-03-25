package com.loopchat.app.ui.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.AuthResult
import com.loopchat.app.data.SupabaseClient
import kotlinx.coroutines.launch

enum class AuthView {
    LOGIN, SIGNUP
}

enum class LoginMethod {
    EMAIL, PHONE
}

data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val phone: String = ""
)

class AuthViewModel : ViewModel() {
    
    var authView by mutableStateOf(AuthView.LOGIN)
        private set
    
    var loginMethod by mutableStateOf(LoginMethod.EMAIL)
        private set
    
    var formState by mutableStateOf(AuthFormState())
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var showPassword by mutableStateOf(false)
        private set
    
    // Rate limiting state
    var failedAttempts by mutableStateOf(0)
        private set
    
    var lockoutUntil by mutableStateOf<Long?>(null)
        private set
    
    var lockoutCountdown by mutableStateOf(0)
        private set
    
    private var lockoutJob: kotlinx.coroutines.Job? = null
    
    val isLockedOut: Boolean
        get() = lockoutUntil?.let { it > System.currentTimeMillis() } ?: false
    
    private fun startLockoutTimer() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            while (lockoutUntil != null && lockoutUntil!! > System.currentTimeMillis()) {
                lockoutCountdown = ((lockoutUntil!! - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                kotlinx.coroutines.delay(1000)
            }
            lockoutCountdown = 0
            lockoutUntil = null
        }
    }
    
    private fun handleFailedAttempt() {
        failedAttempts++
        if (failedAttempts >= 5) {
            lockoutUntil = System.currentTimeMillis() + 30_000
            lockoutCountdown = 30
            startLockoutTimer()
        } else if (failedAttempts >= 3) {
            lockoutUntil = System.currentTimeMillis() + 10_000
            lockoutCountdown = 10
            startLockoutTimer()
        }
    }
    
    fun switchView(view: AuthView) {
        authView = view
        errorMessage = null
    }
    
    fun switchLoginMethod(method: LoginMethod) {
        loginMethod = method
        errorMessage = null
    }
    
    fun updateEmail(email: String) {
        formState = formState.copy(email = email)
    }
    
    fun updatePassword(password: String) {
        formState = formState.copy(password = password)
    }
    
    fun updateConfirmPassword(confirmPassword: String) {
        formState = formState.copy(confirmPassword = confirmPassword)
    }
    
    fun updateFullName(fullName: String) {
        formState = formState.copy(fullName = fullName)
    }
    
    fun updatePhone(phone: String) {
        // Only allow digits and + sign
        val filtered = phone.filter { it.isDigit() || it == '+' }
        formState = formState.copy(phone = filtered)
    }
    
    fun togglePasswordVisibility() {
        showPassword = !showPassword
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    private fun validateLogin(): String? {
        return when (loginMethod) {
            LoginMethod.EMAIL -> {
                when {
                    formState.email.isBlank() -> "Email is required"
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(formState.email).matches() -> "Invalid email address"
                    formState.password.length < 6 -> "Password must be at least 6 characters"
                    else -> null
                }
            }
            LoginMethod.PHONE -> {
                when {
                    formState.phone.isBlank() -> "Phone number is required"
                    formState.phone.length < 10 -> "Invalid phone number"
                    formState.password.length < 6 -> "Password must be at least 6 characters"
                    else -> null
                }
            }
        }
    }
    
    private fun validateSignup(): String? {
        return when {
            formState.fullName.length < 2 -> "Name must be at least 2 characters"
            formState.email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(formState.email).matches() -> "Invalid email address"
            formState.phone.isBlank() -> "Phone number is required"
            formState.phone.length < 10 -> "Invalid phone number"
            formState.password.length < 6 -> "Password must be at least 6 characters"
            formState.password != formState.confirmPassword -> "Passwords don't match"
            else -> null
        }
    }
    
    fun login(context: Context, onSuccess: () -> Unit) {
        // Check lockout
        if (isLockedOut) {
            errorMessage = "Too many failed attempts. Wait ${lockoutCountdown}s."
            return
        }
        
        val validationError = validateLogin()
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            val result = when (loginMethod) {
                LoginMethod.EMAIL -> SupabaseClient.signInWithEmail(
                    formState.email,
                    formState.password,
                    context
                )
                LoginMethod.PHONE -> SupabaseClient.signInWithPhone(
                    formState.phone,
                    formState.password,
                    context
                )
            }
            
            when (result) {
                is AuthResult.Success -> {
                    failedAttempts = 0
                    lockoutUntil = null
                    lockoutCountdown = 0
                    lockoutJob?.cancel()
                    
                    // Trigger FCM upload for new login
                    try {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.loopchat.app.data.SupabaseClient.updateFcmToken(task.result)
                                    com.loopchat.app.data.PrivacySecurityRepository.registerDevice(
                                        httpClient = com.loopchat.app.data.SupabaseClient.httpClient,
                                        deviceName = android.os.Build.MODEL ?: "Android Device",
                                        deviceType = "android",
                                        deviceToken = task.result
                                    )
                                    
                                    // Upload E2EE public key for this device
                                    val deviceId = android.provider.Settings.Secure.getString(
                                        context.contentResolver,
                                        android.provider.Settings.Secure.ANDROID_ID
                                    )
                                    com.loopchat.app.data.KeyExchangeRepository.uploadPublicKey(deviceId)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthViewModel", "Failed to upload FCM token", e)
                    }
                    
                    onSuccess()
                }
                is AuthResult.Error -> {
                    errorMessage = result.message
                    handleFailedAttempt()
                }
            }
            
            isLoading = false
        }
    }
    
    fun signUp(context: Context, onSuccess: () -> Unit) {
        val validationError = validateSignup()
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            val result = SupabaseClient.signUp(
                email = formState.email,
                password = formState.password,
                fullName = formState.fullName,
                phone = formState.phone,
                context = context
            )
            
            when (result) {
                is AuthResult.Success -> {
                    // Clear form and switch to login view
                    formState = AuthFormState(email = formState.email)
                    authView = AuthView.LOGIN
                    errorMessage = null
                    
                    // Trigger FCM upload for new signup
                    try {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.loopchat.app.data.SupabaseClient.updateFcmToken(task.result)
                                    com.loopchat.app.data.PrivacySecurityRepository.registerDevice(
                                        httpClient = com.loopchat.app.data.SupabaseClient.httpClient,
                                        deviceName = android.os.Build.MODEL ?: "Android Device",
                                        deviceType = "android",
                                        deviceToken = task.result
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthViewModel", "Failed to upload FCM token", e)
                    }
                    
                    onSuccess()
                }
                is AuthResult.Error -> errorMessage = result.message
            }
            
            isLoading = false
        }
    }
    
    fun resetForm() {
        formState = AuthFormState()
        errorMessage = null
    }
}
