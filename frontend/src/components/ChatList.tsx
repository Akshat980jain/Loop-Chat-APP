import { Search, Plus, RefreshCw } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Badge } from "./ui/badge";
import { useConversations } from "@/hooks/useConversations";
import { useChatContext } from "@/contexts/ChatContext";
import { supabase } from "@/integrations/supabase/client";
import { useEffect, useState, useRef } from "react";
import { formatDistanceToNow } from "date-fns";
import { mockConversations } from "@/data/mockData";
import { UserStories } from "./UserStories";
import { useHaptics } from "@/hooks/useHaptics";
import { useQueryClient } from "@tanstack/react-query";
import { MobileChatListHeader } from "./MobileChatListHeader";
import { FloatingNewChatButton } from "./FloatingNewChatButton";
import { NewChatDialog } from "./NewChatDialog";
import { cn } from "@/lib/utils";

const recentAvatars = [
  { name: "User1", seed: "Felix" },
  { name: "User2", seed: "Aneka" },
  { name: "User3", seed: "Bob" },
  { name: "User4", seed: "Charlie" },
  { name: "User5", seed: "Dana" },
  { name: "User6", seed: "Emma" }
];

const chats = [
  {
    id: 1,
    name: "Jane Cooper",
    message: "Hello, don't forget to...",
    time: "12:30",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jane",
    unread: 2,
    online: true
  },
  {
    id: 2,
    name: "Jenny Wilson",
    message: "Hi there, nice to me...",
    time: "11:00",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jenny",
    active: true
  },
  {
    id: 3,
    name: "Bessie Cooper",
    message: "How are you, my frien...",
    time: "10:45",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Bessie"
  },
  {
    id: 4,
    name: "Guy Hawkins",
    message: "Where are you right no...",
    time: "09:30",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Guy"
  },
  {
    id: 5,
    name: "Ralph Edwards",
    message: "Hello, I'm looking for y...",
    time: "08:15",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Ralph"
  }
];

const groups = [
  {
    id: 1,
    name: "Sans Shoters",
    message: "Please support, guys,...",
    time: "Yesterday",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Sans",
    members: 5
  },
  {
    id: 2,
    name: "Dribbbble Indo",
    message: "May I share the latest s...",
    time: "2 days ago",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Dribbbble"
  },
  {
    id: 3,
    name: "UI Indonesia",
    message: "Your design looks real...",
    time: "3 days ago",
    avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=UI"
  }
];

type ChatListProps = {
  isMobile?: boolean;
  onMenuClick?: () => void;
  onChatSelect?: () => void;
};

export const ChatList = ({ isMobile = false, onMenuClick, onChatSelect }: ChatListProps) => {
  const [userId, setUserId] = useState<string | undefined>();
  const { data: conversations, isLoading, refetch } = useConversations(userId);
  const { selectedConversationId, setSelectedConversationId, isGuest } = useChatContext();
  const [isPulling, setIsPulling] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const [showFAB, setShowFAB] = useState(false);
  const [showNewChatDialog, setShowNewChatDialog] = useState(false);
  const touchStartY = useRef(0);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();
  const haptics = useHaptics();

  useEffect(() => {
    if (isGuest) return;
    
    const getUser = async () => {
      const { data: { user } } = await (supabase as any).auth.getUser();
      setUserId(user?.id);
    };
    getUser();
  }, [isGuest]);

  const handleTouchStart = (e: React.TouchEvent) => {
    if (scrollContainerRef.current && scrollContainerRef.current.scrollTop === 0) {
      touchStartY.current = e.touches[0].clientY;
    }
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (scrollContainerRef.current && scrollContainerRef.current.scrollTop === 0) {
      const touchY = e.touches[0].clientY;
      const distance = Math.max(0, touchY - touchStartY.current);
      
      if (distance > 0) {
        setPullDistance(Math.min(distance, 100));
        if (distance > 50 && !isPulling) {
          haptics.light();
        }
      }
    }
  };

  const handleTouchEnd = async () => {
    if (pullDistance > 70) {
      setIsPulling(true);
      haptics.medium();
      
      if (!isGuest) {
        await refetch();
      }
      
      setTimeout(() => {
        setIsPulling(false);
        setPullDistance(0);
      }, 1000);
    } else {
      setPullDistance(0);
    }
  };

  const handleNewChatClick = () => {
    haptics.light();
    if (isGuest) {
      return;
    }
    setShowNewChatDialog(true);
  };

  const handleChatCreated = (conversationId: string) => {
    refetch();
    setSelectedConversationId(conversationId);
    if (isMobile && onChatSelect) {
      onChatSelect();
    }
  };

  const handleConversationClick = (conversationId: string) => {
    haptics.light();
    setSelectedConversationId(conversationId);
    if (isMobile && onChatSelect) {
      onChatSelect();
    }
  };

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (!isMobile) return;
    const target = e.currentTarget;
    setShowFAB(target.scrollTop > 200);
  };

  return (
    <>
      <NewChatDialog
        open={showNewChatDialog}
        onOpenChange={setShowNewChatDialog}
        onChatCreated={handleChatCreated}
      />
      
      <aside className={cn(
        "bg-background flex flex-col h-full",
        isMobile ? "w-full" : "w-96 border-l border-border"
      )}>
        {/* Mobile Header */}
        {isMobile && onMenuClick && <MobileChatListHeader onMenuClick={onMenuClick} />}

      {/* Mobile Search Bar */}
      {isMobile && (
        <div className="p-4 border-b border-border">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <Input
              placeholder="Search"
              className="pl-10 bg-muted border-0 text-foreground placeholder:text-muted-foreground"
            />
          </div>
        </div>
      )}

      {/* Search and New Chat - Desktop Only */}
      <div className={cn(
        "p-4 space-y-3 border-b border-border",
        isMobile && "hidden"
      )}>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input
            placeholder="Search"
            className="pl-10 bg-muted border-0 text-foreground placeholder:text-muted-foreground"
          />
        </div>
        <Button 
          className="w-full bg-primary hover:bg-primary/90 text-primary-foreground"
          onClick={handleNewChatClick}
        >
          <Plus className="w-4 h-4 mr-2" />
          New Chats
        </Button>
      </div>

      {/* User Stories - Hide on mobile */}
      {!isMobile && <UserStories />}

      {/* Pull to Refresh Indicator */}
      {pullDistance > 0 && (
        <div 
          className="flex justify-center items-center py-2 transition-all"
          style={{ height: `${pullDistance}px` }}
        >
          <RefreshCw 
            className={`w-5 h-5 text-primary transition-transform ${
              isPulling ? 'animate-spin' : ''
            }`}
            style={{ 
              transform: `rotate(${pullDistance * 3.6}deg)`,
              opacity: Math.min(pullDistance / 70, 1)
            }}
          />
        </div>
      )}

      {/* Chats List */}
      <div 
        ref={scrollContainerRef}
        className="flex-1 overflow-y-auto overflow-x-hidden"
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onScroll={handleScroll}
      >
        {isMobile && (
          <FloatingNewChatButton
            visible={showFAB}
            onClick={handleNewChatClick}
          />
        )}
        <div className="p-4">
          <h3 className="text-sm font-semibold text-foreground mb-3">Chats</h3>
          {isGuest ? (
            <div className="space-y-1">
              {mockConversations.map((conversation: any) => (
                <button
                  key={conversation.id}
                  onClick={() => handleConversationClick(conversation.id)}
                  className={`w-full flex items-center gap-3 p-3 rounded-xl hover:bg-muted transition-colors ${
                    selectedConversationId === conversation.id ? 'bg-muted' : ''
                  }`}
                >
                  <div className="relative">
                    <Avatar className="w-12 h-12">
                      <AvatarImage src={conversation.participant?.avatar_url} />
                      <AvatarFallback>{conversation.participant?.full_name?.[0] || 'U'}</AvatarFallback>
                    </Avatar>
                  </div>
                  <div className="flex-1 text-left">
                    <div className="flex items-center justify-between mb-1">
                      <h4 className="text-sm font-medium text-foreground">
                        {conversation.participant?.full_name}
                      </h4>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {formatDistanceToNow(new Date(conversation.updated_at), { addSuffix: true })}
                    </p>
                  </div>
                </button>
              ))}
            </div>
          ) : isLoading ? (
            <p className="text-sm text-muted-foreground">Loading chats...</p>
          ) : conversations && conversations.length > 0 ? (
            <div className="space-y-1">
              {conversations.map((conversation: any) => (
                <button
                  key={conversation.id}
                  onClick={() => handleConversationClick(conversation.id)}
                  className={`w-full flex items-center gap-3 p-3 rounded-xl hover:bg-muted transition-colors ${
                    selectedConversationId === conversation.id ? 'bg-muted' : ''
                  }`}
                >
                  <div className="relative">
                    <Avatar className="w-12 h-12">
                      <AvatarImage src={conversation.participant?.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${conversation.participant?.username}`} />
                      <AvatarFallback>{conversation.participant?.full_name?.[0] || 'U'}</AvatarFallback>
                    </Avatar>
                  </div>
                  <div className="flex-1 text-left">
                    <div className="flex items-center justify-between mb-1">
                      <h4 className="text-sm font-medium text-foreground">
                        {conversation.participant?.full_name || conversation.participant?.username || 'Unknown User'}
                      </h4>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {formatDistanceToNow(new Date(conversation.updated_at), { addSuffix: true })}
                    </p>
                  </div>
                </button>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">No conversations yet. Start a new chat!</p>
          )}
        </div>
      </div>
      </aside>
    </>
  );
};
