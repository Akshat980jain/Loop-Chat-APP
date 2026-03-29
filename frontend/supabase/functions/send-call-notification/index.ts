/// <reference path="../deno-shims/deno.d.ts" />
// @ts-ignore - Deno URL import resolved at runtime
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
// @ts-ignore - Deno URL import resolved at runtime
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

/**
 * Edge Function to send FCM push notifications for incoming calls.
 * 
 * This function supports two modes:
 * 1. FCM v1 API (recommended) - Uses service account for authentication
 * 2. Legacy FCM API - Uses FCM_SERVER_KEY (requires enabling deprecated API)
 * 
 * Set GOOGLE_SERVICE_ACCOUNT environment variable for v1 API (JSON string of service account).
 * Set FCM_SERVER_KEY for legacy API.
 */

interface ServiceAccount {
    type: string;
    project_id: string;
    private_key_id: string;
    private_key: string;
    client_email: string;
    client_id: string;
    auth_uri: string;
    token_uri: string;
}

/**
 * Generate a JWT for Google OAuth2 authentication
 */
async function createJwt(serviceAccount: ServiceAccount): Promise<string> {
    const header = {
        alg: "RS256",
        typ: "JWT"
    };

    const now = Math.floor(Date.now() / 1000);
    const payload = {
        iss: serviceAccount.client_email,
        sub: serviceAccount.client_email,
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
        scope: "https://www.googleapis.com/auth/firebase.messaging"
    };

    // Base64URL encode
    const encoder = new TextEncoder();
    const base64url = (data: string) => {
        return btoa(data)
            .replace(/\+/g, '-')
            .replace(/\//g, '_')
            .replace(/=/g, '');
    };

    const encodedHeader = base64url(JSON.stringify(header));
    const encodedPayload = base64url(JSON.stringify(payload));
    const signInput = `${encodedHeader}.${encodedPayload}`;

    // Import the private key and sign
    const privateKeyPem = serviceAccount.private_key;
    const pemContents = privateKeyPem
        .replace(/-----BEGIN PRIVATE KEY-----/, '')
        .replace(/-----END PRIVATE KEY-----/, '')
        .replace(/\n/g, '');

    const binaryKey = Uint8Array.from(atob(pemContents), c => c.charCodeAt(0));

    const cryptoKey = await crypto.subtle.importKey(
        "pkcs8",
        binaryKey,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false,
        ["sign"]
    );

    const signature = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        cryptoKey,
        encoder.encode(signInput)
    );

    const encodedSignature = base64url(
        String.fromCharCode(...new Uint8Array(signature))
    );

    return `${signInput}.${encodedSignature}`;
}

/**
 * Get OAuth2 access token using service account
 */
async function getAccessToken(serviceAccount: ServiceAccount): Promise<string> {
    const jwt = await createJwt(serviceAccount);

    const response = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
    });

    const data = await response.json();
    if (!response.ok) {
        throw new Error(`OAuth error: ${data.error_description || data.error}`);
    }

    return data.access_token;
}

/**
 * Send FCM notification using v1 API (modern approach)
 */
async function sendFcmV1(
    serviceAccount: ServiceAccount,
    fcmToken: string,
    data: Record<string, string>
): Promise<{ success: boolean; messageId?: string; error?: string }> {
    const accessToken = await getAccessToken(serviceAccount);

    const message = {
        message: {
            token: fcmToken,
            data: data,
            android: {
                priority: "HIGH",
                ttl: "30s"
            }
        }
    };

    const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${accessToken}`,
            },
            body: JSON.stringify(message),
        }
    );

    const result = await response.json();

    if (response.ok) {
        return { success: true, messageId: result.name };
    } else {
        return { success: false, error: result.error?.message || "FCM v1 error" };
    }
}

/**
 * Send FCM notification using legacy API
 */
async function sendFcmLegacy(
    serverKey: string,
    fcmToken: string,
    data: Record<string, string>
): Promise<{ success: boolean; messageId?: string; error?: string }> {
    const fcmMessage = {
        to: fcmToken,
        priority: "high",
        data: data,
        android: {
            priority: "high",
            ttl: "30s",
        },
    };

    const response = await fetch("https://fcm.googleapis.com/fcm/send", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `key=${serverKey}`,
        },
        body: JSON.stringify(fcmMessage),
    });

    const result = await response.json();

    if (result.success === 1) {
        return { success: true, messageId: result.results?.[0]?.message_id };
    } else {
        return { success: false, error: result.results?.[0]?.error || "Legacy FCM error" };
    }
}

serve(async (req: Request) => {
    // Handle CORS preflight
    if (req.method === "OPTIONS") {
        return new Response("ok", { headers: corsHeaders });
    }

    try {
        const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
        const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
        const GOOGLE_SERVICE_ACCOUNT = Deno.env.get("GOOGLE_SERVICE_ACCOUNT");
        const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY");

        if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
            throw new Error("Missing Supabase environment variables");
        }

        // Check for either FCM authentication method
        const hasServiceAccount = !!GOOGLE_SERVICE_ACCOUNT;
        const hasServerKey = !!FCM_SERVER_KEY;

        if (!hasServiceAccount && !hasServerKey) {
            console.warn("FCM not configured - set GOOGLE_SERVICE_ACCOUNT or FCM_SERVER_KEY");
            return new Response(
                JSON.stringify({ success: false, error: "FCM not configured" }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

        const {
            callId,
            callerId,
            calleeId,
            callerName,
            callType,
            roomUrl,
            calleeToken,
            isGroupCall,
            groupId
        } = await req.json();

        console.log(`Sending call notification: callId=${callId}, from=${callerName}, to=${calleeId}, isGroup=${isGroupCall}`);

        let targetTokens: { userId: string, token: string }[] = [];

        if (isGroupCall && groupId) {
             // Fetch all members of the group
             const { data: participants, error: participantsError } = await supabase
                 .from("conversation_participants")
                 .select("user_id")
                 .eq("conversation_id", groupId);
             
             if (!participantsError && participants) {
                 const allMemberIds = participants.map((p: any) => p.user_id).filter((id: string) => id !== callerId);
                 
                 // Fetch FCM tokens for all members
                 if (allMemberIds.length > 0) {
                     const { data: settingsData } = await supabase
                         .from("user_settings")
                         .select("user_id, fcm_token")
                         .in("user_id", allMemberIds)
                         .not("fcm_token", "is", null);
                     
                     if (settingsData) {
                         targetTokens = settingsData.map((s: any) => ({ userId: s.user_id, token: s.fcm_token }));
                     }
                 }
             }
        } else if (calleeId) {
            // Fetch callee's FCM token from user_settings
            const { data: calleeSettings, error: settingsError } = await supabase
                .from("user_settings")
                .select("fcm_token")
                .eq("user_id", calleeId)
                .single();

            if (!settingsError && calleeSettings?.fcm_token) {
                targetTokens = [{ userId: calleeId, token: calleeSettings.fcm_token }];
            }
        }

        if (targetTokens.length === 0) {
            console.warn("No targets have FCM token registered");
            return new Response(
                JSON.stringify({ success: false, error: "No targets have FCM token" }),
                {
                    status: 404,
                    headers: { ...corsHeaders, "Content-Type": "application/json" }
                }
            );
        }

        // Prepare notification data
        const notificationData = {
            type: "incoming_call",
            call_id: callId,
            caller_id: callerId,
            caller_name: callerName || "Unknown",
            call_type: callType || "audio",
            room_url: roomUrl || "",
            callee_token: calleeToken || "",
            is_group_call: isGroupCall ? "true" : "false",
            group_id: groupId || "",
            timestamp: Date.now().toString(),
        };

        const results = await Promise.all(targetTokens.map(async (target) => {
            let result: { success: boolean; messageId?: string; error?: string };
            
            if (hasServiceAccount) {
                try {
                    const serviceAccount = JSON.parse(GOOGLE_SERVICE_ACCOUNT!) as ServiceAccount;
                    result = await sendFcmV1(serviceAccount, target.token, notificationData);
                } catch (parseError) {
                    if (hasServerKey) {
                        result = await sendFcmLegacy(FCM_SERVER_KEY!, target.token, notificationData);
                    } else {
                        throw new Error("Invalid GOOGLE_SERVICE_ACCOUNT format");
                    }
                }
            } else {
                result = await sendFcmLegacy(FCM_SERVER_KEY!, target.token, notificationData);
            }

            if (!result.success) {
                const errorMessage = result.error || "Unknown FCM error";
                if (errorMessage.includes("NotRegistered") ||
                    errorMessage.includes("InvalidRegistration") ||
                    errorMessage.includes("UNREGISTERED")) {
                    console.log("Removing invalid FCM token for user:", target.userId);
                    await supabase
                        .from("user_settings")
                        .update({ fcm_token: null, fcm_token_updated_at: null })
                        .eq("user_id", target.userId);
                }
            }
            return result;
        }));

        const successCount = results.filter(r => r.success).length;

        if (successCount > 0) {
            return new Response(
                JSON.stringify({
                    success: true,
                    message: `Push notification sent to ${successCount} targets`,
                }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        } else {
            return new Response(
                JSON.stringify({ success: false, error: "Failed to send FCM notifications" }),
                {
                    status: 500,
                    headers: { ...corsHeaders, "Content-Type": "application/json" }
                }
            );
        }
    } catch (error: unknown) {
        console.error("Error:", error);
        const errorMessage = error instanceof Error ? error.message : "Unknown error";
        return new Response(
            JSON.stringify({ error: errorMessage }),
            {
                status: 500,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            }
        );
    }
});
