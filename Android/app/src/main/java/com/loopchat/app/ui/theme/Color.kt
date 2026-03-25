package com.loopchat.app.ui.theme

import androidx.compose.ui.graphics.Color

// ===========================================
// ELECTRIC NOIR COLOR PALETTE
// Premium dark theme — Stitch Design System
// ===========================================

// Primary Colors — Coral / Rose
val Primary = Color(0xFFFF8C94)              // primary
val PrimaryDark = Color(0xFFFF5A6E)          // custom primary override
val PrimaryDim = Color(0xFFFF6F7D)           // primary_dim
val PrimaryContainer = Color(0xFFFF7481)     // primary_container
val PrimaryFixed = Color(0xFFFF7481)         // primary_fixed
val PrimaryFixedDim = Color(0xFFFE596D)      // primary_fixed_dim
val PrimaryLight = Color(0xFFFF8C94)         // alias for backward compat

// On Primary
val OnPrimary = Color(0xFF640018)            // on_primary
val OnPrimaryContainer = Color(0xFF4E0011)   // on_primary_container

// Secondary Colors — Neutral Warm
val Secondary = Color(0xFFE5E2E1)            // secondary
val SecondaryDark = Color(0xFFD6D4D3)        // secondary_dim
val SecondaryLight = Color(0xFFE5E2E1)       // alias
val SecondaryContainer = Color(0xFF474746)   // secondary_container
val OnSecondary = Color(0xFF525151)          // on_secondary

// Tertiary — Purple Accent
val Tertiary = Color(0xFFBF9CFF)             // tertiary
val TertiaryContainer = Color(0xFFB38CF9)    // tertiary_container
val TertiaryDim = Color(0xFFB08AF7)          // tertiary_dim
val OnTertiary = Color(0xFF3C0D7E)           // on_tertiary

// Background & Surface — Obsidian Deep
val Background = Color(0xFF0E0E0E)           // background / surface
val Surface = Color(0xFF0E0E0E)              // surface (same as bg per design)
val SurfaceDim = Color(0xFF0E0E0E)           // surface_dim
val SurfaceBright = Color(0xFF2C2C2C)        // surface_bright
val SurfaceContainer = Color(0xFF1A1919)     // surface_container (Level 1)
val SurfaceContainerHigh = Color(0xFF201F1F) // surface_container_high (Level 2)
val SurfaceContainerHighest = Color(0xFF262626) // surface_container_highest
val SurfaceContainerLow = Color(0xFF131313)  // surface_container_low
val SurfaceContainerLowest = Color(0xFF000000) // surface_container_lowest
val SurfaceVariant = Color(0xFF262626)       // surface_variant
val SurfaceTint = Color(0xFFFF8C94)          // surface_tint

// Text Colors (On Surface)
val TextPrimary = Color(0xFFFFFFFF)          // on_surface / on_background
val TextSecondary = Color(0xFFADAAAA)        // on_surface_variant
val TextMuted = Color(0xFF767575)            // outline (used for muted text)

// Outline / Borders
val Outline = Color(0xFF767575)              // outline
val OutlineVariant = Color(0xFF484847)       // outline_variant

// Error Colors
val ErrorColor = Color(0xFFFF7351)           // error
val ErrorDim = Color(0xFFD53D18)             // error_dim
val ErrorContainer = Color(0xFFB92902)       // error_container
val OnError = Color(0xFF450900)              // on_error
val OnErrorContainer = Color(0xFFFFD2C8)     // on_error_container

// Inverse
val InversePrimary = Color(0xFFB6223D)       // inverse_primary
val InverseSurface = Color(0xFFFCF9F8)       // inverse_surface
val InverseOnSurface = Color(0xFF565555)     // inverse_on_surface

// Chat Message Colors
val MessageSent = PrimaryContainer              // outgoing bubble
val MessageSentGradientEnd = PrimaryFixedDim    // for gradient effect
val MessageReceived = SurfaceContainer          // incoming bubble

// Glassmorphism & Effects
val GlassBackground = SurfaceContainerLow.copy(alpha = 0.8f)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.1f)
val PresenceOnline = Color(0xFF22C55E)

// Gradient color pairs — "Glass & Gradient" rule: 135° angle
val PrimaryGradientColors = listOf(PrimaryFixed, PrimaryFixedDim)
val SecondaryGradientColors = listOf(Tertiary, TertiaryDim)
val SunsetGradientColors = listOf(PrimaryFixed, PrimaryFixedDim)
val StoryGradientColors = listOf(PrimaryFixedDim, PrimaryFixed, Primary)

// Avatar border gradient
val AvatarBorderGradient = listOf(
    PrimaryFixedDim,
    Primary,
    Tertiary
)

// Legacy / Utility colors
val Accent = Primary
val Success = Color(0xFF00E676)
val Info = Color(0xFF29B6F6)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
val Warning = Color(0xFFFBBF24)
val Online = Color(0xFF22C55E)
val Offline = Color(0xFF767575)

// Backward compat — referenced in a few places
val SurfaceLight = OutlineVariant
