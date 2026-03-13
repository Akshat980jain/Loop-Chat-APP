import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Simple SHA-256 hash function for Deno
async function sha256(message: string): Promise<string> {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? '';
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';

    // Authenticate the user
    const authClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: {
        headers: { Authorization: req.headers.get('Authorization')! },
      },
    });

    const { data: { user }, error: userError } = await authClient.auth.getUser();

    if (userError || !user) {
      return new Response(
        JSON.stringify({ error: 'Unauthorized' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const body = await req.json();
    const { sessionId, revokeAllOthers } = body;

    const serviceClient = createClient(supabaseUrl, supabaseServiceKey);

    if (revokeAllOthers) {
      // Revoke all sessions except the current one
      const authHeader = req.headers.get('Authorization') || '';
      const accessToken = authHeader.replace('Bearer ', '');
      const currentTokenHash = await sha256(accessToken);

      const { error: revokeError } = await serviceClient
        .from('user_sessions')
        .update({ is_revoked: true })
        .eq('user_id', user.id)
        .neq('session_token_hash', currentTokenHash)
        .eq('is_revoked', false);

      if (revokeError) {
        console.error('Error revoking sessions:', revokeError);
        return new Response(
          JSON.stringify({ error: 'Failed to revoke sessions' }),
          { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      console.log('All other sessions revoked for user:', user.id);

      return new Response(
        JSON.stringify({ success: true, message: 'All other sessions revoked' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    if (sessionId) {
      // Revoke a specific session — verify it belongs to the user first
      const { data: session } = await serviceClient
        .from('user_sessions')
        .select('user_id')
        .eq('id', sessionId)
        .single();

      if (!session || session.user_id !== user.id) {
        return new Response(
          JSON.stringify({ error: 'Session not found' }),
          { status: 404, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      const { error: revokeError } = await serviceClient
        .from('user_sessions')
        .update({ is_revoked: true })
        .eq('id', sessionId);

      if (revokeError) {
        console.error('Error revoking session:', revokeError);
        return new Response(
          JSON.stringify({ error: 'Failed to revoke session' }),
          { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      console.log('Session revoked:', sessionId);

      return new Response(
        JSON.stringify({ success: true, message: 'Session revoked' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    return new Response(
      JSON.stringify({ error: 'Either sessionId or revokeAllOthers is required' }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Unexpected error in revoke-session:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
