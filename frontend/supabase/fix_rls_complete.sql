-- COMPLETE FIX for RLS policies on conversations and conversation_participants
-- Run this ENTIRE script in Supabase SQL Editor

-- ============================================
-- STEP 1: Drop ALL existing policies
-- ============================================

-- Drop all policies on conversation_participants
DO $$ 
DECLARE
    pol record;
BEGIN
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'conversation_participants' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.conversation_participants', pol.policyname);
    END LOOP;
END $$;

-- Drop all policies on conversations
DO $$ 
DECLARE
    pol record;
BEGIN
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'conversations' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.conversations', pol.policyname);
    END LOOP;
END $$;

-- ============================================
-- STEP 2: Create SECURITY DEFINER functions
-- These functions bypass RLS to avoid infinite recursion
-- ============================================

-- Drop existing functions first
DROP FUNCTION IF EXISTS public.user_is_participant_in_conversation(uuid, uuid);
DROP FUNCTION IF EXISTS public.is_conversation_participant(uuid, uuid);

-- Create helper function to check if user is a participant
CREATE OR REPLACE FUNCTION public.user_is_participant_in_conversation(conv_id uuid, uid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.conversation_participants
    WHERE conversation_id = conv_id AND user_id = uid
  );
$$;

-- Create alias function for conversations table
CREATE OR REPLACE FUNCTION public.is_conversation_participant(conv_id uuid, uid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.conversation_participants
    WHERE conversation_id = conv_id AND user_id = uid
  );
$$;

-- ============================================
-- STEP 3: Enable RLS on both tables
-- ============================================

ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_participants ENABLE ROW LEVEL SECURITY;

-- ============================================
-- STEP 4: Create policies for CONVERSATIONS table
-- ============================================

-- SELECT: Users can view conversations they participate in
CREATE POLICY "conversations_select_policy" 
ON public.conversations 
FOR SELECT 
TO authenticated 
USING (
  public.is_conversation_participant(id, auth.uid())
);

-- INSERT: Any authenticated user can create a conversation
CREATE POLICY "conversations_insert_policy" 
ON public.conversations 
FOR INSERT 
TO authenticated 
WITH CHECK (true);

-- UPDATE: Participants can update their conversations
CREATE POLICY "conversations_update_policy" 
ON public.conversations 
FOR UPDATE 
TO authenticated 
USING (
  public.is_conversation_participant(id, auth.uid())
);

-- DELETE: Participants can delete their conversations
CREATE POLICY "conversations_delete_policy" 
ON public.conversations 
FOR DELETE 
TO authenticated 
USING (
  public.is_conversation_participant(id, auth.uid())
);

-- ============================================
-- STEP 5: Create policies for CONVERSATION_PARTICIPANTS table
-- ============================================

-- SELECT: Users can see their own participation records directly
CREATE POLICY "participants_select_own" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (user_id = auth.uid());

-- SELECT: Users can see other participants in their conversations (via SECURITY DEFINER function)
CREATE POLICY "participants_select_coparticipants" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (
  public.user_is_participant_in_conversation(conversation_id, auth.uid())
);

-- INSERT: Any authenticated user can add participants (for creating conversations)
CREATE POLICY "participants_insert_policy" 
ON public.conversation_participants 
FOR INSERT 
TO authenticated 
WITH CHECK (true);

-- DELETE: Users can remove themselves from conversations
CREATE POLICY "participants_delete_policy" 
ON public.conversation_participants 
FOR DELETE 
TO authenticated 
USING (user_id = auth.uid());

-- ============================================
-- STEP 6: Grant necessary permissions
-- ============================================

GRANT USAGE ON SCHEMA public TO authenticated;
GRANT USAGE ON SCHEMA public TO anon;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.conversations TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.conversation_participants TO authenticated;

GRANT EXECUTE ON FUNCTION public.user_is_participant_in_conversation(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_conversation_participant(uuid, uuid) TO authenticated;

-- ============================================
-- VERIFICATION: Check that policies exist
-- ============================================

-- This will show all policies on both tables
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check 
FROM pg_policies 
WHERE tablename IN ('conversations', 'conversation_participants')
ORDER BY tablename, policyname;
