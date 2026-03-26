// @ts-ignore
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

declare const Deno: any;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const DAILY_API_KEY = Deno.env.get("DAILY_API_KEY");
    if (!DAILY_API_KEY) {
      throw new Error("DAILY_API_KEY not configured");
    }

    const { action, roomName, calleeId, callerId, callType, isGroupCall, groupId } = await req.json();

    if (action === "create") {
      // Create a new Daily.co room
      const roomResponse = await fetch("https://api.daily.co/v1/rooms", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${DAILY_API_KEY}`,
        },
        body: JSON.stringify({
          name: roomName || `room-${Date.now()}`,
          properties: {
            exp: Math.floor(Date.now() / 1000) + 3600, // Expires in 1 hour
            enable_chat: true,
            enable_screenshare: true,
            enable_recording: false,
            start_video_off: false,
            start_audio_off: false,
          },
        }),
      });

      if (!roomResponse.ok) {
        const error = await roomResponse.text();
        console.error("Daily API error:", error);
        throw new Error(`Failed to create room: ${error}`);
      }

      const room = await roomResponse.json();

      // Create meeting tokens for caller and callee
      const createToken = async (userId: string, isOwner: boolean) => {
        const tokenResponse = await fetch("https://api.daily.co/v1/meeting-tokens", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${DAILY_API_KEY}`,
          },
          body: JSON.stringify({
            properties: {
              room_name: room.name,
              user_id: userId,
              is_owner: isOwner,
              exp: Math.floor(Date.now() / 1000) + 3600,
              enable_screenshare: true,
              start_video_off: false,
              start_audio_off: false,
            },
          }),
        });

        if (!tokenResponse.ok) {
          throw new Error("Failed to create meeting token");
        }

        return tokenResponse.json();
      };

      const callerToken = callerId ? await createToken(callerId, true) : null;
      // In a group call, multiple people join, so they request tokens dynamically via get-token when joining.
      // We don't generate a single calleeToken.
      const calleeToken = (!isGroupCall && calleeId) ? await createToken(calleeId, false) : null;

      // Send FCM push notification to callee (if configured)
      const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
      const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
      const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY");

      if (FCM_SERVER_KEY && SUPABASE_URL && SUPABASE_SERVICE_ROLE_KEY && (calleeId || (isGroupCall && groupId))) {
        try {
          // Fetch caller's name for the notification
          // @ts-ignore
          const { createClient } = await import("https://esm.sh/@supabase/supabase-js@2.38.4");
          const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

          // Get caller profile
          const { data: callerProfile } = await supabase
            .from("profiles")
            .select("full_name, username")
            .eq("user_id", callerId)
            .single();

          const callerName = callerProfile?.full_name || callerProfile?.username || "Someone";

          // Call the send-call-notification function internally
          const notificationResponse = await fetch(`${SUPABASE_URL}/functions/v1/send-call-notification`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            },
            body: JSON.stringify({
              callerId,
              calleeId,
              callerName,
              callType: callType || "audio", // Use actual call type from request
              roomUrl: room.url,
              calleeToken: calleeToken?.token,
              isGroupCall,
              groupId
            }),
          });

          if (notificationResponse.ok) {
            console.log("FCM notification sent successfully");
          } else {
            console.warn("Failed to send FCM notification:", await notificationResponse.text());
          }
        } catch (fcmError) {
          // Don't fail the room creation if FCM fails
          console.error("Error sending FCM notification:", fcmError);
        }
      }

      return new Response(
        JSON.stringify({
          success: true,
          room: {
            name: room.name,
            url: room.url,
          },
          callerToken: callerToken?.token,
          calleeToken: calleeToken?.token,
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    if (action === "get-token") {
      // Get a token for an existing room
      const tokenResponse = await fetch("https://api.daily.co/v1/meeting-tokens", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${DAILY_API_KEY}`,
        },
        body: JSON.stringify({
          properties: {
            room_name: roomName,
            user_id: callerId || calleeId,
            exp: Math.floor(Date.now() / 1000) + 3600,
            enable_screenshare: true,
            start_video_off: false,
            start_audio_off: false,
          },
        }),
      });

      if (!tokenResponse.ok) {
        throw new Error("Failed to create meeting token");
      }

      const tokenData = await tokenResponse.json();

      return new Response(
        JSON.stringify({
          success: true,
          token: tokenData.token,
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    if (action === "delete") {
      // Delete a room
      const deleteResponse = await fetch(`https://api.daily.co/v1/rooms/${roomName}`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${DAILY_API_KEY}`,
        },
      });

      return new Response(
        JSON.stringify({
          success: deleteResponse.ok,
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    throw new Error("Invalid action");
  } catch (error: unknown) {
    console.error("Error:", error);
    const errorMessage = error instanceof Error ? error.message : "Unknown error";
    return new Response(
      JSON.stringify({ error: errorMessage }),
      {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  }
});
