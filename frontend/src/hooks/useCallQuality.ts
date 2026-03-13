import { useState, useEffect, useRef, useCallback } from 'react';

export interface CallQualityStats {
  quality: 'excellent' | 'good' | 'fair' | 'poor' | 'unknown';
  packetLoss: number;
  jitter: number;
  roundTripTime: number;
  bitrate: number;
  signalStrength: number; // 0-100 percentage
}

const getQualityFromMetrics = (
  packetLoss: number,
  jitter: number,
  rtt: number
): CallQualityStats['quality'] => {
  // Score calculation based on metrics
  let score = 100;
  
  // Packet loss impact (most important)
  if (packetLoss > 10) score -= 50;
  else if (packetLoss > 5) score -= 30;
  else if (packetLoss > 2) score -= 15;
  else if (packetLoss > 0.5) score -= 5;
  
  // Jitter impact
  if (jitter > 100) score -= 25;
  else if (jitter > 50) score -= 15;
  else if (jitter > 30) score -= 10;
  else if (jitter > 15) score -= 5;
  
  // Round trip time impact
  if (rtt > 500) score -= 25;
  else if (rtt > 300) score -= 15;
  else if (rtt > 150) score -= 10;
  else if (rtt > 100) score -= 5;
  
  if (score >= 85) return 'excellent';
  if (score >= 70) return 'good';
  if (score >= 50) return 'fair';
  return 'poor';
};

export const useCallQuality = (peerConnection: RTCPeerConnection | null) => {
  const [stats, setStats] = useState<CallQualityStats>({
    quality: 'unknown',
    packetLoss: 0,
    jitter: 0,
    roundTripTime: 0,
    bitrate: 0,
    signalStrength: 0,
  });
  
  const prevBytesReceivedRef = useRef<number>(0);
  const prevTimestampRef = useRef<number>(0);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  const calculateStats = useCallback(async () => {
    if (!peerConnection || peerConnection.connectionState !== 'connected') {
      return;
    }

    try {
      const statsReport = await peerConnection.getStats();
      let packetsLost = 0;
      let packetsReceived = 0;
      let jitter = 0;
      let roundTripTime = 0;
      let bytesReceived = 0;
      let timestamp = 0;

      statsReport.forEach((report) => {
        if (report.type === 'inbound-rtp' && report.kind === 'audio') {
          packetsLost = report.packetsLost || 0;
          packetsReceived = report.packetsReceived || 0;
          jitter = (report.jitter || 0) * 1000; // Convert to ms
          bytesReceived = report.bytesReceived || 0;
          timestamp = report.timestamp;
        }
        
        if (report.type === 'candidate-pair' && report.state === 'succeeded') {
          roundTripTime = report.currentRoundTripTime 
            ? report.currentRoundTripTime * 1000 
            : 0; // Convert to ms
        }
      });

      // Calculate packet loss percentage
      const totalPackets = packetsReceived + packetsLost;
      const packetLossPercent = totalPackets > 0 
        ? (packetsLost / totalPackets) * 100 
        : 0;

      // Calculate bitrate
      let bitrate = 0;
      if (prevTimestampRef.current > 0 && timestamp > prevTimestampRef.current) {
        const timeDiff = (timestamp - prevTimestampRef.current) / 1000; // seconds
        const bytesDiff = bytesReceived - prevBytesReceivedRef.current;
        bitrate = (bytesDiff * 8) / timeDiff / 1000; // kbps
      }
      prevBytesReceivedRef.current = bytesReceived;
      prevTimestampRef.current = timestamp;

      // Calculate signal strength (0-100)
      const quality = getQualityFromMetrics(packetLossPercent, jitter, roundTripTime);
      let signalStrength = 0;
      switch (quality) {
        case 'excellent': signalStrength = 100; break;
        case 'good': signalStrength = 75; break;
        case 'fair': signalStrength = 50; break;
        case 'poor': signalStrength = 25; break;
        default: signalStrength = 0;
      }

      setStats({
        quality,
        packetLoss: Math.round(packetLossPercent * 100) / 100,
        jitter: Math.round(jitter * 100) / 100,
        roundTripTime: Math.round(roundTripTime),
        bitrate: Math.round(bitrate),
        signalStrength,
      });
    } catch (error) {
      console.error('Error getting call stats:', error);
    }
  }, [peerConnection]);

  useEffect(() => {
    if (peerConnection) {
      // Update stats every 2 seconds
      intervalRef.current = setInterval(calculateStats, 2000);
      
      return () => {
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
        }
      };
    }
  }, [peerConnection, calculateStats]);

  return stats;
};