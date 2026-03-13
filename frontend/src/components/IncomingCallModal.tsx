import { Phone, PhoneOff, Video } from "lucide-react";
import { Avatar, AvatarImage, AvatarFallback } from "./ui/avatar";
import { Button } from "./ui/button";
import { Dialog, DialogContent } from "./ui/dialog";

interface IncomingCallModalProps {
  isOpen: boolean;
  callerName: string;
  callerAvatar?: string;
  callType: "audio" | "video";
  onAccept: () => void;
  onReject: () => void;
}

export const IncomingCallModal = ({
  isOpen,
  callerName,
  callerAvatar,
  callType,
  onAccept,
  onReject,
}: IncomingCallModalProps) => {
  return (
    <Dialog open={isOpen}>
      <DialogContent className="sm:max-w-md border-2 border-primary/20 bg-card/95 backdrop-blur-sm">
        <div className="flex flex-col items-center gap-6 py-6">
          {/* Avatar with pulse animation */}
          <div className="relative">
            <div className="absolute inset-0 animate-ping rounded-full bg-primary/20" />
            <Avatar className="w-24 h-24 border-4 border-primary/30 relative">
              {callerAvatar && <AvatarImage src={callerAvatar} alt={callerName} />}
              <AvatarFallback className="text-3xl bg-primary/20 text-primary">
                {callerName.split(' ').map(n => n[0]).join('').toUpperCase()}
              </AvatarFallback>
            </Avatar>
          </div>

          {/* Caller info */}
          <div className="text-center">
            <h3 className="text-2xl font-semibold text-foreground mb-1">
              {callerName}
            </h3>
            <p className="text-muted-foreground flex items-center justify-center gap-2">
              {callType === "video" ? (
                <>
                  <Video className="w-4 h-4" />
                  Incoming Video Call
                </>
              ) : (
                <>
                  <Phone className="w-4 h-4" />
                  Incoming Voice Call
                </>
              )}
            </p>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-6 mt-4">
            <Button
              size="lg"
              variant="destructive"
              className="rounded-full w-16 h-16"
              onClick={onReject}
            >
              <PhoneOff className="w-7 h-7" />
            </Button>

            <Button
              size="lg"
              className="rounded-full w-16 h-16 bg-green-600 hover:bg-green-700"
              onClick={onAccept}
            >
              <Phone className="w-7 h-7" />
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};
