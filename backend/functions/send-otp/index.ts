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
    const { phone } = await req.json();

    if (!phone) {
      return new Response(
        JSON.stringify({ error: 'Phone number is required' }),
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

    // --- Rate limiting check (stricter for OTP: 3 per 15 min) ---
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
          endpoint: 'send-otp',
          maxAttempts: 3,
          windowMinutes: 15,
        }),
      });

      if (rateLimitResponse.status === 429) {
        const rateLimitData = await rateLimitResponse.json();
        return new Response(
          JSON.stringify({
            error: 'Too many OTP requests. Please try again later.',
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

    // Check if phone exists in profiles
    const { data: profile, error: profileError } = await supabase
      .from('profiles')
      .select('user_id')
      .eq('phone', phone)
      .single();

    if (profileError || !profile) {
      console.log('Phone not found in profiles:', phone);
      // Don't reveal if phone exists or not for security
      return new Response(
        JSON.stringify({ success: true, message: 'If this phone is registered, you will receive an OTP' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Generate 6-digit OTP
    const otp = Math.floor(100000 + Math.random() * 900000).toString();

    // Delete any existing OTPs for this phone
    await supabase
      .from('password_reset_otps')
      .delete()
      .eq('phone', phone);

    // Store OTP in database
    const { error: insertError } = await supabase
      .from('password_reset_otps')
      .insert({
        phone,
        otp_code: otp,
        expires_at: new Date(Date.now() + 10 * 60 * 1000).toISOString(), // 10 minutes
      });

    if (insertError) {
      console.error('Error storing OTP:', insertError);
      return new Response(
        JSON.stringify({ error: 'Failed to generate OTP' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Send OTP via Twilio
    const twilioAccountSid = Deno.env.get('TWILIO_ACCOUNT_SID')!;
    const twilioAuthToken = Deno.env.get('TWILIO_AUTH_TOKEN')!;
    const twilioPhoneNumberRaw = Deno.env.get('TWILIO_PHONE_NUMBER')!;
    const twilioMessagingServiceSid = Deno.env.get('TWILIO_MESSAGING_SERVICE_SID');

    const twilioUrl = `https://api.twilio.com/2010-04-01/Accounts/${twilioAccountSid}/Messages.json`;
    const twilioAuth = btoa(`${twilioAccountSid}:${twilioAuthToken}`);

    const messageBody = `Your password reset code is: ${otp}. This code expires in 10 minutes.`;

    // Twilio expects E.164 for phone numbers. If the secret is missing '+', add it.
    const twilioFrom = twilioPhoneNumberRaw?.startsWith('+')
      ? twilioPhoneNumberRaw
      : `+${twilioPhoneNumberRaw}`;

    const params = new URLSearchParams({
      To: phone,
      Body: messageBody,
    });

    // Prefer Messaging Service SID if configured (more reliable than a raw From number)
    if (twilioMessagingServiceSid) {
      params.set('MessagingServiceSid', twilioMessagingServiceSid);
    } else {
      params.set('From', twilioFrom);
    }

    const twilioResponse = await fetch(twilioUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Basic ${twilioAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: params,
    });

    if (!twilioResponse.ok) {
      const twilioError = await twilioResponse.text();
      console.error('Twilio error:', twilioError);
      return new Response(
        JSON.stringify({ error: 'Failed to send OTP' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log('OTP sent successfully to:', phone);

    return new Response(
      JSON.stringify({ success: true, message: 'OTP sent successfully' }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in send-otp function:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
