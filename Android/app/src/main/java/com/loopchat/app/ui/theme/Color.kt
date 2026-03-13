package com.loopchat.app.ui.theme

import androidx.compose.ui.graphics.Color

// ===========================================
// SUNSET VIBES COLOR PALETTE
// Premium dark theme with pink & orange tones
// ===========================================

// Primary Colors - Pink Gradient
val Primary = Color(0xFFF472B6)          // Vibrant pink
val PrimaryDark = Color(0xFFEC4899)      // Deeper pink for gradients
val PrimaryLight = Color(0xFFFBCFE8)     // Light pink for highlights

// Secondary Colors - Orange/Amber
val Secondary = Color(0xFFFB923C)        // Warm orange
val SecondaryDark = Color(0xFFF97316)    // Deeper orange
val SecondaryLight = Color(0xFFFED7AA)   // Light orange

// Background & Surface - Neutral Dark
val Background = Color(0xFF18181B)       // Main dark background (zinc-900)
val Surface = Color(0xFF27272A)          // Elevated surfaces (zinc-800)
val SurfaceVariant = Color(0xFF3F3F46)   // Cards, inputs (zinc-700)
val SurfaceLight = Color(0xFF52525B)     // Dividers, borders (zinc-600)

// Text Colors
val TextPrimary = Color(0xFFFAFAFA)      // White text (zinc-50)
val TextSecondary = Color(0xFFA1A1AA)    // Muted text (zinc-400)
val TextMuted = Color(0xFF71717A)        // Very muted (zinc-500)

// Status Colors
val Success = Color(0xFF22C55E)          // Online, success
val Warning = Color(0xFFFBBF24)          // Warnings, pending
val Error = Color(0xFFEF4444)            // Errors, offline
val Info = Color(0xFF38BDF8)             // Info, links

// Chat Message Colors
val MessageSent = Color(0xFFF472B6)      // Sent messages (pink)
val MessageSentGradientEnd = Color(0xFFEC4899)  // Gradient end
val MessageReceived = Color(0xFF3F3F46)  // Received messages (zinc-700)

// Online Status
val Online = Color(0xFF22C55E)           // Green online indicator
val Offline = Color(0xFF71717A)          // Gray offline

// Glassmorphism Colors
val GlassBackground = Color(0xFF27272A).copy(alpha = 0.8f)
val GlassBorder = Color(0xFFF472B6).copy(alpha = 0.3f)
val GlowPink = Color(0xFFF472B6).copy(alpha = 0.4f)
val GlowOrange = Color(0xFFFB923C).copy(alpha = 0.3f)

// Gradient color pairs (for Brush.linearGradient)
val PrimaryGradientColors = listOf(Primary, PrimaryDark)
val SecondaryGradientColors = listOf(Secondary, SecondaryDark)
val SunsetGradientColors = listOf(Primary, Secondary)  // Pink to Orange
val WarmGradientColors = listOf(Color(0xFFFB923C), Color(0xFFF472B6), Color(0xFFEC4899))

// Avatar border gradient
val AvatarBorderGradient = listOf(
    Color(0xFFF472B6),
    Color(0xFFEC4899),
    Color(0xFFFB923C)
)

// Legacy compatibility (Purple - kept for any remaining references)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
