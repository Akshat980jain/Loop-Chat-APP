# Loop Chat Android App

A native Android chat application built with Kotlin and Jetpack Compose, connecting to the same Supabase backend as the web frontend.

## Features

- 📱 Phone number authentication with OTP
- 💬 Real-time messaging with Supabase Realtime
- 📞 Audio/Video calling UI (Daily.co ready)
- 👤 Profile management
- 🌙 Dark theme

## Tech Stack

- **Kotlin** - Modern Android development
- **Jetpack Compose** - Declarative UI
- **Supabase Kotlin SDK** - Backend integration
- **Daily.co Android SDK** - Video/Audio calls

## Setup

1. **Open in Android Studio**
   - Open Android Studio
   - File → Open → Select the `Android` folder

2. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - Wait for dependencies to download

3. **Run the app**
   - Connect an Android device or start an emulator
   - Click the Run button (green play icon)

## Project Structure

```
app/src/main/java/com/loopchat/app/
├── MainActivity.kt          # App entry point
├── LoopChatApplication.kt   # Application class
├── data/
│   ├── SupabaseClient.kt    # Supabase initialization
│   └── models/Models.kt     # Data models
└── ui/
    ├── navigation/          # Navigation setup
    ├── screens/             # All app screens
    └── theme/               # Colors, typography, theme
```

## Configuration

The Supabase credentials are configured in `app/build.gradle.kts`:
- `SUPABASE_URL` - Your Supabase project URL
- `SUPABASE_ANON_KEY` - Your Supabase anon key
- `DAILY_ROOM_URL` - Your Daily.co room URL

## Requirements

- Android Studio Hedgehog or newer
- Android SDK 26+ (Android 8.0)
- Kotlin 1.9+
