-- Stories and Profile Picture Backend Schema
-- Run this in your Supabase SQL Editor

-- =====================================================
-- 1. CREATE STORIES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    media_type TEXT DEFAULT 'image',
    caption TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE DEFAULT (NOW() + INTERVAL '24 hours')
);

-- Add index for faster queries
CREATE INDEX IF NOT EXISTS idx_stories_user_id ON public.stories(user_id);
CREATE INDEX IF NOT EXISTS idx_stories_created_at ON public.stories(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stories_expires_at ON public.stories(expires_at);

-- =====================================================
-- 2. RLS POLICIES FOR STORIES TABLE
-- =====================================================
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;

-- Allow users to read all stories (from any user)
CREATE POLICY "Allow read all stories" 
ON public.stories FOR SELECT 
TO authenticated 
USING (true);

-- Allow users to create their own stories
CREATE POLICY "Allow insert own stories" 
ON public.stories FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = user_id);

-- Allow users to delete their own stories
CREATE POLICY "Allow delete own stories" 
ON public.stories FOR DELETE 
TO authenticated 
USING (auth.uid() = user_id);

-- =====================================================
-- 3. CREATE STORAGE BUCKETS
-- =====================================================
-- Create avatars bucket for profile pictures
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types) 
VALUES (
    'avatars', 
    'avatars', 
    true,
    5242880, -- 5MB max
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif']
)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Create stories bucket for story images
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types) 
VALUES (
    'stories', 
    'stories', 
    true,
    10485760, -- 10MB max
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4']
)
ON CONFLICT (id) DO UPDATE SET public = true;

-- =====================================================
-- 4. STORAGE BUCKET RLS POLICIES
-- =====================================================
-- Avatars bucket policies
CREATE POLICY "Allow authenticated uploads to avatars" 
ON storage.objects FOR INSERT 
TO authenticated 
WITH CHECK (bucket_id = 'avatars');

CREATE POLICY "Allow users to update own avatars" 
ON storage.objects FOR UPDATE 
TO authenticated 
USING (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Allow public read avatars" 
ON storage.objects FOR SELECT 
TO public 
USING (bucket_id = 'avatars');

-- Stories bucket policies
CREATE POLICY "Allow authenticated uploads to stories" 
ON storage.objects FOR INSERT 
TO authenticated 
WITH CHECK (bucket_id = 'stories');

CREATE POLICY "Allow users to delete own stories" 
ON storage.objects FOR DELETE 
TO authenticated 
USING (bucket_id = 'stories' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Allow public read stories" 
ON storage.objects FOR SELECT 
TO public 
USING (bucket_id = 'stories');

-- =====================================================
-- 5. FUNCTION TO AUTO-DELETE EXPIRED STORIES (Optional)
-- =====================================================
-- This function can be called by a cron job to clean up expired stories
CREATE OR REPLACE FUNCTION delete_expired_stories()
RETURNS void AS $$
BEGIN
    DELETE FROM public.stories WHERE expires_at < NOW();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- 6. UPDATE PROFILES TABLE (if avatar_url column missing)
-- =====================================================
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'profiles' 
        AND column_name = 'avatar_url'
    ) THEN
        ALTER TABLE public.profiles ADD COLUMN avatar_url TEXT;
    END IF;
END $$;

-- Grant permissions
GRANT ALL ON public.stories TO authenticated;
GRANT SELECT ON public.stories TO anon;
