import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { CheckCheck } from "lucide-react";

interface MessageProps {
  sender: string;
  content: string;
  timestamp: string;
  isSent: boolean;
  avatar: string;
  isImage?: boolean;
  images?: string[];
}

export const Message = ({ sender, content, timestamp, isSent, avatar, isImage, images }: MessageProps) => {
  // Extract only the time from the timestamp (e.g., "1:23 PM")
  const timeOnly = (() => {
    try {
      const date = new Date(timestamp);
      if (!isNaN(date.getTime())) {
        return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
      }
      // If it's already a formatted string, try to extract time portion
      const timeMatch = timestamp.match(/\d{1,2}:\d{2}\s*[AP]M/i);
      return timeMatch ? timeMatch[0] : timestamp;
    } catch {
      return timestamp;
    }
  })();

  return (
    <div 
      className={`flex gap-3 ${isSent ? 'flex-row-reverse' : ''} animate-in fade-in-0 slide-in-from-bottom-3 duration-300`}
    >
      <Avatar className="w-10 h-10 flex-shrink-0">
        <AvatarImage src={avatar} />
        <AvatarFallback>{sender[0]}</AvatarFallback>
      </Avatar>
      
      <div className={`flex flex-col ${isSent ? 'items-end' : 'items-start'} max-w-[70%]`}>
        {isImage && images ? (
          <div className="relative">
            <div className="flex gap-2">
              {images.map((img, idx) => (
                <img
                  key={idx}
                  src={img}
                  alt={`Shared image ${idx + 1}`}
                  className="w-48 h-36 object-cover rounded-2xl animate-in zoom-in-95 duration-300"
                />
              ))}
            </div>
            <div className={`flex items-center gap-1 mt-1 ${isSent ? 'justify-end' : 'justify-start'}`}>
              <span className="text-xs text-muted-foreground">{timeOnly}</span>
              {isSent && <CheckCheck className="w-3 h-3 text-primary" />}
            </div>
          </div>
        ) : (
          <div className={`px-4 py-2 pb-1 rounded-2xl transition-transform ${
            isSent 
              ? 'bg-primary text-primary-foreground' 
              : 'bg-card text-card-foreground'
          }`}>
            <p className="text-sm">{content}</p>
            <div className={`flex items-center gap-1 mt-1 ${isSent ? 'justify-end' : 'justify-start'}`}>
              <span className={`text-[10px] ${isSent ? 'text-primary-foreground/70' : 'text-muted-foreground'}`}>
                {timeOnly}
              </span>
              {isSent && <CheckCheck className={`w-3 h-3 ${isSent ? 'text-primary-foreground/70' : 'text-primary'}`} />}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
