-- ============================================
-- FIX CALLS TABLE - Add Missing Columns
-- Run this in Supabase SQL Editor
-- ============================================

-- The Android app sends caller_token, callee_token, and room_name
-- when creating a call, but these columns don't exist in the table.
-- Without them, the call record INSERT fails silently,
-- so the callee never sees the incoming call.

-- Step 1: Add missing columns
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS caller_token TEXT;
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS callee_token TEXT;
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS room_name TEXT;

-- Step 2: Verify the columns were added
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'calls' AND table_schema = 'public'
ORDER BY ordinal_position;
