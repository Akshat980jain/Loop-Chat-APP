import { QrCode, Camera, MoreVertical } from "lucide-react";
import { Button } from "./ui/button";
type MobileChatListHeaderProps = {
  onMenuClick: () => void;
};
export const MobileChatListHeader = ({
  onMenuClick
}: MobileChatListHeaderProps) => {
  return <header className="h-16 border-b border-border flex items-center justify-between px-4 bg-sidebar-background md:hidden">
      <h1 className="text-xl font-semibold text-foreground">Loop</h1>
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" className="text-foreground hover:bg-muted">
          <QrCode className="w-5 h-5" />
        </Button>
        <Button variant="ghost" size="icon" className="text-foreground hover:bg-muted">
          <Camera className="w-5 h-5" />
        </Button>
        <Button variant="ghost" size="icon" onClick={onMenuClick} className="text-foreground hover:bg-muted">
          <MoreVertical className="w-5 h-5" />
        </Button>
      </div>
    </header>;
};