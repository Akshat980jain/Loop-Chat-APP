import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * Verifies a Passkey registration response from the Android device.
 * 
 * The device has created a new key pair and signed the challenge.
 * We verify the challenge matches what we stored, then save the
 * public key for future logins.
 */
serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const { credentialId, publicKey, clientDataJSON, attestationObject, transports, deviceName }
      = await req.json();

    if (!credentialId || !publicKey || !clientDataJSON) {
      return new Response(
        JSON.stringify({ error: 'Missing required fields' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    // Authenticate user
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: 'Missing authorization header' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

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

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // 1. Verify the challenge — fetch the stored challenge
    const { data: challengeRecord, error: challengeError } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('user_id', user.id)
      .eq('type', 'registration')
      .gt('expires_at', new Date().toISOString())
      .single();

    if (challengeError || !challengeRecord) {
      return new Response(
        JSON.stringify({ error: 'Challenge expired or not found. Please try again.' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 2. Verify clientDataJSON contains the correct challenge
    //    The clientDataJSON is base64url-encoded. Decode it and check.
    try {
      const clientDataStr = atob(clientDataJSON.replace(/-/g, '+').replace(/_/g, '/'));
      const clientData = JSON.parse(clientDataStr);
      
      if (clientData.challenge !== challengeRecord.challenge) {
        console.error('Challenge mismatch!', {
          expected: challengeRecord.challenge,
          received: clientData.challenge
        });
        return new Response(
          JSON.stringify({ error: 'Challenge verification failed' }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      if (clientData.type !== 'webauthn.create') {
        return new Response(
          JSON.stringify({ error: 'Invalid client data type' }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }
    } catch (e) {
      console.error('Failed to parse clientDataJSON:', e);
      return new Response(
        JSON.stringify({ error: 'Invalid client data' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 3. Store the passkey
    const { error: insertError } = await supabase
      .from('user_passkeys')
      .insert({
        user_id: user.id,
        credential_id: credentialId,
        public_key: publicKey,
        counter: 0,
        transports: transports || [],
        device_name: deviceName || 'Android Device',
      });

    if (insertError) {
      console.error('Failed to store passkey:', insertError);
      if (insertError.code === '23505') {
        return new Response(
          JSON.stringify({ error: 'This passkey is already registered' }),
          { status: 409, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }
      return new Response(
        JSON.stringify({ error: 'Failed to save passkey' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 4. Clean up the challenge
    await supabase
      .from('passkey_challenges')
      .delete()
      .eq('id', challengeRecord.id);

    console.log(`Passkey registered for user ${user.id}, credentialId: ${credentialId}`);

    return new Response(
      JSON.stringify({ success: true, message: 'Passkey registered successfully' }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in passkey-register-verify:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
