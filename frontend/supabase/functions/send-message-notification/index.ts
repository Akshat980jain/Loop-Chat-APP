import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

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
    const header = { alg: "RS256", typ: "JWT" };
    const now = Math.floor(Date.now() / 1000);
    const payload = {
        iss: serviceAccount.client_email,
        sub: serviceAccount.client_email,
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
        scope: "https://www.googleapis.com/auth/firebase.messaging"
    };

    const base64url = (data: string) =>
        btoa(data).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');

    const encodedHeader = base64url(JSON.stringify(header));
    const encodedPayload = base64url(JSON.stringify(payload));
    const signInput = `${encodedHeader}.${encodedPayload}`;

    const pemContents = serviceAccount.private_key
        .replace(/-----BEGIN PRIVATE KEY-----/, '')
        .replace(/-----END PRIVATE KEY-----/, '')
        .replace(/\n/g, '');

    const binaryKey = Uint8Array.from(atob(pemContents), c => c.charCodeAt(0));
    const cryptoKey = await crypto.subtle.importKey(
        "pkcs8", binaryKey,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false, ["sign"]
    );

    const signature = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5", cryptoKey,
        new TextEncoder().encode(signInput)
    );

    const encodedSignature = base64url(
        String.fromCharCode(...new Uint8Array(signature))
    );

    return `${signInput}.${encodedSignature}`;
}

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

async function sendFcmV1(
    serviceAccount: ServiceAccount,
    fcmToken: string,
    data: Record<string, string>
): Promise<{ success: boolean; error?: string }> {
    const accessToken = await getAccessToken(serviceAccount);

    const message = {
        message: {
            token: fcmToken,
            data: data,
            android: {
                priority: "HIGH",
                ttl: "300s"
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
        return { success: true };
    } else {
        return { success: false, error: result.error?.message || "FCM v1 error" };
    }
}

async function sendFcmLegacy(
    serverKey: string,
    fcmToken: string,
    data: Record<string, string>
): Promise<{ success: boolean; error?: string }> {
    const response = await fetch("https://fcm.googleapis.com/fcm/send", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `key=${serverKey}`,
        },
        body: JSON.stringify({
            to: fcmToken,
            priority: "high",
            data: data,
        }),
    });
    const result = await response.json();
    if (result.success === 1) {
        return { success: true };
    } else {
        return { success: false, error: result.results?.[0]?.error || "Legacy FCM error" };
    }
}

serve(async (req) => {
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

        const hasServiceAccount = !!GOOGLE_SERVICE_ACCOUNT;
        const hasServerKey = !!FCM_SERVER_KEY;

        if (!hasServiceAccount && !hasServerKey) {
            return new Response(
                JSON.stringify({ success: false, error: "FCM not configured" }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

        const {
            senderId,
            receiverId,
            senderName,
            messageContent,
            messageType,
            conversationId
        } = await req.json();

        console.log(`Message notification: from=${senderName} conversation=${conversationId}, type=${messageType}`);

        let receiverIds: string[] = [];

        if (conversationId) {
            // Get all participants except the sender
            const { data: participants } = await supabase
                .from("conversation_participants")
                .select("user_id")
                .eq("conversation_id", conversationId)
                .neq("user_id", senderId);
                
            if (participants && participants.length > 0) {
                receiverIds = participants.map((p: any) => p.user_id);
            } else if (receiverId) {
                // Fallback to receiverId if no participants found
                receiverIds = [receiverId];
            }
        } else if (receiverId) {
            receiverIds = [receiverId];
        }

        if (receiverIds.length === 0) {
            console.warn("No receivers found for notification.");
            return new Response(
                JSON.stringify({ success: false, error: "No receivers found" }),
                { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // Fetch valid FCM tokens for the receivers
        const { data: userSettings, error: settingsError } = await supabase
            .from("user_settings")
            .select("user_id, fcm_token")
            .in("user_id", receiverIds)
            .not("fcm_token", "is", null);

        if (settingsError || !userSettings || userSettings.length === 0) {
            console.warn("No valid FCM tokens found for receivers");
            return new Response(
                JSON.stringify({ success: false, error: "No FCM tokens" }),
                { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // Build a preview string
        let preview = messageContent || "";
        if (messageType === "image") preview = "📷 Photo";
        else if (messageType === "video") preview = "🎥 Video";
        else if (messageType === "document") preview = "📄 Document";
        else if (messageType === "audio") preview = "🎵 Audio";
        else if (preview.length > 100) preview = preview.substring(0, 100) + "...";

        const notificationData: Record<string, string> = {
            type: "new_message",
            sender_id: senderId || "",
            sender_name: senderName || "Someone",
            message_preview: preview,
            message_type: messageType || "text",
            conversation_id: conversationId || "",
            timestamp: Date.now().toString(),
        };

        let activeServiceAccount = hasServiceAccount;
        let serviceAccount: ServiceAccount | undefined;
        
        if (activeServiceAccount) {
            try {
                serviceAccount = JSON.parse(GOOGLE_SERVICE_ACCOUNT!) as ServiceAccount;
            } catch (e) {
                activeServiceAccount = false;
            }
        }

        const results = [];
        let successCount = 0;
        
        // Send to all valid tokens
        for (const setting of userSettings) {
            const fcmToken = setting.fcm_token;
            let result: { success: boolean; error?: string };
            
            if (activeServiceAccount && serviceAccount) {
                try {
                    result = await sendFcmV1(serviceAccount, fcmToken, notificationData);
                } catch (err: any) {
                    if (hasServerKey) {
                        result = await sendFcmLegacy(FCM_SERVER_KEY!, fcmToken, notificationData);
                    } else {
                        result = { success: false, error: err.message || "Unknown error" };
                    }
                }
            } else if (hasServerKey) {
                result = await sendFcmLegacy(FCM_SERVER_KEY!, fcmToken, notificationData);
            } else {
                result = { success: false, error: "FCM not configured properly" };
            }
            
            results.push({ user_id: setting.user_id, result });
            
            if (result.success) {
                successCount++;
            } else {
                // Clean up invalid tokens
                if (result.error?.includes("NotRegistered") || result.error?.includes("UNREGISTERED")) {
                    await supabase
                        .from("user_settings")
                        .update({ fcm_token: null })
                        .eq("user_id", setting.user_id);
                }
            }
        }

        console.log("FCM results summary:", `Sent ${successCount}/${userSettings.length} successfully`);

        if (successCount > 0 || userSettings.length === 0) {
            return new Response(
                JSON.stringify({ success: true, details: results }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        } else {
            return new Response(
                JSON.stringify({ success: false, error: "All notifications failed", details: results }),
                { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }
    } catch (error: unknown) {
        console.error("Error:", error);
        const errorMessage = error instanceof Error ? error.message : "Unknown error";
        return new Response(
            JSON.stringify({ error: errorMessage }),
            { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
    }
});
