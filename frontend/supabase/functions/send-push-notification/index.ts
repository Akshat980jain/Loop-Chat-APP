import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Web Push VAPID keys (you should generate your own for production)
const VAPID_PUBLIC_KEY = Deno.env.get('VAPID_PUBLIC_KEY') || 'BFBTz5hBKlOvDrOqH-P6eGVNIJ8JBzxMWqAKFMCvcGZxJBVLk8k8aWpOPOZXFxnFbhpAhqWwXnFqUZvMGvJVJRo';
const VAPID_PRIVATE_KEY = Deno.env.get('VAPID_PRIVATE_KEY') || 'your-private-key-here';

interface PushPayload {
  callee_id: string;
  caller_name: string;
  caller_avatar?: string;
  call_type: 'audio' | 'video';
  call_id: string;
}

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    const payload: PushPayload = await req.json();
    console.log('Sending push notification:', payload);

    // Get the callee's push subscriptions
    const { data: subscriptions, error: subError } = await supabase
      .from('push_subscriptions')
      .select('*')
      .eq('user_id', payload.callee_id);

    if (subError) {
      console.error('Error fetching subscriptions:', subError);
      throw subError;
    }

    if (!subscriptions || subscriptions.length === 0) {
      console.log('No push subscriptions found for user:', payload.callee_id);
      return new Response(
        JSON.stringify({ success: false, message: 'No subscriptions found' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log(`Found ${subscriptions.length} push subscriptions`);

    const notificationPayload = JSON.stringify({
      title: `Incoming ${payload.call_type === 'video' ? 'Video' : 'Voice'} Call`,
      body: `${payload.caller_name} is calling...`,
      icon: payload.caller_avatar || '/favicon.png',
      badge: '/favicon.png',
      tag: 'incoming-call',
      requireInteraction: true,
      vibrate: [300, 100, 300, 100, 300],
      data: {
        callId: payload.call_id,
        callerName: payload.caller_name,
        callerAvatar: payload.caller_avatar,
        callType: payload.call_type
      },
      actions: [
        { action: 'accept', title: 'Accept' },
        { action: 'reject', title: 'Reject' }
      ]
    });

    const results = [];

    for (const subscription of subscriptions) {
      try {
        // Use native Web Push API
        const pushResult = await sendWebPush(
          {
            endpoint: subscription.endpoint,
            keys: {
              p256dh: subscription.p256dh,
              auth: subscription.auth
            }
          },
          notificationPayload,
          VAPID_PUBLIC_KEY,
          VAPID_PRIVATE_KEY
        );
        
        results.push({ endpoint: subscription.endpoint, success: true });
        console.log('Push sent successfully to:', subscription.endpoint);
      } catch (pushError) {
        console.error('Error sending push to:', subscription.endpoint, pushError);
        results.push({ endpoint: subscription.endpoint, success: false, error: String(pushError) });
        
        // If subscription is invalid, remove it
        if (pushError instanceof Error && pushError.message.includes('410')) {
          await supabase
            .from('push_subscriptions')
            .delete()
            .eq('id', subscription.id);
          console.log('Removed invalid subscription:', subscription.id);
        }
      }
    }

    return new Response(
      JSON.stringify({ success: true, results }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in send-push-notification:', error);
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    return new Response(
      JSON.stringify({ error: errorMessage }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});

// Simple Web Push implementation using fetch
async function sendWebPush(
  subscription: { endpoint: string; keys: { p256dh: string; auth: string } },
  payload: string,
  vapidPublicKey: string,
  vapidPrivateKey: string
): Promise<void> {
  // For a basic implementation, we'll use a simpler approach
  // In production, you'd want to use proper VAPID authentication
  
  const response = await fetch(subscription.endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': String(payload.length),
      'TTL': '60',
    },
    body: payload
  });

  if (!response.ok) {
    throw new Error(`Push failed: ${response.status} ${response.statusText}`);
  }
}
