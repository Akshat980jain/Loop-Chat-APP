import { useEffect, useRef, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { IncomingCallModal } from "./IncomingCallModal";
import { useRingtone } from "@/hooks/useRingtone";
import { useCallHistory } from "@/hooks/useCallHistory";
import { useIsMobile } from "@/hooks/use-mobile";
import { useServiceWorker } from "@/hooks/useServiceWorker";

interface Call {
  id: string;
  caller_id: string;
  callee_id: string;
  call_type: string;
  status: string;
  room_url: string | null;
  started_at: string | null;
  ended_at: string | null;
  created_at: string;
}

interface CallerProfile {
  full_name: string;
  avatar_url: string | null;
}

const CALL_TIMEOUT_MS = 30000; // 30 seconds

export const IncomingCallHandler = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const isMobile = useIsMobile();
  const { closeCallNotification } = useServiceWorker();

  const [incomingCall, setIncomingCall] = useState<Call | null>(null);
  const [callerProfile, setCallerProfile] = useState<CallerProfile | null>(null);
  const [userId, setUserId] = useState<string | null>(null);

  const incomingCallIdRef = useRef<string | null>(null);
  const callerProfileRef = useRef<CallerProfile | null>(null);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  const { playRingtone, stopRingtone } = useRingtone();
  const { createCallRecord } = useCallHistory();

  // Keep refs in sync to avoid resubscribing channels on state changes
  useEffect(() => {
    incomingCallIdRef.current = incomingCall?.id ?? null;
  }, [incomingCall?.id]);

  useEffect(() => {
    callerProfileRef.current = callerProfile;
  }, [callerProfile]);

  // Track auth user
  useEffect(() => {
    let isMounted = true;

    const loadUser = async () => {
      const {
        data: { user },
      } = await supabase.auth.getUser();
      if (!isMounted) return;
      setUserId(user?.id ?? null);
    };

    loadUser();

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      setUserId(session?.user?.id ?? null);
    });

    return () => {
      isMounted = false;
      subscription.unsubscribe();
    };
  }, []);

  // Listen for service worker messages (push notification actions)
  useEffect(() => {
    const handleServiceWorkerMessage = (event: MessageEvent) => {
      const { type, callId } = event.data || {};
      
      if (type === 'CALL_ACCEPTED' && callId === incomingCall?.id) {
        handleAccept();
      } else if (type === 'CALL_REJECTED' && callId === incomingCall?.id) {
        handleReject();
      }
    };

    navigator.serviceWorker?.addEventListener('message', handleServiceWorkerMessage);
    
    return () => {
      navigator.serviceWorker?.removeEventListener('message', handleServiceWorkerMessage);
    };
  }, [incomingCall?.id]);

  // Play ringtone when there's an incoming call (only if not on mobile full-screen page)
  useEffect(() => {
    // On mobile, we navigate to full-screen page which handles its own ringtone
    if (isMobile && incomingCall) {
      return;
    }

    if (incomingCall) {
      playRingtone();
    } else {
      stopRingtone();
    }

    return () => {
      stopRingtone();
    };
  }, [incomingCall, playRingtone, stopRingtone, isMobile]);

  // Auto-timeout for incoming calls (desktop modal only)
  useEffect(() => {
    if (!incomingCall || isMobile) return;

    timeoutRef.current = setTimeout(async () => {
      stopRingtone();
      closeCallNotification(); // Close push notification

      // Update call to ended (missed)
      await supabase
        .from("calls")
        .update({ status: "ended", ended_at: new Date().toISOString() })
        .eq("id", incomingCall.id);

      // Record missed call
      if (callerProfileRef.current) {
        createCallRecord({
          other_participant_name: callerProfileRef.current.full_name,
          other_participant_avatar: callerProfileRef.current.avatar_url || undefined,
          call_type: incomingCall.call_type as "audio" | "video",
          direction: "incoming",
          status: "missed",
          duration: 0,
        });
      }

      setIncomingCall(null);
      setCallerProfile(null);
    }, CALL_TIMEOUT_MS);

    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [incomingCall, isMobile, createCallRecord, stopRingtone]);

  // Subscribe to incoming calls
  useEffect(() => {
    if (!userId) return;

    console.log("[IncomingCallHandler] subscribing for user:", userId);

    const channel = supabase
      .channel(`incoming-calls-${userId}`)
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "public",
          table: "calls",
          filter: `callee_id=eq.${userId}`,
        },
        async (payload) => {
          const call = payload.new as Call;
          console.log("[IncomingCallHandler] INSERT received:", call);

          if (call.status !== "ringing") return;

          // Fetch caller profile
          const { data: profile, error } = await supabase
            .from("profiles")
            .select("full_name, avatar_url")
            .eq("user_id", call.caller_id)
            .single();

          if (error) {
            console.error("[IncomingCallHandler] failed to fetch caller profile:", error);
          }

          const callerProf = profile || { full_name: "Unknown", avatar_url: null };

          // On mobile, navigate to full-screen incoming call page
          if (isMobile) {
            const params = new URLSearchParams({
              type: call.call_type,
              name: callerProf.full_name,
              callId: call.id,
            });
            if (callerProf.avatar_url) params.set("avatar", callerProf.avatar_url);
            if (call.room_url) params.set("roomUrl", call.room_url);

            navigate(`/incoming-call?${params.toString()}`);
          } else {
            // On desktop, show modal
            setIncomingCall(call);
            setCallerProfile(callerProf);
          }
        }
      )
      .on(
        "postgres_changes",
        {
          event: "UPDATE",
          schema: "public",
          table: "calls",
        },
        async (payload) => {
          const call = payload.new as Call;
          const currentIncomingId = incomingCallIdRef.current;

          if (!currentIncomingId || call.id !== currentIncomingId) return;

          if (call.status === "ended" || call.status === "rejected" || call.status === "cancelled") {
            if (timeoutRef.current) clearTimeout(timeoutRef.current);

            // Record missed call if it was never answered
            const profile = callerProfileRef.current;
            if (call.status === "ended" && !call.started_at && profile) {
              createCallRecord({
                other_participant_name: profile.full_name,
                other_participant_avatar: profile.avatar_url || undefined,
                call_type: call.call_type as "audio" | "video",
                direction: "incoming",
                status: "missed",
                duration: 0,
              });
            }

            setIncomingCall(null);
            setCallerProfile(null);
          }
        }
      )
      .subscribe((status) => {
        console.log("[IncomingCallHandler] channel status:", status);
      });

    return () => {
      console.log("[IncomingCallHandler] unsubscribing for user:", userId);
      supabase.removeChannel(channel);
    };
  }, [userId, createCallRecord, isMobile, navigate]);

  const handleAccept = async () => {
    if (!incomingCall) return;

    stopRingtone();
    closeCallNotification(); // Close push notification
    if (timeoutRef.current) clearTimeout(timeoutRef.current);

    try {
      await supabase
        .from("calls")
        .update({ status: "accepted", started_at: new Date().toISOString() })
        .eq("id", incomingCall.id);

      if (callerProfile) {
        createCallRecord({
          other_participant_name: callerProfile.full_name,
          other_participant_avatar: callerProfile.avatar_url || undefined,
          call_type: incomingCall.call_type as "audio" | "video",
          direction: "incoming",
          status: "ongoing",
          duration: 0,
        });
      }

      // Get a token for the callee to join with proper permissions
      let calleeToken: string | undefined;
      if (incomingCall.room_url) {
        try {
          const { data: { user } } = await supabase.auth.getUser();
          // Extract room name from URL (e.g., https://domain.daily.co/room-name)
          const roomName = incomingCall.room_url.split('/').pop();
          
          const tokenResponse = await supabase.functions.invoke("daily-room", {
            body: {
              action: "get-token",
              roomName: roomName,
              calleeId: user?.id,
            },
          });
          
          if (tokenResponse.data?.token) {
            calleeToken = tokenResponse.data.token;
            console.log("Got callee token for room:", roomName);
          }
        } catch (err) {
          console.error("Error getting callee token:", err);
        }
      }

      const params = new URLSearchParams({
        type: incomingCall.call_type,
        name: callerProfile?.full_name || "Unknown",
        skipLobby: "true",
        callId: incomingCall.id,
      });

      if (callerProfile?.avatar_url) {
        params.set("avatar", callerProfile.avatar_url);
      }

      if (incomingCall.room_url) {
        params.set("roomUrl", incomingCall.room_url);
      }

      if (calleeToken) {
        params.set("token", calleeToken);
      }

      navigate(`/call?${params.toString()}`);

      setIncomingCall(null);
      setCallerProfile(null);
    } catch (error) {
      console.error("Error accepting call:", error);
    }
  };

  const handleReject = async () => {
    if (!incomingCall) return;

    stopRingtone();
    closeCallNotification(); // Close push notification
    if (timeoutRef.current) clearTimeout(timeoutRef.current);

    try {
      await supabase
        .from("calls")
        .update({ status: "rejected", ended_at: new Date().toISOString() })
        .eq("id", incomingCall.id);

      if (callerProfile) {
        createCallRecord({
          other_participant_name: callerProfile.full_name,
          other_participant_avatar: callerProfile.avatar_url || undefined,
          call_type: incomingCall.call_type as "audio" | "video",
          direction: "incoming",
          status: "rejected",
          duration: 0,
        });
      }

      setIncomingCall(null);
      setCallerProfile(null);
    } catch (error) {
      console.error("Error rejecting call:", error);
    }
  };

  // Don't show modal if we're already on the incoming-call page (mobile)
  if (location.pathname === "/incoming-call") return null;
  if (!incomingCall) return null;

  return (
    <IncomingCallModal
      isOpen={true}
      callerName={callerProfile?.full_name || "Unknown Caller"}
      callerAvatar={callerProfile?.avatar_url || undefined}
      callType={incomingCall.call_type as "audio" | "video"}
      onAccept={handleAccept}
      onReject={handleReject}
    />
  );
};
