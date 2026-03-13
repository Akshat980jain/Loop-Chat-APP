import { useState, useEffect } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "./ui/dialog";
import { Input } from "./ui/input";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { Search, Loader2, QrCode, Users, UserPlus } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";
import { Button } from "./ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs";
import { useContacts } from "@/hooks/useContacts";
import { QRCodeShare } from "./QRCodeShare";

type Profile = {
  id: string;
  user_id: string;
  full_name: string;
  username: string;
  avatar_url?: string;
  status?: string;
  phone?: string;
};

type NewChatDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onChatCreated: (conversationId: string) => void;
};

export const NewChatDialog = ({ open, onOpenChange, onChatCreated }: NewChatDialogProps) => {
  const [searchQuery, setSearchQuery] = useState("");
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [qrOpen, setQrOpen] = useState(false);
  const { toast } = useToast();
  const { contacts, addContact } = useContacts();

  useEffect(() => {
    if (open) {
      fetchProfiles();
    }
  }, [open]);

  const fetchProfiles = async () => {
    setLoading(true);
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      const { data, error } = await supabase
        .from("profiles")
        .select("*")
        .neq("user_id", user.id)
        .order("full_name");

      if (error) throw error;
      setProfiles(data || []);
    } catch (error) {
      console.error("Error fetching profiles:", error);
      toast({
        title: "Error",
        description: "Failed to load users",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const createConversation = async (otherUserId: string) => {
    setCreating(true);
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) throw new Error("Not authenticated");

      // Check if conversation already exists between these two users
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

          if (otherParticipants && otherParticipants.length === 1 && otherParticipants[0].user_id === otherUserId) {
            onChatCreated(conv.conversation_id);
            onOpenChange(false);
            return;
          }
        }
      }

      // Create new conversation using RPC function (handles both conversation and participants atomically)
      const { data: newConversationId, error: convError } = await supabase
        .rpc("create_conversation_with_participants", {
          other_user_id: otherUserId,
        });

      if (convError) throw convError;

      const conversation = { id: newConversationId };

      toast({
        title: "Success",
        description: "New conversation created",
      });

      onChatCreated(conversation.id);
      onOpenChange(false);
    } catch (error) {
      console.error("Error creating conversation:", error);
      toast({
        title: "Error",
        description: "Failed to create conversation",
        variant: "destructive",
      });
    } finally {
      setCreating(false);
    }
  };

  const filteredProfiles = profiles.filter(
    (profile) =>
      profile.full_name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      profile.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (profile.phone && profile.phone.includes(searchQuery))
  );

  const isContact = (userId: string) => {
    return contacts.some((c) => c.contact_user_id === userId);
  };

  const renderProfileList = (profileList: Profile[]) => (
    <div className="max-h-[350px] overflow-y-auto space-y-2">
      {loading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
        </div>
      ) : profileList.length === 0 ? (
        <p className="text-sm text-muted-foreground text-center py-8">
          No users found
        </p>
      ) : (
        profileList.map((profile) => (
          <div
            key={profile.id}
            className="flex items-center gap-3 w-full p-3 rounded-lg hover:bg-muted/50 transition-colors"
          >
            <div className="relative">
              <Avatar className="w-10 h-10">
                <AvatarImage
                  src={profile.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${profile.username}`}
                />
                <AvatarFallback>{profile.full_name[0]}</AvatarFallback>
              </Avatar>
              {profile.status === "online" && (
                <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 rounded-full border-2 border-background" />
              )}
            </div>
            <div className="flex-1 text-left min-w-0">
              <p className="text-sm font-medium truncate">{profile.full_name}</p>
              <p className="text-xs text-muted-foreground truncate">
                @{profile.username}
              </p>
            </div>
            <div className="flex gap-1">
              {!isContact(profile.user_id) && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => addContact(profile.user_id)}
                >
                  <UserPlus className="w-4 h-4" />
                </Button>
              )}
              <Button
                size="sm"
                onClick={() => createConversation(profile.user_id)}
                disabled={creating}
              >
                Chat
              </Button>
            </div>
          </div>
        ))
      )}
    </div>
  );

  const contactProfiles = profiles.filter((p) => isContact(p.user_id));

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-md" aria-describedby={undefined}>
          <DialogHeader>
            <DialogTitle className="flex items-center justify-between">
              New Chat
              <Button size="icon" variant="ghost" onClick={() => setQrOpen(true)}>
                <QrCode className="w-5 h-5" />
              </Button>
            </DialogTitle>
          </DialogHeader>

          <Tabs defaultValue="search" className="w-full">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="search" className="flex items-center gap-2">
                <Search className="w-4 h-4" />
                Search
              </TabsTrigger>
              <TabsTrigger value="contacts" className="flex items-center gap-2">
                <Users className="w-4 h-4" />
                Contacts
              </TabsTrigger>
            </TabsList>

            <TabsContent value="search" className="space-y-4 mt-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <Input
                  placeholder="Search by name, username, or phone..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10"
                />
              </div>
              {renderProfileList(filteredProfiles)}
            </TabsContent>

            <TabsContent value="contacts" className="mt-4">
              {contactProfiles.length === 0 ? (
                <div className="text-center py-8">
                  <Users className="w-12 h-12 text-muted-foreground mx-auto mb-3" />
                  <p className="text-sm text-muted-foreground mb-3">
                    No contacts yet. Add users from the search tab or share your QR code.
                  </p>
                  <Button variant="outline" onClick={() => setQrOpen(true)}>
                    <QrCode className="w-4 h-4 mr-2" />
                    Share QR Code
                  </Button>
                </div>
              ) : (
                renderProfileList(contactProfiles)
              )}
            </TabsContent>
          </Tabs>
        </DialogContent>
      </Dialog>

      <QRCodeShare open={qrOpen} onOpenChange={setQrOpen} />
    </>
  );
};
