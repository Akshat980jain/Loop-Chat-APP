-- 1. Add 'suspended' to group_role ENUM
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'suspended' AND enumtypid = 'public.group_role'::regtype) THEN
        ALTER TYPE public.group_role ADD VALUE 'suspended';
    END IF;
END$$;

-- 2. Add 'is_suspended' column to groups
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS is_suspended BOOLEAN DEFAULT FALSE;

-- 3. RPC to toggle group suspension (Admins and Owners only)
CREATE OR REPLACE FUNCTION public.toggle_group_suspension(
    p_group_id UUID,
    p_is_suspended BOOLEAN
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER 
AS $$
DECLARE
    v_requester_user_id UUID := auth.uid();
    v_requester_profile_id UUID;
    v_requester_role public.group_role;
BEGIN
    -- 1. Look up requester's profile ID
    SELECT id INTO v_requester_profile_id FROM public.profiles WHERE user_id = v_requester_user_id;

    -- 2. Check requester role in the group
    SELECT role INTO v_requester_role 
    FROM public.group_members 
    WHERE group_id = p_group_id AND user_id = v_requester_profile_id;

    -- 3. Authorization check (Admins or Owners can toggle)
    IF v_requester_role IS NULL OR v_requester_role NOT IN ('owner', 'admin') THEN
        RETURN jsonb_build_object('success', false, 'error', 'Forbidden: Not authorized to suspend this group.');
    END IF;

    -- 4. Execute toggle
    UPDATE public.groups SET is_suspended = p_is_suspended WHERE id = p_group_id;

    RETURN jsonb_build_object('success', true, 'action', 'toggle_suspension', 'is_suspended', p_is_suspended);
END;
$$;

-- 4. RPC to permanently delete a group (Owners only)
CREATE OR REPLACE FUNCTION public.delete_group(
    p_group_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER 
AS $$
DECLARE
    v_requester_user_id UUID := auth.uid();
    v_requester_profile_id UUID;
    v_requester_role public.group_role;
BEGIN
    SELECT id INTO v_requester_profile_id FROM public.profiles WHERE user_id = v_requester_user_id;

    SELECT role INTO v_requester_role 
    FROM public.group_members 
    WHERE group_id = p_group_id AND user_id = v_requester_profile_id;

    -- Only Owners can delete the group
    IF v_requester_role IS NULL OR v_requester_role != 'owner' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Forbidden: Only the group owner can permanently delete the group.');
    END IF;

    -- Database CASCADEs should handle deleting messages and members since 'group_id' on 'conversations' falls under CASCADE
    DELETE FROM public.groups WHERE id = p_group_id;

    RETURN jsonb_build_object('success', true, 'action', 'delete_group');
END;
$$;

-- 5. Trigger to restrict messaging if group/user is suspended
CREATE OR REPLACE FUNCTION public.check_messaging_permissions() 
RETURNS trigger AS $$
DECLARE
    v_is_group BOOLEAN;
    v_group_id UUID;
    v_is_suspended BOOLEAN;
    v_user_role public.group_role;
    v_sender_profile_id UUID;
BEGIN
    -- Only check for inserts (new messages)
    IF TG_OP = 'INSERT' THEN
        SELECT is_group, group_id INTO v_is_group, v_group_id
        FROM public.conversations WHERE id = NEW.conversation_id;

        IF v_is_group AND v_group_id IS NOT NULL THEN
            -- Check if group is suspended
            SELECT is_suspended INTO v_is_suspended FROM public.groups WHERE id = v_group_id;
            IF v_is_suspended THEN
                RAISE EXCEPTION 'Forbidden: Group is currently suspended.';
            END IF;

            -- Check if user is suspended
            SELECT id INTO v_sender_profile_id FROM public.profiles WHERE user_id = auth.uid();
            SELECT role INTO v_user_role FROM public.group_members
            WHERE group_id = v_group_id AND user_id = v_sender_profile_id;

            IF v_user_role = 'suspended' THEN
                RAISE EXCEPTION 'Forbidden: You are suspended from this group and cannot send messages.';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_check_messaging_permissions ON public.messages;
CREATE TRIGGER trg_check_messaging_permissions
BEFORE INSERT ON public.messages
FOR EACH ROW EXECUTE FUNCTION public.check_messaging_permissions();
