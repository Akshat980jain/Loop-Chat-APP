package com.loopchat.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loopchat.app.R
import com.loopchat.app.ui.components.GlassCard
import com.loopchat.app.ui.components.GradientButtonLarge
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.viewmodels.AuthView
import com.loopchat.app.ui.viewmodels.AuthViewModel
import com.loopchat.app.ui.viewmodels.LoginMethod

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scrollState = rememberScrollState()
    
    // Check biometric status on first composition
    LaunchedEffect(Unit) {
        activity?.let { viewModel.checkBiometricStatus(it) }
    }
    
    // Auto-trigger biometric prompt for returning users
    LaunchedEffect(viewModel.isBiometricEnrolled, viewModel.hasAutoPrompted) {
        if (viewModel.isBiometricEnrolled && !viewModel.hasAutoPrompted && activity != null) {
            viewModel.markAutoPrompted()
            // Small delay to let the UI render first
            kotlinx.coroutines.delay(500)
            viewModel.attemptBiometricLogin(activity, onAuthSuccess)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Decorative gradient orbs in background
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-50).dp, y = (-50).dp)
                .blur(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = 100.dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Secondary.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // App Logo Image
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Loop Chat Logo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // App Title with gradient
            Text(
                text = "Loop Chat",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (viewModel.authView == AuthView.LOGIN) 
                    "Welcome back! Sign in to continue" 
                else 
                    "Create your account to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Auth View Toggle with gradient selection
            AuthViewToggle(
                currentView = viewModel.authView,
                onViewChange = { viewModel.switchView(it) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Glass Card for auth form
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    when (viewModel.authView) {
                        AuthView.LOGIN -> LoginForm(
                            viewModel = viewModel,
                            onLogin = { viewModel.login(context, onAuthSuccess) },
                            onBiometricLogin = {
                                activity?.let { act ->
                                    viewModel.attemptBiometricLogin(act, onAuthSuccess)
                                }
                            },
                            onVerifyOtp = { viewModel.verifyOtp(context, onAuthSuccess) },
                            onSendOtp = { viewModel.sendOtp() }
                        )
                        AuthView.SIGNUP -> SignupForm(
                            viewModel = viewModel,
                            onSignup = { 
                                viewModel.signUp(context) {
                                    // Show success message - user needs to sign in
                                }
                            }
                        )
                    }
                }
            }
            
            // Error Message
            viewModel.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorColor.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = error,
                        color = ErrorColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // Biometric Enrollment Dialog
    if (viewModel.isBiometricEnrollDialogVisible && activity != null) {
        BiometricEnrollmentDialog(
            onEnable = { viewModel.enableBiometricLogin(activity) },
            onDismiss = { viewModel.dismissBiometricEnrollDialog() }
        )
    }
}

@Composable
private fun AuthViewToggle(
    currentView: AuthView,
    onViewChange: (AuthView) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Primary.copy(alpha = 0.3f),
                        Secondary.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = Surface
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            AuthView.entries.forEach { view ->
                val isSelected = view == currentView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    brush = Brush.horizontalGradient(PrimaryGradientColors)
                                )
                            } else {
                                Modifier.background(Color.Transparent)
                            }
                        )
                        .clickable { onViewChange(view) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (view == AuthView.LOGIN) "Sign In" else "Sign Up",
                        textAlign = TextAlign.Center,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    viewModel: AuthViewModel,
    onLogin: () -> Unit,
    onBiometricLogin: () -> Unit,
    onVerifyOtp: () -> Unit,
    onSendOtp: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    Column {
        // Login Method Toggle
        LoginMethodToggle(
            currentMethod = viewModel.loginMethod,
            onMethodChange = { viewModel.switchLoginMethod(it) }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        if (viewModel.loginMethod == LoginMethod.EMAIL) {
            // Email Field
            OutlinedTextField(
                value = viewModel.formState.email,
                onValueChange = { viewModel.updateEmail(it) },
                label = { Text("Email Address") },
                placeholder = { Text("Enter your email", color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Primary)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceVariant,
                    focusedLabelColor = Primary,
                    cursorColor = Primary
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password Field
            OutlinedTextField(
                value = viewModel.formState.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text("Password") },
                placeholder = { Text("••••••••", color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Primary)
                },
                trailingIcon = {
                    IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                        Icon(
                            imageVector = if (viewModel.showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (viewModel.showPassword) "Hide password" else "Show password",
                            tint = TextSecondary
                        )
                    }
                },
                visualTransformation = if (viewModel.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceVariant,
                    focusedLabelColor = Primary,
                    cursorColor = Primary
                )
            )
            
            // Forgot Password link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { /* TODO: Forgot password flow */ }) {
                    Text("Forgot Password?", color = Primary, fontSize = 13.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Gradient Login Button
            GradientButtonLarge(
                text = "Sign In",
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading && !viewModel.biometricLoginInProgress,
                isLoading = viewModel.isLoading
            )
        } else {
            // PHONE OTP FLOW
            if (!viewModel.isOtpSent) {
                // Phone Field
                OutlinedTextField(
                    value = viewModel.formState.phone,
                    onValueChange = { viewModel.updatePhone(it) },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+91XXXXXXXXXX", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Primary)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedLabelColor = Primary,
                        cursorColor = Primary
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Gradient Send OTP Button
                GradientButtonLarge(
                    text = "Send Secure Code",
                    onClick = onSendOtp,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isLoading,
                    isLoading = viewModel.isLoading
                )
            } else {
                // OTP Verification Field
                Text(
                    text = "We sent a safe 6-digit code to ${viewModel.formState.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = viewModel.otpCode,
                    onValueChange = { viewModel.updateOtpCode(it) },
                    label = { Text("6-Digit Code") },
                    placeholder = { Text("123456", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Primary)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedLabelColor = Primary,
                        cursorColor = Primary
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Verify Button
                GradientButtonLarge(
                    text = "Verify & Sign In",
                    onClick = onVerifyOtp,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isLoading && viewModel.otpCode.length == 6,
                    isLoading = viewModel.isLoading
                )
                
                // Resend Code
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onSendOtp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Resend Code", color = Primary)
                }
            }
        }
        
        // Biometric Login Section — ALWAYS show when biometric hardware is available
        if (viewModel.isBiometricAvailable) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // Divider with "or login with"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(
                    modifier = Modifier.weight(1f),
                    color = SurfaceVariant,
                    thickness = 1.dp
                )
                Text(
                    text = "  or  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Divider(
                    modifier = Modifier.weight(1f),
                    color = SurfaceVariant,
                    thickness = 1.dp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Fingerprint Button with pulsing animation
            BiometricLoginButton(
                onClick = {
                    if (viewModel.isBiometricEnrolled) {
                        // Already enrolled — do biometric login
                        onBiometricLogin()
                    } else {
                        // Not enrolled — notify user to log in manually to enable
                        viewModel.showError("Sign in with password first to enable fingerprint login.")
                    }
                },
                isLoading = viewModel.biometricLoginInProgress
            )
            
            // "Use Password instead" link
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { /* Already showing password form above */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Use Password instead",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
            }
        }
    }
}

/**
 * Animated fingerprint login button with a pulsing glow effect.
 */
@Composable
private fun BiometricLoginButton(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "biometric_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Outer glow ring (pulsing)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Fingerprint icon button
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isLoading) { onClick() },
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(PrimaryGradientColors),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Login with fingerprint",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = if (isLoading) "Verifying..." else "Tap to login with Fingerprint",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/**
 * Dialog shown after a successful password login, asking the user
 * if they want to enable biometric (fingerprint) login for next time.
 */
@Composable
private fun BiometricEnrollmentDialog(
    onEnable: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(PrimaryGradientColors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        },
        title = {
            Text(
                text = "Enable Fingerprint Login?",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Sign in faster next time using your fingerprint. " +
                    "Your credentials will be securely encrypted on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = TextSecondary)
            }
        },
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun LoginMethodToggle(
    currentMethod: LoginMethod,
    onMethodChange: (LoginMethod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(10.dp))
            .padding(4.dp)
    ) {
        LoginMethod.entries.forEach { method ->
            val isSelected = method == currentMethod
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onMethodChange(method) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) Surface else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (method == LoginMethod.EMAIL) Icons.Default.Email else Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) Primary else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (method == LoginMethod.EMAIL) "Email" else "Phone",
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Primary else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SignupForm(
    viewModel: AuthViewModel,
    onSignup: () -> Unit
) {
    Column {
        // Full Name Field
        OutlinedTextField(
            value = viewModel.formState.fullName,
            onValueChange = { viewModel.updateFullName(it) },
            label = { Text("Full Name") },
            placeholder = { Text("John Doe", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, tint = Primary)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary,
                cursorColor = Primary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Email Field
        OutlinedTextField(
            value = viewModel.formState.email,
            onValueChange = { viewModel.updateEmail(it) },
            label = { Text("Email") },
            placeholder = { Text("your@email.com", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null, tint = Primary)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary,
                cursorColor = Primary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Phone Field
        OutlinedTextField(
            value = viewModel.formState.phone,
            onValueChange = { viewModel.updatePhone(it) },
            label = { Text("Phone Number") },
            placeholder = { Text("+91XXXXXXXXXX", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Primary)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary,
                cursorColor = Primary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Password Field
        OutlinedTextField(
            value = viewModel.formState.password,
            onValueChange = { viewModel.updatePassword(it) },
            label = { Text("Password") },
            placeholder = { Text("Min 6 characters", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Primary)
            },
            trailingIcon = {
                IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                    Icon(
                        imageVector = if (viewModel.showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (viewModel.showPassword) "Hide password" else "Show password",
                        tint = TextSecondary
                    )
                }
            },
            visualTransformation = if (viewModel.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary,
                cursorColor = Primary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Confirm Password Field
        OutlinedTextField(
            value = viewModel.formState.confirmPassword,
            onValueChange = { viewModel.updateConfirmPassword(it) },
            label = { Text("Confirm Password") },
            placeholder = { Text("Re-enter password", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Primary)
            },
            visualTransformation = if (viewModel.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary,
                cursorColor = Primary
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Gradient Signup Button
        GradientButtonLarge(
            text = "Create Account",
            onClick = onSignup,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isLoading,
            isLoading = viewModel.isLoading
        )
    }
}
