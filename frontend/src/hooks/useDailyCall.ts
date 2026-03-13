import { useState, useCallback, useRef, useEffect } from "react";
import DailyIframe, { DailyCall, DailyParticipant, DailyEventObject } from "@daily-co/daily-js";
import { supabase } from "@/integrations/supabase/client";

// Fixed Daily.co room URL - using pre-created room
const DAILY_ROOM_URL = "https://akshatjain.daily.co/ChatRoom";
const DAILY_ROOM_NAME = "ChatRoom";

interface DailyCallState {
  isJoining: boolean;
  isJoined: boolean;
  isMuted: boolean;
  isVideoOff: boolean;
  isScreenSharing: boolean;
  isFrontCamera: boolean;
  participants: DailyParticipant[];
  error: string | null;
  callEnded: boolean;
  hasRemoteParticipant: boolean;
}

interface UseDailyCallReturn extends DailyCallState {
  createAndJoinRoom: (calleeId: string, callType: "audio" | "video") => Promise<{ roomUrl: string; roomName: string } | null>;
  joinRoom: (roomUrl: string, token?: string, callType?: "audio" | "video") => Promise<boolean>;
  leaveCall: () => Promise<void>;
  toggleMute: () => void;
  toggleVideo: () => void;
  toggleScreenShare: () => Promise<void>;
  switchCamera: () => Promise<void>;
  getLocalVideoTrack: () => MediaStreamTrack | null;
  getRemoteVideoTrack: () => MediaStreamTrack | null;
  callObject: DailyCall | null;
}

export const useDailyCall = (): UseDailyCallReturn => {
  const [state, setState] = useState<DailyCallState>({
    isJoining: false,
    isJoined: false,
    isMuted: false,
    isVideoOff: false,
    isScreenSharing: false,
    isFrontCamera: true,
    participants: [],
    error: null,
    callEnded: false,
    hasRemoteParticipant: false,
  });

  const cameraDevicesRef = useRef<MediaDeviceInfo[]>([]);
  const currentCameraIndexRef = useRef<number>(0);

  const callObjectRef = useRef<DailyCall | null>(null);

  const handleParticipantUpdated = useCallback((event?: DailyEventObject) => {
    if (!callObjectRef.current) return;

    const participants = callObjectRef.current.participants();
    const remoteParticipants = Object.values(participants).filter(p => !p.local);
    const hasRemote = remoteParticipants.length > 0;

    // Log audio/video track status for debugging
    const local = participants.local;
    if (local) {
      console.log("Local audio track:", local.tracks?.audio?.state, "persistentTrack:", !!local.tracks?.audio?.persistentTrack);
      console.log("Local video track:", local.tracks?.video?.state);
    }

    if (remoteParticipants.length > 0) {
      const remote = remoteParticipants[0];
      console.log("Remote audio track:", remote.tracks?.audio?.state, "persistentTrack:", !!remote.tracks?.audio?.persistentTrack);
      console.log("Remote video track:", remote.tracks?.video?.state);
    }

    setState(prev => ({
      ...prev,
      participants: Object.values(participants),
      hasRemoteParticipant: hasRemote,
    }));
  }, []);

  // Handle when remote participant leaves - end call for local user too
  const handleParticipantLeft = useCallback((event?: DailyEventObject) => {
    if (!callObjectRef.current) return;

    const participants = callObjectRef.current.participants();
    const remoteParticipants = Object.values(participants).filter(p => !p.local);

    // Update participants state
    setState(prev => ({
      ...prev,
      participants: Object.values(participants),
    }));

    // If no remote participants left, signal that call has ended
    if (remoteParticipants.length === 0 && state.isJoined) {
      console.log("Remote participant left, ending call");
      setState(prev => ({
        ...prev,
        callEnded: true,
      }));
    }
  }, [state.isJoined]);

  const handleJoinedMeeting = useCallback(() => {
    setState(prev => ({
      ...prev,
      isJoining: false,
      isJoined: true,
      callEnded: false,
    }));
    handleParticipantUpdated();
  }, [handleParticipantUpdated]);

  const handleLeftMeeting = useCallback(() => {
    setState(prev => ({
      ...prev,
      isJoined: false,
      participants: [],
    }));
  }, []);

  const handleError = useCallback((event?: DailyEventObject) => {
    console.error("Daily error:", event);
    setState(prev => ({
      ...prev,
      error: event?.errorMsg || "Unknown error occurred",
      isJoining: false,
    }));
  }, []);

  // Helper to request camera/microphone permissions before joining
  const requestMediaPermissions = useCallback(async (video: boolean): Promise<boolean> => {
    try {
      console.log("Requesting media permissions, video:", video);
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: video ? { facingMode: "user" } : false,
      });
      // Stop the temporary stream - Daily.co will create its own
      stream.getTracks().forEach(track => track.stop());
      console.log("Media permissions granted");
      return true;
    } catch (error) {
      console.error("Media permission error:", error);
      if (error instanceof Error) {
        if (error.name === "NotAllowedError") {
          setState(prev => ({
            ...prev,
            error: "Camera/microphone permission denied. Please allow access in your browser settings.",
          }));
        } else if (error.name === "NotFoundError") {
          setState(prev => ({
            ...prev,
            error: "No camera or microphone found on this device.",
          }));
        } else {
          setState(prev => ({
            ...prev,
            error: `Failed to access camera: ${error.message}`,
          }));
        }
      }
      return false;
    }
  }, []);

  const createAndJoinRoom = useCallback(async (
    calleeId: string,
    callType: "audio" | "video"
  ): Promise<{ roomUrl: string; roomName: string } | null> => {
    try {
      // Clean up any existing call object first
      if (callObjectRef.current) {
        try {
          await callObjectRef.current.leave();
          await callObjectRef.current.destroy();
        } catch (e) {
          // Ignore cleanup errors
        }
        callObjectRef.current = null;
      }

      setState(prev => ({ ...prev, isJoining: true, error: null }));

      // Request camera/microphone permissions upfront
      const hasPermission = await requestMediaPermissions(callType === "video");
      if (!hasPermission) {
        setState(prev => ({ ...prev, isJoining: false }));
        return null;
      }

      setState(prev => ({ ...prev, isJoining: true, error: null }));

      const { data: { user } } = await supabase.auth.getUser();
      if (!user) throw new Error("Not authenticated");

      // Use the fixed pre-created room and get a token
      const response = await supabase.functions.invoke("daily-room", {
        body: {
          action: "get-token",
          roomName: DAILY_ROOM_NAME,
          callerId: user.id,
        },
      });

      if (response.error) throw new Error(response.error.message);

      const callerToken = response.data?.token;

      // Store the call in the database with room URL for the callee
      const { data: callData, error: callError } = await supabase
        .from("calls")
        .insert({
          caller_id: user.id,
          callee_id: calleeId,
          call_type: callType,
          status: "ringing",
          room_url: DAILY_ROOM_URL,
        })
        .select()
        .single();

      if (callError) {
        console.error("Error creating call record:", callError);
      }

      // Create and join
      const callObject = DailyIframe.createCallObject({
        url: DAILY_ROOM_URL,
        token: callerToken,
        videoSource: callType === "video",
        audioSource: true,
      });

      callObjectRef.current = callObject;

      // Set up event listeners
      callObject.on("joined-meeting", handleJoinedMeeting);
      callObject.on("left-meeting", handleLeftMeeting);
      callObject.on("participant-joined", handleParticipantUpdated);
      callObject.on("participant-updated", handleParticipantUpdated);
      callObject.on("participant-left", handleParticipantLeft);
      callObject.on("track-started", handleParticipantUpdated);
      callObject.on("error", handleError);

      await callObject.join();

      if (callType === "audio") {
        await callObject.setLocalVideo(false);
        setState(prev => ({ ...prev, isVideoOff: true }));
      }

      return { roomUrl: DAILY_ROOM_URL, roomName: DAILY_ROOM_NAME };
    } catch (error) {
      console.error("Error creating/joining room:", error);
      setState(prev => ({
        ...prev,
        isJoining: false,
        error: error instanceof Error ? error.message : "Failed to create room",
      }));
      return null;
    }
  }, [handleJoinedMeeting, handleLeftMeeting, handleParticipantUpdated, handleParticipantLeft, handleError, requestMediaPermissions]);

  const joinRoom = useCallback(async (roomUrl: string, token?: string, callType: "audio" | "video" = "video"): Promise<boolean> => {
    try {
      // Clean up any existing call object first
      if (callObjectRef.current) {
        try {
          await callObjectRef.current.leave();
          await callObjectRef.current.destroy();
        } catch (e) {
          // Ignore cleanup errors
        }
        callObjectRef.current = null;
      }

      setState(prev => ({ ...prev, isJoining: true, error: null }));

      // Request camera/microphone permissions upfront
      const hasPermission = await requestMediaPermissions(callType === "video");
      if (!hasPermission) {
        setState(prev => ({ ...prev, isJoining: false }));
        return false;
      }

      // Only include token in options if it's a valid string
      // For audio calls, disable video source initially
      const callOptions: { url: string; token?: string; videoSource: boolean; audioSource: boolean } = {
        url: roomUrl,
        videoSource: callType === "video",
        audioSource: true,
      };

      if (token && typeof token === 'string' && token.length > 0) {
        callOptions.token = token;
      }

      const callObject = DailyIframe.createCallObject(callOptions);

      callObjectRef.current = callObject;

      callObject.on("joined-meeting", handleJoinedMeeting);
      callObject.on("left-meeting", handleLeftMeeting);
      callObject.on("participant-joined", handleParticipantUpdated);
      callObject.on("participant-updated", handleParticipantUpdated);
      callObject.on("participant-left", handleParticipantLeft);
      callObject.on("track-started", handleParticipantUpdated);
      callObject.on("error", handleError);

      await callObject.join();

      // For audio calls, ensure video is off
      if (callType === "audio") {
        await callObject.setLocalVideo(false);
        setState(prev => ({ ...prev, isVideoOff: true }));
      }

      return true;
    } catch (error) {
      console.error("Error joining room:", error);
      setState(prev => ({
        ...prev,
        isJoining: false,
        error: error instanceof Error ? error.message : "Failed to join room",
      }));
      return false;
    }
  }, [handleJoinedMeeting, handleLeftMeeting, handleParticipantUpdated, handleParticipantLeft, handleError, requestMediaPermissions]);

  const leaveCall = useCallback(async () => {
    if (callObjectRef.current) {
      await callObjectRef.current.leave();
      await callObjectRef.current.destroy();
      callObjectRef.current = null;
    }

    setState({
      isJoining: false,
      isJoined: false,
      isMuted: false,
      isVideoOff: false,
      isScreenSharing: false,
      isFrontCamera: true,
      participants: [],
      error: null,
      callEnded: false,
      hasRemoteParticipant: false,
    });
  }, []);

  const toggleMute = useCallback(() => {
    if (!callObjectRef.current) return;

    const currentMuted = state.isMuted;
    callObjectRef.current.setLocalAudio(currentMuted);
    setState(prev => ({ ...prev, isMuted: !currentMuted }));
  }, [state.isMuted]);

  const toggleVideo = useCallback(() => {
    if (!callObjectRef.current) return;

    const currentVideoOff = state.isVideoOff;
    callObjectRef.current.setLocalVideo(currentVideoOff);
    setState(prev => ({ ...prev, isVideoOff: !currentVideoOff }));
  }, [state.isVideoOff]);

  const toggleScreenShare = useCallback(async () => {
    if (!callObjectRef.current) return;

    if (state.isScreenSharing) {
      await callObjectRef.current.stopScreenShare();
      setState(prev => ({ ...prev, isScreenSharing: false }));
    } else {
      await callObjectRef.current.startScreenShare();
      setState(prev => ({ ...prev, isScreenSharing: true }));
    }
  }, [state.isScreenSharing]);

  const switchCamera = useCallback(async () => {
    if (!callObjectRef.current) return;

    try {
      // Get available video devices
      const devices = await navigator.mediaDevices.enumerateDevices();
      const videoDevices = devices.filter(d => d.kind === 'videoinput');

      if (videoDevices.length < 2) {
        console.log("Only one camera available");
        return;
      }

      cameraDevicesRef.current = videoDevices;

      // Cycle to next camera
      currentCameraIndexRef.current = (currentCameraIndexRef.current + 1) % videoDevices.length;
      const nextDevice = videoDevices[currentCameraIndexRef.current];

      // Switch camera using Daily's setInputDevicesAsync
      await callObjectRef.current.setInputDevicesAsync({
        videoDeviceId: nextDevice.deviceId,
      });

      // Determine if it's front camera (usually contains "front" or is first device)
      const isFront = nextDevice.label.toLowerCase().includes('front') ||
        nextDevice.label.toLowerCase().includes('user') ||
        currentCameraIndexRef.current === 0;

      setState(prev => ({ ...prev, isFrontCamera: isFront }));
      console.log("Switched to camera:", nextDevice.label);
    } catch (error) {
      console.error("Error switching camera:", error);
    }
  }, []);

  const getLocalVideoTrack = useCallback((): MediaStreamTrack | null => {
    if (!callObjectRef.current) return null;
    const local = callObjectRef.current.participants().local;
    return local?.tracks?.video?.persistentTrack || null;
  }, []);

  const getRemoteVideoTrack = useCallback((): MediaStreamTrack | null => {
    if (!callObjectRef.current) return null;
    const participants = callObjectRef.current.participants();
    const remoteParticipant = Object.values(participants).find(p => !p.local);
    return remoteParticipant?.tracks?.video?.persistentTrack || null;
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (callObjectRef.current) {
        callObjectRef.current.leave();
        callObjectRef.current.destroy();
      }
    };
  }, []);

  return {
    ...state,
    createAndJoinRoom,
    joinRoom,
    leaveCall,
    toggleMute,
    toggleVideo,
    toggleScreenShare,
    switchCamera,
    getLocalVideoTrack,
    getRemoteVideoTrack,
    callObject: callObjectRef.current,
  };
};
