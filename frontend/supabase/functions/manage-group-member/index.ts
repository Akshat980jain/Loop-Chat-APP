import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface ManageMemberRequest {
  group_id: string;
  user_id: string;
  action: 'add' | 'remove' | 'update_role';
  role?: 'owner' | 'admin' | 'member';
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '' // Used to bypass RLS for admin actions
    );

    // Verify requesting user
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) throw new Error('No authorization header');

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(
      authHeader.replace('Bearer ', '')
    );
    if (authError || !user) throw new Error('Unauthorized');

    const { group_id, user_id, action, role }: ManageMemberRequest = await req.json();

    if (!group_id || !user_id || !action) {
      throw new Error('Missing required fields');
    }

    // Check if requester is authorized (owner or admin of the group)
    // We use the service role client, so we manually check
    const { data: requesterRole, error: roleError } = await supabaseClient
      .from('group_members')
      .select('role')
      .eq('group_id', group_id)
      .eq('user_id', user.id)
      .single();

    if (roleError || !requesterRole || !['owner', 'admin'].includes(requesterRole.role)) {
      // Allow if action is 'add' and they are the owner who just created the group 
      // (This edge case happens if the group is newly created and they are adding the first members)
      // Actually, we should check if they created the group.
      const { data: groupData } = await supabaseClient
        .from('groups')
        .select('created_by')
        .eq('id', group_id)
        .single();
        
      if (!groupData || groupData.created_by !== user.id) {
          throw new Error('Forbidden: You are not authorized to manage this group.');
      }
    }

    let result;

    if (action === 'add') {
      result = await supabaseClient
        .from('group_members')
        .insert({
          group_id,
          user_id,
          role: role || 'member',
          added_by: user.id
        });
    } else if (action === 'remove') {
      // Prevent owner from removing themselves unless they transfer ownership first?
      // Or prevent removing the owner.
      if (requesterRole?.role !== 'owner') {
          // Admin cannot remove another admin or owner
          const { data: targetRole } = await supabaseClient
            .from('group_members')
            .select('role')
            .eq('group_id', group_id)
            .eq('user_id', user_id)
            .single();
            
          if (targetRole && ['owner', 'admin'].includes(targetRole.role)) {
              throw new Error('Forbidden: Admins cannot remove other Admins or Owners.');
          }
      }

      result = await supabaseClient
        .from('group_members')
        .delete()
        .eq('group_id', group_id)
        .eq('user_id', user_id);
    } else if (action === 'update_role') {
      if (requesterRole?.role !== 'owner') {
          throw new Error('Forbidden: Only the owner can change roles.');
      }
      if (!role) {
          throw new Error('Role is required for update_role action.');
      }

      result = await supabaseClient
        .from('group_members')
        .update({ role })
        .eq('group_id', group_id)
        .eq('user_id', user_id);
    } else {
      throw new Error(`Invalid action: ${action}`);
    }

    if (result.error) throw result.error;

    return new Response(JSON.stringify({ success: true, action, user_id, group_id }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    });

  } catch (error: any) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    });
  }
});
