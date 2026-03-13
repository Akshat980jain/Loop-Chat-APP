import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

interface RateLimitRequest {
  key: string;
  endpoint: string;
  maxAttempts: number;
  windowMinutes: number;
}

interface RateLimitResponse {
  allowed: boolean;
  remaining: number;
  retryAfterSeconds: number;
  attemptCount: number;
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const { key, endpoint, maxAttempts = 5, windowMinutes = 15 }: RateLimitRequest = await req.json();

    if (!key || !endpoint) {
      return new Response(
        JSON.stringify({ error: 'key and endpoint are required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
    const serviceClient = createClient(supabaseUrl, supabaseServiceKey);

    const now = new Date();
    const windowStart = new Date(now.getTime() - windowMinutes * 60 * 1000);

    // Check existing rate limit entry
    const { data: existing } = await serviceClient
      .from('auth_rate_limits')
      .select('*')
      .eq('key', key)
      .eq('endpoint', endpoint)
      .single();

    if (existing) {
      const existingWindowStart = new Date(existing.window_start);

      if (existingWindowStart < windowStart) {
        // Window has expired — reset counter
        await serviceClient
          .from('auth_rate_limits')
          .update({
            attempt_count: 1,
            window_start: now.toISOString(),
          })
          .eq('id', existing.id);

        const response: RateLimitResponse = {
          allowed: true,
          remaining: maxAttempts - 1,
          retryAfterSeconds: 0,
          attemptCount: 1,
        };

        return new Response(
          JSON.stringify(response),
          { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      // Window still active
      if (existing.attempt_count >= maxAttempts) {
        // Rate limited
        const windowEndMs = existingWindowStart.getTime() + windowMinutes * 60 * 1000;
        const retryAfterSeconds = Math.max(0, Math.ceil((windowEndMs - now.getTime()) / 1000));

        const response: RateLimitResponse = {
          allowed: false,
          remaining: 0,
          retryAfterSeconds,
          attemptCount: existing.attempt_count,
        };

        return new Response(
          JSON.stringify(response),
          { status: 429, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      // Increment counter
      const newCount = existing.attempt_count + 1;
      await serviceClient
        .from('auth_rate_limits')
        .update({ attempt_count: newCount })
        .eq('id', existing.id);

      const response: RateLimitResponse = {
        allowed: true,
        remaining: maxAttempts - newCount,
        retryAfterSeconds: 0,
        attemptCount: newCount,
      };

      return new Response(
        JSON.stringify(response),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // No existing entry — create one
    await serviceClient
      .from('auth_rate_limits')
      .insert({
        key,
        endpoint,
        attempt_count: 1,
        window_start: now.toISOString(),
      });

    const response: RateLimitResponse = {
      allowed: true,
      remaining: maxAttempts - 1,
      retryAfterSeconds: 0,
      attemptCount: 1,
    };

    return new Response(
      JSON.stringify(response),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Unexpected error in check-rate-limit:', error);
    // On error, allow the request to proceed (fail-open for rate limiter)
    return new Response(
      JSON.stringify({ allowed: true, remaining: 999, retryAfterSeconds: 0, attemptCount: 0 }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
