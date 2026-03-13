import { useState } from "react";
import { Sidebar } from "./Sidebar";
import { ChatArea } from "./ChatArea";
import { ChatList } from "./ChatList";
import { ChatProvider } from "@/contexts/ChatContext";
import { MobileBottomNav } from "./MobileBottomNav";
import { MobileDrawer } from "./MobileDrawer";
import { CallsList } from "./CallsList";
import { ContactsList } from "./ContactsList";
import { useIsMobile } from "@/hooks/use-mobile";
import { useChatContext } from "@/contexts/ChatContext";
import { cn } from "@/lib/utils";

type ChatLayoutProps = {
  isGuest?: boolean;
};

type MobileView = "list" | "chat" | "updates" | "contacts" | "calls";

const ChatLayoutContent = ({ isGuest = false }: ChatLayoutProps) => {
  const isMobile = useIsMobile();
  const [mobileView, setMobileView] = useState<MobileView>("list");
  const [activeTab, setActiveTab] = useState<"chats" | "updates" | "contacts" | "calls">("chats");
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const { setSelectedConversationId } = useChatContext();

  const handleTabChange = (tab: "chats" | "updates" | "contacts" | "calls") => {
    setActiveTab(tab);
    if (tab === "updates") {
      setMobileView("updates");
    } else if (tab === "contacts") {
      setMobileView("contacts");
    } else if (tab === "calls") {
      setMobileView("calls");
    } else {
      setMobileView("list");
    }
  };

  const handleStartChatFromContacts = (conversationId: string) => {
    setSelectedConversationId(conversationId);
    setActiveTab("chats");
    setMobileView("chat");
  };

  return (
    <div className="flex h-screen w-full bg-background overflow-hidden overflow-x-hidden">
      {/* Desktop Layout */}
      <div className="hidden md:flex w-full">
        <Sidebar isGuest={isGuest} />
        <ChatArea />
        <ChatList />
      </div>

      {/* Mobile/Tablet Layout */}
      <div className="flex md:hidden flex-col w-full pb-16">
        <MobileDrawer
          isOpen={isDrawerOpen}
          onClose={() => setIsDrawerOpen(false)}
          isGuest={isGuest}
        />

        {/* Chat List View */}
        <div
          className={cn(
            "absolute inset-0 transition-transform duration-300 ease-out",
            mobileView === "list" ? "translate-x-0" : "-translate-x-full"
          )}
        >
          <ChatList
            isMobile={true}
            onMenuClick={() => setIsDrawerOpen(true)}
            onChatSelect={() => setMobileView("chat")}
          />
        </div>

        {/* Chat View */}
        <div
          className={cn(
            "absolute inset-0 transition-transform duration-300 ease-out",
            mobileView === "chat" ? "translate-x-0" : "translate-x-full"
          )}
        >
          <ChatArea
            isMobile={true}
            onBack={() => setMobileView("list")}
            onMenuClick={() => setIsDrawerOpen(true)}
          />
        </div>

        {/* Updates View */}
        <div
          className={cn(
            "absolute inset-0 bg-background transition-opacity duration-300 flex items-center justify-center",
            mobileView === "updates" ? "opacity-100" : "opacity-0 pointer-events-none"
          )}
        >
          <p className="text-muted-foreground">Updates coming soon</p>
        </div>

        {/* Contacts View */}
        <div
          className={cn(
            "absolute inset-0 bg-background transition-opacity duration-300",
            mobileView === "contacts" ? "opacity-100" : "opacity-0 pointer-events-none"
          )}
        >
          <ContactsList onStartChat={handleStartChatFromContacts} />
        </div>

        {/* Calls View */}
        <div
          className={cn(
            "absolute inset-0 bg-background transition-opacity duration-300",
            mobileView === "calls" ? "opacity-100" : "opacity-0 pointer-events-none"
          )}
        >
          <CallsList />
        </div>

        <MobileBottomNav activeTab={activeTab} onTabChange={handleTabChange} />
      </div>
    </div>
  );
};

export const ChatLayout = ({ isGuest = false }: ChatLayoutProps) => {
  return (
    <ChatProvider isGuest={isGuest}>
      <ChatLayoutContent isGuest={isGuest} />
    </ChatProvider>
  );
};
