package com.loopchat.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.loopchat.app.data.SecuritySettings
import com.loopchat.app.ui.theme.*
import com.loopchat.app.ui.theme.Background
import com.loopchat.app.ui.theme.Primary
import com.loopchat.app.ui.theme.Secondary
import com.loopchat.app.ui.theme.TextPrimary
import com.loopchat.app.ui.theme.TextSecondary

/**
 * Premium Biometric Setup Screen.
 * 
 * Allows users to configure Fingerprint Login and App Lock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricSetupScreen(
    settings: SecuritySettings,
    onEnableLogin: (FragmentActivity) -> Unit,
    onDisableLogin: () -> Unit,
    onEnableLock: (FragmentActivity) -> Unit,
    onDisableLock: () -> Unit,
    onRegisterPasskey: (FragmentActivity) -> Unit,
    isPasskeyRegistering: Boolean = false,
    errorMessage: String? = null,
    successMessage: String? = null,
    onClearMessages: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    // Resolve the FragmentActivity from the current context chain.
    // BiometricPrompt requires a FragmentActivity.
    val activity: FragmentActivity? = remember(context) {
        var c: android.content.Context = context
        while (c is android.content.ContextWrapper && c !is FragmentActivity) {
            c = c.baseContext
        }
        c as? FragmentActivity
    }
    var activityMissingError by remember { mutableStateOf(false) }

    // Fingerprint pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biometric Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Visual Header
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Glows
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .background(Primary.copy(alpha = 0.2f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale * 0.9f)
                        .alpha(pulseAlpha * 0.5f)
                        .background(Primary.copy(alpha = 0.1f), CircleShape)
                )
                
                // Fingerprint Icon
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = Surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = Primary
                        )
                    }
                }
            }

            Text(
                text = "Secure your messages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Use biometric authentication for faster logins and to keep your chats private.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            // Options List
            BiometricOptionCard(
                title = "Fingerprint Login",
                description = "Sign in to your account with your fingerprint instead of a password.",
                icon = Icons.Default.Fingerprint,
                isEnabled = settings.biometric_login_enabled,
                onToggle = { enabled ->
                    if (enabled) {
                        val act = activity
                        if (act != null) onEnableLogin(act)
                        else activityMissingError = true
                    } else {
                        onDisableLogin()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            BiometricOptionCard(
                title = "App Lock",
                description = "Require fingerprint to open the app. Your chats stay private even if someone has your phone.",
                icon = Icons.Default.Lock,
                isEnabled = settings.biometric_lock_enabled,
                onToggle = { enabled ->
                    if (enabled) {
                        val act = activity
                        if (act != null) onEnableLock(act)
                        else activityMissingError = true
                    } else {
                        onDisableLock()
                    }
                }
            )

            // Show error if the activity could not be resolved
            if (activityMissingError) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { activityMissingError = false },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { activityMissingError = false }) {
                            Text("OK")
                        }
                    },
                    title = { Text("Error") },
                    text = { Text("Could not start biometric authentication. Please restart the app and try again.") }
                )
            }

            // Show error message
            if (errorMessage != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { onClearMessages() },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { onClearMessages() }) {
                            Text("OK")
                        }
                    },
                    title = { Text("Error") },
                    text = { Text(errorMessage ?: "") }
                )
            }

            // Show success message
            if (successMessage != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { onClearMessages() },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { onClearMessages() }) {
                            Text("OK")
                        }
                    },
                    title = { Text("Success") },
                    text = { Text(successMessage ?: "") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Passkey Registration Card ───────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(Primary.copy(alpha = 0.2f), Secondary.copy(alpha = 0.15f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cross-Device Passkey",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Use your fingerprint to log in on any device synced with your Google account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val act = activity
                            if (act != null) onRegisterPasskey(act)
                            else activityMissingError = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isPasskeyRegistering,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary
                        )
                    ) {
                        if (isPasskeyRegistering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isPasskeyRegistering) "Registering..." else "Register Passkey",
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
            
            // Helpful Tip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Your fingerprint identity is unique to Loop Chat. " +
                            "If you add or remove fingerprints on your device, " +
                            "you'll need to re-enable fingerprint login with your password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun BiometricOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isEnabled) Primary.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEnabled) Primary else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.DarkGray.copy(alpha = 0.5f)
                )
            )
        }
    }
}
