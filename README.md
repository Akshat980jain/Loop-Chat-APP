# 🔄 Loop Chat

Loop Chat is a secure, real-time messaging and voice/video calling application built with a modern technology stack. It features end-to-end encryption (E2EE), group messaging, stories, and passkey-based biometric authentication.

---

## 📱 Tech Stack & Architecture

### Mobile App (Android)
* **Language**: Kotlin
* **UI Framework**: Jetpack Compose
* **Local Storage**: Room Database (Single Source of Truth)
* **Network & API**: Ktor HTTP Client & WebSockets
* **Authentication**: WebAuthn Passkeys & Biometrics
* **VoIP & Signaling**: Daily.co WebRTC SDK & Firebase Cloud Messaging (FCM)

### Web Application (Frontend)
* **Framework**: React.js + Vite
* **Styling**: Tailwind CSS + Shadcn UI
* **Build Tool**: Bun / npm

### Backend Services (Supabase)
* **Database**: PostgreSQL (with Row-Level Security policies)
* **Realtime**: Supabase Realtime Channels (PostgreSQL replication & broadcasts)
* **Edge Functions**: Deno + TypeScript
* **Storage**: Supabase Buckets (Media attachments, stories, and avatar assets)

---

## 📁 Repository Structure

```
Loop Chat/
├── Android/         # Native Kotlin Android client application
├── frontend/        # React web client application
│   └── supabase/    # Supabase configuration, SQL migrations, and Edge Functions
└── .gitignore       # Git ignore specifications
```

---

## 🛠️ Setup & Run Instructions

### 1. Android Native App
1. Open the `/Android` directory in **Android Studio**.
2. Sync the project with Gradle files.
3. Configure your `local.properties` or environment variables for Supabase connection credentials:
   * `SUPABASE_URL`
   * `SUPABASE_ANON_KEY`
4. Add your `google-services.json` to `/Android/app/` for FCM push notifications.
5. Build and run the app on an Android device or emulator.

### 2. Web Frontend
1. Navigate to the `/frontend` directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Set up the environment secrets inside `.env`.
4. Run the local development server:
   ```bash
   npm run dev
   ```

### 3. Deploying Supabase Edge Functions
To deploy or update the unified Edge Functions:
1. Navigate to `/frontend` (where the `supabase` CLI directory is structured):
   ```bash
   cd frontend
   ```
2. Deploy the notifications webhook or other functions:
   ```bash
   supabase functions deploy send-notification --project-ref your_project_ref
   ```
