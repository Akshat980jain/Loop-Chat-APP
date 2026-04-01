import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * Verifies a Passkey login (authentication) response.
 * 
 * The device has signed the challenge with the private key.
 * We verify the signature against the stored public key,
 * then use Supabase admin API to generate a session for the user.
 */
serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const {
      credentialId,
      clientDataJSON,
      authenticatorData,
      signature,
      userHandle
    } = await req.json();

    if (!credentialId || !clientDataJSON || !authenticatorData || !signature) {
      return new Response(
        JSON.stringify({ error: 'Missing required fields' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // 1. Find the passkey by credential ID
    const { data: passkey, error: pkError } = await supabase
      .from('user_passkeys')
      .select('*')
      .eq('credential_id', credentialId)
      .single();

    if (pkError || !passkey) {
      console.error('Passkey not found for credential:', credentialId);
      return new Response(
        JSON.stringify({ error: 'Passkey not found' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 2. Verify the challenge from clientDataJSON
    const { data: challengeRecord, error: challengeError } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('user_id', passkey.user_id)
      .eq('type', 'authentication')
      .gt('expires_at', new Date().toISOString())
      .single();

    if (challengeError || !challengeRecord) {
      return new Response(
        JSON.stringify({ error: 'Challenge expired or not found. Please try again.' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 3. Parse clientDataJSON and verify challenge match
    try {
      const clientDataStr = atob(clientDataJSON.replace(/-/g, '+').replace(/_/g, '/'));
      const clientData = JSON.parse(clientDataStr);

      if (clientData.challenge !== challengeRecord.challenge) {
        console.error('Challenge mismatch during login');
        return new Response(
          JSON.stringify({ error: 'Challenge verification failed' }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      if (clientData.type !== 'webauthn.get') {
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

    // 4. Verify the signature using Web Crypto API
    const isValid = await verifySignature(
      passkey.public_key,
      authenticatorData,
      clientDataJSON,
      signature
    );

    if (!isValid) {
      console.error('Signature verification failed for credential:', credentialId);
      return new Response(
        JSON.stringify({ error: 'Signature verification failed' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // 5. Update counter and last_used_at (replay prevention)
    await supabase
      .from('user_passkeys')
      .update({
        counter: passkey.counter + 1,
        last_used_at: new Date().toISOString(),
      })
      .eq('id', passkey.id);

    // 6. Clean up the challenge
    await supabase
      .from('passkey_challenges')
      .delete()
      .eq('id', challengeRecord.id);

    // 7. Generate a Supabase session for the user
    //    We use a custom JWT approach: generate tokens via admin API
    const { data: sessionData, error: sessionError } = await supabase.auth.admin
      .generateLink({
        type: 'magiclink',
        email: '', // We'll use direct token generation below
      });

    // Alternative: Use the admin API to create a session directly
    // Since Supabase doesn't have a direct "create session for user" API,
    // we'll use a signed JWT approach with the service role key
    const jwtPayload = {
      sub: passkey.user_id,
      aud: 'authenticated',
      role: 'authenticated',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600, // 1 hour
    };

    // Sign with the JWT secret
    const jwtSecret = Deno.env.get('JWT_SECRET');
    if (!jwtSecret) {
      console.error('JWT_SECRET not configured');
      return new Response(
        JSON.stringify({ error: 'Server configuration error' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const accessToken = await createJWT(jwtPayload, jwtSecret);

    // Generate a refresh token (random 64-char string)
    const refreshBytes = new Uint8Array(48);
    crypto.getRandomValues(refreshBytes);
    const refreshToken = btoa(String.fromCharCode(...refreshBytes))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

    // Get user info for the response
    const { data: { user: authUser } } = await supabase.auth.admin.getUserById(passkey.user_id);

    console.log(`Passkey login successful for user ${passkey.user_id}`);

    return new Response(
      JSON.stringify({
        success: true,
        access_token: accessToken,
        refresh_token: refreshToken,
        user: authUser ? {
          id: authUser.id,
          email: authUser.email,
          phone: authUser.phone,
        } : null,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in passkey-login-verify:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});

/**
 * Verify an ES256 (ECDSA P-256) WebAuthn signature using Web Crypto API.
 */
async function verifySignature(
  publicKeyB64: string,
  authenticatorDataB64: string,
  clientDataJSONB64: string,
  signatureB64: string
): Promise<boolean> {
  try {
    // Decode all base64url values
    const publicKeyDer = base64UrlToUint8Array(publicKeyB64);
    const authenticatorData = base64UrlToUint8Array(authenticatorDataB64);
    const clientDataJSON = base64UrlToUint8Array(clientDataJSONB64);
    const signature = base64UrlToUint8Array(signatureB64);

    // Hash the clientDataJSON
    const clientDataHash = new Uint8Array(
      await crypto.subtle.digest('SHA-256', clientDataJSON.buffer as ArrayBuffer)
    );

    // Concatenate authenticatorData + clientDataHash = signedData
    const signedData = new Uint8Array(authenticatorData.length + clientDataHash.length);
    signedData.set(authenticatorData, 0);
    signedData.set(clientDataHash, authenticatorData.length);

    // Import the public key (SPKI format)
    const key = await crypto.subtle.importKey(
      'spki',
      publicKeyDer.buffer as ArrayBuffer,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify']
    );

    // Convert DER signature to raw format for Web Crypto
    const rawSignature = derToRaw(signature);

    // Verify
    const valid = await crypto.subtle.verify(
      { name: 'ECDSA', hash: 'SHA-256' },
      key,
      rawSignature.buffer as ArrayBuffer,
      signedData.buffer as ArrayBuffer
    );

    return valid;
  } catch (e) {
    console.error('Signature verification error:', e);
    return false;
  }
}

/**
 * Convert base64url string to Uint8Array
 */
function base64UrlToUint8Array(base64url: string): Uint8Array {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - base64.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Convert DER-encoded ECDSA signature to raw format (r + s)
 * WebAuthn signatures are DER-encoded, but Web Crypto expects raw.
 */
function derToRaw(der: Uint8Array): Uint8Array {
  // DER format: 0x30 [total-len] 0x02 [r-len] [r] 0x02 [s-len] [s]
  const raw = new Uint8Array(64);
  
  let offset = 2; // skip 0x30 and total length
  
  // Read r
  offset += 1; // skip 0x02
  const rLen = der[offset++];
  const rStart = rLen === 33 ? offset + 1 : offset; // skip leading zero if present
  const rCopyLen = Math.min(32, rLen);
  raw.set(der.slice(rStart, rStart + rCopyLen), 32 - rCopyLen);
  offset += rLen;
  
  // Read s
  offset += 1; // skip 0x02
  const sLen = der[offset++];
  const sStart = sLen === 33 ? offset + 1 : offset; // skip leading zero if present
  const sCopyLen = Math.min(32, sLen);
  raw.set(der.slice(sStart, sStart + sCopyLen), 64 - sCopyLen);
  
  return raw;
}

/**
 * Create a signed JWT using HMAC-SHA256
 */
async function createJWT(payload: Record<string, any>, secret: string): Promise<string> {
  const header = { alg: 'HS256', typ: 'JWT' };
  
  const encodedHeader = btoa(JSON.stringify(header))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const encodedPayload = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  
  const data = `${encodedHeader}.${encodedPayload}`;
  
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  
  const signatureBytes = new Uint8Array(
    await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data))
  );
  
  const encodedSignature = btoa(String.fromCharCode(...signatureBytes))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  
  return `${data}.${encodedSignature}`;
}
