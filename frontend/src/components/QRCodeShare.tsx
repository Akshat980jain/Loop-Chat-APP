import { useState, useEffect } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "./ui/dialog";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";
import { Copy, Share2, UserPlus } from "lucide-react";
import { useContacts } from "@/hooks/useContacts";

type QRCodeShareProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export const QRCodeShare = ({ open, onOpenChange }: QRCodeShareProps) => {
  const [userId, setUserId] = useState<string | null>(null);
  const [username, setUsername] = useState<string>("");
  const [scanInput, setScanInput] = useState("");
  const [activeTab, setActiveTab] = useState<"share" | "scan">("share");
  const { toast } = useToast();
  const { addContact } = useContacts();

  useEffect(() => {
    const fetchUserData = async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (user) {
        setUserId(user.id);
        const { data: profile } = await supabase
          .from("profiles")
          .select("username")
          .eq("user_id", user.id)
          .single();
        if (profile) {
          setUsername(profile.username);
        }
      }
    };
    if (open) {
      fetchUserData();
    }
  }, [open]);

  const qrData = userId ? `chatapp://connect/${userId}` : "";

  const copyLink = () => {
    navigator.clipboard.writeText(qrData);
    toast({
      title: "Copied!",
      description: "Your connect link has been copied",
    });
  };

  const shareLink = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: "Connect with me",
          text: `Connect with @${username} on ChatApp`,
          url: qrData,
        });
      } catch (error) {
        console.log("Share cancelled");
      }
    } else {
      copyLink();
    }
  };

  const handleAddFromCode = async () => {
    const trimmed = scanInput.trim();
    if (!trimmed) return;

    // Extract user ID from QR code URL or use as-is if UUID
    const match = trimmed.match(/chatapp:\/\/connect\/([a-f0-9-]+)/i);
    const contactUserId = match ? match[1] : trimmed;

    // Validate it's a UUID format
    const uuidRegex = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
    if (!uuidRegex.test(contactUserId)) {
      toast({
        title: "Invalid code",
        description: "Please enter a valid user ID or QR code",
        variant: "destructive",
      });
      return;
    }

    if (contactUserId === userId) {
      toast({
        title: "That's you!",
        description: "You can't add yourself as a contact",
        variant: "destructive",
      });
      return;
    }

    // Check if user exists
    const { data: profile } = await supabase
      .from("profiles")
      .select("full_name")
      .eq("user_id", contactUserId)
      .single();

    if (!profile) {
      toast({
        title: "User not found",
        description: "No user found with this ID",
        variant: "destructive",
      });
      return;
    }

    const success = await addContact(contactUserId);
    if (success) {
      setScanInput("");
      onOpenChange(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Share QR Code</DialogTitle>
        </DialogHeader>

        <div className="flex gap-2 mb-4">
          <Button
            variant={activeTab === "share" ? "default" : "outline"}
            size="sm"
            onClick={() => setActiveTab("share")}
            className="flex-1"
          >
            My QR Code
          </Button>
          <Button
            variant={activeTab === "scan" ? "default" : "outline"}
            size="sm"
            onClick={() => setActiveTab("scan")}
            className="flex-1"
          >
            Add by Code
          </Button>
        </div>

        {activeTab === "share" ? (
          <div className="flex flex-col items-center gap-4">
            <div className="bg-white p-4 rounded-xl">
              {qrData && (
                <QRCodeSVG
                  value={qrData}
                  size={200}
                  level="H"
                  includeMargin
                />
              )}
            </div>
            <p className="text-sm text-muted-foreground text-center">
              Let others scan this QR code to add you as a contact
            </p>
            <p className="text-sm font-medium">@{username}</p>
            <div className="flex gap-2 w-full">
              <Button variant="outline" className="flex-1" onClick={copyLink}>
                <Copy className="w-4 h-4 mr-2" />
                Copy Link
              </Button>
              <Button className="flex-1" onClick={shareLink}>
                <Share2 className="w-4 h-4 mr-2" />
                Share
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Enter the user ID or paste a QR code link to add a contact
            </p>
            <Input
              placeholder="Paste user ID or QR link..."
              value={scanInput}
              onChange={(e) => setScanInput(e.target.value)}
            />
            <Button className="w-full" onClick={handleAddFromCode} disabled={!scanInput.trim()}>
              <UserPlus className="w-4 h-4 mr-2" />
              Add Contact
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
};
