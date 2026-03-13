import { Dialog, DialogContent, DialogHeader, DialogTitle } from "./ui/dialog";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { Button } from "./ui/button";
import { MessageCircle, Phone, Video, MoreVertical } from "lucide-react";
import { useHaptics } from "@/hooks/useHaptics";
import { useNavigate } from "react-router-dom";
import { useChatContext } from "@/contexts/ChatContext";
import { supabase } from "@/integrations/supabase/client";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useState } from "react";

interface ProfileViewProps {
  user: any;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const ProfileView = ({ user, open, onOpenChange }: ProfileViewProps) => {
  const haptics = useHaptics();
  const navigate = useNavigate();
  const { setSelectedConversationId } = useChatContext();
  const queryClient = useQueryClient();
  const [isCreating, setIsCreating] = useState(false);

  if (!user) return null;

  const handleCall = (type: "audio" | "video") => {
    haptics.medium();
    const name = user.full_name || user.username || 'Unknown User';
    const avatar = user.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${user.username}`;
    onOpenChange(false);
    navigate(`/call?type=${type}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(avatar)}`);
  };

  const handleMessage = async () => {
    haptics.medium();
    setIsCreating(true);
    
    try {
      const { data: { user: currentUser } } = await supabase.auth.getUser();
      if (!currentUser) {
        toast.error("Please sign in to send messages");
        return;
      }

      // Check if conversation already exists
      const { data: existingConversations } = await supabase
        .from("conversation_participants")
        .select("conversation_id")
        .eq("user_id", currentUser.id);

      if (existingConversations) {
        for (const conv of existingConversations) {
          const { data: participants } = await supabase
            .from("conversation_participants")
            .select("user_id")
            .eq("conversation_id", conv.conversation_id);

          if (participants?.some(p => p.user_id === user.id)) {
            setSelectedConversationId(conv.conversation_id);
            onOpenChange(false);
            setIsCreating(false);
            return;
          }
        }
      }

      // Create new conversation
      const { data: conversation, error: convError } = await supabase
        .from("conversations")
        .insert({})
        .select()
        .single();

      if (convError) throw convError;

      // Add both participants
      const { error: participantsError } = await supabase
        .from("conversation_participants")
        .insert([
          { conversation_id: conversation.id, user_id: currentUser.id },
          { conversation_id: conversation.id, user_id: user.id },
        ]);

      if (participantsError) throw participantsError;

      await queryClient.invalidateQueries({ queryKey: ["conversations"] });
      setSelectedConversationId(conversation.id);
      onOpenChange(false);
      toast.success("Chat opened");
    } catch (error: any) {
      console.error("Error creating conversation:", error);
      toast.error("Failed to open chat");
    } finally {
      setIsCreating(false);
    }
  };

  const handleAction = (action: string) => {
    haptics.medium();
    console.log(`Action: ${action} for user:`, user.username);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-center">Profile</DialogTitle>
        </DialogHeader>
        
        <div className="flex flex-col items-center gap-4 py-4">
          <div className="relative">
            <div className="p-1 rounded-full bg-gradient-to-tr from-primary via-accent to-primary">
              <Avatar className="w-32 h-32 border-4 border-background">
                <AvatarImage src={user.avatar_url} />
                <AvatarFallback className="text-3xl">{user.full_name[0]}</AvatarFallback>
              </Avatar>
            </div>
            {user.status === 'online' && (
              <div className="absolute bottom-2 right-2 w-6 h-6 bg-green-500 rounded-full border-4 border-background" />
            )}
          </div>

          <div className="text-center">
            <h3 className="text-xl font-semibold text-foreground">{user.full_name}</h3>
            <p className="text-sm text-muted-foreground">@{user.username}</p>
            <p className="text-xs text-muted-foreground mt-1 capitalize">
              {user.status}
            </p>
          </div>

          <div className="flex gap-3 w-full mt-2">
            <Button 
              variant="outline" 
              size="lg" 
              className="flex-1"
              onClick={handleMessage}
              disabled={isCreating}
            >
              <MessageCircle className="w-5 h-5 mr-2" />
              {isCreating ? "Opening..." : "Message"}
            </Button>
            <Button 
              variant="outline" 
              size="icon"
              onClick={() => handleCall('audio')}
            >
              <Phone className="w-5 h-5" />
            </Button>
            <Button 
              variant="outline" 
              size="icon"
              onClick={() => handleCall('video')}
            >
              <Video className="w-5 h-5" />
            </Button>
            <Button 
              variant="outline" 
              size="icon"
              onClick={() => handleAction('more')}
            >
              <MoreVertical className="w-5 h-5" />
            </Button>
          </div>

          <div className="w-full mt-4 p-4 rounded-lg bg-muted">
            <h4 className="text-sm font-semibold text-foreground mb-2">About</h4>
            <p className="text-sm text-muted-foreground">
              Hey there! I'm using Loop. Feel free to message me anytime!
            </p>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};
