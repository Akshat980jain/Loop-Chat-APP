-- Fix infinite recursion in conversation_participants RLS policy
-- The original policy queries conversation_participants within itself, causing infinite recursion

-- Drop the problematic policy
DROP POLICY IF EXISTS "Allow users to view participants in their conversations" ON public.conversation_participants;

-- Create a new policy that uses the user_id directly to avoid recursion
-- Users can see participants if:
-- 1. They are the participant themselves (user_id = auth.uid())
-- 2. They are also a participant in the same conversation (using SECURITY DEFINER function)
CREATE POLICY "Allow users to view participants in their conversations" 
ON public.conversation_participants 
FOR SELECT 
TO authenticated 
USING (
  user_id = auth.uid() 
  OR 
  public.user_is_participant_in_conversation(conversation_id, auth.uid())
);

-- Also fix the conversations policy if it has similar issues
DROP POLICY IF EXISTS "Allow participants to view conversations" ON public.conversations;

CREATE POLICY "Allow participants to view conversations" 
ON public.conversations 
FOR SELECT 
TO authenticated 
USING (
  public.is_conversation_participant(id, auth.uid())
);

-- Enable RLS on conversation_participants if not already enabled
ALTER TABLE public.conversation_participants ENABLE ROW LEVEL SECURITY;

-- Enable RLS on conversations if not already enabled  
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
