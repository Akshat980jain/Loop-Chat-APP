-- RPC function to manage group members (Add, Remove, Update Role)
-- Now also syncs conversation_participants and sends notifications.

CREATE OR REPLACE FUNCTION public.manage_group_member(
    p_group_id UUID,
    p_user_id UUID, -- This should be the profiles.id of the target user
    p_action TEXT,
    p_role TEXT DEFAULT 'member'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER 
AS $$
DECLARE
    v_requester_user_id UUID := auth.uid();
    v_requester_profile_id UUID;
    v_requester_role public.group_role;
    v_group_exists BOOLEAN;
    v_conversation_id UUID;
    v_target_auth_user_id UUID;
    v_group_name TEXT;
    v_requester_name TEXT;
BEGIN
    -- 1. Look up requester's profile ID
    SELECT id INTO v_requester_profile_id FROM public.profiles WHERE user_id = v_requester_user_id;
    
    IF v_requester_profile_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Profile missing: Please ensure your user profile is created.');
    END IF;

    -- 2. Check if group exists and get group name
    SELECT EXISTS(SELECT 1 FROM public.groups WHERE id = p_group_id) INTO v_group_exists;
    IF NOT v_group_exists THEN
        RETURN jsonb_build_object('success', false, 'error', 'Group not found');
    END IF;

    SELECT name INTO v_group_name FROM public.groups WHERE id = p_group_id;

    -- 3. Check requester role in the group
    SELECT role INTO v_requester_role 
    FROM public.group_members 
    WHERE group_id = p_group_id AND user_id = v_requester_profile_id;

    -- 4. Authorization check
    IF v_requester_role IS NULL OR v_requester_role NOT IN ('owner', 'admin') THEN
        IF NOT EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND created_by = v_requester_user_id) THEN
            RETURN jsonb_build_object('success', false, 'error', 'Forbidden: Not authorized to manage this group.');
        END IF;
    END IF;

    -- 5. Look up the conversation linked to this group
    SELECT id INTO v_conversation_id
    FROM public.conversations
    WHERE group_id = p_group_id AND is_group = true
    LIMIT 1;

    -- 6. Look up the target user's auth user ID (profiles.user_id → auth.users.id)
    SELECT user_id INTO v_target_auth_user_id FROM public.profiles WHERE id = p_user_id;

    -- 7. Get requester's display name for notifications
    SELECT COALESCE(full_name, username, 'Someone') INTO v_requester_name
    FROM public.profiles WHERE id = v_requester_profile_id;

    -- 8. Perform action
    IF p_action = 'add' THEN
        -- Add to group_members
        INSERT INTO public.group_members (group_id, user_id, role, added_by)
        VALUES (p_group_id, p_user_id, p_role::public.group_role, v_requester_profile_id)
        ON CONFLICT (group_id, user_id) 
        DO UPDATE SET role = p_role::public.group_role;

        -- Sync: add to conversation_participants so the group appears in their chat list
        IF v_conversation_id IS NOT NULL AND v_target_auth_user_id IS NOT NULL THEN
            INSERT INTO public.conversation_participants (conversation_id, user_id)
            VALUES (v_conversation_id, v_target_auth_user_id)
            ON CONFLICT DO NOTHING;
        END IF;

        -- Create notification for the added member
        IF v_target_auth_user_id IS NOT NULL THEN
            INSERT INTO public.notifications (user_id, type, title, body, data)
            VALUES (
                v_target_auth_user_id,
                'group_invite',
                'Added to Group',
                'You were added to "' || COALESCE(v_group_name, 'a group') || '" by ' || v_requester_name,
                jsonb_build_object(
                    'group_id', p_group_id::text,
                    'conversation_id', v_conversation_id::text
                )
            );
        END IF;

        RETURN jsonb_build_object('success', true, 'action', 'add', 'conversation_id', v_conversation_id);

    ELSIF p_action = 'remove' THEN
        -- Remove from group_members
        DELETE FROM public.group_members 
        WHERE group_id = p_group_id AND user_id = p_user_id;

        -- Sync: remove from conversation_participants
        IF v_conversation_id IS NOT NULL AND v_target_auth_user_id IS NOT NULL THEN
            DELETE FROM public.conversation_participants
            WHERE conversation_id = v_conversation_id AND user_id = v_target_auth_user_id;
        END IF;

        RETURN jsonb_build_object('success', true, 'action', 'remove');

    ELSIF p_action = 'update_role' THEN
        IF v_requester_role != 'owner' THEN
            RETURN jsonb_build_object('success', false, 'error', 'Only owners can update roles.');
        END IF;
        
        UPDATE public.group_members SET role = p_role::public.group_role 
        WHERE group_id = p_group_id AND user_id = p_user_id;
        RETURN jsonb_build_object('success', true, 'action', 'update_role');

    ELSE
        RETURN jsonb_build_object('success', false, 'error', 'Invalid action: ' || p_action);
    END IF;
END;
$$;
