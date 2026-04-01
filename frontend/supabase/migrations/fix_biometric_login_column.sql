-- ============================================
-- BIOMETRIC SETTINGS FIX
-- Run this in Supabase SQL Editor
-- ============================================
-- This adds the missing 'biometric_login_enabled' column to security_settings.
-- The original phase2 migration only created 'biometric_lock_enabled'.
-- The Android app needs both columns to work correctly.

-- 1. Add the missing column (safe to run even if it already exists)
ALTER TABLE public.security_settings
    ADD COLUMN IF NOT EXISTS biometric_login_enabled BOOLEAN DEFAULT false;

-- 2. Verify the fix — you should see both columns
SELECT
    column_name,
    data_type,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name   = 'security_settings'
ORDER BY ordinal_position;
