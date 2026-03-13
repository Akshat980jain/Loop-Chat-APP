import { Phone, Video, PhoneMissed, PhoneOutgoing, PhoneIncoming, Clock } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { Button } from "./ui/button";
import { cn } from "@/lib/utils";
import { useNavigate } from "react-router-dom";
import { useCallHistory } from "@/hooks/useCallHistory";
import { format, formatDistanceToNow } from "date-fns";

const formatDuration = (seconds: number): string => {
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  if (mins < 60) {
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }
  const hours = Math.floor(mins / 60);
  const remainingMins = mins % 60;
  return `${hours}:${remainingMins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
};

const mockCalls = [
  {
    id: "1",
    name: "Jane Cooper",
    time: new Date(Date.now() - 3600000).toISOString(),
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jane",
    type: "video" as const,
    direction: "incoming" as const,
    status: "completed" as const,
    duration: 245,
  },
  {
    id: "2",
    name: "Jenny Wilson",
    time: new Date(Date.now() - 7200000).toISOString(),
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jenny",
    type: "audio" as const,
    direction: "outgoing" as const,
    status: "completed" as const,
    duration: 180,
  },
  {
    id: "3",
    name: "Bessie Cooper",
    time: new Date(Date.now() - 86400000).toISOString(),
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Bessie",
    type: "audio" as const,
    direction: "incoming" as const,
    status: "missed" as const,
    duration: 0,
  },
  {
    id: "4",
    name: "Guy Hawkins",
    time: new Date(Date.now() - 172800000).toISOString(),
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Guy",
    type: "video" as const,
    direction: "outgoing" as const,
    status: "rejected" as const,
    duration: 0,
  },
];

export const CallsList = () => {
  const navigate = useNavigate();
  const { callHistory, isLoading } = useCallHistory();

  const handleCall = (call: { type: string; name: string; avatar: string }) => {
    navigate(`/call?type=${call.type}&name=${encodeURIComponent(call.name)}&avatar=${encodeURIComponent(call.avatar)}`);
  };

  // Demo incoming call button
  const handleTestIncomingCall = () => {
    navigate(`/incoming-call?type=video&name=Sarah Connor&avatar=${encodeURIComponent('https://api.dicebear.com/7.x/avataaars/svg?seed=Sarah')}`);
  };

  // Use database history if available, fallback to mock data
  const calls = callHistory && callHistory.length > 0 
    ? callHistory.map(call => ({
        id: call.id,
        name: call.other_participant_name,
        time: call.created_at,
        avatar: call.other_participant_avatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${call.other_participant_name}`,
        type: call.call_type,
        direction: call.direction,
        status: call.status,
        duration: call.duration || 0,
      })) 
    : mockCalls;

  const getCallIcon = (call: typeof calls[0]) => {
    if (call.status === 'missed') {
      return <PhoneMissed className="w-3 h-3 text-destructive flex-shrink-0" />;
    }
    if (call.direction === 'incoming') {
      return <PhoneIncoming className="w-3 h-3 text-green-500 flex-shrink-0" />;
    }
    return <PhoneOutgoing className="w-3 h-3 text-primary flex-shrink-0" />;
  };

  const getStatusText = (call: typeof calls[0]) => {
    if (call.status === 'missed') return 'Missed';
    if (call.status === 'rejected') return 'Declined';
    if (call.status === 'ongoing') return 'Ongoing';
    return call.direction === 'incoming' ? 'Incoming' : 'Outgoing';
  };

  const getTimeDisplay = (time: string) => {
    const date = new Date(time);
    const now = new Date();
    const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24));
    
    if (diffDays === 0) {
      return `Today, ${format(date, 'h:mm a')}`;
    } else if (diffDays === 1) {
      return `Yesterday, ${format(date, 'h:mm a')}`;
    } else if (diffDays < 7) {
      return format(date, 'EEEE, h:mm a');
    }
    return format(date, 'MMM d, h:mm a');
  };

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Header */}
      <header className="h-16 border-b border-border flex items-center justify-between px-4 bg-sidebar-background">
        <h1 className="text-xl font-semibold text-foreground">Calls</h1>
        <Button 
          variant="outline" 
          size="sm" 
          onClick={handleTestIncomingCall}
          className="text-xs"
        >
          Test Call
        </Button>
      </header>

      {/* Calls List */}
      <div className="flex-1 overflow-y-auto p-2 sm:p-4">
        {isLoading ? (
          <div className="flex items-center justify-center h-32">
            <p className="text-muted-foreground">Loading call history...</p>
          </div>
        ) : calls.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-32 text-center">
            <Phone className="w-12 h-12 text-muted-foreground mb-2 opacity-50" />
            <p className="text-muted-foreground">No call history yet</p>
          </div>
        ) : (
          <div className="space-y-1 sm:space-y-2">
            {calls.map((call) => (
              <div
                key={call.id}
                className={cn(
                  "flex items-center gap-2 sm:gap-3 p-2 sm:p-3 rounded-xl hover:bg-muted transition-colors",
                  call.status === 'missed' && "bg-destructive/5"
                )}
              >
                <Avatar className="w-10 h-10 sm:w-12 sm:h-12 flex-shrink-0">
                  <AvatarImage src={call.avatar} />
                  <AvatarFallback>{call.name[0]}</AvatarFallback>
                </Avatar>
                <div className="flex-1 min-w-0">
                  <h4 className={cn(
                    "text-sm font-medium truncate",
                    call.status === 'missed' ? "text-destructive" : "text-foreground"
                  )}>
                    {call.name}
                  </h4>
                  <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    {getCallIcon(call)}
                    <span className={cn(
                      call.status === 'missed' && "text-destructive",
                      "truncate"
                    )}>
                      {getStatusText(call)}
                    </span>
                    {call.type === "video" && (
                      <Video className="w-3 h-3 flex-shrink-0 opacity-60" />
                    )}
                    {call.status === 'completed' && call.duration > 0 && (
                      <span className="flex items-center gap-0.5 text-muted-foreground/80">
                        <Clock className="w-3 h-3" />
                        {formatDuration(call.duration)}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-muted-foreground/70 mt-0.5 truncate">
                    {getTimeDisplay(call.time)}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  className={cn(
                    "flex-shrink-0",
                    call.status === 'missed' 
                      ? "text-destructive hover:bg-destructive/10"
                      : "text-primary hover:bg-primary/10"
                  )}
                  onClick={() => handleCall({ ...call })}
                >
                  {call.type === "video" ? (
                    <Video className="w-4 h-4 sm:w-5 sm:h-5" />
                  ) : (
                    <Phone className="w-4 h-4 sm:w-5 sm:h-5" />
                  )}
                </Button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
