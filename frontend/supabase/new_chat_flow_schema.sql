-- ============================================
-- NEW CHAT FLOW - Backend Schema Verification & Setup
-- ============================================
-- This script ensures all necessary tables, indexes, and RLS policies
-- are properly configured to support the new chat flow

-- ============================================
-- STEP 1: Verify/Create Tables
-- ============================================

-- Profiles table (should already exist from auth)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE UNIQUE NOT NULL,
    full_name TEXT,
    username TEXT UNIQUE,
    avatar_url TEXT,
    phone TEXT,
    bio TEXT,
    status TEXT DEFAULT 'offline',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Conversations table
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    is_group BOOLEAN DEFAULT false,
    group_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Conversation participants table
CREATE TABLE IF NOT EXISTS public.conversation_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(conversation_id, user_id)
);

-- Messages table
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE NOT NULL,
    sender_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Contacts table
CREATE TABLE IF NOT EXISTS public.contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    contact_user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    nickname TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, contact_user_id)
);

-- ============================================
-- STEP 2: Create Indexes for Performance
-- ============================================

CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON public.profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles(username);
CREATE INDEX IF NOT EXISTS idx_profiles_phone ON public.profiles(phone);

CREATE INDEX IF NOT EXISTS idx_conversation_participants_conversation_id 
    ON public.conversation_participants(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversation_participants_user_id 
    ON public.conversation_participants(user_id);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id 
    ON public.messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at 
    ON public.messages(created_at);

CREATE INDEX IF NOT EXISTS idx_contacts_user_id 
    ON public.contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_user_id 
    ON public.contacts(contact_user_id);

-- ============================================
-- STEP 3: Enable RLS on All Tables
-- ============================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;

-- ============================================
-- STEP 4: Create SECURITY DEFINER Functions
-- (These bypass RLS to avoid infinite recursion)
-- ============================================

-- Drop existing functions first
DROP FUNCTION IF EXISTS public.user_is_participant_in_conversation(uuid, uuid);
DROP FUNCTION IF EXISTS public.is_conversation_participant(uuid, uuid);

-- Helper function to check if user is a participant
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

-- Alias function for conversations table
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
-- STEP 5: Drop All Existing Policies
-- ============================================

DO $$ 
DECLARE
    pol record;
BEGIN
    -- Drop policies on profiles
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'profiles' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.profiles', pol.policyname);
    END LOOP;
    
    -- Drop policies on conversations
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'conversations' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.conversations', pol.policyname);
    END LOOP;
    
    -- Drop policies on conversation_participants
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'conversation_participants' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.conversation_participants', pol.policyname);
    END LOOP;
    
    -- Drop policies on messages
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'messages' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.messages', pol.policyname);
    END LOOP;
    
    -- Drop policies on contacts
    FOR pol IN 
        SELECT policyname 
        FROM pg_policies 
        WHERE tablename = 'contacts' AND schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.contacts', pol.policyname);
    END LOOP;
END $$;

-- ============================================
-- STEP 6: Create RLS Policies for PROFILES
-- ============================================

-- SELECT: All authenticated users can view all profiles (for search)
CREATE POLICY "profiles_select_all" 
ON public.profiles 
FOR SELECT 
TO authenticated 
USING (true);

-- INSERT: Users can only create their own profile
CREATE POLICY "profiles_insert_own" 
ON public.profiles 
FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = user_id);

-- UPDATE: Users can only update their own profile
CREATE POLICY "profiles_update_own" 
ON public.profiles 
FOR UPDATE 
TO authenticated 
USING (auth.uid() = user_id);

-- ============================================
-- STEP 7: Create RLS Policies for CONVERSATIONS
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
-- STEP 8: Create RLS Policies for CONVERSATION_PARTICIPANTS
-- ============================================

-- SELECT: Users can see their own participation records
CREATE POLICY "participants_select_own" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (user_id = auth.uid());

-- SELECT: Users can see other participants in their conversations
CREATE POLICY "participants_select_coparticipants" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (
  public.user_is_participant_in_conversation(conversation_id, auth.uid())
);

-- INSERT: Any authenticated user can add participants
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
-- STEP 9: Create RLS Policies for MESSAGES
-- ============================================

-- SELECT: Users can view messages in conversations they participate in
CREATE POLICY "messages_select_policy" 
ON public.messages 
FOR SELECT 
TO authenticated 
USING (
  public.is_conversation_participant(conversation_id, auth.uid())
);

-- INSERT: Users can send messages to conversations they participate in
CREATE POLICY "messages_insert_policy" 
ON public.messages 
FOR INSERT 
TO authenticated 
WITH CHECK (
  auth.uid() = sender_id AND
  public.is_conversation_participant(conversation_id, auth.uid())
);

-- DELETE: Users can delete their own messages
CREATE POLICY "messages_delete_policy" 
ON public.messages 
FOR DELETE 
TO authenticated 
USING (auth.uid() = sender_id);

-- ============================================
-- STEP 10: Create RLS Policies for CONTACTS
-- ============================================

-- SELECT: Users can view their own contacts
CREATE POLICY "contacts_select_own" 
ON public.contacts 
FOR SELECT 
TO authenticated 
USING (user_id = auth.uid());

-- INSERT: Users can add their own contacts
CREATE POLICY "contacts_insert_own" 
ON public.contacts 
FOR INSERT 
TO authenticated 
WITH CHECK (user_id = auth.uid());

-- DELETE: Users can delete their own contacts
CREATE POLICY "contacts_delete_own" 
ON public.contacts 
FOR DELETE 
TO authenticated 
USING (user_id = auth.uid());

-- ============================================
-- STEP 11: Grant Permissions
-- ============================================

GRANT USAGE ON SCHEMA public TO authenticated;
GRANT USAGE ON SCHEMA public TO anon;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.conversations TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.conversation_participants TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.messages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.contacts TO authenticated;

GRANT SELECT ON public.profiles TO anon;

GRANT EXECUTE ON FUNCTION public.user_is_participant_in_conversation(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_conversation_participant(uuid, uuid) TO authenticated;

-- ============================================
-- STEP 12: Verification Queries
-- ============================================

-- Check tables exist
SELECT 
    tablename,
    schemaname,
    tableowner
FROM pg_tables 
WHERE schemaname = 'public' 
AND tablename IN ('profiles', 'conversations', 'conversation_participants', 'messages', 'contacts')
ORDER BY tablename;

-- Check RLS is enabled
SELECT 
    tablename,
    rowsecurity
FROM pg_tables 
WHERE schemaname = 'public' 
AND tablename IN ('profiles', 'conversations', 'conversation_participants', 'messages', 'contacts')
ORDER BY tablename;

-- Check policies exist
SELECT 
    schemaname, 
    tablename, 
    policyname, 
    permissive, 
    roles, 
    cmd
FROM pg_policies 
WHERE tablename IN ('profiles', 'conversations', 'conversation_participants', 'messages', 'contacts')
ORDER BY tablename, policyname;

-- Check indexes exist
SELECT 
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
AND tablename IN ('profiles', 'conversations', 'conversation_participants', 'messages', 'contacts')
ORDER BY tablename, indexname;
