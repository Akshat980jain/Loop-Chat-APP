import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const { phone, password } = await req.json();

    if (!phone || !password) {
      return new Response(
        JSON.stringify({ error: 'Phone number and password are required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Validate phone format
    const phoneRegex = /^\+?[1-9]\d{1,14}$/;
    if (!phoneRegex.test(phone)) {
      return new Response(
        JSON.stringify({ error: 'Invalid phone number format' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // --- Rate limiting check ---
    const ip = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || 'unknown';
    const rateLimitKey = `${phone}_${ip}`;

    try {
      const rateLimitResponse = await fetch(`${supabaseUrl}/functions/v1/check-rate-limit`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${Deno.env.get('SUPABASE_ANON_KEY')}`,
        },
        body: JSON.stringify({
          key: rateLimitKey,
          endpoint: 'login',
          maxAttempts: 5,
          windowMinutes: 15,
        }),
      });

      if (rateLimitResponse.status === 429) {
        const rateLimitData = await rateLimitResponse.json();
        return new Response(
          JSON.stringify({
            error: 'Too many login attempts. Please try again later.',
            retryAfterSeconds: rateLimitData.retryAfterSeconds,
          }),
          { status: 429, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }
    } catch (rlError) {
      // Fail open — if rate limiter is down, allow the request
      console.error('Rate limit check failed (allowing request):', rlError);
    }
    // --- End rate limiting check ---

    // Normalize phone number - remove leading + and country code variations for matching
    const normalizedPhone = phone.replace(/^\+/, '');
    // Also try matching just the last 10 digits (for cases where country code is stored differently)
    const phoneDigits = phone.replace(/\D/g, '');
    const last10Digits = phoneDigits.slice(-10);

    console.log('Looking for phone:', phone, 'normalized:', normalizedPhone, 'last10:', last10Digits);

    // Find user by phone number in profiles - try multiple formats
    let profile = null;
    let profileError = null;

    // First try exact match
    const exactMatch = await supabase
      .from('profiles')
      .select('user_id')
      .eq('phone', phone)
      .maybeSingle();

    if (exactMatch.data) {
      profile = exactMatch.data;
    } else {
      // Try without + prefix
      const normalizedMatch = await supabase
        .from('profiles')
        .select('user_id')
        .eq('phone', normalizedPhone)
        .maybeSingle();

      if (normalizedMatch.data) {
        profile = normalizedMatch.data;
      } else {
        // Try last 10 digits
        const digitsMatch = await supabase
          .from('profiles')
          .select('user_id')
          .eq('phone', last10Digits)
          .maybeSingle();

        if (digitsMatch.data) {
          profile = digitsMatch.data;
        }
        profileError = digitsMatch.error;
      }
    }

    if (profileError) {
      console.error('Error fetching profile:', profileError);
      return new Response(
        JSON.stringify({ error: 'Failed to lookup user' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    if (!profile) {
      console.log('Phone not found:', phone);
      return new Response(
        JSON.stringify({ error: 'Phone number not registered' }),
        { status: 404, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Get user email from auth.users using admin API
    const { data: userData, error: userError } = await supabase.auth.admin.getUserById(profile.user_id);

    if (userError || !userData?.user?.email) {
      console.error('Error fetching user:', userError);
      return new Response(
        JSON.stringify({ error: 'Failed to lookup user email' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const email = userData.user.email;

    // Attempt to sign in with email and password
    // We need to use a separate client with anon key for this
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY')!;
    const supabaseClient = createClient(supabaseUrl, supabaseAnonKey);

    const { data: signInData, error: signInError } = await supabaseClient.auth.signInWithPassword({
      email,
      password,
    });

    if (signInError) {
      console.log('Sign in failed:', signInError.message);
      return new Response(
        JSON.stringify({ error: 'Invalid credentials' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log('Phone login successful for:', phone);

    return new Response(
      JSON.stringify({ 
        success: true, 
        session: signInData.session,
        user: signInData.user 
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in login-with-phone function:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
