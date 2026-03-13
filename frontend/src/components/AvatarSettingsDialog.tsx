import { useState } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useGenerateAvatar, useToggleAvatarPreference } from "@/hooks/useAvatarSettings";
import { useToast } from "@/hooks/use-toast";
import { Wand2, Loader2, Sparkles } from "lucide-react";

interface AvatarSettingsDialogProps {
  currentAvatarUrl: string | null;
  generatedAvatarUrl: string | null;
  useGeneratedAvatar: boolean;
  userName: string;
}

const avatarStyles = [
  { id: 'modern', name: 'Modern', description: 'Clean geometric design' },
  { id: 'cartoon', name: 'Cartoon', description: 'Cute friendly character' },
  { id: 'abstract', name: 'Abstract', description: 'Geometric shapes' },
  { id: 'nature', name: 'Nature', description: 'Landscape inspired' },
  { id: 'pixel', name: 'Pixel', description: 'Retro 8-bit style' },
] as const;

export const AvatarSettingsDialog = ({
  currentAvatarUrl,
  generatedAvatarUrl,
  useGeneratedAvatar,
  userName,
}: AvatarSettingsDialogProps) => {
  const [open, setOpen] = useState(false);
  const [selectedStyle, setSelectedStyle] = useState<typeof avatarStyles[number]['id']>('modern');
  const { toast } = useToast();
  const generateAvatar = useGenerateAvatar();
  const togglePreference = useToggleAvatarPreference();

  const displayedAvatarUrl = useGeneratedAvatar ? generatedAvatarUrl : currentAvatarUrl;

  const handleGenerate = async () => {
    try {
      await generateAvatar.mutateAsync({ style: selectedStyle });
      toast({
        title: "Avatar generated!",
        description: "Your new AI avatar has been created.",
      });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "Failed to generate avatar";
      toast({
        title: "Error",
        description: message,
        variant: "destructive",
      });
    }
  };

  const handleToggle = async (checked: boolean) => {
    try {
      await togglePreference.mutateAsync(checked);
      toast({
        title: checked ? "Using AI avatar" : "Using profile photo",
        description: checked 
          ? "Others will see your generated avatar" 
          : "Others will see your profile photo",
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to update preference",
        variant: "destructive",
      });
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2">
          <Sparkles className="w-4 h-4" />
          Avatar Settings
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Avatar Settings</DialogTitle>
        </DialogHeader>
        
        <div className="space-y-6 py-4">
          {/* Current avatars preview */}
          <div className="flex items-center justify-center gap-6">
            <div className="text-center">
              <Avatar className="w-20 h-20 mb-2">
                <AvatarImage src={currentAvatarUrl || undefined} />
                <AvatarFallback>{userName[0]}</AvatarFallback>
              </Avatar>
              <p className="text-xs text-muted-foreground">Profile Photo</p>
            </div>
            
            <div className="text-muted-foreground">or</div>
            
            <div className="text-center">
              <Avatar className="w-20 h-20 mb-2">
                <AvatarImage src={generatedAvatarUrl || undefined} />
                <AvatarFallback>
                  <Wand2 className="w-6 h-6" />
                </AvatarFallback>
              </Avatar>
              <p className="text-xs text-muted-foreground">AI Avatar</p>
            </div>
          </div>

          {/* Toggle preference */}
          <div className="flex items-center justify-between p-4 bg-muted/50 rounded-lg">
            <div>
              <Label htmlFor="avatar-toggle" className="font-medium">Use AI Avatar</Label>
              <p className="text-sm text-muted-foreground">Show generated avatar to others</p>
            </div>
            <Switch
              id="avatar-toggle"
              checked={useGeneratedAvatar}
              onCheckedChange={handleToggle}
              disabled={togglePreference.isPending || !generatedAvatarUrl}
            />
          </div>

          {/* Generate section */}
          <div className="space-y-3">
            <Label>Generate New AI Avatar</Label>
            <div className="grid grid-cols-2 gap-2">
              {avatarStyles.map((style) => (
                <button
                  key={style.id}
                  onClick={() => setSelectedStyle(style.id)}
                  className={`p-3 rounded-lg border text-left transition-colors ${
                    selectedStyle === style.id 
                      ? 'border-primary bg-primary/10' 
                      : 'border-border hover:border-primary/50'
                  }`}
                >
                  <p className="font-medium text-sm">{style.name}</p>
                  <p className="text-xs text-muted-foreground">{style.description}</p>
                </button>
              ))}
            </div>
            
            <Button 
              onClick={handleGenerate} 
              disabled={generateAvatar.isPending}
              className="w-full gap-2"
            >
              {generateAvatar.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Generating...
                </>
              ) : (
                <>
                  <Wand2 className="w-4 h-4" />
                  Generate Avatar
                </>
              )}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};
