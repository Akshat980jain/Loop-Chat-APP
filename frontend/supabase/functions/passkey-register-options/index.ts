import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * Generates registration options for a new Passkey.
 * 
 * Called by the Android app when the user wants to enable passkey login.
 * Returns a random challenge and the user's info so the device can
 * generate a new key pair.
 */
serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    // Get the user's JWT from the Authorization header
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: 'Missing authorization header' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Create a user-scoped client to get the user info
    const supabaseUser = createClient(supabaseUrl, Deno.env.get('SUPABASE_ANON_KEY')!, {
      global: { headers: { Authorization: authHeader } }
    });
    const { data: { user }, error: userError } = await supabaseUser.auth.getUser();
    if (userError || !user) {
      return new Response(
        JSON.stringify({ error: 'Unauthorized' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Service-role client for DB writes
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // Generate a random challenge (32 bytes, base64url encoded)
    const challengeBytes = new Uint8Array(32);
    crypto.getRandomValues(challengeBytes);
    const challenge = btoa(String.fromCharCode(...challengeBytes))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

    // Clean up any existing challenges for this user
    await supabase
      .from('passkey_challenges')
      .delete()
      .eq('user_id', user.id);

    // Store the challenge
    const { error: insertError } = await supabase
      .from('passkey_challenges')
      .insert({
        user_id: user.id,
        challenge: challenge,
        type: 'registration',
      });

    if (insertError) {
      console.error('Failed to store challenge:', insertError);
      return new Response(
        JSON.stringify({ error: 'Failed to generate registration options' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Get existing passkeys to set as excludeCredentials
    const { data: existingPasskeys } = await supabase
      .from('user_passkeys')
      .select('credential_id')
      .eq('user_id', user.id);

    const excludeCredentials = (existingPasskeys || []).map((pk: any) => ({
      id: pk.credential_id,
      type: 'public-key',
    }));

    // Build the PublicKeyCredentialCreationOptions JSON
    // This is what Android's CredentialManager expects
    const registrationOptions = {
      challenge: challenge,
      rp: {
        name: "Loop Chat",
        id: Deno.env.get('PASSKEY_RP_ID') || "loop-chat.vercel.app",
      },
      user: {
        id: btoa(user.id).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''),
        name: user.email || user.phone || user.id,
        displayName: user.user_metadata?.full_name || user.email || "Loop Chat User",
      },
      pubKeyCredParams: [
        { alg: -7,   type: "public-key" },  // ES256 (ECDSA w/ SHA-256)
        { alg: -257, type: "public-key" },  // RS256 (RSASSA-PKCS1-v1_5 w/ SHA-256)
      ],
      timeout: 120000,  // 2 minutes
      excludeCredentials: excludeCredentials,
      authenticatorSelection: {
        authenticatorAttachment: "platform",
        residentKey: "required",
        userVerification: "required",
      },
      attestation: "none",
    };

    return new Response(
      JSON.stringify(registrationOptions),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in passkey-register-options:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
