package com.loopchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loopchat.app.data.SecuritySettings
import com.loopchat.app.data.UserDevice
import com.loopchat.app.ui.theme.*

/**
 * Security Settings Screen
 */
@Composable
fun SecuritySettingsScreen(
    settings: SecuritySettings,
    onEnableTwoStep: (String, String?) -> Unit,
    onDisableTwoStep: () -> Unit,
    onEnableBiometric: () -> Unit,
    onDisableBiometric: () -> Unit,
    onActiveSessionsClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTwoStepDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Header
        Surface(
            color = Surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                }
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Two-Step Verification
            item {
                SecuritySettingCard(
                    icon = Icons.Default.Lock,
                    title = "Two-Step Verification",
                    subtitle = if (settings.two_step_enabled) "Enabled" else "Add extra security",
                    isEnabled = settings.two_step_enabled,
                    onClick = {
                        if (settings.two_step_enabled) {
                            onDisableTwoStep()
                        } else {
                            showTwoStepDialog = true
                        }
                    }
                )
            }
            
            // Biometric Lock
            item {
                SecuritySettingCard(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric Lock",
                    subtitle = if (settings.biometric_lock_enabled) "Enabled" else "Lock app with fingerprint",
                    isEnabled = settings.biometric_lock_enabled,
                    onClick = {
                        if (settings.biometric_lock_enabled) {
                            onDisableBiometric()
                        } else {
                            onEnableBiometric()
                        }
                    }
                )
            }
            
            // Security Notifications... (leaving as is in next chunk or assume it's fine)
            // Security Notifications
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Surface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Security Notifications",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Get notified about security changes",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = settings.security_notifications_enabled,
                            onCheckedChange = { /* Handle toggle */ },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Primary
                            )
                        )
                    }
                }
            }
            
            // Active Sessions
            item {
                SecuritySettingCard(
                    icon = Icons.Default.Devices,
                    title = "Active Sessions",
                    subtitle = "Manage your signed-in devices",
                    isEnabled = true,
                    onClick = onActiveSessionsClick
                )
            }
        }
    }
    
    if (showTwoStepDialog) {
        TwoStepSetupDialog(
            onSetupComplete = { pin, email ->
                onEnableTwoStep(pin, email)
                showTwoStepDialog = false
            },
            onDismiss = { showTwoStepDialog = false }
        )
    }
}

@Composable
private fun SecuritySettingCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isEnabled) Primary else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) Primary else TextSecondary
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}

/**
 * Device Management Screen
 */
@Composable
fun DeviceManagementScreen(
    devices: List<UserDevice>,
    currentDeviceId: String,
    onRemoveDevice: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Header
        Surface(
            color = Surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                }
                Text(
                    text = "Linked Devices",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(devices) { device ->
                DeviceItem(
                    device = device,
                    isCurrentDevice = device.device_token == currentDeviceId,
                    onRemove = { onRemoveDevice(device.id) }
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: UserDevice,
    isCurrentDevice: Boolean,
    onRemove: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (device.device_type) {
                    "android" -> Icons.Default.PhoneAndroid
                    "ios" -> Icons.Default.PhoneIphone
                    else -> Icons.Default.Computer
                },
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentDevice) "${device.device_name} (This Device)" else device.device_name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "Last active: ${formatTimestamp(device.last_active)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            if (!isCurrentDevice) { // Only allow removing other devices? Or allow removing self (logout)?
                IconButton(onClick = { showRemoveDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove device",
                        tint = ErrorColor
                    )
                }
            }
        }
    }
    
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Device?") },
            text = { Text("This device will be logged out and removed from your account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showRemoveDialog = false
                    }
                ) {
                    Text("Remove", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Disappearing Messages Dialog
 */
@Composable
fun DisappearingMessagesDialog(
    currentDuration: Int?,
    onDurationSelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disappearing Messages") },
        text = {
            Column {
                Text(
                    text = "Messages will automatically delete after the selected time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                DisappearingDurationOption(
                    label = "Off",
                    duration = null,
                    isSelected = currentDuration == null,
                    onSelect = { onDurationSelected(null) }
                )
                DisappearingDurationOption(
                    label = "24 hours",
                    duration = 86400,
                    isSelected = currentDuration == 86400,
                    onSelect = { onDurationSelected(86400) }
                )
                DisappearingDurationOption(
                    label = "7 days",
                    duration = 604800,
                    isSelected = currentDuration == 604800,
                    onSelect = { onDurationSelected(604800) }
                )
                DisappearingDurationOption(
                    label = "90 days",
                    duration = 7776000,
                    isSelected = currentDuration == 7776000,
                    onSelect = { onDurationSelected(7776000) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DisappearingDurationOption(
    label: String,
    duration: Int?,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Primary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

/**
 * Two-Step Verification Setup Dialog
 */
@Composable
fun TwoStepSetupDialog(
    onSetupComplete: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Two-Step Verification") },
        text = {
            Column {
                when (step) {
                    1 -> {
                        Text("Enter a 6-digit PIN")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6) pin = it },
                            placeholder = { Text("PIN") },
                            singleLine = true
                        )
                    }
                    2 -> {
                        Text("Confirm your PIN")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 6) confirmPin = it },
                            placeholder = { Text("Confirm PIN") },
                            singleLine = true
                        )
                    }
                    3 -> {
                        Text("Add recovery email (optional)")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = { Text("Email") },
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (step) {
                        1 -> if (pin.length == 6) step = 2
                        2 -> if (confirmPin == pin) step = 3
                        3 -> {
                            // Hash PIN and complete setup
                            onSetupComplete(pin, email.ifEmpty { null })
                            onDismiss()
                        }
                    }
                },
                enabled = when (step) {
                    1 -> pin.length == 6
                    2 -> confirmPin.length == 6
                    else -> true
                }
            ) {
                Text(if (step == 3) "Enable" else "Next")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper function
private fun formatTimestamp(timestamp: String): String {
    // Simple formatting - in production use proper date formatting
    return timestamp.substringBefore("T")
}
