# Local Development Guide

This guide explains how to run this project locally with your own Supabase backend.

## Prerequisites

- Node.js 18+ installed
- Supabase CLI installed (`npm install -g supabase`)
- A Supabase account (free tier works)

## Step 1: Create a Supabase Project

1. Go to [supabase.com](https://supabase.com) and create a new project
2. Note down your project credentials:
   - Project URL (e.g., `https://your-project-id.supabase.co`)
   - Anon/Public Key
   - Service Role Key (for edge functions)

## Step 2: Set Up Environment Variables

Create a `.env.local` file in the project root:

```env
VITE_SUPABASE_URL=https://your-project-id.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=your-anon-key
VITE_SUPABASE_PROJECT_ID=your-project-id
```

## Step 3: Run Database Migrations

The database schema needs to be applied to your Supabase project. You can do this via the Supabase dashboard SQL editor or using the CLI.

### Option A: Using Supabase Dashboard

Go to your Supabase dashboard → SQL Editor and run the following SQL:

```sql
-- Create profiles table
CREATE TABLE public.profiles (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE,
  full_name TEXT NOT NULL,
  username TEXT NOT NULL UNIQUE,
  phone TEXT,
  avatar_url TEXT,
  generated_avatar_url TEXT,
  bio TEXT,
  status TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create user_settings table
CREATE TABLE public.user_settings (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE,
  language TEXT DEFAULT 'en',
  time_format TEXT DEFAULT '12h',
  date_format TEXT DEFAULT 'MM/DD/YYYY',
  notification_sounds BOOLEAN DEFAULT true,
  message_sounds BOOLEAN DEFAULT true,
  show_online_status BOOLEAN DEFAULT true,
  show_read_receipts BOOLEAN DEFAULT true,
  show_typing_indicator BOOLEAN DEFAULT true,
  profile_photo_visibility TEXT DEFAULT 'everyone',
  status_visibility TEXT DEFAULT 'everyone',
  group_add_permission TEXT DEFAULT 'everyone',
  two_factor_enabled BOOLEAN DEFAULT false,
  end_to_end_encryption BOOLEAN DEFAULT true,
  do_not_disturb BOOLEAN DEFAULT false,
  direct_message_notifications BOOLEAN DEFAULT true,
  group_message_notifications BOOLEAN DEFAULT true,
  mention_notifications BOOLEAN DEFAULT true,
  use_generated_avatar BOOLEAN DEFAULT false,
  generated_avatar_url TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create conversations table
CREATE TABLE public.conversations (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create conversation_participants table
CREATE TABLE public.conversation_participants (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  UNIQUE(conversation_id, user_id)
);

-- Create messages table
CREATE TABLE public.messages (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create contacts table
CREATE TABLE public.contacts (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  contact_user_id UUID NOT NULL,
  nickname TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  UNIQUE(user_id, contact_user_id)
);

-- Create calls table
CREATE TABLE public.calls (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  caller_id UUID NOT NULL,
  callee_id UUID NOT NULL,
  call_type TEXT NOT NULL DEFAULT 'audio',
  status TEXT NOT NULL DEFAULT 'pending',
  room_url TEXT,
  started_at TIMESTAMP WITH TIME ZONE,
  ended_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create call_signals table
CREATE TABLE public.call_signals (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  call_id UUID NOT NULL REFERENCES public.calls(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL,
  signal_type TEXT NOT NULL,
  signal_data JSONB NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create call_history table
CREATE TABLE public.call_history (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  other_participant_name TEXT NOT NULL,
  other_participant_avatar TEXT,
  call_type TEXT NOT NULL,
  direction TEXT NOT NULL,
  status TEXT NOT NULL,
  duration INTEGER,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create stories table
CREATE TABLE public.stories (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  media_url TEXT NOT NULL,
  media_type TEXT NOT NULL DEFAULT 'image',
  caption TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (now() + interval '24 hours')
);

-- Create story_views table
CREATE TABLE public.story_views (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  story_id UUID NOT NULL REFERENCES public.stories(id) ON DELETE CASCADE,
  viewer_id UUID NOT NULL,
  viewed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  UNIQUE(story_id, viewer_id)
);

-- Create password_reset_otps table
CREATE TABLE public.password_reset_otps (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  phone TEXT NOT NULL,
  otp_code TEXT NOT NULL,
  verified BOOLEAN DEFAULT false,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (now() + interval '10 minutes')
);

-- Enable Row Level Security on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.call_signals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.call_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_otps ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for profiles
CREATE POLICY "Users can view all profiles" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Users can update own profile" ON public.profiles FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own profile" ON public.profiles FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Create RLS policies for user_settings
CREATE POLICY "Users can view own settings" ON public.user_settings FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update own settings" ON public.user_settings FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own settings" ON public.user_settings FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Create RLS policies for conversations
CREATE POLICY "Users can view their conversations" ON public.conversations FOR SELECT 
  USING (EXISTS (SELECT 1 FROM public.conversation_participants WHERE conversation_id = id AND user_id = auth.uid()));
CREATE POLICY "Users can create conversations" ON public.conversations FOR INSERT WITH CHECK (true);

-- Create RLS policies for conversation_participants
CREATE POLICY "Users can view conversation participants" ON public.conversation_participants FOR SELECT 
  USING (EXISTS (SELECT 1 FROM public.conversation_participants cp WHERE cp.conversation_id = conversation_id AND cp.user_id = auth.uid()));
CREATE POLICY "Users can add participants" ON public.conversation_participants FOR INSERT WITH CHECK (true);

-- Create RLS policies for messages
CREATE POLICY "Users can view messages in their conversations" ON public.messages FOR SELECT 
  USING (EXISTS (SELECT 1 FROM public.conversation_participants WHERE conversation_id = messages.conversation_id AND user_id = auth.uid()));
CREATE POLICY "Users can send messages" ON public.messages FOR INSERT WITH CHECK (auth.uid() = sender_id);

-- Create RLS policies for contacts
CREATE POLICY "Users can view own contacts" ON public.contacts FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can add contacts" ON public.contacts FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own contacts" ON public.contacts FOR DELETE USING (auth.uid() = user_id);

-- Create RLS policies for calls
CREATE POLICY "Users can view their calls" ON public.calls FOR SELECT USING (auth.uid() = caller_id OR auth.uid() = callee_id);
CREATE POLICY "Users can create calls" ON public.calls FOR INSERT WITH CHECK (auth.uid() = caller_id);
CREATE POLICY "Users can update their calls" ON public.calls FOR UPDATE USING (auth.uid() = caller_id OR auth.uid() = callee_id);

-- Create RLS policies for call_signals
CREATE POLICY "Users can view call signals" ON public.call_signals FOR SELECT 
  USING (EXISTS (SELECT 1 FROM public.calls WHERE id = call_id AND (caller_id = auth.uid() OR callee_id = auth.uid())));
CREATE POLICY "Users can send call signals" ON public.call_signals FOR INSERT WITH CHECK (auth.uid() = sender_id);

-- Create RLS policies for call_history
CREATE POLICY "Users can view own call history" ON public.call_history FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert call history" ON public.call_history FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Create RLS policies for stories
CREATE POLICY "Anyone can view stories" ON public.stories FOR SELECT USING (true);
CREATE POLICY "Users can create stories" ON public.stories FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own stories" ON public.stories FOR DELETE USING (auth.uid() = user_id);

-- Create RLS policies for story_views
CREATE POLICY "Story owners can view story views" ON public.story_views FOR SELECT 
  USING (EXISTS (SELECT 1 FROM public.stories WHERE id = story_id AND user_id = auth.uid()));
CREATE POLICY "Users can record story views" ON public.story_views FOR INSERT WITH CHECK (auth.uid() = viewer_id);

-- Create RLS policies for password_reset_otps
CREATE POLICY "Anyone can create OTPs" ON public.password_reset_otps FOR INSERT WITH CHECK (true);
CREATE POLICY "Anyone can view OTPs by phone" ON public.password_reset_otps FOR SELECT USING (true);
CREATE POLICY "Anyone can delete OTPs" ON public.password_reset_otps FOR DELETE USING (true);

-- Create helper functions
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (user_id, full_name, username, phone)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', 'User'),
    COALESCE(NEW.raw_user_meta_data->>'username', 'user_' || substring(NEW.id::text, 1, 8)),
    NEW.raw_user_meta_data->>'phone'
  );
  
  INSERT INTO public.user_settings (user_id)
  VALUES (NEW.id);
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.is_conversation_participant(conversation_uuid uuid, user_uuid uuid)
RETURNS boolean AS $$
  SELECT EXISTS (
    SELECT 1
    FROM conversation_participants
    WHERE conversation_id = conversation_uuid
      AND user_id = user_uuid
  );
$$ LANGUAGE sql STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.cleanup_expired_otps()
RETURNS void AS $$
BEGIN
  DELETE FROM public.password_reset_otps WHERE expires_at < now();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Create triggers
CREATE TRIGGER update_profiles_updated_at
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE TRIGGER update_user_settings_updated_at
  BEFORE UPDATE ON public.user_settings
  FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE TRIGGER update_conversations_updated_at
  BEFORE UPDATE ON public.conversations
  FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- Create trigger for new user registration
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Create storage bucket for attachments
INSERT INTO storage.buckets (id, name, public) VALUES ('chat-attachments', 'chat-attachments', true);

-- Storage policies
CREATE POLICY "Anyone can view attachments" ON storage.objects FOR SELECT USING (bucket_id = 'chat-attachments');
CREATE POLICY "Authenticated users can upload attachments" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'chat-attachments' AND auth.role() = 'authenticated');
CREATE POLICY "Users can delete own attachments" ON storage.objects FOR DELETE USING (bucket_id = 'chat-attachments' AND auth.uid()::text = (storage.foldername(name))[1]);

-- Enable realtime for messages
ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
ALTER PUBLICATION supabase_realtime ADD TABLE public.calls;
ALTER PUBLICATION supabase_realtime ADD TABLE public.call_signals;
```

### Option B: Using Supabase CLI

```bash
# Link to your Supabase project
supabase link --project-ref your-project-id

# Push the migrations
supabase db push
```

## Step 4: Set Up Edge Functions Secrets

In your Supabase dashboard, go to Settings → Edge Functions → Secrets and add:

| Secret Name | Description |
|-------------|-------------|
| `DAILY_API_KEY` | Your Daily.co API key for video calls |
| `TWILIO_ACCOUNT_SID` | Twilio Account SID for SMS |
| `TWILIO_AUTH_TOKEN` | Twilio Auth Token |
| `TWILIO_PHONE_NUMBER` | Your Twilio phone number |
| `OPENAI_API_KEY` | OpenAI API key (for voice-to-text) |

## Step 5: Deploy Edge Functions

```bash
# Login to Supabase CLI
supabase login

# Link your project
supabase link --project-ref your-project-id

# Deploy all edge functions
supabase functions deploy
```

Or deploy individual functions:

```bash
supabase functions deploy daily-room
supabase functions deploy create-story
supabase functions deploy delete-account
supabase functions deploy generate-avatar
supabase functions deploy get-profile
supabase functions deploy get-stories
supabase functions deploy get-story-views
supabase functions deploy login-with-phone
supabase functions deploy record-story-view
supabase functions deploy send-otp
supabase functions deploy update-profile
supabase functions deploy update-settings
supabase functions deploy upload-avatar
supabase functions deploy verify-otp
supabase functions deploy voice-to-text
```

## Step 6: Update Supabase Client (if needed)

If your local `.env.local` isn't being picked up, update `src/integrations/supabase/client.ts`:

```typescript
import { createClient } from '@supabase/supabase-js';
import type { Database } from './types';

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL || "YOUR_SUPABASE_URL";
const SUPABASE_PUBLISHABLE_KEY = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || "YOUR_ANON_KEY";

export const supabase = createClient<Database>(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY);
```

## Step 7: Run the Project

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

## Edge Functions Overview

| Function | Description | Required Secrets |
|----------|-------------|------------------|
| `daily-room` | Creates/manages Daily.co video rooms | `DAILY_API_KEY` |
| `create-story` | Creates user stories | None |
| `delete-account` | Deletes user account and data | None |
| `generate-avatar` | Generates AI avatars | `OPENAI_API_KEY` |
| `get-profile` | Fetches user profile | None |
| `get-stories` | Fetches all active stories | None |
| `get-story-views` | Gets story view analytics | None |
| `login-with-phone` | Phone number login | None |
| `record-story-view` | Records story views | None |
| `send-otp` | Sends OTP via SMS | `TWILIO_*` secrets |
| `update-profile` | Updates user profile | None |
| `update-settings` | Updates user settings | None |
| `upload-avatar` | Uploads avatar image | None |
| `verify-otp` | Verifies OTP codes | None |
| `voice-to-text` | Converts audio to text | `OPENAI_API_KEY` |

## Troubleshooting

### CORS Issues
Make sure your Supabase project URL is added to your CORS allowed origins in Supabase dashboard.

### Authentication Issues
- Enable "Auto Confirm" for email signups in Supabase Auth settings during development
- Check that the `on_auth_user_created` trigger is working

### Edge Function Errors
- Check function logs: `supabase functions logs function-name`
- Verify all required secrets are set

## Mobile Development (Capacitor)

For iOS/Android builds:

```bash
# Build web assets
npm run build

# Sync with native projects
npx cap sync

# Open in Xcode (iOS)
npx cap open ios

# Open in Android Studio
npx cap open android
```
