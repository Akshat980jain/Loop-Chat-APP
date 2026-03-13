# VoIP Setup Guide

This guide walks you through setting up Firebase Cloud Messaging for push notifications in Loop Chat.

## Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Click **"Add project"**
3. Enter project name: `LoopChat` (or any name)
4. Disable Google Analytics (optional, not needed for FCM)
5. Click **"Create project"**

## Step 2: Add Android App to Firebase

1. In Firebase Console, click the **Android icon** to add an Android app
2. Enter package name: `com.loopchat.app`
3. Enter app nickname: `Loop Chat`
4. Click **"Register app"**
5. Download `google-services.json`
6. Replace the placeholder file at:
   ```
   e:\Lovable Chat App\Android\app\google-services.json
   ```

## Step 3: Get FCM Server Key

1. In Firebase Console, click the **gear icon** (Settings) → **Project settings**
2. Go to **"Cloud Messaging"** tab
3. If "Cloud Messaging API (Legacy)" is disabled, click the three dots and **"Enable"**
4. Copy the **Server key** (it starts with `AAAA...`)
5. Save this key - you'll need it for Supabase

## Step 4: Add FCM Key to Supabase Secrets

1. Go to your [Supabase Dashboard](https://supabase.com/dashboard)
2. Select your project
3. Go to **Settings** → **Edge Functions**
4. Under **"Secrets"**, click **"Add new secret"**
5. Add:
   - Name: `FCM_SERVER_KEY`
   - Value: `(paste your Server key from Step 3)`
6. Click **Save**

## Step 4b: Add Daily.co API Key (Required for Video Calls)

> **Important:** Without this step, calls will work but will use a shared demo room. For proper per-call room creation, you need a Daily.co API key.

1. Go to [Daily.co Dashboard](https://dashboard.daily.co)
2. Create a free account if you don't have one
3. Go to **Developers** → **API Keys**
4. Copy your **API Key**
5. In Supabase Dashboard → **Settings** → **Edge Functions** → **Secrets**
6. Add:
   - Name: `DAILY_API_KEY`
   - Value: `(paste your Daily.co API key)`
7. Click **Save**

## Step 5: Update Database Schema

1. Go to Supabase Dashboard → **SQL Editor**
2. Copy and paste the contents of this file:
   ```
   e:\Lovable Chat App\frontend\supabase\add_fcm_token_schema.sql
   ```
3. Click **Run** to execute the SQL

Or run directly:
```sql
ALTER TABLE user_settings 
ADD COLUMN IF NOT EXISTS fcm_token TEXT,
ADD COLUMN IF NOT EXISTS fcm_token_updated_at TIMESTAMPTZ;
```

## Step 6: Deploy Edge Functions

Open terminal in the frontend directory and run:

```bash
cd "e:\Lovable Chat App\frontend"

# Login to Supabase (if not already)
npx supabase login

# Link to your project (if not already)
npx supabase link --project-ref slsvdfhfsokbaptzbclz

# Deploy the new edge function
npx supabase functions deploy send-call-notification

# Redeploy daily-room with FCM integration
npx supabase functions deploy daily-room
```

## Step 7: Verify Setup

### Check Firebase Configuration
1. The `google-services.json` file should have your actual project details
2. It should NOT have placeholder values like `YOUR_PROJECT_NUMBER`

### Check Supabase Secrets
1. Go to Settings → Edge Functions → Secrets
2. Verify `FCM_SERVER_KEY` is listed

### Check Database Schema
Run this query in Supabase SQL Editor:
```sql
SELECT column_name FROM information_schema.columns 
WHERE table_name = 'user_settings' 
AND column_name LIKE 'fcm%';
```
Should return: `fcm_token` and `fcm_token_updated_at`

## Step 8: Test

1. Install the APK on two Android devices
2. Login with different accounts on each device
3. From Device A, initiate a call to Device B
4. Device B should receive a push notification (even if app is closed)
5. Full-screen incoming call UI should appear

## Troubleshooting

### No push notifications received
- Check if FCM token is stored: Look in `user_settings` table for the user
- Check Edge Function logs in Supabase Dashboard
- Verify FCM_SERVER_KEY is set correctly

### Build errors after adding google-services.json
- Make sure the package name in Firebase matches `com.loopchat.app`
- Rebuild with: `.\gradlew.bat clean assembleDebug`

### "FCM not configured" error in logs
- The `FCM_SERVER_KEY` secret is missing in Supabase
- Add it via Settings → Edge Functions → Secrets
