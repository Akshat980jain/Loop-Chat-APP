import { useState, useRef, useCallback } from "react";
import { useToast } from "@/hooks/use-toast";

interface WebRTCConfig {
  iceServers: RTCIceServer[];
}

const defaultConfig: WebRTCConfig = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" },
  ],
};

export const useWebRTC = () => {
  const { toast } = useToast();
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [isScreenSharing, setIsScreenSharing] = useState(false);
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null);
  const screenStreamRef = useRef<MediaStream | null>(null);

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
        description: "Failed to access camera/microphone",
        variant: "destructive",
      });
      throw error;
    }
  }, [toast]);

  const createPeerConnection = useCallback(() => {
    const pc = new RTCPeerConnection(defaultConfig);

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        console.log("New ICE candidate:", event.candidate);
        // In production, send this to the other peer via signaling server
      }
    };

    pc.ontrack = (event) => {
      console.log("Received remote track");
      setRemoteStream(event.streams[0]);
    };

    pc.oniceconnectionstatechange = () => {
      console.log("ICE connection state:", pc.iceConnectionState);
    };

    peerConnectionRef.current = pc;
    return pc;
  }, []);

  const addLocalStream = useCallback((stream: MediaStream) => {
    if (!peerConnectionRef.current) return;

    stream.getTracks().forEach((track) => {
      peerConnectionRef.current?.addTrack(track, stream);
    });
  }, []);

  const toggleScreenShare = useCallback(async () => {
    try {
      if (!isScreenSharing) {
        // Start screen sharing
      const screenStream = await navigator.mediaDevices.getDisplayMedia({
        video: true,
        audio: false,
      });

        screenStreamRef.current = screenStream;

        // Replace video track
        if (localStream && peerConnectionRef.current) {
          const videoTrack = screenStream.getVideoTracks()[0];
          const sender = peerConnectionRef.current
            .getSenders()
            .find((s) => s.track?.kind === "video");

          if (sender) {
            sender.replaceTrack(videoTrack);
          }

          // Update local stream
          const oldVideoTrack = localStream.getVideoTracks()[0];
          localStream.removeTrack(oldVideoTrack);
          localStream.addTrack(videoTrack);
          setLocalStream(new MediaStream(localStream.getTracks()));

          // Stop screen sharing when user clicks browser's stop button
          videoTrack.onended = () => {
            stopScreenShare();
          };
        }

        setIsScreenSharing(true);
        toast({
          title: "Screen Sharing Started",
          description: "Your screen is now being shared",
        });
      } else {
        await stopScreenShare();
      }
    } catch (error) {
      console.error("Error toggling screen share:", error);
      toast({
        title: "Screen Share Error",
        description: "Failed to toggle screen sharing",
        variant: "destructive",
      });
    }
  }, [isScreenSharing, localStream, toast]);

  const stopScreenShare = useCallback(async () => {
    if (screenStreamRef.current) {
      screenStreamRef.current.getTracks().forEach((track) => track.stop());
      screenStreamRef.current = null;
    }

    // Switch back to camera
    if (localStream && peerConnectionRef.current) {
      const newStream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
      });

      const videoTrack = newStream.getVideoTracks()[0];
      const sender = peerConnectionRef.current
        .getSenders()
        .find((s) => s.track?.kind === "video");

      if (sender) {
        sender.replaceTrack(videoTrack);
      }

      const oldVideoTrack = localStream.getVideoTracks()[0];
      localStream.removeTrack(oldVideoTrack);
      localStream.addTrack(videoTrack);
      setLocalStream(new MediaStream(localStream.getTracks()));
    }

    setIsScreenSharing(false);
    toast({
      title: "Screen Sharing Stopped",
      description: "You're back to camera view",
    });
  }, [localStream, toast]);

  const cleanup = useCallback(() => {
    if (localStream) {
      localStream.getTracks().forEach((track) => track.stop());
    }
    if (screenStreamRef.current) {
      screenStreamRef.current.getTracks().forEach((track) => track.stop());
    }
    if (peerConnectionRef.current) {
      peerConnectionRef.current.close();
    }
    setLocalStream(null);
    setRemoteStream(null);
    setIsScreenSharing(false);
  }, [localStream]);

  return {
    localStream,
    remoteStream,
    isScreenSharing,
    initializeLocalStream,
    createPeerConnection,
    addLocalStream,
    toggleScreenShare,
    cleanup,
  };
};
