import { Phone, Video, MoreVertical, Paperclip, Mic, Send } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Message } from "./Message";
import { useChatContext } from "@/contexts/ChatContext";
import { useMessages } from "@/hooks/useMessages";
import { useState, useEffect } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";
import { format } from "date-fns";
import { mockMessages, mockConversations } from "@/data/mockData";
import { MobileChatHeader } from "./MobileChatHeader";
import { VoiceRecorder } from "./VoiceRecorder";
import { cn } from "@/lib/utils";
import { useNavigate } from "react-router-dom";

type ChatAreaProps = {
  isMobile?: boolean;
  onBack?: () => void;
  onMenuClick?: () => void;
};

export const ChatArea = ({ isMobile = false, onBack, onMenuClick }: ChatAreaProps) => {
  const navigate = useNavigate();
  const { selectedConversationId, isGuest } = useChatContext();
  const { data: messages, isLoading } = useMessages(selectedConversationId);
  const [messageInput, setMessageInput] = useState("");
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [otherParticipant, setOtherParticipant] = useState<any>(null);
  const [guestMessages, setGuestMessages] = useState<Record<string, any[]>>({});
  const [isRecordingVoice, setIsRecordingVoice] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    if (isGuest) {
      setCurrentUserId("guest");
      return;
    }

    const getUser = async () => {
      const { data: { user } } = await supabase.auth.getUser();
      setCurrentUserId(user?.id || null);
    };
    getUser();
  }, [isGuest]);

  useEffect(() => {
    if (isGuest && selectedConversationId) {
      const conversation = mockConversations.find(c => c.id === selectedConversationId);
      if (conversation) {
        setOtherParticipant(conversation.participant);
      }
      return;
    }

    const fetchOtherParticipant = async () => {
      if (!selectedConversationId || !currentUserId) return;

      console.log("Fetching other participant for:", { selectedConversationId, currentUserId });

      // First, get the other participant's user_id
      const { data: participantData, error: participantError } = await supabase
        .from("conversation_participants")
        .select("user_id")
        .eq("conversation_id", selectedConversationId)
        .neq("user_id", currentUserId)
        .single();

      console.log("Participant query result:", { participantData, participantError });

      if (participantError || !participantData) {
        console.error("Error fetching participant:", participantError);
        return;
      }

      // Then, get the profile for that user_id
      const { data: profileData, error: profileError } = await supabase
        .from("profiles")
        .select("id, user_id, full_name, username, avatar_url, status")
        .eq("user_id", participantData.user_id)
        .single();

      console.log("Profile query result:", { profileData, profileError });

      if (profileError) {
        console.error("Error fetching profile:", profileError);
        return;
      }

      if (profileData) {
        console.log("Setting other participant:", profileData);
        setOtherParticipant(profileData);
      }
    };

    fetchOtherParticipant();
  }, [selectedConversationId, currentUserId, isGuest]);

  const handleSendMessage = async (content?: string) => {
    const messageContent = content || messageInput;
    if (!messageContent.trim() || !selectedConversationId || !currentUserId) return;

    if (isGuest) {
      const newMessage = {
        id: `guest-msg-${Date.now()}`,
        conversation_id: selectedConversationId,
        sender_id: "guest",
        content: messageContent,
        created_at: new Date().toISOString(),
        sender: {
          id: "guest",
          full_name: "Guest User",
          username: "guest",
          avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=guest",
        },
      };

      setGuestMessages(prev => ({
        ...prev,
        [selectedConversationId]: [...(prev[selectedConversationId] || []), newMessage],
      }));

      setMessageInput("");
      return;
    }

    const { error } = await (supabase as any)
      .from("messages")
      .insert({
        conversation_id: selectedConversationId,
        sender_id: currentUserId,
        content: messageContent,
      });

    if (error) {
      toast({
        title: "Error",
        description: "Failed to send message",
        variant: "destructive",
      });
      return;
    }

    setMessageInput("");
  };

  const handleVoiceMessageSend = (transcript: string) => {
    handleSendMessage(transcript);
    setIsRecordingVoice(false);
  };

  const handleCall = async (type: "audio" | "video") => {
    // Get the other participant's user_id for WebRTC call
    if (!selectedConversationId || !currentUserId) return;

    let calleeId = null;

    if (!isGuest) {
      const { data } = await supabase
        .from("conversation_participants")
        .select("user_id")
        .eq("conversation_id", selectedConversationId)
        .neq("user_id", currentUserId)
        .single();

      calleeId = data?.user_id;
    }

    const name = otherParticipant?.full_name || otherParticipant?.username || 'Unknown User';
    const avatar = otherParticipant?.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${otherParticipant?.username}`;

    if (calleeId) {
      navigate(`/call?type=${type}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(avatar)}&calleeId=${calleeId}`);
    } else {
      toast({
        title: "Cannot start call",
        description: isGuest ? "Calls are not available in guest mode" : "Could not find the other participant",
        variant: "destructive",
      });
    }
  };

  const handleFileAttachment = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !selectedConversationId || !currentUserId) return;

    if (isGuest) {
      toast({
        title: "Feature unavailable",
        description: "File uploads are not available in guest mode",
        variant: "destructive",
      });
      return;
    }

    setIsUploading(true);

    try {
      const fileExt = file.name.split('.').pop();
      const fileName = `${Math.random()}.${fileExt}`;
      // Use avatars/{user_id}/chat-attachments/ path to match storage RLS policy
      const filePath = `avatars/${currentUserId}/chat-attachments/${fileName}`;

      const { error: uploadError } = await supabase.storage
        .from('chat-attachments')
        .upload(filePath, file);

      if (uploadError) throw uploadError;

      const { data: { publicUrl } } = supabase.storage
        .from('chat-attachments')
        .getPublicUrl(filePath);

      await handleSendMessage(publicUrl);

      toast({
        title: "Success",
        description: "File uploaded successfully",
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to upload file",
        variant: "destructive",
      });
    } finally {
      setIsUploading(false);
      e.target.value = '';
    }
  };

  if (!selectedConversationId) {
    return (
      <main className="flex-1 flex items-center justify-center bg-background">
        <p className="text-muted-foreground">Select a conversation to start chatting</p>
      </main>
    );
  }

  return (
    <main className="flex-1 flex flex-col bg-background">
      {/* Mobile Chat Header */}
      {isMobile && onBack && onMenuClick && (
        <MobileChatHeader
          onBack={onBack}
          onMenuClick={onMenuClick}
          onCall={handleCall}
          otherParticipant={otherParticipant}
        />
      )}

      {/* Desktop Chat Header */}
      <header className={cn(
        "h-16 border-b border-border flex items-center justify-between px-6",
        isMobile && "hidden"
      )}>
        <div className="flex items-center gap-3">
          <Avatar className="w-10 h-10">
            <AvatarImage src={otherParticipant?.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${otherParticipant?.username}`} />
            <AvatarFallback>{otherParticipant?.full_name?.[0] || 'U'}</AvatarFallback>
          </Avatar>
          <div>
            <h3 className="font-semibold text-foreground">
              {otherParticipant?.full_name || otherParticipant?.username || 'Unknown User'}
            </h3>
            <p className="text-xs text-muted-foreground">
              {otherParticipant?.status || 'offline'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            className="text-foreground hover:bg-muted"
            onClick={() => handleCall("audio")}
          >
            <Phone className="w-5 h-5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="text-foreground hover:bg-muted"
            onClick={() => handleCall("video")}
          >
            <Video className="w-5 h-5" />
          </Button>
          <Button variant="ghost" size="icon" className="text-foreground hover:bg-muted">
            <MoreVertical className="w-5 h-5" />
          </Button>
        </div>
      </header>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {isGuest ? (
          selectedConversationId ? (
            (() => {
              const allMessages = [
                ...(mockMessages[selectedConversationId] || []),
                ...(guestMessages[selectedConversationId] || []),
              ].sort((a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime());

              return allMessages.length > 0 ? (
                allMessages.map((message: any) => (
                  <Message
                    key={message.id}
                    sender={message.sender.full_name || message.sender.username}
                    content={message.content}
                    timestamp={format(new Date(message.created_at), 'PPp')}
                    isSent={message.sender_id === currentUserId}
                    avatar={message.sender.avatar_url}
                  />
                ))
              ) : (
                <p className="text-center text-muted-foreground">No messages yet. Start the conversation!</p>
              );
            })()
          ) : (
            <p className="text-center text-muted-foreground">Select a conversation to start chatting</p>
          )
        ) : isLoading ? (
          <p className="text-center text-muted-foreground">Loading messages...</p>
        ) : messages && messages.length > 0 ? (
          messages.map((message: any) => (
            <Message
              key={message.id}
              sender={message.sender.full_name || message.sender.username}
              content={message.content}
              timestamp={format(new Date(message.created_at), 'PPp')}
              isSent={message.sender_id === currentUserId}
              avatar={message.sender.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${message.sender.username}`}
            />
          ))
        ) : (
          <p className="text-center text-muted-foreground">No messages yet. Start the conversation!</p>
        )}
      </div>

      {/* Message Input */}
      <div className="p-4 border-t border-border">
        {isRecordingVoice ? (
          <VoiceRecorder
            onVoiceMessageSend={handleVoiceMessageSend}
            onCancel={() => setIsRecordingVoice(false)}
          />
        ) : (
          <div className="flex items-center gap-3">
            <input
              type="file"
              id="file-upload"
              className="hidden"
              onChange={handleFileAttachment}
              accept="image/*,video/*,.pdf,.doc,.docx"
            />
            <Button
              variant="ghost"
              size="icon"
              className="text-muted-foreground hover:bg-muted"
              onClick={() => document.getElementById('file-upload')?.click()}
              disabled={isUploading}
            >
              <Paperclip className="w-5 h-5" />
            </Button>
            <Input
              placeholder="Type a message"
              value={messageInput}
              onChange={(e) => setMessageInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSendMessage();
                }
              }}
              className="flex-1 bg-muted border-0 text-foreground placeholder:text-muted-foreground"
            />
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setIsRecordingVoice(true)}
              className="text-muted-foreground hover:bg-muted"
            >
              <Mic className="w-5 h-5" />
            </Button>
            <Button
              size="icon"
              onClick={() => handleSendMessage()}
              className="bg-primary hover:bg-primary/90 text-primary-foreground"
            >
              <Send className="w-5 h-5" />
            </Button>
          </div>
        )}
      </div>
    </main>
  );
};
