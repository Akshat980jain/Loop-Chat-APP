-- Phase 1: Core Messaging & Media Features
-- Database Schema Migration

-- ============================================
-- 1. MESSAGE REACTIONS
-- ============================================

CREATE TABLE IF NOT EXISTS public.message_reactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reaction TEXT NOT NULL, -- emoji character
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(message_id, user_id, reaction)
);

CREATE INDEX idx_message_reactions_message_id ON public.message_reactions(message_id);
CREATE INDEX idx_message_reactions_user_id ON public.message_reactions(user_id);

-- RLS for message_reactions
ALTER TABLE public.message_reactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view reactions on their messages"
ON public.message_reactions FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.messages m
        JOIN public.conversation_participants cp ON m.conversation_id = cp.conversation_id
        WHERE m.id = message_reactions.message_id
        AND cp.user_id = auth.uid()
    )
);

CREATE POLICY "Users can add reactions"
ON public.message_reactions FOR INSERT
TO authenticated
WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can remove their own reactions"
ON public.message_reactions FOR DELETE
TO authenticated
USING (user_id = auth.uid());

-- ============================================
-- 2. MESSAGE EDITS
-- ============================================

CREATE TABLE IF NOT EXISTS public.message_edits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    previous_content TEXT NOT NULL,
    edited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    edited_by UUID NOT NULL REFERENCES auth.users(id)
);

CREATE INDEX idx_message_edits_message_id ON public.message_edits(message_id);

-- Add edited flag to messages table
ALTER TABLE public.messages 
ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS reply_to_message_id UUID REFERENCES public.messages(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_messages_reply_to ON public.messages(reply_to_message_id);

-- RLS for message_edits
ALTER TABLE public.message_edits ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view edit history of their messages"
ON public.message_edits FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.messages m
        JOIN public.conversation_participants cp ON m.conversation_id = cp.conversation_id
        WHERE m.id = message_edits.message_id
        AND cp.user_id = auth.uid()
    )
);

-- ============================================
-- 3. STARRED MESSAGES
-- ============================================

CREATE TABLE IF NOT EXISTS public.starred_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    starred_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(message_id, user_id)
);

CREATE INDEX idx_starred_messages_user_id ON public.starred_messages(user_id);
CREATE INDEX idx_starred_messages_message_id ON public.starred_messages(message_id);

-- RLS for starred_messages
ALTER TABLE public.starred_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view their starred messages"
ON public.starred_messages FOR SELECT
TO authenticated
USING (user_id = auth.uid());

CREATE POLICY "Users can star messages"
ON public.starred_messages FOR INSERT
TO authenticated
WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can unstar messages"
ON public.starred_messages FOR DELETE
TO authenticated
USING (user_id = auth.uid());

-- ============================================
-- 4. MESSAGE DELIVERIES (Status Tracking)
-- ============================================

CREATE TABLE IF NOT EXISTS public.message_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(message_id, user_id)
);

CREATE INDEX idx_message_deliveries_message_id ON public.message_deliveries(message_id);
CREATE INDEX idx_message_deliveries_user_id ON public.message_deliveries(user_id);

-- RLS for message_deliveries
ALTER TABLE public.message_deliveries ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view delivery status of their messages"
ON public.message_deliveries FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.messages m
        WHERE m.id = message_deliveries.message_id
        AND (m.sender_id = auth.uid() OR message_deliveries.user_id = auth.uid())
    )
);

CREATE POLICY "Users can update their delivery status"
ON public.message_deliveries FOR INSERT
TO authenticated
WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can update their read status"
ON public.message_deliveries FOR UPDATE
TO authenticated
USING (user_id = auth.uid());

-- ============================================
-- 5. MEDIA MESSAGES
-- ============================================

CREATE TABLE IF NOT EXISTS public.media_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    media_type TEXT NOT NULL, -- 'image', 'video', 'document', 'audio'
    media_url TEXT NOT NULL,
    thumbnail_url TEXT,
    file_name TEXT,
    file_size BIGINT,
    mime_type TEXT,
    duration INTEGER, -- for audio/video in seconds
    caption TEXT,
    width INTEGER, -- for images/videos
    height INTEGER, -- for images/videos
    view_once BOOLEAN DEFAULT FALSE,
    viewed_by UUID[] DEFAULT '{}', -- array of user IDs who viewed
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_media_messages_message_id ON public.media_messages(message_id);
CREATE INDEX idx_media_messages_media_type ON public.media_messages(media_type);

-- RLS for media_messages
ALTER TABLE public.media_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view media in their conversations"
ON public.media_messages FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.messages m
        JOIN public.conversation_participants cp ON m.conversation_id = cp.conversation_id
        WHERE m.id = media_messages.message_id
        AND cp.user_id = auth.uid()
    )
);

CREATE POLICY "Users can upload media"
ON public.media_messages FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.messages m
        WHERE m.id = media_messages.message_id
        AND m.sender_id = auth.uid()
    )
);

-- ============================================
-- 6. VOICE MESSAGES
-- ============================================

CREATE TABLE IF NOT EXISTS public.voice_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    audio_url TEXT NOT NULL,
    duration INTEGER NOT NULL, -- in seconds
    waveform JSONB, -- array of amplitude values for waveform visualization
    view_once BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_voice_messages_message_id ON public.voice_messages(message_id);

-- RLS for voice_messages
ALTER TABLE public.voice_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view voice messages in their conversations"
ON public.voice_messages FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.messages m
        JOIN public.conversation_participants cp ON m.conversation_id = cp.conversation_id
        WHERE m.id = voice_messages.message_id
        AND cp.user_id = auth.uid()
    )
);

CREATE POLICY "Users can send voice messages"
ON public.voice_messages FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.messages m
        WHERE m.id = voice_messages.message_id
        AND m.sender_id = auth.uid()
    )
);

-- ============================================
-- 7. LOCATION MESSAGES
-- ============================================

CREATE TABLE IF NOT EXISTS public.location_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address TEXT,
    is_live BOOLEAN DEFAULT FALSE,
    live_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_location_messages_message_id ON public.location_messages(message_id);

-- RLS for location_messages
ALTER TABLE public.location_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view locations in their conversations"
ON public.location_messages FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.messages m
        JOIN public.conversation_participants cp ON m.conversation_id = cp.conversation_id
        WHERE m.id = location_messages.message_id
        AND cp.user_id = auth.uid()
    )
);

CREATE POLICY "Users can share locations"
ON public.location_messages FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.messages m
        WHERE m.id = location_messages.message_id
        AND m.sender_id = auth.uid()
    )
);

-- ============================================
-- 8. MESSAGE STATUS UPDATES
-- ============================================

-- Add additional columns to messages table
ALTER TABLE public.messages
ADD COLUMN IF NOT EXISTS forwarded BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_for_everyone BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_messages_deleted ON public.messages(deleted_for_everyone, deleted_at);

-- ============================================
-- 9. TYPING INDICATORS (using Supabase Realtime)
-- ============================================

CREATE TABLE IF NOT EXISTS public.typing_indicators (
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    is_typing BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX idx_typing_indicators_conversation ON public.typing_indicators(conversation_id);

-- RLS for typing_indicators
ALTER TABLE public.typing_indicators ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view typing in their conversations"
ON public.typing_indicators FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.conversation_participants cp
        WHERE cp.conversation_id = typing_indicators.conversation_id
        AND cp.user_id = auth.uid()
    )
);

CREATE POLICY "Users can update their typing status"
ON public.typing_indicators FOR INSERT
TO authenticated
WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can update their own typing status"
ON public.typing_indicators FOR UPDATE
TO authenticated
USING (user_id = auth.uid());

-- ============================================
-- 10. STORAGE BUCKETS (Execute in Supabase Dashboard)
-- ============================================

-- Create storage buckets via Supabase Dashboard or API:
-- 1. 'media' bucket for images, videos, documents
-- 2. 'voice-messages' bucket for voice recordings

-- Storage policies will be set via Dashboard with:
-- - Public read access for media URLs
-- - Authenticated write access
-- - File size limits (images: 10MB, videos: 100MB, docs: 50MB)

-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- Check all tables exist
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN (
    'message_reactions',
    'message_edits',
    'starred_messages',
    'message_deliveries',
    'media_messages',
    'voice_messages',
    'location_messages',
    'typing_indicators'
)
ORDER BY table_name;

-- Check RLS is enabled
SELECT tablename, rowsecurity 
FROM pg_tables 
WHERE schemaname = 'public'
AND tablename IN (
    'message_reactions',
    'message_edits',
    'starred_messages',
    'message_deliveries',
    'media_messages',
    'voice_messages',
    'location_messages',
    'typing_indicators'
)
ORDER BY tablename;

-- Check indexes
SELECT tablename, indexname 
FROM pg_indexes 
WHERE schemaname = 'public'
AND tablename LIKE '%message%'
ORDER BY tablename, indexname;
