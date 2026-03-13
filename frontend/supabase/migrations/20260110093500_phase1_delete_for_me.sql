-- ============================================
-- Phase 1 Enhancement: Delete for Me
-- ============================================

CREATE TABLE IF NOT EXISTS public.deleted_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, message_id)
);

CREATE INDEX idx_deleted_messages_user_id ON public.deleted_messages(user_id);
CREATE INDEX idx_deleted_messages_message_id ON public.deleted_messages(message_id);

-- RLS
ALTER TABLE public.deleted_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view their own deleted messages"
ON public.deleted_messages FOR SELECT
TO authenticated
USING (user_id = auth.uid());

CREATE POLICY "Users can delete messages for themselves"
ON public.deleted_messages FOR INSERT
TO authenticated
WITH CHECK (user_id = auth.uid());

-- Optional: Policy to allow undeleting if we wanted, but delete is usually permanent 
-- for the user view.
