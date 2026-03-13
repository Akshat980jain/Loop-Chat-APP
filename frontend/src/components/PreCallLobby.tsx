import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Video, VideoOff, Mic, MicOff, Phone, X, Settings, Volume2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";

interface PreCallLobbyProps {
  name: string;
  avatar?: string | null;
  callType: "audio" | "video";
  onJoin: (settings: { videoEnabled: boolean; audioEnabled: boolean }) => void;
  onCancel: () => void;
  isConnecting?: boolean;
}

export const PreCallLobby = ({
  name,
  avatar,
  callType,
  onJoin,
  onCancel,
  isConnecting = false,
}: PreCallLobbyProps) => {
  const [videoEnabled, setVideoEnabled] = useState(callType === "video");
  const [audioEnabled, setAudioEnabled] = useState(true);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [audioLevel, setAudioLevel] = useState(0);
  const videoRef = useRef<HTMLVideoElement>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const animationFrameRef = useRef<number | null>(null);

  // Initialize media stream
  useEffect(() => {
    let mediaStream: MediaStream | null = null;

    const initMedia = async () => {
      try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
          video: callType === "video",
          audio: true,
        });
        setStream(mediaStream);

        if (videoRef.current && callType === "video") {
          videoRef.current.srcObject = mediaStream;
        }

        // Set up audio level monitoring
        audioContextRef.current = new AudioContext();
        analyserRef.current = audioContextRef.current.createAnalyser();
        const source = audioContextRef.current.createMediaStreamSource(mediaStream);
        source.connect(analyserRef.current);
        analyserRef.current.fftSize = 256;

        const updateAudioLevel = () => {
          if (analyserRef.current) {
            const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);
            analyserRef.current.getByteFrequencyData(dataArray);
            const average = dataArray.reduce((a, b) => a + b) / dataArray.length;
            setAudioLevel(average / 255);
          }
          animationFrameRef.current = requestAnimationFrame(updateAudioLevel);
        };
        updateAudioLevel();
      } catch (error) {
        console.error("Error accessing media devices:", error);
      }
    };

    initMedia();

    return () => {
      if (mediaStream) {
        mediaStream.getTracks().forEach(track => track.stop());
      }
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
      if (audioContextRef.current) {
        audioContextRef.current.close();
      }
    };
  }, [callType]);

  // Handle video toggle
  useEffect(() => {
    if (stream) {
      stream.getVideoTracks().forEach(track => {
        track.enabled = videoEnabled;
      });
    }
  }, [videoEnabled, stream]);

  // Handle audio toggle
  useEffect(() => {
    if (stream) {
      stream.getAudioTracks().forEach(track => {
        track.enabled = audioEnabled;
      });
    }
  }, [audioEnabled, stream]);

  const handleJoin = () => {
    // Stop the preview stream before joining
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
    }
    onJoin({ videoEnabled, audioEnabled });
  };

  const handleCancel = () => {
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
    }
    onCancel();
  };

  return (
    <div className="h-screen bg-background flex flex-col">
      {/* Header */}
      <div className="p-4 border-b border-border flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Avatar className="w-10 h-10">
            {avatar && <AvatarImage src={avatar} alt={name} />}
            <AvatarFallback className="bg-primary/20 text-primary">
              {name.split(' ').map(n => n[0]).join('').toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <div>
            <h2 className="font-semibold text-foreground">{name}</h2>
            <p className="text-sm text-muted-foreground">
              {callType === "video" ? "Video call" : "Audio call"}
            </p>
          </div>
        </div>
        <Button variant="ghost" size="icon" onClick={handleCancel}>
          <X className="w-5 h-5" />
        </Button>
      </div>

      {/* Preview area */}
      <div className="flex-1 flex items-center justify-center p-6">
        <div className="w-full max-w-md">
          {callType === "video" ? (
            <div className="relative aspect-video rounded-2xl overflow-hidden bg-muted border border-border">
              {videoEnabled ? (
                <video
                  ref={videoRef}
                  autoPlay
                  playsInline
                  muted
                  className="w-full h-full object-cover mirror"
                />
              ) : (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-4">
                  <Avatar className="w-24 h-24">
                    {avatar && <AvatarImage src={avatar} alt={name} />}
                    <AvatarFallback className="text-3xl bg-primary/20 text-primary">
                      {name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)}
                    </AvatarFallback>
                  </Avatar>
                  <p className="text-muted-foreground">Camera is off</p>
                </div>
              )}
              
              {/* Audio level indicator */}
              {audioEnabled && (
                <div className="absolute bottom-4 left-4 flex items-center gap-2 bg-background/80 rounded-full px-3 py-1.5">
                  <Volume2 className="w-4 h-4 text-primary" />
                  <div className="w-20 h-2 bg-muted rounded-full overflow-hidden">
                    <div 
                      className="h-full bg-primary transition-all duration-75"
                      style={{ width: `${audioLevel * 100}%` }}
                    />
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="flex flex-col items-center gap-6 py-12">
              <Avatar className="w-32 h-32 border-4 border-primary/20">
                {avatar && <AvatarImage src={avatar} alt={name} />}
                <AvatarFallback className="text-4xl bg-primary/20 text-primary">
                  {name.split(' ').map(n => n[0]).join('').toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="text-center">
                <h3 className="text-xl font-semibold text-foreground">{name}</h3>
                <p className="text-muted-foreground mt-1">Ready to start audio call</p>
              </div>
              
              {/* Audio level indicator for audio calls */}
              {audioEnabled && (
                <div className="flex items-center gap-3 bg-muted/50 rounded-full px-4 py-2">
                  <Volume2 className="w-5 h-5 text-primary" />
                  <div className="w-32 h-2 bg-muted rounded-full overflow-hidden">
                    <div 
                      className="h-full bg-primary transition-all duration-75"
                      style={{ width: `${audioLevel * 100}%` }}
                    />
                  </div>
                  <span className="text-sm text-muted-foreground">Mic active</span>
                </div>
              )}
            </div>
          )}

          {/* Media controls */}
          <div className="flex items-center justify-center gap-4 mt-6">
            <Button
              variant={audioEnabled ? "secondary" : "destructive"}
              size="lg"
              className="rounded-full w-14 h-14"
              onClick={() => setAudioEnabled(!audioEnabled)}
            >
              {audioEnabled ? (
                <Mic className="w-6 h-6" />
              ) : (
                <MicOff className="w-6 h-6" />
              )}
            </Button>

            {callType === "video" && (
              <Button
                variant={videoEnabled ? "secondary" : "destructive"}
                size="lg"
                className="rounded-full w-14 h-14"
                onClick={() => setVideoEnabled(!videoEnabled)}
              >
                {videoEnabled ? (
                  <Video className="w-6 h-6" />
                ) : (
                  <VideoOff className="w-6 h-6" />
                )}
              </Button>
            )}
          </div>
        </div>
      </div>

      {/* Join/Cancel buttons */}
      <div className="p-6 border-t border-border">
        <div className="max-w-md mx-auto flex gap-4">
          <Button
            variant="outline"
            className="flex-1"
            onClick={handleCancel}
            disabled={isConnecting}
          >
            Cancel
          </Button>
          <Button
            className="flex-1 gap-2"
            onClick={handleJoin}
            disabled={isConnecting}
          >
            {isConnecting ? (
              <>
                <div className="w-4 h-4 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full animate-spin" />
                Connecting...
              </>
            ) : (
              <>
                <Phone className="w-5 h-5" />
                Join Call
              </>
            )}
          </Button>
        </div>
      </div>
    </div>
  );
};
