-- Fix infinite recursion in group_members RLS policies
-- Run this in the Supabase SQL Editor

-- 1. Drop the problematic recursive policies
DROP POLICY IF EXISTS "Users can view members of their groups" ON public.group_members;
DROP POLICY IF EXISTS "Admins and Owners can remove members" ON public.group_members;
DROP POLICY IF EXISTS "Users can view groups they belong to" ON public.groups;
DROP POLICY IF EXISTS "Admins and Owners can update groups" ON public.groups;

-- 2. Create a helper function with SECURITY DEFINER
-- This function runs with elevated privileges to bypass RLS, avoiding recursion
CREATE OR REPLACE FUNCTION public.is_group_member(gid uuid, uid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.group_members
    WHERE group_id = gid AND user_id = uid
  );
$$;

-- 3. Re-implement 'group_members' policies using the non-recursive function
CREATE POLICY "Users can view members of their groups"
ON public.group_members FOR SELECT
TO authenticated
USING (
    public.is_group_member(group_id, auth.uid())
);

CREATE POLICY "Admins and Owners can remove members"
ON public.group_members FOR DELETE
TO authenticated
USING (
    (
        SELECT role IN ('owner', 'admin') 
        FROM public.group_members 
        WHERE group_id = group_members.group_id AND user_id = auth.uid()
        LIMIT 1
    )
    OR user_id = auth.uid()
);
-- Note: The remove policy might still need careful handling if it recurses.
-- Let's use the function for role check too to be safe.

CREATE OR REPLACE FUNCTION public.get_group_role(gid uuid, uid uuid)
RETURNS public.group_role
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
  SELECT role FROM public.group_members
  WHERE group_id = gid AND user_id = uid
  LIMIT 1;
$$;

DROP POLICY IF EXISTS "Admins and Owners can remove members" ON public.group_members;
CREATE POLICY "Admins and Owners can remove members"
ON public.group_members FOR DELETE
TO authenticated
USING (
    public.get_group_role(group_id, auth.uid()) IN ('owner', 'admin')
    OR user_id = auth.uid()
);

-- 4. Re-implement 'groups' policies
CREATE POLICY "Users can view groups they belong to"
ON public.groups FOR SELECT
TO authenticated
USING (
    public.is_group_member(id, auth.uid())
);

CREATE POLICY "Admins and Owners can update groups"
ON public.groups FOR UPDATE
TO authenticated
USING (
    public.get_group_role(id, auth.uid()) IN ('owner', 'admin')
);
