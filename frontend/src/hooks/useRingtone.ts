import { useRef, useCallback, useEffect } from 'react';
import { Haptics, ImpactStyle } from '@capacitor/haptics';

export const useRingtone = () => {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const vibrationIntervalRef = useRef<number | null>(null);

  // Create audio element on mount
  useEffect(() => {
    // Create a simple ringtone using Web Audio API oscillator
    // This creates a pleasant two-tone pattern
    return () => {
      stopRingtone();
    };
  }, []);

  const playRingtone = useCallback(async () => {
    try {
      // Create audio context for ringtone
      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      
      // Create a simple two-tone ringtone
      const playTone = (frequency: number, startTime: number, duration: number) => {
        const oscillator = audioContext.createOscillator();
        const gainNode = audioContext.createGain();
        
        oscillator.connect(gainNode);
        gainNode.connect(audioContext.destination);
        
        oscillator.type = 'sine';
        oscillator.frequency.value = frequency;
        
        gainNode.gain.setValueAtTime(0, startTime);
        gainNode.gain.linearRampToValueAtTime(0.3, startTime + 0.05);
        gainNode.gain.linearRampToValueAtTime(0, startTime + duration);
        
        oscillator.start(startTime);
        oscillator.stop(startTime + duration);
      };

      // Play ringtone pattern
      const playPattern = () => {
        const now = audioContext.currentTime;
        // First note (higher pitch)
        playTone(880, now, 0.15); // A5
        playTone(880, now + 0.2, 0.15);
        // Second note (lower pitch)
        playTone(659, now + 0.5, 0.15); // E5
        playTone(659, now + 0.7, 0.15);
      };

      // Play pattern immediately
      playPattern();

      // Store reference for cleanup
      audioRef.current = {
        pause: () => audioContext.close(),
        currentTime: 0,
      } as unknown as HTMLAudioElement;

      // Repeat every 2 seconds
      const repeatInterval = setInterval(() => {
        if (audioContext.state === 'running') {
          playPattern();
        }
      }, 2000);

      // Store the interval ID
      (audioRef.current as any)._repeatInterval = repeatInterval;

      // Start vibration pattern
      startVibration();
    } catch (error) {
      console.log('Audio playback not available:', error);
    }
  }, []);

  const startVibration = useCallback(async () => {
    // Vibration pattern: vibrate for 500ms, pause for 500ms
    const vibrate = async () => {
      try {
        await Haptics.impact({ style: ImpactStyle.Heavy });
        setTimeout(async () => {
          try {
            await Haptics.impact({ style: ImpactStyle.Heavy });
          } catch (e) {
            // Haptics not available
          }
        }, 200);
      } catch (error) {
        // Haptics not available on this platform
      }
    };

    // Vibrate immediately
    vibrate();

    // Set up interval for repeated vibration
    vibrationIntervalRef.current = window.setInterval(vibrate, 1500);
  }, []);

  const stopRingtone = useCallback(() => {
    // Stop audio
    if (audioRef.current) {
      try {
        audioRef.current.pause();
        // Clear repeat interval if exists
        if ((audioRef.current as any)._repeatInterval) {
          clearInterval((audioRef.current as any)._repeatInterval);
        }
      } catch (e) {
        // Ignore cleanup errors
      }
      audioRef.current = null;
    }

    // Stop vibration
    if (vibrationIntervalRef.current) {
      clearInterval(vibrationIntervalRef.current);
      vibrationIntervalRef.current = null;
    }
  }, []);

  return {
    playRingtone,
    stopRingtone,
  };
};