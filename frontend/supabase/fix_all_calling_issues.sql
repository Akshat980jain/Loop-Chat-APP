-- ============================================
-- COMPREHENSIVE FIX: All Missing Columns & Policies
-- Run this in Supabase SQL Editor
-- ============================================

-- ==== FIX #2: Add missing columns to calls table ====
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS caller_token TEXT;
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS callee_token TEXT;
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS room_name TEXT;

-- ==== FIX #5: Add fcm_token column to user_settings ====
ALTER TABLE public.user_settings ADD COLUMN IF NOT EXISTS fcm_token TEXT;
ALTER TABLE public.user_settings ADD COLUMN IF NOT EXISTS fcm_token_updated_at TIMESTAMP WITH TIME ZONE;

-- ==== FIX: Messages RLS policies ====
-- Drop ALL existing policies on messages table
DO $$ 
DECLARE
    pol record;
BEGIN
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'messages' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.messages', pol.policyname);
    END LOOP;
END $$;

ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "messages_select_policy" 
ON public.messages FOR SELECT TO authenticated USING (true);

CREATE POLICY "messages_insert_policy" 
ON public.messages FOR INSERT TO authenticated 
WITH CHECK (sender_id = auth.uid());

CREATE POLICY "messages_update_policy" 
ON public.messages FOR UPDATE TO authenticated 
USING (sender_id = auth.uid());

CREATE POLICY "messages_delete_policy" 
ON public.messages FOR DELETE TO authenticated 
USING (sender_id = auth.uid());

-- ==== FIX: Conversations UPDATE policy ====
DO $$ 
DECLARE
    pol record;
BEGIN
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'conversations' AND schemaname = 'public' AND cmd = 'UPDATE'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.conversations', pol.policyname);
    END LOOP;
END $$;

CREATE POLICY "conversations_update_policy" 
ON public.conversations FOR UPDATE TO authenticated USING (true);

-- ==== FIX: Calls RLS - ensure callee can SELECT calls ====
-- Make sure both caller and callee can view and update calls
DO $$ 
DECLARE
    pol record;
BEGIN
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'calls' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.calls', pol.policyname);
    END LOOP;
END $$;

ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;

CREATE POLICY "calls_select_policy" ON public.calls 
FOR SELECT TO authenticated 
USING (auth.uid() = caller_id OR auth.uid() = callee_id);

CREATE POLICY "calls_insert_policy" ON public.calls 
FOR INSERT TO authenticated 
WITH CHECK (auth.uid() = caller_id);

CREATE POLICY "calls_update_policy" ON public.calls 
FOR UPDATE TO authenticated 
USING (auth.uid() = caller_id OR auth.uid() = callee_id);

CREATE POLICY "calls_delete_policy" ON public.calls 
FOR DELETE TO authenticated 
USING (auth.uid() = caller_id);

-- ==== Grant permissions ====
GRANT SELECT, INSERT, UPDATE, DELETE ON public.calls TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.messages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.conversations TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.user_settings TO authenticated;

-- ==== Verify ====
SELECT 'CALLS columns:' as info;
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'calls' AND table_schema = 'public'
ORDER BY ordinal_position;

SELECT 'USER_SETTINGS fcm_token:' as info;
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'user_settings' AND table_schema = 'public' AND column_name LIKE 'fcm%';

SELECT 'ALL POLICIES:' as info;
SELECT tablename, policyname, cmd 
FROM pg_policies 
WHERE tablename IN ('calls', 'messages', 'conversations')
ORDER BY tablename, policyname;
