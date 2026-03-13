import { useState, useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Phone, PhoneOff, Video } from "lucide-react";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { supabase } from "@/integrations/supabase/client";
import { useRingtone } from "@/hooks/useRingtone";
import { useCallHistory } from "@/hooks/useCallHistory";

const CALL_TIMEOUT_MS = 30000; // 30 seconds

const IncomingCall = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { playRingtone, stopRingtone } = useRingtone();
  const { createCallRecord } = useCallHistory();

  const callerName = searchParams.get("name") || "Unknown Caller";
  const callerAvatar = searchParams.get("avatar") || undefined;
  const callType = (searchParams.get("type") || "audio") as "audio" | "video";
  const callId = searchParams.get("callId");
  const roomUrl = searchParams.get("roomUrl");

  const [timeRemaining, setTimeRemaining] = useState(30);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);
  const countdownRef = useRef<NodeJS.Timeout | null>(null);

  // Play ringtone on mount
  useEffect(() => {
    playRingtone();
    return () => stopRingtone();
  }, [playRingtone, stopRingtone]);

  // Auto-timeout after 30 seconds
  useEffect(() => {
    if (!callId) return;

    // Countdown timer
    countdownRef.current = setInterval(() => {
      setTimeRemaining((prev) => {
        if (prev <= 1) {
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    // Timeout handler
    timeoutRef.current = setTimeout(async () => {
      stopRingtone();

      // Update call to missed/ended
      await supabase
        .from("calls")
        .update({ status: "ended", ended_at: new Date().toISOString() })
        .eq("id", callId);

      // Record missed call
      createCallRecord({
        other_participant_name: callerName,
        other_participant_avatar: callerAvatar,
        call_type: callType,
        direction: "incoming",
        status: "missed",
        duration: 0,
      });

      navigate("/", { replace: true });
    }, CALL_TIMEOUT_MS);

    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (countdownRef.current) clearInterval(countdownRef.current);
    };
  }, [callId, callerName, callerAvatar, callType, createCallRecord, navigate, stopRingtone]);

  // Listen for call status changes (e.g., caller cancels)
  useEffect(() => {
    if (!callId) return;

    const channel = supabase
      .channel(`incoming-call-status-${callId}`)
      .on(
        "postgres_changes",
        {
          event: "UPDATE",
          schema: "public",
          table: "calls",
          filter: `id=eq.${callId}`,
        },
        (payload) => {
          const call = payload.new as { status: string };
          if (call.status === "ended" || call.status === "cancelled") {
            stopRingtone();
            navigate("/", { replace: true });
          }
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [callId, navigate, stopRingtone]);

  const handleAccept = async () => {
    stopRingtone();
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    if (countdownRef.current) clearInterval(countdownRef.current);

    if (callId) {
      await supabase
        .from("calls")
        .update({ status: "accepted", started_at: new Date().toISOString() })
        .eq("id", callId);

      createCallRecord({
        other_participant_name: callerName,
        other_participant_avatar: callerAvatar,
        call_type: callType,
        direction: "incoming",
        status: "ongoing",
        duration: 0,
      });
    }

    // Get a token for the callee to join with proper permissions
    let calleeToken: string | undefined;
    if (roomUrl) {
      try {
        const { data: { user } } = await supabase.auth.getUser();
        // Extract room name from URL (e.g., https://domain.daily.co/room-name)
        const roomName = roomUrl.split('/').pop();
        
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
      type: callType,
      name: callerName,
      skipLobby: "true",
    });
    if (callerAvatar) params.set("avatar", callerAvatar);
    if (callId) params.set("callId", callId);
    if (roomUrl) params.set("roomUrl", roomUrl);
    if (calleeToken) params.set("token", calleeToken);

    navigate(`/call?${params.toString()}`, { replace: true });
  };

  const handleReject = async () => {
    stopRingtone();
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    if (countdownRef.current) clearInterval(countdownRef.current);

    if (callId) {
      await supabase
        .from("calls")
        .update({ status: "rejected", ended_at: new Date().toISOString() })
        .eq("id", callId);

      createCallRecord({
        other_participant_name: callerName,
        other_participant_avatar: callerAvatar,
        call_type: callType,
        direction: "incoming",
        status: "rejected",
        duration: 0,
      });
    }

    navigate("/", { replace: true });
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-gradient-to-b from-primary/20 via-background to-background">
      {/* Ripple effect background */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none overflow-hidden">
        <div className="absolute w-64 h-64 rounded-full bg-primary/10 animate-ping" style={{ animationDuration: "2s" }} />
        <div className="absolute w-48 h-48 rounded-full bg-primary/20 animate-ping" style={{ animationDuration: "1.5s", animationDelay: "0.5s" }} />
      </div>

      {/* Content */}
      <div className="relative z-10 flex flex-col items-center gap-6 px-6 text-center">
        {/* Avatar */}
        <div className="relative">
          <div className="absolute inset-0 animate-pulse rounded-full bg-primary/30" />
          <Avatar className="w-32 h-32 border-4 border-primary/40 relative">
            {callerAvatar && <AvatarImage src={callerAvatar} alt={callerName} />}
            <AvatarFallback className="text-4xl bg-primary/20 text-primary">
              {callerName
                .split(" ")
                .map((n) => n[0])
                .join("")
                .toUpperCase()}
            </AvatarFallback>
          </Avatar>
        </div>

        {/* Caller info */}
        <div>
          <h2 className="text-3xl font-bold text-foreground mb-2">{callerName}</h2>
          <p className="text-muted-foreground flex items-center justify-center gap-2 text-lg">
            {callType === "video" ? (
              <>
                <Video className="w-5 h-5" />
                Incoming Video Call
              </>
            ) : (
              <>
                <Phone className="w-5 h-5" />
                Incoming Voice Call
              </>
            )}
          </p>
          <p className="text-sm text-muted-foreground mt-2">
            Auto-decline in {timeRemaining}s
          </p>
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-8 mt-8">
          <div className="flex flex-col items-center gap-2">
            <Button
              size="lg"
              variant="destructive"
              className="rounded-full w-20 h-20 shadow-lg shadow-destructive/30"
              onClick={handleReject}
            >
              <PhoneOff className="w-8 h-8" />
            </Button>
            <span className="text-sm text-muted-foreground">Decline</span>
          </div>

          <div className="flex flex-col items-center gap-2">
            <Button
              size="lg"
              className="rounded-full w-20 h-20 bg-green-600 hover:bg-green-700 shadow-lg shadow-green-600/30"
              onClick={handleAccept}
            >
              <Phone className="w-8 h-8" />
            </Button>
            <span className="text-sm text-muted-foreground">Accept</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default IncomingCall;
