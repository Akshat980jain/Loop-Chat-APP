import { useRef, useCallback, useEffect } from 'react';

// Simple ringback tone for the CALLER - just a "tring tring" sound, no vibration
export const useRingbackTone = () => {
  const audioContextRef = useRef<AudioContext | null>(null);
  const repeatIntervalRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      stopRingback();
    };
  }, []);

  const playRingback = useCallback(() => {
    try {
      // Create audio context for ringback tone
      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      audioContextRef.current = audioContext;
      
      // Standard North American ringback: 440Hz + 480Hz for 2 seconds, 4 seconds off
      const playRingbackTone = () => {
        if (!audioContextRef.current || audioContextRef.current.state !== 'running') return;
        
        const now = audioContextRef.current.currentTime;
        
        // Create two oscillators for dual-tone (440Hz + 480Hz)
        const createTone = (frequency: number) => {
          const oscillator = audioContextRef.current!.createOscillator();
          const gainNode = audioContextRef.current!.createGain();
          
          oscillator.connect(gainNode);
          gainNode.connect(audioContextRef.current!.destination);
          
          oscillator.type = 'sine';
          oscillator.frequency.value = frequency;
          
          // Fade in and out for a smooth sound
          gainNode.gain.setValueAtTime(0, now);
          gainNode.gain.linearRampToValueAtTime(0.15, now + 0.05);
          gainNode.gain.setValueAtTime(0.15, now + 1.95);
          gainNode.gain.linearRampToValueAtTime(0, now + 2);
          
          oscillator.start(now);
          oscillator.stop(now + 2);
        };
        
        createTone(440); // 440Hz
        createTone(480); // 480Hz
      };

      // Play pattern immediately
      playRingbackTone();

      // Repeat every 6 seconds (2 seconds on, 4 seconds off)
      repeatIntervalRef.current = window.setInterval(() => {
        if (audioContextRef.current?.state === 'running') {
          playRingbackTone();
        }
      }, 6000);

    } catch (error) {
      console.log('Audio playback not available:', error);
    }
  }, []);

  const stopRingback = useCallback(() => {
    // Clear repeat interval
    if (repeatIntervalRef.current) {
      clearInterval(repeatIntervalRef.current);
      repeatIntervalRef.current = null;
    }

    // Close audio context
    if (audioContextRef.current) {
      try {
        audioContextRef.current.close();
      } catch (e) {
        // Ignore cleanup errors
      }
      audioContextRef.current = null;
    }
  }, []);

  return {
    playRingback,
    stopRingback,
  };
};
