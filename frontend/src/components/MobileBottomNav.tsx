import { MessageSquare, Phone, Users, Radio } from "lucide-react";
import { cn } from "@/lib/utils";

type MobileBottomNavProps = {
  activeTab: "chats" | "updates" | "contacts" | "calls";
  onTabChange: (tab: "chats" | "updates" | "contacts" | "calls") => void;
};

export const MobileBottomNav = ({ activeTab, onTabChange }: MobileBottomNavProps) => {
  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-sidebar-background border-t border-border h-16 flex items-center justify-around px-4 md:hidden z-50">
      <button
        onClick={() => onTabChange("chats")}
        className={cn(
          "flex flex-col items-center justify-center gap-1 flex-1 py-2 rounded-lg transition-all duration-200",
          activeTab === "chats"
            ? "text-primary"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        <MessageSquare className={cn("w-6 h-6", activeTab === "chats" && "scale-110")} />
        <span className="text-xs font-medium">Chats</span>
      </button>
      
      <button
        onClick={() => onTabChange("updates")}
        className={cn(
          "flex flex-col items-center justify-center gap-1 flex-1 py-2 rounded-lg transition-all duration-200",
          activeTab === "updates"
            ? "text-primary"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        <Radio className={cn("w-6 h-6", activeTab === "updates" && "scale-110")} />
        <span className="text-xs font-medium">Updates</span>
      </button>
      
      <button
        onClick={() => onTabChange("contacts")}
        className={cn(
          "flex flex-col items-center justify-center gap-1 flex-1 py-2 rounded-lg transition-all duration-200",
          activeTab === "contacts"
            ? "text-primary"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        <Users className={cn("w-6 h-6", activeTab === "contacts" && "scale-110")} />
        <span className="text-xs font-medium">Contacts</span>
      </button>
      
      <button
        onClick={() => onTabChange("calls")}
        className={cn(
          "flex flex-col items-center justify-center gap-1 flex-1 py-2 rounded-lg transition-all duration-200",
          activeTab === "calls"
            ? "text-primary"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        <Phone className={cn("w-6 h-6", activeTab === "calls" && "scale-110")} />
        <span className="text-xs font-medium">Calls</span>
      </button>
    </nav>
  );
};
