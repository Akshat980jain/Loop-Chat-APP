import { useState } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { useContacts } from "@/hooks/useContacts";
import { MessageCircle, Trash2, Search, UserPlus, QrCode, Loader2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { supabase } from "@/integrations/supabase/client";
import { QRCodeShare } from "./QRCodeShare";

type ContactsListProps = {
  onStartChat: (userId: string) => void;
};

export const ContactsList = ({ onStartChat }: ContactsListProps) => {
  const { contacts, loading, removeContact } = useContacts();
  const [searchQuery, setSearchQuery] = useState("");
  const [qrDialogOpen, setQrDialogOpen] = useState(false);
  const { toast } = useToast();

  const filteredContacts = contacts.filter((contact) => {
    const displayName = contact.nickname || contact.profile?.full_name || "";
    const username = contact.profile?.username || "";
    const query = searchQuery.toLowerCase();
    return displayName.toLowerCase().includes(query) || username.toLowerCase().includes(query);
  });

  const handleStartChat = async (contactUserId: string) => {
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      // Check if conversation exists
      const { data: existingConversations } = await supabase
        .from("conversation_participants")
        .select("conversation_id")
        .eq("user_id", user.id);

      if (existingConversations) {
        for (const conv of existingConversations) {
          const { data: otherParticipants } = await supabase
            .from("conversation_participants")
            .select("user_id")
            .eq("conversation_id", conv.conversation_id)
            .neq("user_id", user.id);

          if (otherParticipants?.length === 1 && otherParticipants[0].user_id === contactUserId) {
            onStartChat(conv.conversation_id);
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

      await supabase
        .from("conversation_participants")
        .insert([
          { conversation_id: conversation.id, user_id: user.id },
          { conversation_id: conversation.id, user_id: contactUserId },
        ]);

      onStartChat(conversation.id);
    } catch (error) {
      console.error("Error starting chat:", error);
      toast({
        title: "Error",
        description: "Failed to start conversation",
        variant: "destructive",
      });
    }
  };

  const handleRemoveContact = async (contactId: string, name: string) => {
    if (confirm(`Remove ${name} from contacts?`)) {
      await removeContact(contactId);
    }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="p-4 border-b border-border">
        <div className="flex items-center gap-2 mb-3">
          <h2 className="text-lg font-semibold flex-1">Contacts</h2>
          <Button size="icon" variant="ghost" onClick={() => setQrDialogOpen(true)}>
            <QrCode className="w-5 h-5" />
          </Button>
        </div>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input
            placeholder="Search contacts..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : filteredContacts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
            <UserPlus className="w-12 h-12 text-muted-foreground mb-4" />
            <p className="text-muted-foreground mb-2">
              {searchQuery ? "No contacts found" : "No contacts yet"}
            </p>
            <p className="text-sm text-muted-foreground mb-4">
              Share your QR code to let others add you
            </p>
            <Button onClick={() => setQrDialogOpen(true)}>
              <QrCode className="w-4 h-4 mr-2" />
              Share QR Code
            </Button>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {filteredContacts.map((contact) => {
              const displayName = contact.nickname || contact.profile?.full_name || "Unknown";
              const avatar = contact.profile?.avatar_url || 
                `https://api.dicebear.com/7.x/avataaars/svg?seed=${contact.profile?.username || contact.contact_user_id}`;

              return (
                <div
                  key={contact.id}
                  className="flex items-center gap-3 p-4 hover:bg-muted/50 transition-colors"
                >
                  <Avatar className="w-12 h-12">
                    <AvatarImage src={avatar} />
                    <AvatarFallback>{displayName[0]}</AvatarFallback>
                  </Avatar>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium truncate">{displayName}</p>
                    {contact.profile?.username && (
                      <p className="text-sm text-muted-foreground truncate">
                        @{contact.profile.username}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-1">
                    <Button
                      size="icon"
                      variant="ghost"
                      onClick={() => handleStartChat(contact.contact_user_id)}
                    >
                      <MessageCircle className="w-5 h-5" />
                    </Button>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="text-destructive hover:text-destructive"
                      onClick={() => handleRemoveContact(contact.id, displayName)}
                    >
                      <Trash2 className="w-5 h-5" />
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <QRCodeShare open={qrDialogOpen} onOpenChange={setQrDialogOpen} />
    </div>
  );
};
