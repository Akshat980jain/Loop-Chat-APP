import { useEffect, useState, useRef, useCallback } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Phone, PhoneOff, Mic, MicOff, Video, VideoOff, Volume2, VolumeX, Monitor, MonitorOff, Users, SwitchCamera, Speaker } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { PreCallLobby } from "@/components/PreCallLobby";
import { useDailyCall } from "@/hooks/useDailyCall";
import { useCallHistory } from "@/hooks/useCallHistory";
import { usePushNotifications } from "@/hooks/usePushNotifications";
import { useRingbackTone } from "@/hooks/useRingbackTone";
import { useToast } from "@/hooks/use-toast";
import { supabase } from "@/integrations/supabase/client";

const Call = () => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [searchParams] = useSearchParams();
  const type = (searchParams.get("type") || "audio") as "audio" | "video";
  const name = searchParams.get("name") || "Unknown";
  const avatar = searchParams.get("avatar");
  const calleeId = searchParams.get("calleeId");
  const roomUrl = searchParams.get("roomUrl");
  const roomToken = searchParams.get("token");
  const skipLobby = searchParams.get("skipLobby") === "true";
  const callIdFromUrl = searchParams.get("callId");

  const [showLobby, setShowLobby] = useState(!skipLobby && !roomUrl);
  const [isSpeakerOff, setIsSpeakerOff] = useState(false);
  const [isLoudspeaker, setIsLoudspeaker] = useState(true); // Default to loudspeaker on mobile
  const [callDuration, setCallDuration] = useState(0);
  const [callHistoryId, setCallHistoryId] = useState<string | null>(null);
  const [initialSettings, setInitialSettings] = useState<{ videoEnabled: boolean; audioEnabled: boolean } | null>(null);
  const [currentCallId, setCurrentCallId] = useState<string | null>(callIdFromUrl);
  const [callStatus, setCallStatus] = useState<"ringing" | "connecting" | "connected" | "ended">("ringing");
  const [ringTimeout, setRingTimeout] = useState(30);

  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const durationIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const ringTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const ringCountdownRef = useRef<NodeJS.Timeout | null>(null);

  const { playRingback, stopRingback } = useRingbackTone();

  const {
    isJoining,
    isJoined,
    isMuted,
    isVideoOff,
    isScreenSharing,
    isFrontCamera,
    participants,
    error,
    callEnded,
    hasRemoteParticipant,
    createAndJoinRoom,
    joinRoom,
    leaveCall,
    toggleMute,
    toggleVideo,
    toggleScreenShare,
    switchCamera,
    callObject,
  } = useDailyCall();

  const { createCallRecord } = useCallHistory();

  // Push notifications for background calls
  usePushNotifications();

  // Initialize call after lobby
  const startCall = useCallback(async (settings: { videoEnabled: boolean; audioEnabled: boolean }) => {
    // Get fresh params from URL in case of stale closure
    const currentParams = new URLSearchParams(window.location.search);
    const currentCalleeId = currentParams.get("calleeId");
    const currentRoomUrl = currentParams.get("roomUrl");
    const currentToken = currentParams.get("token");

    setInitialSettings(settings);
    setShowLobby(false);

    if (!currentCalleeId && !currentRoomUrl) {
      toast({
        title: "Invalid Call",
        description: "Missing call information",
        variant: "destructive",
      });
      navigate(-1);
      return;
    }

    try {
      if (currentCalleeId && !currentRoomUrl) {
        // Outgoing call - create room and join
        // Start ringback tone for caller while waiting for callee to answer
        playRingback();

        const result = await createAndJoinRoom(currentCalleeId, type);

        if (result) {
          // Get the call ID from the database to track call status
          const { data: { user } } = await supabase.auth.getUser();
          if (user) {
            // Find the call we just created
            const { data: callData } = await supabase
              .from('calls')
              .select('id')
              .eq('caller_id', user.id)
              .eq('callee_id', currentCalleeId)
              .order('created_at', { ascending: false })
              .limit(1)
              .single();

            if (callData) {
              setCurrentCallId(callData.id);
            }

            const session = await supabase.auth.getSession();
            const token = session.data.session?.access_token;

            const response = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/rest/v1/call_history`, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
                'Authorization': `Bearer ${token}`,
                'Prefer': 'return=representation',
              },
              body: JSON.stringify({
                user_id: user.id,
                other_participant_name: name,
                other_participant_avatar: avatar || null,
                call_type: type,
                duration: 0,
                status: "ongoing",
                direction: "outgoing",
              }),
            });

            if (response.ok) {
              const [record] = await response.json();
              setCallHistoryId(record.id);
            }
          }

          toast({
            title: "Calling...",
            description: `Connecting to ${name}`,
          });
        } else {
          stopRingback();
        }
      } else if (currentRoomUrl) {
        // Incoming call - join existing room with call type
        await joinRoom(currentRoomUrl, currentToken || undefined, type);
      }
    } catch (err) {
      console.error("Error initializing call:", err);
      toast({
        title: "Call Failed",
        description: "Failed to initialize call",
        variant: "destructive",
      });
      navigate(-1);
    }
  }, [type, name, avatar, createAndJoinRoom, joinRoom, navigate, toast]);

  // Handle incoming calls (skip lobby)
  useEffect(() => {
    if (roomUrl && !showLobby) {
      startCall({ videoEnabled: type === "video", audioEnabled: true });
    }
  }, []);

  // Stop ringback tone and start duration timer when remote participant joins
  useEffect(() => {
    if (isJoined && hasRemoteParticipant) {
      // Stop ringback tone when callee answers
      stopRingback();
      setCallStatus("connected");

      // Clear ring timeout
      if (ringTimeoutRef.current) clearTimeout(ringTimeoutRef.current);
      if (ringCountdownRef.current) clearInterval(ringCountdownRef.current);

      if (!durationIntervalRef.current) {
        durationIntervalRef.current = setInterval(() => {
          setCallDuration(prev => prev + 1);
        }, 1000);
      }
    }

    return () => {
      if (durationIntervalRef.current) {
        clearInterval(durationIntervalRef.current);
        durationIntervalRef.current = null;
      }
    };
  }, [isJoined, hasRemoteParticipant, stopRingback]);

  // Update call status based on connection state
  useEffect(() => {
    if (isJoining) {
      setCallStatus("connecting");
    } else if (isJoined && !hasRemoteParticipant) {
      setCallStatus("ringing");
    } else if (isJoined && hasRemoteParticipant) {
      setCallStatus("connected");
    }
  }, [isJoining, isJoined, hasRemoteParticipant]);

  // 30s timeout for outgoing calls waiting for answer
  useEffect(() => {
    // Only for outgoing calls (calleeId present, no roomUrl)
    if (!calleeId || roomUrl) return;
    if (callStatus === "connected") return;
    if (!isJoined) return;

    // Start countdown
    ringCountdownRef.current = setInterval(() => {
      setRingTimeout(prev => {
        if (prev <= 1) return 0;
        return prev - 1;
      });
    }, 1000);

    // Set timeout to cancel call after 30s
    ringTimeoutRef.current = setTimeout(async () => {
      stopRingback();

      // Update call status to ended (no answer)
      if (currentCallId) {
        await supabase
          .from("calls")
          .update({ status: "ended", ended_at: new Date().toISOString() })
          .eq("id", currentCallId);
      }

      // Record as missed call from caller's perspective
      createCallRecord({
        other_participant_name: name,
        other_participant_avatar: avatar || undefined,
        call_type: type,
        direction: "outgoing",
        status: "missed",
        duration: 0,
      });

      toast({
        title: "No Answer",
        description: `${name} didn't answer`,
      });

      navigate("/", { replace: true });
    }, 30000);

    return () => {
      if (ringTimeoutRef.current) clearTimeout(ringTimeoutRef.current);
      if (ringCountdownRef.current) clearInterval(ringCountdownRef.current);
    };
  }, [calleeId, roomUrl, callStatus, isJoined, currentCallId, name, avatar, type, createCallRecord, navigate, stopRingback, toast]);

  // Listen for call status changes in database (for when other user ends call)
  useEffect(() => {
    if (!currentCallId) return;

    const channel = supabase
      .channel(`call-status-${currentCallId}`)
      .on(
        'postgres_changes',
        {
          event: 'UPDATE',
          schema: 'public',
          table: 'calls',
          filter: `id=eq.${currentCallId}`,
        },
        (payload) => {
          const call = payload.new as { id: string; status: string };
          console.log("Call status updated:", call.status);

          // Update local status based on DB status
          if (call.status === "accepted") {
            setCallStatus("connected");
          }

          if (call.status === 'ended' || call.status === 'rejected' || call.status === 'cancelled') {
            setCallStatus("ended");

            // Clear timeouts
            if (ringTimeoutRef.current) clearTimeout(ringTimeoutRef.current);
            if (ringCountdownRef.current) clearInterval(ringCountdownRef.current);

            toast({
              title: call.status === "rejected" ? "Call Declined" : "Call Ended",
              description: call.status === "rejected" ? `${name} declined the call` : "The call has ended",
            });
            stopRingback();
            handleEndCall();
          }
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [currentCallId, toast, stopRingback, name]);

  // Auto-end call when remote participant leaves
  useEffect(() => {
    if (callEnded && isJoined) {
      toast({
        title: "Call Ended",
        description: "The other participant has left the call",
      });
      handleEndCall();
    }
  }, [callEnded, isJoined]);
  // Update video elements when participants change - includes screen share
  useEffect(() => {
    if (!callObject) return;

    const updateVideoTracks = () => {
      const allParticipants = callObject.participants();

      // Local video
      const local = allParticipants.local;
      if (localVideoRef.current && local?.tracks?.video?.persistentTrack) {
        const stream = new MediaStream([local.tracks.video.persistentTrack]);
        localVideoRef.current.srcObject = stream;
      }

      // Remote video (first remote participant) - prioritize screen share
      const remote = Object.values(allParticipants).find(p => !p.local);
      if (remoteVideoRef.current && remote) {
        // Check for screen share track first
        const screenTrack = remote?.tracks?.screenVideo?.persistentTrack;
        const videoTrack = remote?.tracks?.video?.persistentTrack;

        if (screenTrack && remote?.tracks?.screenVideo?.state === "playable") {
          const stream = new MediaStream([screenTrack]);
          remoteVideoRef.current.srcObject = stream;
        } else if (videoTrack) {
          const stream = new MediaStream([videoTrack]);
          remoteVideoRef.current.srcObject = stream;
        }
      }
    };

    updateVideoTracks();

    callObject.on("participant-updated", updateVideoTracks);
    callObject.on("track-started", updateVideoTracks);

    return () => {
      callObject.off("participant-updated", updateVideoTracks);
      callObject.off("track-started", updateVideoTracks);
    };
  }, [callObject, participants]);

  // Handle remote audio - create a separate audio element for remote audio with mobile speaker support
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const currentAudioTrackIdRef = useRef<string | null>(null);

  // Function to configure audio output for mobile
  const configureAudioOutput = useCallback(async (audioElement: HTMLAudioElement) => {
    try {
      // For mobile devices, try to use setSinkId for speaker output
      // @ts-ignore - setSinkId is not in all browsers
      if (typeof audioElement.setSinkId === 'function' && isLoudspeaker) {
        // Get available audio output devices
        const devices = await navigator.mediaDevices.enumerateDevices();
        const speakers = devices.filter(d => d.kind === 'audiooutput');

        // Find the speaker (usually the one that's not 'default' or contains 'speaker')
        const speakerDevice = speakers.find(d =>
          d.label.toLowerCase().includes('speaker') ||
          d.deviceId === 'default'
        );

        if (speakerDevice) {
          // @ts-ignore
          await audioElement.setSinkId(speakerDevice.deviceId);
          console.log("Audio output set to:", speakerDevice.label);
        }
      }
    } catch (error) {
      console.log("Could not set audio output device:", error);
    }
  }, [isLoudspeaker]);

  useEffect(() => {
    if (!callObject) return;

    // Create audio element once with mobile-optimized settings
    if (!remoteAudioRef.current) {
      const audioEl = document.createElement('audio');
      audioEl.autoplay = true;
      (audioEl as any).playsInline = true;
      audioEl.setAttribute('playsinline', '');
      audioEl.setAttribute('webkit-playsinline', '');
      // Ensure volume is at max for mobile
      audioEl.volume = 1.0;
      document.body.appendChild(audioEl);
      remoteAudioRef.current = audioEl;

      // Configure audio output for mobile speaker
      configureAudioOutput(audioEl);
    }

    const setupRemoteAudio = () => {
      const allParticipants = callObject.participants();
      const remote = Object.values(allParticipants).find(p => !p.local);

      if (remote?.tracks?.audio?.persistentTrack && remoteAudioRef.current) {
        const track = remote.tracks.audio.persistentTrack;

        // Only update if track changed to avoid play() conflicts
        if (currentAudioTrackIdRef.current !== track.id) {
          currentAudioTrackIdRef.current = track.id;
          const audioStream = new MediaStream([track]);
          remoteAudioRef.current.srcObject = audioStream;
          remoteAudioRef.current.muted = isSpeakerOff;
          remoteAudioRef.current.volume = 1.0;

          // Force play with user gesture handling for mobile
          const playPromise = remoteAudioRef.current.play();
          if (playPromise !== undefined) {
            playPromise
              .then(() => {
                console.log("Remote audio playing successfully");
              })
              .catch(err => {
                console.log("Audio play error, will retry on user interaction:", err);
                // On mobile, audio may require user interaction
                // Add a one-time click handler to start audio
                const startAudio = () => {
                  if (remoteAudioRef.current) {
                    remoteAudioRef.current.play().catch(e => console.log("Retry play failed:", e));
                  }
                  document.removeEventListener('touchstart', startAudio);
                  document.removeEventListener('click', startAudio);
                };
                document.addEventListener('touchstart', startAudio, { once: true });
                document.addEventListener('click', startAudio, { once: true });
              });
          }
          console.log("Remote audio track connected:", track.id);
        }
      }
    };

    setupRemoteAudio();

    callObject.on("participant-joined", setupRemoteAudio);
    callObject.on("participant-updated", setupRemoteAudio);
    callObject.on("track-started", setupRemoteAudio);

    return () => {
      callObject.off("participant-joined", setupRemoteAudio);
      callObject.off("participant-updated", setupRemoteAudio);
      callObject.off("track-started", setupRemoteAudio);
    };
  }, [callObject, isSpeakerOff, configureAudioOutput]);

  // Handle speaker mute separately
  useEffect(() => {
    if (remoteAudioRef.current) {
      remoteAudioRef.current.muted = isSpeakerOff;
      remoteAudioRef.current.volume = 1.0;
    }
  }, [isSpeakerOff]);

  // Handle loudspeaker toggle - reconfigure audio output
  useEffect(() => {
    if (remoteAudioRef.current) {
      configureAudioOutput(remoteAudioRef.current);
    }
  }, [isLoudspeaker, configureAudioOutput]);

  // Cleanup audio element on unmount
  useEffect(() => {
    return () => {
      if (remoteAudioRef.current) {
        remoteAudioRef.current.srcObject = null;
        remoteAudioRef.current.remove();
        remoteAudioRef.current = null;
      }
    };
  }, []);

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const handleEndCall = useCallback(() => {
    stopRingback();

    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
      durationIntervalRef.current = null;
    }

    // Navigate immediately - don't wait for async operations
    navigate("/");

    // Update call status to ended in database so other user is notified (fire and forget)
    if (currentCallId) {
      supabase
        .from('calls')
        .update({ status: 'ended', ended_at: new Date().toISOString() })
        .eq('id', currentCallId)
        .then(({ error }) => {
          if (error) console.error("Error updating call status:", error);
          else console.log("Call status updated");
        });
    }

    // Update call history with final duration and status (fire and forget)
    if (callHistoryId && callDuration > 0) {
      supabase.auth.getSession().then(({ data: session }) => {
        const token = session.session?.access_token;
        if (token) {
          fetch(`${import.meta.env.VITE_SUPABASE_URL}/rest/v1/call_history?id=eq.${callHistoryId}`, {
            method: 'PATCH',
            headers: {
              'Content-Type': 'application/json',
              'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
              'Authorization': `Bearer ${token}`,
            },
            body: JSON.stringify({
              duration: callDuration,
              status: 'completed',
            }),
          }).catch(err => console.error("Error updating call history:", err));
        }
      });
    }

    // Leave call in background (fire and forget)
    leaveCall();
  }, [callHistoryId, callDuration, currentCallId, leaveCall, navigate, stopRingback]);

  const handleToggleScreenShare = async () => {
    try {
      await toggleScreenShare();
      toast({
        title: isScreenSharing ? "Screen sharing stopped" : "Screen sharing started",
        description: isScreenSharing ? "You are now sharing your camera" : "Others can now see your screen",
      });
    } catch (err) {
      toast({
        title: "Screen share failed",
        description: "Could not share screen",
        variant: "destructive",
      });
    }
  };

  const getStatusText = () => {
    if (error) return "Error";
    if (callStatus === "connected") return formatDuration(callDuration);
    if (callStatus === "connecting" || isJoining) return "Connecting...";
    if (callStatus === "ringing" && isJoined && !hasRemoteParticipant) {
      // Outgoing call waiting for answer
      if (calleeId && !roomUrl) {
        return `Ringing... (${ringTimeout}s)`;
      }
      return "Ringing...";
    }
    return "Waiting...";
  };

  const getStatusColor = () => {
    switch (callStatus) {
      case "connected":
        return "text-green-500";
      case "connecting":
        return "text-yellow-500";
      case "ringing":
        return "text-primary animate-pulse";
      default:
        return "text-muted-foreground";
    }
  };

  // Show lobby for outgoing calls
  if (showLobby) {
    return (
      <PreCallLobby
        name={name}
        avatar={avatar}
        callType={type}
        onJoin={startCall}
        onCancel={() => navigate(-1)}
        isConnecting={isJoining}
      />
    );
  }

  const remoteParticipantCount = participants.filter(p => !p.local).length;
  const hasRemoteVideo = participants.some(p => !p.local && (p.tracks?.video?.state === "playable" || p.tracks?.screenVideo?.state === "playable"));
  const isRemoteScreenSharing = participants.some(p => !p.local && p.tracks?.screenVideo?.state === "playable");

  return (
    <div className="h-screen bg-background flex flex-col">
      {/* Video/Avatar Area */}
      <div className="flex-1 relative overflow-hidden">
        {/* Show remote video if available (for both audio and video calls when video is enabled) */}
        {hasRemoteVideo ? (
          <div className="absolute inset-0 bg-gradient-to-b from-muted/50 to-muted">
            {/* Remote video */}
            <video
              ref={remoteVideoRef}
              autoPlay
              playsInline
              className="w-full h-full object-cover"
            />
          </div>
        ) : (
          <div className="absolute inset-0 bg-gradient-to-b from-primary/10 to-background flex flex-col items-center justify-center gap-6">
            <Avatar className="w-32 h-32 border-4 border-primary/20">
              {avatar && <AvatarImage src={avatar} alt={name} />}
              <AvatarFallback className="text-4xl bg-primary/20 text-primary">
                {name.split(' ').map(n => n[0]).join('').toUpperCase()}
              </AvatarFallback>
            </Avatar>
            <div className="text-center">
              <h2 className="text-2xl font-semibold text-foreground">{name}</h2>
              <p className={cn("mt-2 font-medium", getStatusColor())}>{getStatusText()}</p>
              {callStatus === "connected" && (
                <div className="flex items-center justify-center gap-2 mt-2">
                  <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
                  <span className="text-sm text-green-500">Connected</span>
                </div>
              )}
              {callStatus === "ringing" && calleeId && !roomUrl && (
                <div className="flex items-center justify-center gap-2 mt-2">
                  <div className="w-2 h-2 bg-primary rounded-full animate-ping" />
                  <span className="text-sm text-primary">Calling...</span>
                </div>
              )}
              {error && (
                <p className="text-destructive text-sm mt-2">{error}</p>
              )}
            </div>
          </div>
        )}

        {/* Local video preview - show when local video is enabled */}
        {!isVideoOff && (
          <div className={cn(
            "absolute top-4 right-4 w-32 h-48 sm:w-40 sm:h-60 md:w-48 md:h-72 rounded-lg overflow-hidden border-2 border-border shadow-lg transition-all"
          )}>
            <video
              ref={localVideoRef}
              autoPlay
              playsInline
              muted
              className="w-full h-full object-cover mirror"
            />
          </div>
        )}

        {/* Status overlay */}
        <div className="absolute top-4 left-4 flex flex-col gap-2">
          {/* Participant count */}
          {remoteParticipantCount > 0 && (
            <div className="bg-background/90 text-foreground px-3 py-1 rounded-full text-sm flex items-center gap-2">
              <Users className="w-4 h-4" />
              {remoteParticipantCount + 1} in call
            </div>
          )}

          {isMuted && (
            <div className="bg-destructive/90 text-destructive-foreground px-3 py-1 rounded-full text-sm flex items-center gap-2">
              <MicOff className="w-4 h-4" />
              Muted
            </div>
          )}
          {isVideoOff && type === "video" && (
            <div className="bg-muted/90 text-muted-foreground px-3 py-1 rounded-full text-sm flex items-center gap-2">
              <VideoOff className="w-4 h-4" />
              Camera Off
            </div>
          )}
          {isScreenSharing && (
            <div className="bg-primary/90 text-primary-foreground px-3 py-1 rounded-full text-sm flex items-center gap-2">
              <Monitor className="w-4 h-4" />
              Sharing Screen
            </div>
          )}
          {isRemoteScreenSharing && (
            <div className="bg-blue-500/90 text-white px-3 py-1 rounded-full text-sm flex items-center gap-2">
              <Monitor className="w-4 h-4" />
              Viewing Screen
            </div>
          )}
        </div>
      </div>

      {/* Controls */}
      <div className="p-4 sm:p-6 bg-card border-t border-border">
        <div className="max-w-2xl mx-auto flex flex-wrap items-center justify-center gap-3 sm:gap-4">
          {/* Mute */}
          <Button
            size="lg"
            variant={isMuted ? "destructive" : "secondary"}
            className="rounded-full w-12 h-12 sm:w-14 sm:h-14"
            onClick={toggleMute}
            disabled={!isJoined}
            title={isMuted ? "Unmute" : "Mute"}
          >
            {isMuted ? <MicOff className="w-5 h-5 sm:w-6 sm:h-6" /> : <Mic className="w-5 h-5 sm:w-6 sm:h-6" />}
          </Button>

          {/* Video toggle - available for all calls, allows starting video in audio calls */}
          <Button
            size="lg"
            variant={isVideoOff ? "secondary" : "default"}
            className="rounded-full w-12 h-12 sm:w-14 sm:h-14"
            onClick={toggleVideo}
            disabled={!isJoined}
            title={type === "audio" ? "Start video" : (isVideoOff ? "Turn camera on" : "Turn camera off")}
          >
            {isVideoOff ? <VideoOff className="w-5 h-5 sm:w-6 sm:h-6" /> : <Video className="w-5 h-5 sm:w-6 sm:h-6" />}
          </Button>

          {/* Camera switch - only show when video is on */}
          {!isVideoOff && (
            <Button
              size="lg"
              variant="secondary"
              className="rounded-full w-12 h-12 sm:w-14 sm:h-14"
              onClick={switchCamera}
              disabled={!isJoined}
              title={isFrontCamera ? "Switch to back camera" : "Switch to front camera"}
            >
              <SwitchCamera className="w-5 h-5 sm:w-6 sm:h-6" />
            </Button>
          )}

          {/* Screen share - available for all calls (hidden on mobile) */}
          <Button
            size="lg"
            variant={isScreenSharing ? "default" : "secondary"}
            className="rounded-full w-12 h-12 sm:w-14 sm:h-14 hidden sm:flex"
            onClick={handleToggleScreenShare}
            disabled={!isJoined}
            title={isScreenSharing ? "Stop sharing" : "Share screen"}
          >
            {isScreenSharing ? <MonitorOff className="w-5 h-5 sm:w-6 sm:h-6" /> : <Monitor className="w-5 h-5 sm:w-6 sm:h-6" />}
          </Button>

          {/* Loudspeaker toggle - for mobile speaker mode */}
          <Button
            size="lg"
            variant={isLoudspeaker ? "default" : "secondary"}
            className="rounded-full w-12 h-12 sm:w-14 sm:h-14"
            onClick={() => setIsLoudspeaker(!isLoudspeaker)}
            title={isLoudspeaker ? "Switch to earpiece" : "Switch to speaker"}
          >
            <Speaker className="w-5 h-5 sm:w-6 sm:h-6" />
          </Button>

          {/* Mute speaker/volume */}
          <Button
            size="lg"
            variant={isSpeakerOff ? "destructive" : "secondary"}
            className="rounded-full w-12 h-12 sm:w-14 sm:h-14"
            onClick={() => setIsSpeakerOff(!isSpeakerOff)}
            title={isSpeakerOff ? "Unmute audio" : "Mute audio"}
          >
            {isSpeakerOff ? <VolumeX className="w-5 h-5 sm:w-6 sm:h-6" /> : <Volume2 className="w-5 h-5 sm:w-6 sm:h-6" />}
          </Button>

          {/* End call */}
          <Button
            size="lg"
            variant="destructive"
            className="rounded-full w-14 h-14 sm:w-16 sm:h-16 ml-2 sm:ml-4"
            onClick={handleEndCall}
            title="End call"
          >
            <PhoneOff className="w-6 h-6 sm:w-7 sm:h-7" />
          </Button>
        </div>
      </div>
    </div>
  );
};

export default Call;
