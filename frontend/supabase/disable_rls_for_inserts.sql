-- ============================================
-- DISABLE RLS RESTRICTIONS FOR ANDROID APP
-- Run this ENTIRE script in Supabase SQL Editor
-- ============================================

-- 1. Allow any authenticated user to SELECT from conversation_participants
DROP POLICY IF EXISTS "participants_select_own" ON public.conversation_participants;
DROP POLICY IF EXISTS "participants_select_coparticipants" ON public.conversation_participants;
CREATE POLICY "participants_select_all" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (true);

-- 2. Allow any authenticated user to INSERT into conversation_participants
DROP POLICY IF EXISTS "participants_insert_policy" ON public.conversation_participants;
CREATE POLICY "participants_insert_policy" 
ON public.conversation_participants 
FOR INSERT 
TO authenticated 
WITH CHECK (true);

-- 3. Allow any authenticated user to SELECT from conversations
DROP POLICY IF EXISTS "conversations_select_policy" ON public.conversations;
CREATE POLICY "conversations_select_all" 
ON public.conversations 
FOR SELECT 
TO authenticated 
USING (true);

-- 4. Allow any authenticated user to INSERT into conversations
DROP POLICY IF EXISTS "conversations_insert_policy" ON public.conversations;
CREATE POLICY "conversations_insert_policy" 
ON public.conversations 
FOR INSERT 
TO authenticated 
WITH CHECK (true);

-- 5. Allow any authenticated user to SELECT from contacts
DROP POLICY IF EXISTS "contacts_select_policy" ON public.contacts;
CREATE POLICY "contacts_select_all" 
ON public.contacts 
FOR SELECT 
TO authenticated 
USING (true);

-- 6. Allow any authenticated user to INSERT into contacts
DROP POLICY IF EXISTS "contacts_insert_policy" ON public.contacts;
CREATE POLICY "contacts_insert_policy" 
ON public.contacts 
FOR INSERT 
TO authenticated 
WITH CHECK (true);

-- 7. Allow any authenticated user to SELECT/INSERT messages
DROP POLICY IF EXISTS "messages_select_policy" ON public.messages;
DROP POLICY IF EXISTS "messages_insert_policy" ON public.messages;
CREATE POLICY "messages_select_all" 
ON public.messages 
FOR SELECT 
TO authenticated 
USING (true);

CREATE POLICY "messages_insert_policy" 
ON public.messages 
FOR INSERT 
TO authenticated 
WITH CHECK (true);

-- 8. Allow SELECT on profiles
DROP POLICY IF EXISTS "profiles_select_policy" ON public.profiles;
CREATE POLICY "profiles_select_all" 
ON public.profiles 
FOR SELECT 
TO authenticated 
USING (true);

-- Verify policies were created
SELECT tablename, policyname, cmd 
FROM pg_policies 
WHERE tablename IN ('conversations', 'conversation_participants', 'contacts', 'messages', 'profiles')
ORDER BY tablename, policyname;
