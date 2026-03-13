-- =============================================================================
-- VoIP FCM Token Schema Update
-- =============================================================================
-- Run this SQL in your Supabase SQL Editor to add FCM token storage
-- 
-- This enables push notifications for incoming calls when the app is closed
-- =============================================================================

-- Add FCM token columns to user_settings table
ALTER TABLE user_settings 
ADD COLUMN IF NOT EXISTS fcm_token TEXT,
ADD COLUMN IF NOT EXISTS fcm_token_updated_at TIMESTAMPTZ;

-- Create an index for faster token lookups
CREATE INDEX IF NOT EXISTS idx_user_settings_fcm_token 
ON user_settings(user_id) 
WHERE fcm_token IS NOT NULL;

-- RLS policy for FCM token updates (users can only update their own token)
CREATE POLICY IF NOT EXISTS "Users can update their own FCM token"
ON user_settings
FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- Verify the changes
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'user_settings' 
AND column_name IN ('fcm_token', 'fcm_token_updated_at');
