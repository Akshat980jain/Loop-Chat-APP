-- FIX: handle_new_group trigger was checking profiles.id = NEW.created_by
-- but NEW.created_by is auth.uid() (auth user ID), NOT profiles.id (profile UUID).
-- This caused the profile_exists check to ALWAYS fail, so the creator was
-- never added to conversation_participants or group_members by the trigger.
-- 
-- Fix: Use profiles.user_id = NEW.created_by (since user_id is the auth user ID).
-- Also: conversation_participants.user_id should use auth user ID directly,
--        but group_members.user_id should use the profile ID.

CREATE OR REPLACE FUNCTION public.handle_new_group()
RETURNS TRIGGER AS $$
DECLARE
    new_conversation_id UUID;
    v_profile_id UUID;
BEGIN
    -- Auto-create corresponding conversation
    INSERT INTO public.conversations (is_group, group_id, updated_at)
    VALUES (true, NEW.id, NOW())
    RETURNING id INTO new_conversation_id;

    -- Look up the creator's profile ID using their auth user ID
    SELECT id INTO v_profile_id FROM public.profiles WHERE user_id = NEW.created_by;

    IF NEW.created_by IS NOT NULL THEN
        -- Add creator to conversation_participants (uses auth user ID)
        INSERT INTO public.conversation_participants (conversation_id, user_id)
        VALUES (new_conversation_id, NEW.created_by)
        ON CONFLICT DO NOTHING;

        -- Auto-add the creator as the owner in group_members (uses profile ID)
        IF v_profile_id IS NOT NULL THEN
            INSERT INTO public.group_members (group_id, user_id, role, joined_at, added_by)
            VALUES (NEW.id, v_profile_id, 'owner', NOW(), v_profile_id)
            ON CONFLICT DO NOTHING;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
