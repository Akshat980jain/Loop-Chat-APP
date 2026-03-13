import { cn } from "@/lib/utils";
import { Wifi, WifiOff, Signal, SignalLow, SignalMedium, SignalHigh } from "lucide-react";
import { CallQualityStats } from "@/hooks/useCallQuality";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

interface CallQualityIndicatorProps {
  stats: CallQualityStats;
  showDetails?: boolean;
  className?: string;
}

export const CallQualityIndicator = ({
  stats,
  showDetails = false,
  className,
}: CallQualityIndicatorProps) => {
  const getQualityIcon = () => {
    switch (stats.quality) {
      case 'excellent':
        return <SignalHigh className="w-4 h-4" />;
      case 'good':
        return <SignalMedium className="w-4 h-4" />;
      case 'fair':
        return <SignalLow className="w-4 h-4" />;
      case 'poor':
        return <Signal className="w-4 h-4" />;
      default:
        return <WifiOff className="w-4 h-4" />;
    }
  };

  const getQualityColor = () => {
    switch (stats.quality) {
      case 'excellent':
        return 'text-green-500';
      case 'good':
        return 'text-green-400';
      case 'fair':
        return 'text-yellow-500';
      case 'poor':
        return 'text-red-500';
      default:
        return 'text-muted-foreground';
    }
  };

  const getQualityLabel = () => {
    switch (stats.quality) {
      case 'excellent':
        return 'Excellent';
      case 'good':
        return 'Good';
      case 'fair':
        return 'Fair';
      case 'poor':
        return 'Poor';
      default:
        return 'Connecting...';
    }
  };

  // Signal bars visualization
  const SignalBars = () => {
    const bars = 4;
    const activeBars = Math.ceil(stats.signalStrength / 25);
    
    return (
      <div className="flex items-end gap-0.5 h-4">
        {Array.from({ length: bars }).map((_, i) => (
          <div
            key={i}
            className={cn(
              "w-1 rounded-sm transition-all",
              i < activeBars ? getQualityColor().replace('text-', 'bg-') : 'bg-muted',
            )}
            style={{ height: `${(i + 1) * 25}%` }}
          />
        ))}
      </div>
    );
  };

  const content = (
    <div
      className={cn(
        "flex items-center gap-2 px-2 py-1 rounded-full bg-background/80 backdrop-blur-sm border border-border",
        className
      )}
    >
      <div className={cn("flex items-center gap-1.5", getQualityColor())}>
        <SignalBars />
        {showDetails && (
          <span className="text-xs font-medium">{getQualityLabel()}</span>
        )}
      </div>
    </div>
  );

  if (!showDetails) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>{content}</TooltipTrigger>
          <TooltipContent side="bottom" className="max-w-[200px]">
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Connection Quality</span>
                <span className={cn("text-sm font-semibold", getQualityColor())}>
                  {getQualityLabel()}
                </span>
              </div>
              <div className="text-xs space-y-0.5 text-muted-foreground">
                <div className="flex justify-between">
                  <span>Packet Loss:</span>
                  <span>{stats.packetLoss.toFixed(1)}%</span>
                </div>
                <div className="flex justify-between">
                  <span>Latency:</span>
                  <span>{stats.roundTripTime}ms</span>
                </div>
                <div className="flex justify-between">
                  <span>Jitter:</span>
                  <span>{stats.jitter.toFixed(1)}ms</span>
                </div>
                {stats.bitrate > 0 && (
                  <div className="flex justify-between">
                    <span>Bitrate:</span>
                    <span>{stats.bitrate} kbps</span>
                  </div>
                )}
              </div>
            </div>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  return content;
};