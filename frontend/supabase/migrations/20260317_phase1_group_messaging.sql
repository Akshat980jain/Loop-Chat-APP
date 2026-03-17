-- Phase 1.1: Group Messaging Engine Schema
-- This migration creates the tables and RLS policies necessary for multi-user group chats.

-- 1. Create the base 'groups' table
CREATE TABLE IF NOT EXISTS public.groups (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(name) > 0 AND char_length(name) <= 100),
    description TEXT,
    avatar_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::jsonb
);

-- 2. Create the enum for member roles if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'group_role') THEN
        CREATE TYPE group_role AS ENUM ('owner', 'admin', 'member');
    END IF;
END$$;

-- 3. Create the 'group_members' table linking users to groups
CREATE TABLE IF NOT EXISTS public.group_members (
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    role group_role DEFAULT 'member',
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    added_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    PRIMARY KEY (group_id, user_id)
);

-- 4. Alter existing 'conversations' to support group chats
ALTER TABLE public.conversations
ADD COLUMN IF NOT EXISTS is_group BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE;

-- 5. Performance Indexes
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON public.group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON public.group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_groups_created_at ON public.groups(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_group_id ON public.conversations(group_id);

-- 6. Enable Row Level Security (RLS)
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;

-- 7. RLS Policies for 'groups'

-- A user can SELECT a group if they are a member of it.
CREATE POLICY "Users can view groups they belong to"
ON public.groups FOR SELECT
USING (
    EXISTS (
        SELECT 1 FROM public.group_members 
        WHERE group_members.group_id = groups.id 
        AND group_members.user_id = auth.uid()
    )
);

-- Any authenticated user can create a group.
CREATE POLICY "Users can create groups"
ON public.groups FOR INSERT
WITH CHECK (auth.uid() = created_by);

-- Only group 'owner' or 'admin' can UPDATE group details.
CREATE POLICY "Admins and Owners can update groups"
ON public.groups FOR UPDATE
USING (
    EXISTS (
        SELECT 1 FROM public.group_members 
        WHERE group_members.group_id = groups.id 
        AND group_members.user_id = auth.uid()
        AND role IN ('owner', 'admin')
    )
);

-- 8. RLS Policies for 'group_members'

-- A user can SELECT all members of a group they belong to.
CREATE POLICY "Users can view members of their groups"
ON public.group_members FOR SELECT
USING (
    EXISTS (
        -- They are trying to view members of group X, check if they are in group X
        SELECT 1 FROM public.group_members member_check
        WHERE member_check.group_id = group_members.group_id 
        AND member_check.user_id = auth.uid()
    )
);

-- Let users insert themselves when creating a group (handled via RPC or triggers usually, but opening for creator)
-- Note: Edge functions should ideally handle adding new members to prevent users inserting unauthorized members.
-- We will rely on a secure Edge Function to mutate group_members broadly, but for initial creation we allow it if they are the owner.
CREATE POLICY "Group creators can add initial members"
ON public.group_members FOR INSERT
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.groups
        WHERE groups.id = group_id
        AND groups.created_by = auth.uid()
    )
);

-- Admins/owners can remove members.
CREATE POLICY "Admins and Owners can remove members"
ON public.group_members FOR DELETE
USING (
    EXISTS (
        SELECT 1 FROM public.group_members checker
        WHERE checker.group_id = group_members.group_id 
        AND checker.user_id = auth.uid()
        AND checker.role IN ('owner', 'admin')
    )
    OR group_members.user_id = auth.uid() -- A member can leave the group
);

-- 9. Trigger to auto-create 'conversation' when a 'group' is created
CREATE OR REPLACE FUNCTION public.handle_new_group()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.conversations (is_group, group_id, updated_at)
    VALUES (true, NEW.id, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_group_created ON public.groups;
CREATE TRIGGER on_group_created
    AFTER INSERT ON public.groups
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_group();
