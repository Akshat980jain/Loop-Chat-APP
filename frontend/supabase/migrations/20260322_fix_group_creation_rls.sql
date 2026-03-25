-- DEFENSIVE FIX: Ensure profiles exist before adding group members
-- and improve trigger resilience to prevent 409 Conflict errors.

-- 1. Update handle_new_group trigger to be more defensive
CREATE OR REPLACE FUNCTION public.handle_new_group()
RETURNS TRIGGER AS $$
DECLARE
    new_conversation_id UUID;
    profile_exists BOOLEAN;
BEGIN
    -- Auto-create corresponding conversation
    INSERT INTO public.conversations (is_group, group_id, updated_at)
    VALUES (true, NEW.id, NOW())
    RETURNING id INTO new_conversation_id;

    -- Check if the creator actually has a profile before trying to add them as a member
    -- This prevents the 409 Conflict (foreign key violation)
    SELECT EXISTS (SELECT 1 FROM public.profiles WHERE id = NEW.created_by) INTO profile_exists;

    IF NEW.created_by IS NOT NULL AND profile_exists THEN
        -- Add creator to conversation_participants
        INSERT INTO public.conversation_participants (conversation_id, user_id)
        VALUES (new_conversation_id, NEW.created_by)
        ON CONFLICT DO NOTHING;

        -- Auto-add the creator as the owner in group_members
        INSERT INTO public.group_members (group_id, user_id, role, joined_at, added_by)
        VALUES (NEW.id, NEW.created_by, 'owner', NOW(), NEW.created_by)
        ON CONFLICT DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Ensure RLS is disabled as requested
ALTER TABLE public.groups DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_participants DISABLE ROW LEVEL SECURITY;

-- 3. Final GRANTs
GRANT ALL ON TABLE public.groups TO authenticated;
GRANT ALL ON TABLE public.group_members TO authenticated;
GRANT ALL ON TABLE public.conversations TO authenticated;
GRANT ALL ON TABLE public.conversation_participants TO authenticated;
GRANT ALL ON TABLE public.profiles TO authenticated;
