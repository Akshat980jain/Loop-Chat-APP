import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Helper: Base64URL encoding
function base64url(buf: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(buf)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

// Helper: Get Google OAuth2 access token for FCM v1 API using Web Crypto (0 dependencies)
async function getFcmAccessToken(clientEmail: string, privateKeyPem: string): Promise<string> {
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  
  // Clean PEM
  const pemContents = privateKeyPem
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s/g, "");
  
  // Convert Base64 to ArrayBuffer
  const binaryDerString = atob(pemContents);
  const binaryDer = new Uint8Array(binaryDerString.length);
  for (let i = 0; i < binaryDerString.length; i++) {
    binaryDer[i] = binaryDerString.charCodeAt(i);
  }

  // Import private key in PKCS#8 format
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256",
    },
    false,
    ["sign"]
  );

  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };

  const encoder = new TextEncoder();
  const encodedHeader = base64url(encoder.encode(JSON.stringify(header)));
  const encodedPayload = base64url(encoder.encode(JSON.stringify(payload)));
  const stringToSign = `${encodedHeader}.${encodedPayload}`;

  // Sign JWT
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    encoder.encode(stringToSign)
  );

  const jwt = `${stringToSign}.${base64url(signature)}`;

  // Request Access Token
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to retrieve OAuth token: ${errorText}`);
  }

  const data = await response.json();
  return data.access_token;
}

// Helper: Send request to Google FCM v1 API
async function sendFcmMessage(projectId: string, accessToken: string, messagePayload: any) {
  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const response = await fetch(fcmUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ message: messagePayload }),
  });

  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(`FCM API error: ${response.status} - ${responseText}`);
  }
  return JSON.parse(responseText);
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const payload = await req.json();
    console.log("Notification trigger received:", JSON.stringify(payload));

    const { table, event, record } = payload;
    if (!record) {
      return new Response(JSON.stringify({ error: "Missing record payload" }), { status: 400, headers: corsHeaders });
    }

    // Load Firebase secrets
    const projectId = Deno.env.get('FIREBASE_PROJECT_ID');
    const clientEmail = Deno.env.get('FIREBASE_CLIENT_EMAIL');
    const privateKey = Deno.env.get('FIREBASE_PRIVATE_KEY');

    if (!projectId || !clientEmail || !privateKey) {
      console.error("Firebase environment configuration is missing on Supabase.");
      return new Response(
        JSON.stringify({ error: "Firebase environment keys not set on Supabase secrets" }),
        { status: 500, headers: corsHeaders }
      );
    }

    // Connect to Supabase
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // Get FCM Access Token
    const fcmAccessToken = await getFcmAccessToken(clientEmail, privateKey);

    // --- CASE 1: Call Table Insert (Incoming Call Signaling) ---
    if (table === 'calls' && (event === 'INSERT' || (event === 'UPDATE' && record.status === 'ringing'))) {
      const { id: callId, caller_id: callerId, callee_id: calleeId, call_type: callType, room_url: roomUrl, callee_token: calleeToken } = record;

      console.log(`Processing call signaling: callId=${callId}, calleeId=${calleeId}`);

      // Query callee FCM token
      const { data: calleeSettings, error: tokenError } = await supabase
        .from('user_settings')
        .select('fcm_token')
        .eq('user_id', calleeId)
        .single();

      if (tokenError || !calleeSettings?.fcm_token) {
        console.warn(`Callee ${calleeId} has no registered FCM token. Cannot deliver notification.`);
        return new Response(JSON.stringify({ success: false, message: "No FCM token for callee" }), { headers: corsHeaders });
      }

      // Query caller name
      const { data: callerProfile } = await supabase
        .from('profiles')
        .select('full_name')
        .eq('user_id', callerId)
        .single();

      const callerName = callerProfile?.full_name || "Someone";

      // Prepare FCM Data-only message payload (crucial for background wake up on Android)
      const fcmMessage = {
        token: calleeSettings.fcm_token,
        data: {
          type: "incoming_call",
          call_id: String(callId),
          caller_id: String(callerId),
          caller_name: String(callerName),
          call_type: String(callType),
          room_url: String(roomUrl || ""),
          callee_token: String(calleeToken || ""),
        },
        android: {
          priority: "high",
          ttl: "0s", // Deliver immediately, do not cache/delay
        }
      };

      const result = await sendFcmMessage(projectId, fcmAccessToken, fcmMessage);
      console.log("FCM Call notification sent successfully:", result);
      return new Response(JSON.stringify({ success: true, result }), { headers: corsHeaders });
    }

    // --- CASE 2: Messages Table Insert (Chat Message Notification) ---
    if (table === 'messages' && event === 'INSERT') {
      const { conversation_id: conversationId, sender_id: senderId, content, message_type: messageType } = record;

      console.log(`Processing chat message notification: conversationId=${conversationId}`);

      // Find other participants in the conversation
      const { data: participants, error: partError } = await supabase
        .from('conversation_participants')
        .select('user_id')
        .eq('conversation_id', conversationId)
        .neq('user_id', senderId);

      if (partError || !participants || participants.length === 0) {
        console.log("No other participants in the conversation to notify.");
        return new Response(JSON.stringify({ success: true, message: "No recipients" }), { headers: corsHeaders });
      }

      // Fetch sender name
      const { data: senderProfile } = await supabase
        .from('profiles')
        .select('full_name')
        .eq('user_id', senderId)
        .single();

      const senderName = senderProfile?.full_name || "Someone";
      const messagePreview = messageType === "text" ? content : `Sent a ${messageType}`;

      const userIds = participants.map((p: any) => p.user_id);

      // Fetch FCM tokens of all participants
      const { data: settingsList, error: listError } = await supabase
        .from('user_settings')
        .select('user_id, fcm_token')
        .in('user_id', userIds);

      if (listError || !settingsList || settingsList.length === 0) {
        console.log("No registered FCM tokens found for recipients.");
        return new Response(JSON.stringify({ success: true, message: "No FCM tokens found" }), { headers: corsHeaders });
      }

      // Send to each recipient FCM token
      const sendPromises = settingsList
        .filter((s: any) => s.fcm_token)
        .map(async (recipient: any) => {
          const fcmMessage = {
            token: recipient.fcm_token,
            data: {
              type: "new_message",
              sender_name: String(senderName),
              message_preview: String(messagePreview),
              conversation_id: String(conversationId),
              sender_id: String(senderId),
            },
            android: {
              priority: "high"
            }
          };
          try {
            return await sendFcmMessage(projectId, fcmAccessToken, fcmMessage);
          } catch (err) {
            console.error(`Failed to send FCM to user ${recipient.user_id}:`, err);
            return null;
          }
        });

      const results = await Promise.all(sendPromises);
      console.log(`Delivered chat notifications. Sent ${results.filter(r => r !== null).length} of ${sendPromises.length}`);
      return new Response(JSON.stringify({ success: true, delivered: results.length }), { headers: corsHeaders });
    }

    return new Response(JSON.stringify({ success: true, message: "Table/event ignored" }), { headers: corsHeaders });

  } catch (error: any) {
    console.error("Error in send-notification function:", error);
    return new Response(
      JSON.stringify({ error: error.message || "Internal Server Error" }),
      { status: 500, headers: corsHeaders }
    );
  }
});
