--
-- Loop Chat Phase 3: Privacy & Security (End-to-End Encryption & Vanish Mode)
-- 
-- IMPORTANT: Run this entire script in the Supabase SQL Editor to apply changes.
--

-- 1. Create table for storing RSA Public Keys
-- This table acts as a public key registry where clients request another user's key to encrypt payloads.
CREATE TABLE IF NOT EXISTS public.user_public_keys (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL, -- The Base64 encoded RSA 2048-bit Public Key
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Enable RLS for public keys
ALTER TABLE public.user_public_keys ENABLE ROW LEVEL SECURITY;

-- Policy: Anyone logged in can read public keys (so they can encrypt messages)
CREATE POLICY "Any authenticated user can view public keys"
ON public.user_public_keys FOR SELECT
TO authenticated
USING (true);

-- Policy: Users can only insert/update their own public key
CREATE POLICY "Users can insert their own public key"
ON public.user_public_keys FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own public key"
ON public.user_public_keys FOR UPDATE
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- 2. Vanish Mode (Ephemeral Messages) Support
-- Add an expires_at column to the messages table if it doesn't already exist
ALTER TABLE public.messages 
ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

-- 3. Create a Postgres Function to delete expired messages
-- This function looks for any messages past their expiration time and deletes them.
CREATE OR REPLACE FUNCTION public.delete_expired_messages()
RETURNS void AS $$
BEGIN
  -- Delete all messages where expires_at is in the past
  DELETE FROM public.messages
  WHERE expires_at IS NOT NULL AND expires_at < timezone('utc'::text, now());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Note on Auto-Deletion:
-- To make the auto-delete automatic, you would typically use pg_cron (if enabled on the database).
-- If you have pg_cron enabled, you can run the following to schedule cleanup every 5 minutes:
-- 
-- SELECT cron.schedule(
--   'cleanup-expired-messages',
--   '*/5 * * * *',
--   'SELECT public.delete_expired_messages();'
-- );
--
-- Alternatively, if pg_cron is not available on your plan, you can trigger `rpc('delete_expired_messages')` 
-- manually or from an Edge Function, or simply rely on clients filtering out expired messages locally.
