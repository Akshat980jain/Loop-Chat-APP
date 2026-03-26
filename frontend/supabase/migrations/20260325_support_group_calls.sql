-- ============================================
-- ADD GROUP CALLING SUPPORT TO CALLS TABLE
-- Run this in Supabase SQL Editor
-- ============================================

-- Add group_id to track which conversation a group call belongs to
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES public.conversations(id);

-- Make callee_id nullable (since group calls won't have a single callee)
ALTER TABLE public.calls ALTER COLUMN callee_id DROP NOT NULL;

-- Update RLS policies to allow access if auth.uid() is a participant of the group_id
DO $$ 
DECLARE
    pol record;
BEGIN
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'calls' AND schemaname = 'public' 
              AND policyname IN ('calls_select_policy', 'calls_update_policy')
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.calls', pol.policyname);
    END LOOP;
END $$;

-- Select policy: User can view call if caller/callee or if they are in the group
CREATE POLICY "calls_select_policy" ON public.calls 
FOR SELECT TO authenticated 
USING (
    auth.uid() = caller_id OR 
    auth.uid() = callee_id OR 
    (group_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.conversation_participants cp 
        WHERE cp.conversation_id = calls.group_id AND cp.user_id = auth.uid()
    ))
);

-- Update policy: User can update call (e.g. answer it) if caller/callee or in the group
CREATE POLICY "calls_update_policy" ON public.calls 
FOR UPDATE TO authenticated 
USING (
    auth.uid() = caller_id OR 
    auth.uid() = callee_id OR 
    (group_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.conversation_participants cp 
        WHERE cp.conversation_id = calls.group_id AND cp.user_id = auth.uid()
    ))
);
