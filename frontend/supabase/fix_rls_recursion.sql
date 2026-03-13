-- Fix infinite recursion in conversation_participants RLS policies
-- Run this in the Supabase SQL Editor

-- First, drop ALL existing policies on conversation_participants
DROP POLICY IF EXISTS "Allow users to view participants in their conversations" ON public.conversation_participants;
DROP POLICY IF EXISTS "Allow users to add participants" ON public.conversation_participants;
DROP POLICY IF EXISTS "Allow users to insert participants" ON public.conversation_participants;
DROP POLICY IF EXISTS "Users can view participants in their conversations" ON public.conversation_participants;
DROP POLICY IF EXISTS "Users can add participants to conversations they're in" ON public.conversation_participants;

-- Drop ALL existing policies on conversations
DROP POLICY IF EXISTS "Allow participants to view conversations" ON public.conversations;
DROP POLICY IF EXISTS "Users can view their conversations" ON public.conversations;
DROP POLICY IF EXISTS "Allow users to create conversations" ON public.conversations;
DROP POLICY IF EXISTS "Users can create conversations" ON public.conversations;

-- Create or replace the helper function with SECURITY DEFINER
CREATE OR REPLACE FUNCTION public.user_is_participant_in_conversation(conv_id uuid, uid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.conversation_participants
    WHERE conversation_id = conv_id AND user_id = uid
  );
$$;

-- Create or replace is_conversation_participant function
CREATE OR REPLACE FUNCTION public.is_conversation_participant(conv_id uuid, uid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.conversation_participants
    WHERE conversation_id = conv_id AND user_id = uid
  );
$$;

-- Enable RLS
ALTER TABLE public.conversation_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;

-- Create simple, non-recursive policies for conversation_participants
-- Policy 1: Users can see their own participant records
CREATE POLICY "Users can see own participation" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (user_id = auth.uid());

-- Policy 2: Users can see other participants using the SECURITY DEFINER function
CREATE POLICY "Users can see co-participants" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (
  public.user_is_participant_in_conversation(conversation_id, auth.uid())
);

-- Policy 3: Users can insert participants (for creating new conversations)
CREATE POLICY "Users can add participants" 
ON public.conversation_participants 
FOR INSERT 
TO authenticated 
WITH CHECK (true);

-- Create policies for conversations table
CREATE POLICY "Users can view their conversations" 
ON public.conversations 
FOR SELECT 
TO authenticated 
USING (
  public.is_conversation_participant(id, auth.uid())
);

CREATE POLICY "Users can create conversations" 
ON public.conversations 
FOR INSERT 
TO authenticated 
WITH CHECK (true);
