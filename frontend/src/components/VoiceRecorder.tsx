import { useState, useRef, useEffect } from "react";
import { Mic, X, Send, Play, Pause } from "lucide-react";
import { Button } from "./ui/button";
import { cn } from "@/lib/utils";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";

type VoiceRecorderProps = {
  onVoiceMessageSend: (transcript: string) => void;
  onCancel: () => void;
};

export const VoiceRecorder = ({ onVoiceMessageSend, onCancel }: VoiceRecorderProps) => {
  const [isRecording, setIsRecording] = useState(false);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [audioLevels, setAudioLevels] = useState<number[]>(new Array(40).fill(0));
  const [isTranscribing, setIsTranscribing] = useState(false);
  
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const audioPlayerRef = useRef<HTMLAudioElement | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const { toast } = useToast();

  useEffect(() => {
    startRecording();
    return () => {
      cleanup();
    };
  }, []);

  const cleanup = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    if (audioContextRef.current) {
      audioContextRef.current.close();
    }
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
    }
    if (timerRef.current) {
      clearInterval(timerRef.current);
    }
    if (audioPlayerRef.current) {
      audioPlayerRef.current.pause();
    }
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ 
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        } 
      });

      audioContextRef.current = new AudioContext();
      const source = audioContextRef.current.createMediaStreamSource(stream);
      analyserRef.current = audioContextRef.current.createAnalyser();
      analyserRef.current.fftSize = 256;
      source.connect(analyserRef.current);

      mediaRecorderRef.current = new MediaRecorder(stream, {
        mimeType: 'audio/webm',
      });

      chunksRef.current = [];

      mediaRecorderRef.current.ondataavailable = (e) => {
        if (e.data.size > 0) {
          chunksRef.current.push(e.data);
        }
      };

      mediaRecorderRef.current.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: 'audio/webm' });
        setAudioBlob(blob);
        stream.getTracks().forEach(track => track.stop());
      };

      mediaRecorderRef.current.start();
      setIsRecording(true);

      // Start timer
      timerRef.current = setInterval(() => {
        setRecordingTime(prev => prev + 1);
      }, 1000);

      // Start visualization
      visualize();
    } catch (error) {
      console.error('Error accessing microphone:', error);
      toast({
        title: "Microphone Error",
        description: "Could not access your microphone. Please check permissions.",
        variant: "destructive",
      });
      onCancel();
    }
  };

  const visualize = () => {
    if (!analyserRef.current) return;

    const bufferLength = analyserRef.current.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);

    const draw = () => {
      animationFrameRef.current = requestAnimationFrame(draw);
      analyserRef.current!.getByteFrequencyData(dataArray);

      const samples = 40;
      const step = Math.floor(bufferLength / samples);
      const newLevels = [];

      for (let i = 0; i < samples; i++) {
        const value = dataArray[i * step];
        newLevels.push(value / 255);
      }

      setAudioLevels(newLevels);
    };

    draw();
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
    }
    if (timerRef.current) {
      clearInterval(timerRef.current);
    }
    setIsRecording(false);
  };

  const playRecording = () => {
    if (!audioBlob) return;

    if (audioPlayerRef.current) {
      audioPlayerRef.current.pause();
    }

    const audio = new Audio(URL.createObjectURL(audioBlob));
    audioPlayerRef.current = audio;

    audio.onended = () => {
      setIsPlaying(false);
    };

    audio.play();
    setIsPlaying(true);
  };

  const pausePlayback = () => {
    if (audioPlayerRef.current) {
      audioPlayerRef.current.pause();
      setIsPlaying(false);
    }
  };

  const sendVoiceMessage = async () => {
    if (!audioBlob) return;

    setIsTranscribing(true);

    try {
      // Convert blob to base64
      const reader = new FileReader();
      reader.readAsDataURL(audioBlob);
      
      reader.onloadend = async () => {
        const base64Audio = (reader.result as string).split(',')[1];

        const { data, error } = await supabase.functions.invoke('voice-to-text', {
          body: { audio: base64Audio },
        });

        if (error) throw error;

        onVoiceMessageSend(data.text);
        cleanup();
      };
    } catch (error) {
      console.error('Error transcribing audio:', error);
      toast({
        title: "Transcription Error",
        description: "Failed to transcribe your voice message. Please try again.",
        variant: "destructive",
      });
      setIsTranscribing(false);
    }
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="flex items-center gap-3 p-4 bg-muted/50 rounded-lg animate-fade-in">
      {/* Cancel Button */}
      <Button
        variant="ghost"
        size="icon"
        onClick={() => {
          cleanup();
          onCancel();
        }}
        className="text-destructive hover:bg-destructive/10"
      >
        <X className="w-5 h-5" />
      </Button>

      {/* Waveform Visualization */}
      <div className="flex-1 flex items-center gap-1 h-10">
        {audioLevels.map((level, i) => (
          <div
            key={i}
            className={cn(
              "flex-1 bg-primary rounded-full transition-all duration-100",
              isRecording ? "animate-pulse" : ""
            )}
            style={{
              height: `${Math.max(4, level * 40)}px`,
              opacity: isRecording ? 0.8 : 0.4,
            }}
          />
        ))}
      </div>

      {/* Timer */}
      <span className="text-sm text-muted-foreground font-mono min-w-[3rem]">
        {formatTime(recordingTime)}
      </span>

      {/* Controls */}
      {isRecording ? (
        <Button
          onClick={stopRecording}
          className="bg-primary hover:bg-primary/90"
        >
          <Mic className="w-4 h-4 mr-2" />
          Stop
        </Button>
      ) : (
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="icon"
            onClick={isPlaying ? pausePlayback : playRecording}
            disabled={!audioBlob}
          >
            {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
          </Button>
          <Button
            onClick={sendVoiceMessage}
            disabled={!audioBlob || isTranscribing}
            className="bg-primary hover:bg-primary/90"
          >
            {isTranscribing ? (
              "Transcribing..."
            ) : (
              <>
                <Send className="w-4 h-4 mr-2" />
                Send
              </>
            )}
          </Button>
        </div>
      )}
    </div>
  );
};
