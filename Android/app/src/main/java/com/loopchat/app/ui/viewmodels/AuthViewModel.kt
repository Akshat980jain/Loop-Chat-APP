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
import com.loopchat.app.data.PasskeyManager
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

    /**
     * True when the Keystore key has been invalidated because the device's
     * biometric enrollment changed (fingerprints added/removed). The UI
     * should show a message asking the user to sign in with their password
     * to re-enroll fingerprint login.
     */
    var isBiometricKeyInvalidated by mutableStateOf(false)
        private set

    /** Pending success action to defer navigation until after dialog */
    private var pendingAuthSuccessAction: (() -> Unit)? = null
    
    // ============================================
    // BIOMETRIC METHODS
    // ============================================
    
    /**
     * Check biometric availability, enrollment status, and key validity.
     * Should be called once when the auth screen loads.
     */
    fun checkBiometricStatus(activity: FragmentActivity) {
        val status = BiometricAuthManager.canAuthenticate(activity)
        isBiometricAvailable = (status == BiometricAuthManager.BiometricStatus.AVAILABLE)
        
        val hasCredentials = isBiometricAvailable && 
            BiometricCredentialStore.hasStoredCredentials(activity)
        
        if (hasCredentials) {
            // Check if the Keystore key is still valid (biometrics unchanged)
            val keyValid = BiometricAuthManager.isKeyValid()
            if (keyValid) {
                isBiometricEnrolled = true
                isBiometricKeyInvalidated = false
            } else {
                // Key invalidated — biometric enrollment changed on device
                isBiometricEnrolled = false
                isBiometricKeyInvalidated = true
                Log.w(TAG, "Biometric key invalidated — fingerprints changed on device")
                // Clear stale credentials
                BiometricCredentialStore.clearCredentials(activity)
            }
        } else {
            isBiometricEnrolled = false
        }
        
        Log.d(TAG, "Biometric status: available=$isBiometricAvailable, enrolled=$isBiometricEnrolled, keyInvalidated=$isBiometricKeyInvalidated")
    }
    
    /**
     * Attempt to login using stored biometric credentials.
     *
     * This triggers a **crypto-based** biometric prompt: the biometric scan
     * unlocks the Keystore key, which decrypts the stored Supabase refresh
     * token. If the device's biometric enrollment has changed since the user
     * enrolled in Loop Chat, the key will be permanently invalidated and the
     * user must re-enter their password.
     */
    fun attemptBiometricLogin(
        activity: FragmentActivity,
        onSuccess: () -> Unit
    ) {
        if (!isBiometricEnrolled) {
            errorMessage = "Biometric login is not set up"
            return
        }
        
        val iv = BiometricCredentialStore.getStoredIv(activity)
        if (iv == null) {
            errorMessage = "Biometric data corrupted. Please sign in with password."
            isBiometricEnrolled = false
            BiometricCredentialStore.clearCredentials(activity)
            return
        }
        
        biometricLoginInProgress = true
        errorMessage = null
        
        BiometricAuthManager.authenticateForDecryption(
            activity = activity,
            iv = iv,
            title = "Loop Chat",
            subtitle = "Use your fingerprint to sign in",
            negativeButtonText = "Use Password",
            onResult = { result ->
                when (result) {
                    is BiometricAuthManager.CryptoAuthResult.Success -> {
                        // Biometric verified & Cipher unlocked — decrypt credentials
                        viewModelScope.launch {
                            performBiometricLogin(activity, result.cipher, onSuccess)
                        }
                    }
                    is BiometricAuthManager.CryptoAuthResult.Cancelled -> {
                        biometricLoginInProgress = false
                        // User chose password — do nothing
                    }
                    is BiometricAuthManager.CryptoAuthResult.Error -> {
                        biometricLoginInProgress = false
                        errorMessage = result.message
                    }
                    is BiometricAuthManager.CryptoAuthResult.KeyInvalidated -> {
                        biometricLoginInProgress = false
                        isBiometricEnrolled = false
                        isBiometricKeyInvalidated = true
                        BiometricCredentialStore.clearCredentials(activity)
                        errorMessage = "Your device fingerprints have changed. Please sign in with your password to re-enable fingerprint login."
                    }
                }
            }
        )
    }
    
    /**
     * After biometric verification, decrypt the stored refresh token
     * and use it to create a new Supabase session.
     */
    private suspend fun performBiometricLogin(
        context: Context,
        cipher: javax.crypto.Cipher,
        onSuccess: () -> Unit
    ) {
        val credentials = BiometricCredentialStore.getStoredCredentials(context, cipher)
        
        if (credentials == null) {
            biometricLoginInProgress = false
            errorMessage = "Failed to decrypt stored credentials. Please sign in with password."
            isBiometricEnrolled = false
            BiometricCredentialStore.clearCredentials(context)
            return
        }
        
        isLoading = true
        
        val refreshed = SupabaseClient.refreshSession(context, credentials.refreshToken)
        
        if (refreshed) {
            Log.d(TAG, "Biometric login successful")
            
            // Note: We do NOT update the stored refresh token here because
            // re-encryption would require another biometric prompt. The original
            // token stored at enrollment time continues to work for refreshing
            // sessions. If Supabase eventually invalidates it, the user will
            // need to re-enter their password (which is the correct behavior).
            BiometricCredentialStore.markTokenPotentiallyStale(context)
            
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
     *
     * Generates a new Keystore key, triggers a crypto-based biometric prompt,
     * and stores the encrypted refresh token on success.
     */
    fun enableBiometricLogin(activity: FragmentActivity) {
        // Generate a fresh Keystore key for this enrollment
        BiometricAuthManager.generateKey()
        
        BiometricAuthManager.authenticateForEncryption(
            activity = activity,
            title = "Enable Fingerprint Login",
            subtitle = "Verify your fingerprint to enable quick login",
            negativeButtonText = "Cancel",
            onResult = { result ->
                when (result) {
                    is BiometricAuthManager.CryptoAuthResult.Success -> {
                        viewModelScope.launch {
                            val refreshToken = SupabaseClient.getRefreshToken(activity)
                            val userId = SupabaseClient.currentUserId
                            val email = SupabaseClient.currentEmail
                            
                            if (refreshToken != null && userId != null) {
                                BiometricCredentialStore.storeCredentials(
                                    context = activity,
                                    cipher = result.cipher,
                                    userId = userId,
                                    email = email ?: "",
                                    refreshToken = refreshToken
                                )
                                
                                // Update backend setting
                                PrivacySecurityRepository.enableBiometricLogin(SupabaseClient.httpClient)
                                
                                isBiometricEnrolled = true
                                isBiometricKeyInvalidated = false
                                isBiometricEnrollDialogVisible = false
                                Log.d(TAG, "Biometric login enabled (crypto-bound)")
                                pendingAuthSuccessAction?.invoke()
                                pendingAuthSuccessAction = null
                            } else {
                                errorMessage = "Failed to enable biometric login"
                                isBiometricEnrollDialogVisible = false
                                pendingAuthSuccessAction?.invoke()
                                pendingAuthSuccessAction = null
                            }
                        }
                    }
                    is BiometricAuthManager.CryptoAuthResult.Cancelled -> {
                        Log.d(TAG, "Biometric enrollment cancelled by user")
                        isBiometricEnrollDialogVisible = false
                        BiometricAuthManager.deleteKey()
                        pendingAuthSuccessAction?.invoke()
                        pendingAuthSuccessAction = null
                    }
                    is BiometricAuthManager.CryptoAuthResult.Error -> {
                        Log.w(TAG, "Biometric enrollment failed: ${result.message}")
                        isBiometricEnrollDialogVisible = false
                        BiometricAuthManager.deleteKey()
                        pendingAuthSuccessAction?.invoke()
                        pendingAuthSuccessAction = null
                    }
                    is BiometricAuthManager.CryptoAuthResult.KeyInvalidated -> {
                        Log.w(TAG, "Key invalidated during enrollment — should not happen with fresh key")
                        isBiometricEnrollDialogVisible = false
                        errorMessage = "Biometric setup failed. Please try again."
                        pendingAuthSuccessAction?.invoke()
                        pendingAuthSuccessAction = null
                    }
                }
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
            isBiometricKeyInvalidated = false
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
    // PASSKEY LOGIN (Cross-Device Biometric)
    // ============================================

    /** Whether a passkey login attempt is in progress */
    var passkeyLoginInProgress by mutableStateOf(false)
        private set

    /**
     * Attempt to log in using a Passkey (WebAuthn).
     *
     * The user types their email/phone, then taps the "Passkey" button.
     * The CredentialManager handles the biometric prompt and key retrieval.
     * On success, we receive session tokens and log the user in.
     */
    fun attemptPasskeyLogin(
        activity: FragmentActivity,
        onSuccess: () -> Unit
    ) {
        val email = formState.email.takeIf { it.isNotBlank() }
        val phone = formState.phone.takeIf { it.isNotBlank() }

        if (email == null && phone == null) {
            errorMessage = "Please enter your email or phone to use passkey login."
            return
        }

        passkeyLoginInProgress = true
        errorMessage = null

        viewModelScope.launch {
            val result = PasskeyManager.loginWithPasskey(
                context = activity,
                httpClient = SupabaseClient.httpClient,
                email = email,
                phone = phone
            )

            passkeyLoginInProgress = false

            when (result) {
                is PasskeyManager.LoginResult.Success -> {
                    Log.d(TAG, "Passkey login successful")

                    // Save the session just like a normal login
                    SupabaseClient.savePasskeySession(
                        context = activity,
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                        userId = result.userId,
                        email = result.email,
                        phone = result.phone
                    )

                    // Reset state
                    failedAttempts = 0
                    lockoutUntil = null
                    lockoutCountdown = 0
                    lockoutJob?.cancel()

                    // Upload FCM and track session
                    uploadFcmToken(activity)
                    SupabaseClient.trackSession(activity)

                    isLoading = false
                    onSuccess()
                }
                is PasskeyManager.LoginResult.Cancelled -> {
                    Log.d(TAG, "Passkey login cancelled")
                }
                is PasskeyManager.LoginResult.NoPasskeys -> {
                    errorMessage = "No passkey found for this account. Sign in with password first, then register a passkey in Settings."
                }
                is PasskeyManager.LoginResult.Error -> {
                    errorMessage = result.message
                }
            }
        }
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
