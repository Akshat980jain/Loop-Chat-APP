import { useState, useRef, useCallback, useEffect } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";
import { RealtimeChannel } from "@supabase/supabase-js";

interface CallSignal {
  id: string;
  call_id: string;
  sender_id: string;
  signal_type: string;
  signal_data: RTCSessionDescriptionInit | RTCIceCandidateInit;
  created_at: string;
}

interface Call {
  id: string;
  caller_id: string;
  callee_id: string;
  call_type: string;
  status: string;
  started_at: string | null;
  ended_at: string | null;
  created_at: string;
}

// STUN servers for NAT traversal (free public servers)
// For production, add TURN servers for better connectivity behind strict NATs
const ICE_SERVERS: RTCIceServer[] = [
  // Google STUN servers
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "stun:stun1.l.google.com:19302" },
  { urls: "stun:stun2.l.google.com:19302" },
  { urls: "stun:stun3.l.google.com:19302" },
  { urls: "stun:stun4.l.google.com:19302" },
  // Additional public STUN servers for better coverage
  { urls: "stun:stun.stunprotocol.org:3478" },
  { urls: "stun:stun.voip.blackberry.com:3478" },
  // Free TURN servers from OpenRelay (for NAT traversal)
  // Note: For production, use your own TURN server or a service like Twilio/Xirsys
  {
    urls: "turn:openrelay.metered.ca:80",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
  {
    urls: "turn:openrelay.metered.ca:443",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
  {
    urls: "turn:openrelay.metered.ca:443?transport=tcp",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
];

export const useCallSignaling = () => {
  const { toast } = useToast();
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [callId, setCallId] = useState<string | null>(null);
  const [incomingCall, setIncomingCall] = useState<Call | null>(null);

  const peerConnectionRef = useRef<RTCPeerConnection | null>(null);
  const channelRef = useRef<RealtimeChannel | null>(null);
  const currentUserIdRef = useRef<string | null>(null);
  const pendingCandidatesRef = useRef<RTCIceCandidate[]>([]);

  // Get current user ID
  useEffect(() => {
    const getCurrentUser = async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (user) {
        currentUserIdRef.current = user.id;
      }
    };
    getCurrentUser();
  }, []);

  // Listen for incoming calls
  useEffect(() => {
    const listenForIncomingCalls = async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      const channel = supabase
        .channel('incoming-calls')
        .on(
          'postgres_changes',
          {
            event: 'INSERT',
            schema: 'public',
            table: 'calls',
            filter: `callee_id=eq.${user.id}`,
          },
          (payload) => {
            const call = payload.new as Call;
            if (call.status === 'ringing') {
              setIncomingCall(call);
            }
          }
        )
        .subscribe();

      return () => {
        supabase.removeChannel(channel);
      };
    };

    listenForIncomingCalls();
  }, []);

  const initializeLocalStream = useCallback(async (video: boolean = true) => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
        video: video ? {
          width: { ideal: 1280 },
          height: { ideal: 720 },
        } : false,
      });

      setLocalStream(stream);
      return stream;
    } catch (error) {
      console.error("Error accessing media devices:", error);
      toast({
        title: "Media Access Error",
        description: "Failed to access camera/microphone. Please check permissions.",
        variant: "destructive",
      });
      throw error;
    }
  }, [toast]);

  const sendSignal = async (callIdParam: string, signalType: string, signalData: RTCSessionDescriptionInit | RTCIceCandidateInit) => {
    if (!currentUserIdRef.current) return;
    
    const session = await supabase.auth.getSession();
    const token = session.data.session?.access_token;
    
    // Direct insert using fetch to Supabase REST API (bypassing type checking for new tables)
    const response = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/rest/v1/call_signals`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({
        call_id: callIdParam,
        sender_id: currentUserIdRef.current,
        signal_type: signalType,
        signal_data: signalData,
      }),
    });
    
    if (!response.ok) {
      console.error('Error sending signal:', await response.text());
    }
  };

  const createPeerConnection = useCallback((callIdParam: string) => {
    console.log("Creating peer connection for call:", callIdParam);
    
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });

    pc.onicecandidate = async (event) => {
      if (event.candidate && currentUserIdRef.current) {
        console.log("Sending ICE candidate");
        await sendSignal(callIdParam, 'ice-candidate', event.candidate.toJSON());
      }
    };

    pc.ontrack = (event) => {
      console.log("Received remote track");
      setRemoteStream(event.streams[0]);
    };

    pc.oniceconnectionstatechange = () => {
      console.log("ICE connection state:", pc.iceConnectionState);
      if (pc.iceConnectionState === 'connected') {
        setIsConnected(true);
        setIsConnecting(false);
      } else if (pc.iceConnectionState === 'disconnected' || pc.iceConnectionState === 'failed') {
        setIsConnected(false);
      }
    };

    pc.onconnectionstatechange = () => {
      console.log("Connection state:", pc.connectionState);
    };

    peerConnectionRef.current = pc;
    return pc;
  }, []);

  const subscribeToSignals = useCallback((callIdParam: string) => {
    console.log("Subscribing to signals for call:", callIdParam);
    
    const channel = supabase
      .channel(`call-signals-${callIdParam}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'call_signals',
          filter: `call_id=eq.${callIdParam}`,
        },
        async (payload) => {
          const signal = payload.new as CallSignal;
          
          // Ignore our own signals
          if (signal.sender_id === currentUserIdRef.current) return;
          
          console.log("Received signal:", signal.signal_type);
          
          const pc = peerConnectionRef.current;
          if (!pc) {
            console.log("No peer connection yet");
            return;
          }

          try {
            if (signal.signal_type === 'offer') {
              console.log("Processing offer");
              await pc.setRemoteDescription(new RTCSessionDescription(signal.signal_data as RTCSessionDescriptionInit));
              
              // Add any pending candidates
              for (const candidate of pendingCandidatesRef.current) {
                await pc.addIceCandidate(candidate);
              }
              pendingCandidatesRef.current = [];
              
              const answer = await pc.createAnswer();
              await pc.setLocalDescription(answer);
              
              await sendSignal(callIdParam, 'answer', answer);
            } else if (signal.signal_type === 'answer') {
              console.log("Processing answer");
              await pc.setRemoteDescription(new RTCSessionDescription(signal.signal_data as RTCSessionDescriptionInit));
              
              // Add any pending candidates
              for (const candidate of pendingCandidatesRef.current) {
                await pc.addIceCandidate(candidate);
              }
              pendingCandidatesRef.current = [];
            } else if (signal.signal_type === 'ice-candidate') {
              console.log("Processing ICE candidate");
              const candidate = new RTCIceCandidate(signal.signal_data as RTCIceCandidateInit);
              
              if (pc.remoteDescription) {
                await pc.addIceCandidate(candidate);
              } else {
                // Queue the candidate if remote description not set yet
                pendingCandidatesRef.current.push(candidate);
              }
            }
          } catch (error) {
            console.error("Error processing signal:", error);
          }
        }
      )
      .subscribe();

    channelRef.current = channel;
    return channel;
  }, []);

  const createCall = async (calleeId: string, callType: string): Promise<Call> => {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) throw new Error("Not authenticated");
    
    const response = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/rest/v1/calls`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
        'Authorization': `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`,
        'Prefer': 'return=representation',
      },
      body: JSON.stringify({
        caller_id: user.id,
        callee_id: calleeId,
        call_type: callType,
        status: 'ringing',
      }),
    });
    
    if (!response.ok) {
      throw new Error('Failed to create call');
    }
    
    const [call] = await response.json();

    // Send push notification to callee
    try {
      // Get caller profile for notification
      const { data: callerProfile } = await supabase
        .from('profiles')
        .select('full_name, avatar_url')
        .eq('user_id', user.id)
        .single();

      // Trigger push notification via edge function
      await supabase.functions.invoke('send-push-notification', {
        body: {
          callee_id: calleeId,
          caller_name: callerProfile?.full_name || 'Unknown',
          caller_avatar: callerProfile?.avatar_url,
          call_type: callType,
          call_id: call.id
        }
      });
      console.log('Push notification sent to callee');
    } catch (pushError) {
      console.error('Error sending push notification:', pushError);
      // Don't fail the call if push fails
    }

    return call;
  };

  const startCall = useCallback(async (calleeId: string, callType: 'audio' | 'video' = 'audio') => {
    try {
      setIsConnecting(true);
      
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) throw new Error("Not authenticated");
      
      // Initialize local stream
      const stream = await initializeLocalStream(callType === 'video');
      
      // Create call record
      const call = await createCall(calleeId, callType);
      setCallId(call.id);
      
      // Create peer connection
      const pc = createPeerConnection(call.id);
      
      // Add local tracks
      stream.getTracks().forEach((track) => {
        pc.addTrack(track, stream);
      });
      
      // Subscribe to signals
      subscribeToSignals(call.id);
      
      // Create and send offer
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      
      await sendSignal(call.id, 'offer', offer);
      
      console.log("Call initiated, waiting for answer...");
      
      return call.id;
    } catch (error) {
      console.error("Error starting call:", error);
      setIsConnecting(false);
      toast({
        title: "Call Failed",
        description: "Could not start the call. Please try again.",
        variant: "destructive",
      });
      throw error;
    }
  }, [initializeLocalStream, createPeerConnection, subscribeToSignals, toast]);

  const acceptCall = useCallback(async (call: Call) => {
    try {
      setIsConnecting(true);
      setCallId(call.id);
      setIncomingCall(null);
      
      // Initialize local stream
      const stream = await initializeLocalStream(call.call_type === 'video');
      
      // Create peer connection
      const pc = createPeerConnection(call.id);
      
      // Add local tracks
      stream.getTracks().forEach((track) => {
        pc.addTrack(track, stream);
      });
      
      // Subscribe to signals first to receive the offer
      subscribeToSignals(call.id);
      
      // Update call status
      await fetch(`${import.meta.env.VITE_SUPABASE_URL}/rest/v1/calls?id=eq.${call.id}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
          'Authorization': `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`,
        },
        body: JSON.stringify({ 
          status: 'connected', 
          started_at: new Date().toISOString() 
        }),
      });
      
      // Fetch existing signals (the offer should be there)
      const signalsResponse = await fetch(
        `${import.meta.env.VITE_SUPABASE_URL}/rest/v1/call_signals?call_id=eq.${call.id}&order=created_at.asc`,
        {
          headers: {
            'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
            'Authorization': `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`,
          },
        }
      );
      
      const signals: CallSignal[] = await signalsResponse.json();
      
      // Process existing signals
      for (const signal of signals || []) {
        if (signal.sender_id === currentUserIdRef.current) continue;
        
        if (signal.signal_type === 'offer') {
          console.log("Processing existing offer");
          await pc.setRemoteDescription(new RTCSessionDescription(signal.signal_data as RTCSessionDescriptionInit));
          
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          
          await sendSignal(call.id, 'answer', answer);
        } else if (signal.signal_type === 'ice-candidate') {
          if (pc.remoteDescription) {
            await pc.addIceCandidate(new RTCIceCandidate(signal.signal_data as RTCIceCandidateInit));
          } else {
            pendingCandidatesRef.current.push(new RTCIceCandidate(signal.signal_data as RTCIceCandidateInit));
          }
        }
      }
      
      console.log("Call accepted");
    } catch (error) {
      console.error("Error accepting call:", error);
      setIsConnecting(false);
      toast({
        title: "Call Failed",
        description: "Could not accept the call.",
        variant: "destructive",
      });
      throw error;
    }
  }, [initializeLocalStream, createPeerConnection, subscribeToSignals, toast]);

  const rejectCall = useCallback(async (call: Call) => {
    try {
      await fetch(`${import.meta.env.VITE_SUPABASE_URL}/rest/v1/calls?id=eq.${call.id}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
          'Authorization': `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`,
        },
        body: JSON.stringify({ 
          status: 'rejected', 
          ended_at: new Date().toISOString() 
        }),
      });
      
      setIncomingCall(null);
    } catch (error) {
      console.error("Error rejecting call:", error);
    }
  }, []);

  const endCall = useCallback(async () => {
    try {
      // Stop all tracks
      if (localStream) {
        localStream.getTracks().forEach((track) => track.stop());
      }
      
      // Close peer connection
      if (peerConnectionRef.current) {
        peerConnectionRef.current.close();
        peerConnectionRef.current = null;
      }
      
      // Unsubscribe from channel
      if (channelRef.current) {
        supabase.removeChannel(channelRef.current);
        channelRef.current = null;
      }
      
      // Update call status
      if (callId) {
        await fetch(`${import.meta.env.VITE_SUPABASE_URL}/rest/v1/calls?id=eq.${callId}`, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            'apikey': import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
            'Authorization': `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`,
          },
          body: JSON.stringify({ 
            status: 'ended', 
            ended_at: new Date().toISOString() 
          }),
        });
      }
      
      setLocalStream(null);
      setRemoteStream(null);
      setIsConnected(false);
      setIsConnecting(false);
      setCallId(null);
      pendingCandidatesRef.current = [];
      
      console.log("Call ended");
    } catch (error) {
      console.error("Error ending call:", error);
    }
  }, [localStream, callId]);

  const toggleMute = useCallback(() => {
    if (localStream) {
      const audioTrack = localStream.getAudioTracks()[0];
      if (audioTrack) {
        audioTrack.enabled = !audioTrack.enabled;
        return !audioTrack.enabled;
      }
    }
    return false;
  }, [localStream]);

  const toggleVideo = useCallback(() => {
    if (localStream) {
      const videoTrack = localStream.getVideoTracks()[0];
      if (videoTrack) {
        videoTrack.enabled = !videoTrack.enabled;
        return !videoTrack.enabled;
      }
    }
    return false;
  }, [localStream]);

  const startScreenShare = useCallback(async (): Promise<MediaStream | null> => {
    try {
      const screenStream = await navigator.mediaDevices.getDisplayMedia({
        video: true,
        audio: false,
      });

      const pc = peerConnectionRef.current;
      if (pc && localStream) {
        // Get the video sender
        const videoSender = pc.getSenders().find(
          sender => sender.track?.kind === 'video'
        );

        if (videoSender) {
          // Replace the camera track with screen track
          const screenTrack = screenStream.getVideoTracks()[0];
          await videoSender.replaceTrack(screenTrack);

          // When screen sharing stops, revert to camera
          screenTrack.onended = async () => {
            const cameraTrack = localStream.getVideoTracks()[0];
            if (cameraTrack && videoSender) {
              await videoSender.replaceTrack(cameraTrack);
            }
          };
        }
      }

      return screenStream;
    } catch (error) {
      console.error('Error starting screen share:', error);
      return null;
    }
  }, [localStream]);

  const stopScreenShare = useCallback(async (screenStream: MediaStream) => {
    // Stop screen sharing tracks
    screenStream.getTracks().forEach(track => track.stop());

    // Revert to camera
    const pc = peerConnectionRef.current;
    if (pc && localStream) {
      const videoSender = pc.getSenders().find(
        sender => sender.track?.kind === 'video'
      );
      const cameraTrack = localStream.getVideoTracks()[0];
      
      if (videoSender && cameraTrack) {
        await videoSender.replaceTrack(cameraTrack);
      }
    }
  }, [localStream]);

  // Get peer connection for stats
  const getPeerConnection = useCallback(() => {
    return peerConnectionRef.current;
  }, []);

  return {
    localStream,
    remoteStream,
    isConnected,
    isConnecting,
    callId,
    incomingCall,
    startCall,
    acceptCall,
    rejectCall,
    endCall,
    toggleMute,
    toggleVideo,
    startScreenShare,
    stopScreenShare,
    getPeerConnection,
  };
};
