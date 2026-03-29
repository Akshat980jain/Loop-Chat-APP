package com.loopchat.app.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.AuthResult
import com.loopchat.app.data.BiometricAuthManager
import com.loopchat.app.data.BiometricCredentialStore
import com.loopchat.app.data.PrivacySecurityRepository
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
    
    companion object {
        private const val TAG = "AuthViewModel"
    }
    
    var authView by mutableStateOf(AuthView.LOGIN)
        private set
    
    var loginMethod by mutableStateOf(LoginMethod.EMAIL)
        private set
    
    var formState by mutableStateOf(AuthFormState())
        private set
        
    // OTP State
    var isOtpSent by mutableStateOf(false)
        private set
        
    var otpCode by mutableStateOf("")
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
    
    // ============================================
    // BIOMETRIC AUTHENTICATION STATE
    // ============================================
    
    /** Whether the device has biometric hardware with enrolled biometrics */
    var isBiometricAvailable by mutableStateOf(false)
        private set
    
    /** Whether the user has opted-in to biometric login (credentials stored) */
    var isBiometricEnrolled by mutableStateOf(false)
        private set
    
    /** Show the enrollment dialog after a successful password login */
    var isBiometricEnrollDialogVisible by mutableStateOf(false)
        private set
    
    /** Loading state while biometric login is in progress */
    var biometricLoginInProgress by mutableStateOf(false)
        private set
    
    /** Whether the auto-prompt has already been shown this session */
    var hasAutoPrompted by mutableStateOf(false)
        private set

    /** Pending success action to defer navigation until after dialog */
    private var pendingAuthSuccessAction: (() -> Unit)? = null
    
    // ============================================
    // BIOMETRIC METHODS
    // ============================================
    
    /**
     * Check biometric availability and enrollment status.
     * Should be called once when the auth screen loads.
     */
    fun checkBiometricStatus(activity: FragmentActivity) {
        val status = BiometricAuthManager.canAuthenticate(activity)
        isBiometricAvailable = (status == BiometricAuthManager.BiometricStatus.AVAILABLE)
        isBiometricEnrolled = isBiometricAvailable && 
            BiometricCredentialStore.hasStoredCredentials(activity)
        
        Log.d(TAG, "Biometric status: available=$isBiometricAvailable, enrolled=$isBiometricEnrolled")
    }
    
    /**
     * Attempt to login using stored biometric credentials.
     * This triggers the system biometric prompt, then uses the stored
     * refresh token to create a new session.
     */
    fun attemptBiometricLogin(
        activity: FragmentActivity,
        onSuccess: () -> Unit
    ) {
        if (!isBiometricEnrolled) {
            errorMessage = "Biometric login is not set up"
            return
        }
        
        biometricLoginInProgress = true
        errorMessage = null
        
        BiometricAuthManager.authenticate(
            activity = activity,
            title = "Loop Chat",
            subtitle = "Use your fingerprint to sign in",
            negativeButtonText = "Use Password",
            onSuccess = {
                // Biometric verified — now use the stored refresh token
                viewModelScope.launch {
                    performBiometricLogin(activity, onSuccess)
                }
            },
            onError = { error ->
                biometricLoginInProgress = false
                errorMessage = error
            },
            onFallback = {
                biometricLoginInProgress = false
                // User chose to use password — do nothing, they see the form
            }
        )
    }
    
    /**
     * After biometric verification, decrypt the stored refresh token
     * and use it to create a new Supabase session.
     */
    private suspend fun performBiometricLogin(
        context: Context,
        onSuccess: () -> Unit
    ) {
        val credentials = BiometricCredentialStore.getStoredCredentials(context)
        
        if (credentials == null) {
            biometricLoginInProgress = false
            errorMessage = "Stored credentials not found. Please sign in with password."
            isBiometricEnrolled = false
            BiometricCredentialStore.clearCredentials(context)
            return
        }
        
        isLoading = true
        
        val refreshed = SupabaseClient.refreshSession(context, credentials.refreshToken)
        
        if (refreshed) {
            Log.d(TAG, "Biometric login successful")
            
            // Update the stored refresh token with the new one
            val newRefreshToken = SupabaseClient.getRefreshToken(context)
            if (newRefreshToken != null) {
                BiometricCredentialStore.updateRefreshToken(context, newRefreshToken)
            }
            
            // Reset failed attempts
            failedAttempts = 0
            lockoutUntil = null
            lockoutCountdown = 0
            lockoutJob?.cancel()
            
            // Upload FCM token for the new session
            uploadFcmToken(context)
            
            // Track session
            SupabaseClient.trackSession(context)
            
            isLoading = false
            biometricLoginInProgress = false
            onSuccess()
        } else {
            Log.w(TAG, "Biometric login failed — refresh token expired")
            isLoading = false
            biometricLoginInProgress = false
            errorMessage = "Session expired. Please sign in with your password."
            
            // Clear invalid credentials
            BiometricCredentialStore.clearCredentials(context)
            isBiometricEnrolled = false
        }
    }
    
    /**
     * Enable biometric login after a successful password sign-in.
     * Triggers the biometric prompt for enrollment confirmation,
     * then stores the current refresh token encrypted.
     */
    fun enableBiometricLogin(activity: FragmentActivity) {
        BiometricAuthManager.authenticate(
            activity = activity,
            title = "Enable Biometric Login",
            subtitle = "Verify your fingerprint to enable quick login",
            negativeButtonText = "Cancel",
            onSuccess = {
                viewModelScope.launch {
                    val refreshToken = SupabaseClient.getRefreshToken(activity)
                    val userId = SupabaseClient.currentUserId
                    val email = SupabaseClient.currentEmail
                    
                    if (refreshToken != null && userId != null) {
                        BiometricCredentialStore.storeCredentials(
                            context = activity,
                            userId = userId,
                            email = email ?: "",
                            refreshToken = refreshToken
                        )
                        
                        // Update backend setting
                        PrivacySecurityRepository.enableBiometricLogin(SupabaseClient.httpClient)
                        
                        isBiometricEnrolled = true
                        isBiometricEnrollDialogVisible = false
                        Log.d(TAG, "Biometric login enabled successfully")
                        pendingAuthSuccessAction?.invoke()
                        pendingAuthSuccessAction = null
                    } else {
                        errorMessage = "Failed to enable biometric login"
                        isBiometricEnrollDialogVisible = false
                        pendingAuthSuccessAction?.invoke()
                        pendingAuthSuccessAction = null
                    }
                }
            },
            onError = { error ->
                Log.w(TAG, "Biometric enrollment failed: $error")
                isBiometricEnrollDialogVisible = false
                pendingAuthSuccessAction?.invoke()
                pendingAuthSuccessAction = null
            },
            onFallback = {
                isBiometricEnrollDialogVisible = false
                pendingAuthSuccessAction?.invoke()
                pendingAuthSuccessAction = null
            }
        )
    }
    
    /**
     * Disable biometric login and clear stored credentials.
     */
    fun disableBiometricLogin(context: Context) {
        viewModelScope.launch {
            BiometricCredentialStore.clearCredentials(context)
            PrivacySecurityRepository.disableBiometricLogin(SupabaseClient.httpClient)
            isBiometricEnrolled = false
            Log.d(TAG, "Biometric login disabled")
        }
    }
    
    /**
     * Dismiss the biometric enrollment dialog without enabling.
     */
    fun dismissBiometricEnrollDialog() {
        isBiometricEnrollDialogVisible = false
        pendingAuthSuccessAction?.invoke()
        pendingAuthSuccessAction = null
    }
    
    /**
     * Show the biometric enrollment dialog (called from UI when
     * fingerprint button tapped but user hasn't enrolled yet).
     */
    fun showBiometricEnrollDialog() {
        isBiometricEnrollDialogVisible = true
    }
    
    /**
     * Mark auto-prompt as shown so it doesn't re-trigger.
     */
    fun markAutoPrompted() {
        hasAutoPrompted = true
    }
    
    /**
     * Show a custom error message
     */
    fun showError(message: String) {
        errorMessage = message
    }
    
    // ============================================
    // EXISTING METHODS (unchanged logic)
    // ============================================
    
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
        isOtpSent = false
        otpCode = ""
        formState = formState.copy(password = "") // Clear password
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
    
    fun updateOtpCode(code: String) {
        val filtered = code.filter { it.isDigit() }
        if (filtered.length <= 6) {
            otpCode = filtered
        }
    }
    
    fun togglePasswordVisibility() {
        showPassword = !showPassword
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    private fun formatE164(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (phone.startsWith("+")) {
            "+" + digits
        } else {
            // Default to no prefix if they didn't provide one, 
            // but validateLogin will catch if it's missing entirely
            digits
        }
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
                val phone = formState.phone
                when {
                    phone.isBlank() -> "Phone number is required"
                    !phone.startsWith("+") -> "Enter country code starting with + (e.g., +91...)"
                    phone.length < 10 -> "Phone number is too short"
                    else -> null
                }
            }
        }
    }
    
    private fun validateSignup(): String? {
        val phone = formState.phone
        return when {
            formState.fullName.length < 2 -> "Name must be at least 2 characters"
            formState.email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(formState.email).matches() -> "Invalid email address"
            phone.isBlank() -> "Phone number is required"
            !phone.startsWith("+") -> "Include '+' and country code (e.g., +91)"
            phone.length < 10 -> "Invalid phone number"
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
            
            val cleanedPhone = formatE164(formState.phone)
            Log.d(TAG, "Attempting login with phone: $cleanedPhone")

            val result = when (loginMethod) {
                LoginMethod.EMAIL -> SupabaseClient.signInWithEmail(
                    formState.email,
                    formState.password,
                    context
                )
                LoginMethod.PHONE -> SupabaseClient.signInWithPhone(
                    cleanedPhone,
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
                    
                    // Upload FCM token
                    uploadFcmToken(context)
                    
                    // Check if we should prompt for biometric enrollment
                    if (isBiometricAvailable && !isBiometricEnrolled) {
                        pendingAuthSuccessAction = onSuccess
                        isBiometricEnrollDialogVisible = true
                    } else {
                        onSuccess()
                    }
                }
                is AuthResult.Error -> {
                    errorMessage = result.message
                    handleFailedAttempt()
                }
            }
            
            isLoading = false
        }
    }
    
    fun sendOtp() {
        val phone = formState.phone
        if (phone.isBlank() || !phone.startsWith("+") || phone.length < 10) {
            errorMessage = "Enter country code starting with + (e.g., +91...)"
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            val cleanedPhone = formatE164(phone)
            Log.d(TAG, "Sending OTP to: $cleanedPhone")
            
            val result = SupabaseClient.sendPhoneOtp(cleanedPhone)
            
            if (result is AuthResult.Success) {
                isOtpSent = true
                otpCode = ""
                errorMessage = null
            } else if (result is AuthResult.Error) {
                errorMessage = result.message
                Log.e(TAG, "OTP Send failed: ${result.message}")
            }
            
            isLoading = false
        }
    }
    
    fun verifyOtp(context: Context, onSuccess: () -> Unit) {
        if (otpCode.length < 6) {
            errorMessage = "Please enter a 6-digit code"
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            val cleanedPhone = formatE164(formState.phone)
            Log.d(TAG, "Verifying OTP for: $cleanedPhone")
            
            val result = SupabaseClient.verifyPhoneOtp(cleanedPhone, otpCode, context)
            
            when (result) {
                is AuthResult.Success -> {
                    failedAttempts = 0
                    lockoutUntil = null
                    lockoutCountdown = 0
                    lockoutJob?.cancel()
                    
                    uploadFcmToken(context)
                    
                    if (isBiometricAvailable && !isBiometricEnrolled) {
                        pendingAuthSuccessAction = onSuccess
                        isBiometricEnrollDialogVisible = true
                    } else {
                        onSuccess()
                    }
                }
                is AuthResult.Error -> {
                    errorMessage = result.message
                    Log.w(TAG, "OTP Verification failed: ${result.message}")
                }
            }
            
            isLoading = false
        }
    }
    
    /**
     * Helper to upload FCM token after login.
     */
    private fun uploadFcmToken(context: Context) {
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
            Log.e(TAG, "Failed to upload FCM token", e)
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
                    
                    // Upload FCM token
                    uploadFcmToken(context)
                    
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
