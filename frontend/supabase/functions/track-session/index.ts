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
    // Authenticate user with their token
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? '';
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';

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

    const { device_info } = await req.json();

    // Get the access token from Authorization header
    const authHeader = req.headers.get('Authorization') || '';
    const accessToken = authHeader.replace('Bearer ', '');
    const tokenHash = await sha256(accessToken);

    // Extract IP address
    const ipAddress = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
      || req.headers.get('cf-connecting-ip')
      || req.headers.get('x-real-ip')
      || 'unknown';

    // Use service role client to bypass RLS for insert
    const serviceClient = createClient(supabaseUrl, supabaseServiceKey);

    // Check if a session with this token hash already exists
    const { data: existingSession } = await serviceClient
      .from('user_sessions')
      .select('id')
      .eq('session_token_hash', tokenHash)
      .eq('user_id', user.id)
      .maybeSingle();

    if (existingSession) {
      // Update last_active
      await serviceClient
        .from('user_sessions')
        .update({ last_active: new Date().toISOString(), device_info, ip_address: ipAddress })
        .eq('id', existingSession.id);

      return new Response(
        JSON.stringify({ success: true, session_id: existingSession.id, updated: true }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Insert new session
    const { data: newSession, error: insertError } = await serviceClient
      .from('user_sessions')
      .insert({
        user_id: user.id,
        session_token_hash: tokenHash,
        device_info: device_info || {},
        ip_address: ipAddress,
      })
      .select('id')
      .single();

    if (insertError) {
      console.error('Error inserting session:', insertError);
      return new Response(
        JSON.stringify({ error: 'Failed to track session' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Clean up sessions older than 30 days for this user
    await serviceClient
      .from('user_sessions')
      .delete()
      .eq('user_id', user.id)
      .lt('created_at', new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString());

    console.log('Session tracked for user:', user.id);

    return new Response(
      JSON.stringify({ success: true, session_id: newSession.id }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Unexpected error in track-session:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
