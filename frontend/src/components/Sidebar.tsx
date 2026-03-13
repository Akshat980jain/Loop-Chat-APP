import { Settings, Bell, Lock, Languages, Moon, LogOut, LogIn } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { Button } from "./ui/button";
import { Switch } from "./ui/switch";
import { useNavigate, useLocation, useSearchParams } from "react-router-dom";
import { useTheme } from "next-themes";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";
import { useProfile } from "@/hooks/useProfile";
import { AvatarSettingsDialog } from "./AvatarSettingsDialog";

type SidebarProps = {
  isGuest?: boolean;
};
export const Sidebar = ({
  isGuest = false
}: SidebarProps) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { theme, setTheme } = useTheme();
  const { toast } = useToast();
  const { data: profileData, isLoading: loading } = useProfile();
  const activeSection = location.pathname === "/settings" ? searchParams.get("section") : null;

  const profile = profileData?.profile;
  const settings = profileData?.settings;
  
  // Determine which avatar to display based on user preference
  const displayAvatarUrl = settings?.use_generated_avatar && profile?.generated_avatar_url
    ? profile.generated_avatar_url
    : profile?.avatar_url;
  const handleLogout = async () => {
    if (isGuest) {
      localStorage.removeItem("guestMode");
      toast({
        title: "Guest session ended",
        description: "Redirecting to login..."
      });
      navigate("/auth");
      return;
    }
    const {
      error
    } = await supabase.auth.signOut();
    if (error) {
      toast({
        title: "Error",
        description: "Failed to log out. Please try again.",
        variant: "destructive"
      });
    } else {
      toast({
        title: "Logged out",
        description: "You have been successfully logged out."
      });
      navigate("/auth");
    }
  };
  const handleLogin = () => {
    localStorage.removeItem("guestMode");
    navigate("/auth");
  };
  const getInitials = (name: string) => {
    return name.split(" ").map(n => n[0]).join("").toUpperCase().slice(0, 2);
  };
  return <aside className="w-64 bg-sidebar-background border-r border-border flex flex-col">
      {/* User Profile */}
      <div className="p-6 border-b border-border">
        <div className="relative w-24 h-24 mx-auto mb-4">
          <Avatar className="w-24 h-24">
            <AvatarImage src={isGuest ? "https://api.dicebear.com/7.x/avataaars/svg?seed=guest" : displayAvatarUrl || `https://api.dicebear.com/7.x/avataaars/svg?seed=${profile?.username}`} />
            <AvatarFallback>
              {loading ? "..." : isGuest ? "GU" : profile ? getInitials(profile.full_name) : "U"}
            </AvatarFallback>
          </Avatar>
        </div>
        <h2 className="text-center text-lg font-semibold text-foreground">
          {loading ? "Loading..." : isGuest ? "Guest User" : profile?.full_name || "User"}
        </h2>
        <p className="text-center text-sm text-muted-foreground">
          {isGuest ? "Try the app without signing up" : loading ? "" : profile?.status || "Online"}
        </p>
        
        {/* Avatar Settings for logged in users */}
        {!isGuest && !loading && profile && (
          <div className="flex justify-center mt-3">
            <AvatarSettingsDialog
              currentAvatarUrl={profile.avatar_url}
              generatedAvatarUrl={profile.generated_avatar_url}
              useGeneratedAvatar={settings?.use_generated_avatar || false}
              userName={profile.full_name}
            />
          </div>
        )}
        
        {isGuest && <Button onClick={handleLogin} className="w-full mt-4" size="sm">
            <LogIn className="w-4 h-4 mr-2" />
            Create Account
          </Button>}
      </div>

      {/* Menu Items */}
      <nav className="flex-1 px-4 py-4 space-y-1">
        <button onClick={() => navigate("/settings?section=general")} className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors ${activeSection === "general" ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"}`}>
          <Settings className="w-5 h-5" />
          <span className="text-sm">General Settings</span>
        </button>
        <button onClick={() => navigate("/settings?section=notifications")} className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors ${activeSection === "notifications" ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"}`}>
          <Bell className="w-5 h-5" />
          <span className="text-sm">Notifications</span>
        </button>
        <button onClick={() => navigate("/settings?section=privacy")} className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors ${activeSection === "privacy" ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"}`}>
          <Lock className="w-5 h-5" />
          <span className="text-sm">Privacy and Security</span>
        </button>
        <button onClick={() => navigate("/settings?section=language")} className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors ${activeSection === "language" ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"}`}>
          <Languages className="w-5 h-5" />
          <span className="text-sm">Language</span>
        </button>
        <div className="w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-foreground">
          <div className="flex items-center gap-3">
            <Moon className="w-5 h-5" />
            <span className="text-sm">Dark Mode</span>
          </div>
          <Switch checked={theme === "dark"} onCheckedChange={checked => setTheme(checked ? "dark" : "light")} />
        </div>
        <button onClick={handleLogout} className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-destructive/10 transition-colors text-destructive">
          <LogOut className="w-5 h-5" />
          <span className="text-sm">{isGuest ? "Exit Guest Mode" : "Log Out"}</span>
        </button>
      </nav>

      {/* Premium Card */}
      
    </aside>;
};