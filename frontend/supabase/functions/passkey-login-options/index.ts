import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * Generates authentication (login) options for Passkey login.
 * 
 * This is called BEFORE the user is authenticated (they're on the login screen).
 * We accept an email/phone to find the user's passkeys, then return a challenge
 * and the list of credential IDs the device should match against.
 */
serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const { email, phone } = await req.json();

    if (!email && !phone) {
      return new Response(
        JSON.stringify({ error: 'Email or phone is required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // 1. Find the user by email or phone
    let userId: string | null = null;

    if (email) {
      const { data: users, error } = await supabase.auth.admin.listUsers();
      if (!error && users) {
        const found = users.users.find(
          (u: any) => u.email?.toLowerCase() === email.toLowerCase()
        );
        if (found) userId = found.id;
      }
    } else if (phone) {
      const { data: profile } = await supabase
        .from('profiles')
        .select('user_id')
        .eq('phone', phone)
        .single();
      if (profile) userId = profile.user_id;
    }

    if (!userId) {
      // Don't reveal whether the user exists — return empty credentials
      const challengeBytes = new Uint8Array(32);
      crypto.getRandomValues(challengeBytes);
      const challenge = btoa(String.fromCharCode(...challengeBytes))
        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

      return new Response(
        JSON.stringify({
          challenge: challenge,
          rpId: Deno.env.get('PASSKEY_RP_ID') || "loop-chat.vercel.app",
          allowCredentials: [],
          timeout: 120000,
          userVerification: "required",
        }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 2. Get the user's registered passkeys
    const { data: passkeys, error: pkError } = await supabase
      .from('user_passkeys')
      .select('credential_id, transports')
      .eq('user_id', userId);

    if (pkError || !passkeys || passkeys.length === 0) {
      return new Response(
        JSON.stringify({ error: 'No passkeys registered for this account' }),
        { status: 404, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 3. Generate a challenge
    const challengeBytes = new Uint8Array(32);
    crypto.getRandomValues(challengeBytes);
    const challenge = btoa(String.fromCharCode(...challengeBytes))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

    // Clean up old challenges
    await supabase
      .from('passkey_challenges')
      .delete()
      .eq('user_id', userId);

    // Store the new challenge
    await supabase
      .from('passkey_challenges')
      .insert({
        user_id: userId,
        challenge: challenge,
        type: 'authentication',
      });

    // 4. Build authentication options
    const allowCredentials = passkeys.map((pk: any) => ({
      id: pk.credential_id,
      type: 'public-key',
      transports: pk.transports || [],
    }));

    const authenticationOptions = {
      challenge: challenge,
      rpId: Deno.env.get('PASSKEY_RP_ID') || "loop-chat.vercel.app",
      allowCredentials: allowCredentials,
      timeout: 120000,
      userVerification: "required",
    };

    return new Response(
      JSON.stringify(authenticationOptions),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in passkey-login-options:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
