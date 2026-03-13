package com.loopchat.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopchat.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Attachment menu for selecting media type
 */
@Composable
fun AttachmentMenu(
    onImageSelected: () -> Unit,
    onVideoSelected: () -> Unit,
    onDocumentSelected: () -> Unit,
    onLocationSelected: () -> Unit,
    onCameraSelected: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Send",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOption(
                    icon = Icons.Default.Image,
                    label = "Photo",
                    color = Primary,
                    onClick = {
                        onImageSelected()
                        onDismiss()
                    }
                )
                
                AttachmentOption(
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    color = Secondary,
                    onClick = {
                        onVideoSelected()
                        onDismiss()
                    }
                )
                
                AttachmentOption(
                    icon = Icons.Default.Description,
                    label = "Document",
                    color = Warning,
                    onClick = {
                        onDocumentSelected()
                        onDismiss()
                    }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOption(
                    icon = Icons.Default.LocationOn,
                    label = "Location",
                    color = Online,
                    onClick = {
                        onLocationSelected()
                        onDismiss()
                    }
                )
                
                AttachmentOption(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    color = Error,
                    onClick = {
                        onCameraSelected()
                        onDismiss()
                    }
                )
                
                // Placeholder for alignment
                Box(modifier = Modifier.size(64.dp))
            }
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

/**
 * Voice recording button with hold-to-record
 */
@Composable
fun VoiceRecordButton(
    isRecording: Boolean,
    duration: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            // Recording UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Cancel button
                IconButton(onClick = onCancelRecording) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Error
                    )
                }
                
                // Recording indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing red dot
                    var visible by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            visible = !visible
                            delay(500)
                        }
                    }
                    
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Error)
                        )
                    }
                    
                    // Duration
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                
                // Send button
                IconButton(
                    onClick = onStopRecording,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Primary)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        } else {
            // Microphone button
            IconButton(
                onClick = onStartRecording,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Primary)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Record voice",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Voice message playback UI
 */
@Composable
fun VoiceMessagePlayer(
    duration: Int,
    waveform: List<Float>,
    isPlaying: Boolean,
    currentPosition: Int,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSpeedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play/Pause button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Primary
            )
        }
        
        // Waveform
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Waveform(
                waveform = waveform,
                progress = currentPosition.toFloat() / duration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
        }
        
        // Duration
        Text(
            text = formatDuration(if (isPlaying) currentPosition / 1000 else duration),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        
        // Speed button
        TextButton(onClick = onSpeedChange) {
            Text(
                text = "${playbackSpeed}x",
                style = MaterialTheme.typography.labelSmall,
                color = Primary
            )
        }
    }
}

/**
 * Waveform visualization
 */
@Composable
fun Waveform(
    waveform: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width / waveform.size
        val maxHeight = size.height
        
        waveform.forEachIndexed { index, amplitude ->
            val barHeight = amplitude * maxHeight
            val x = index * barWidth
            val y = (maxHeight - barHeight) / 2
            
            val color = if (index.toFloat() / waveform.size < progress) {
                Primary
            } else {
                TextMuted
            }
            
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
            )
        }
    }
}

/**
 * Media preview (image/video)
 */
@Composable
fun MediaPreview(
    mediaUrl: String,
    mediaType: String,
    caption: String?,
    onMediaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant)
                .clickable(onClick = onMediaClick)
        ) {
            when {
                mediaType.startsWith("image/") -> {
                    coil.compose.AsyncImage(
                        model = mediaUrl,
                        contentDescription = "Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                mediaType.startsWith("video/") -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Play video",
                            modifier = Modifier.size(64.dp),
                            tint = Color.White
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Document",
                            modifier = Modifier.size(48.dp),
                            tint = TextSecondary
                        )
                    }
                }
            }
        }
        
        caption?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

/**
 * Location message display
 */
@Composable
fun LocationDisplay(
    latitude: Double,
    longitude: Double,
    address: String?,
    isLive: Boolean,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onLocationClick),
        color = SurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = if (isLive) Error else Primary
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    if (isLive) {
                        Text(
                            text = "Live Location",
                            style = MaterialTheme.typography.labelMedium,
                            color = Error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = address ?: "Location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    
                    Text(
                        text = "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = "Open in maps",
                    tint = Primary
                )
            }
        }
    }
}

/**
 * Format duration in MM:SS
 */
private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

// Canvas import
// Canvas import moved to top
