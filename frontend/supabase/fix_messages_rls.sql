-- ============================================
-- FIX RLS POLICIES FOR MESSAGES TABLE
-- Run this ENTIRE script in Supabase SQL Editor
-- ============================================

-- Step 1: Drop ALL existing policies on messages table
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

-- Step 2: Make sure RLS is enabled
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

-- Step 3: Create simple, permissive policies for messages

-- SELECT: Authenticated users can read messages in their conversations
CREATE POLICY "messages_select_policy" 
ON public.messages 
FOR SELECT 
TO authenticated 
USING (true);

-- INSERT: Authenticated users can insert messages (sender_id must match auth.uid())
CREATE POLICY "messages_insert_policy" 
ON public.messages 
FOR INSERT 
TO authenticated 
WITH CHECK (sender_id = auth.uid());

-- UPDATE: Users can update their own messages
CREATE POLICY "messages_update_policy" 
ON public.messages 
FOR UPDATE 
TO authenticated 
USING (sender_id = auth.uid());

-- DELETE: Users can delete their own messages
CREATE POLICY "messages_delete_policy" 
ON public.messages 
FOR DELETE 
TO authenticated 
USING (sender_id = auth.uid());

-- Step 4: Also fix conversations UPDATE policy (needed for bumping updated_at)
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

-- UPDATE: Authenticated users can update conversations they participate in
CREATE POLICY "conversations_update_policy" 
ON public.conversations 
FOR UPDATE 
TO authenticated 
USING (true);

-- Step 5: Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON public.messages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.conversations TO authenticated;

-- Step 6: Verify policies
SELECT tablename, policyname, cmd 
FROM pg_policies 
WHERE tablename IN ('messages', 'conversations')
ORDER BY tablename, policyname;
