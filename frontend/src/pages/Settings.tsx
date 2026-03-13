import { useNavigate, useSearchParams } from "react-router-dom";
import { Settings as SettingsIcon, Bell, Lock, Languages, Moon, ArrowLeft, User, Smartphone, Monitor, Tablet, LogOut, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { useTheme } from "next-themes";
import { Separator } from "@/components/ui/separator";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useProfile, useUpdateSettings } from "@/hooks/useProfile";
import { Skeleton } from "@/components/ui/skeleton";
import { useSessions, useRevokeSession, useRevokeAllOtherSessions, maskIpAddress, type UserSession } from "@/hooks/useSessionManagement";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

const settingsSections = [
  { id: "general", icon: SettingsIcon, label: "General Settings" },
  { id: "notifications", icon: Bell, label: "Notifications" },
  { id: "privacy", icon: Lock, label: "Privacy and Security" },
  { id: "sessions", icon: Smartphone, label: "Active Sessions" },
  { id: "language", icon: Languages, label: "Language" }
];

export default function Settings() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeSection = searchParams.get("section") || "general";
  const { theme, setTheme } = useTheme();
  const { data: profileData, isLoading } = useProfile();
  const updateSettings = useUpdateSettings();

  const setActiveSection = (section: string) => {
    setSearchParams({ section });
  };

  return (
    <div className="flex h-screen bg-background">
      <aside className="w-72 border-r border-border p-6">
        <Button variant="ghost" className="mb-6 -ml-2 text-foreground hover:bg-muted" onClick={() => navigate("/")}>
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Chat
        </Button>

        <div onClick={() => navigate("/profile")} className="flex items-center gap-3 p-4 rounded-lg bg-muted/50 hover:bg-muted cursor-pointer transition-colors mb-6">
          {isLoading ? (
            <>
              <Skeleton className="w-12 h-12 rounded-full" />
              <div className="flex-1"><Skeleton className="h-4 w-24 mb-1" /><Skeleton className="h-3 w-20" /></div>
            </>
          ) : (
            <>
              <Avatar className="w-12 h-12 border-2 border-primary/20">
                <AvatarImage src={profileData?.profile?.avatar_url || undefined} />
                <AvatarFallback className="bg-primary/10">{profileData?.profile?.full_name?.[0] || "U"}</AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-foreground truncate">{profileData?.profile?.full_name || "User"}</p>
                <p className="text-sm text-muted-foreground truncate">@{profileData?.profile?.username || "username"}</p>
              </div>
              <User className="w-5 h-5 text-muted-foreground" />
            </>
          )}
        </div>

        <h2 className="text-xl font-semibold text-foreground mb-6">Settings</h2>
        <nav className="space-y-1">
          {settingsSections.map((section) => {
            const Icon = section.icon;
            return (
              <button key={section.id} onClick={() => setActiveSection(section.id)} className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${activeSection === section.id ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"}`}>
                <Icon className="w-5 h-5" />
                <span className="text-sm font-medium">{section.label}</span>
              </button>
            );
          })}
        </nav>

        <Separator className="my-6" />
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Moon className="w-5 h-5 text-foreground" />
            <Label htmlFor="dark-mode" className="text-sm font-medium text-foreground cursor-pointer">Dark Mode</Label>
          </div>
          <Switch id="dark-mode" checked={theme === "dark"} onCheckedChange={(checked) => setTheme(checked ? "dark" : "light")} />
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto p-8">
        {activeSection === "general" && <GeneralSettings settings={profileData?.settings} updateSettings={updateSettings} />}
        {activeSection === "notifications" && <NotificationSettings settings={profileData?.settings} updateSettings={updateSettings} />}
        {activeSection === "privacy" && <PrivacySettings settings={profileData?.settings} updateSettings={updateSettings} />}
        {activeSection === "sessions" && <SessionsSettings />}
        {activeSection === "language" && <LanguageSettings settings={profileData?.settings} updateSettings={updateSettings} />}
      </main>
    </div>
  );
}

function GeneralSettings({ settings, updateSettings }: { settings: any; updateSettings: any }) {
  const navigate = useNavigate();
  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-foreground mb-2">General Settings</h1>
      <p className="text-muted-foreground mb-8">Manage your account settings and preferences</p>
      <div className="space-y-6">
        <div>
          <h3 className="text-lg font-semibold text-foreground mb-4">Profile Settings</h3>
          <Button onClick={() => navigate("/profile")} className="w-full">Edit Profile</Button>
        </div>
        <Separator />
        <div>
          <h3 className="text-lg font-semibold text-foreground mb-4">Chat Preferences</h3>
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div><Label className="text-foreground">Show online status</Label><p className="text-sm text-muted-foreground">Let others see when you're online</p></div>
              <Switch checked={settings?.show_online_status ?? true} onCheckedChange={(checked) => updateSettings.mutate({ show_online_status: checked })} />
            </div>
            <div className="flex items-center justify-between">
              <div><Label className="text-foreground">Read receipts</Label><p className="text-sm text-muted-foreground">Send read receipts to message senders</p></div>
              <Switch checked={settings?.show_read_receipts ?? true} onCheckedChange={(checked) => updateSettings.mutate({ show_read_receipts: checked })} />
            </div>
            <div className="flex items-center justify-between">
              <div><Label className="text-foreground">Typing indicators</Label><p className="text-sm text-muted-foreground">Show when you're typing</p></div>
              <Switch checked={settings?.show_typing_indicator ?? true} onCheckedChange={(checked) => updateSettings.mutate({ show_typing_indicator: checked })} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function NotificationSettings({ settings, updateSettings }: { settings: any; updateSettings: any }) {
  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-foreground mb-2">Notifications</h1>
      <p className="text-muted-foreground mb-8">Choose what notifications you receive</p>
      <div className="space-y-6">
        <div className="space-y-4">
          <div className="flex items-center justify-between"><div><Label className="text-foreground">Direct messages</Label><p className="text-sm text-muted-foreground">Get notified for new direct messages</p></div><Switch checked={settings?.direct_message_notifications ?? true} onCheckedChange={(checked) => updateSettings.mutate({ direct_message_notifications: checked })} /></div>
          <div className="flex items-center justify-between"><div><Label className="text-foreground">Group messages</Label><p className="text-sm text-muted-foreground">Get notified for group messages</p></div><Switch checked={settings?.group_message_notifications ?? true} onCheckedChange={(checked) => updateSettings.mutate({ group_message_notifications: checked })} /></div>
          <div className="flex items-center justify-between"><div><Label className="text-foreground">Mentions</Label><p className="text-sm text-muted-foreground">Get notified when someone mentions you</p></div><Switch checked={settings?.mention_notifications ?? true} onCheckedChange={(checked) => updateSettings.mutate({ mention_notifications: checked })} /></div>
        </div>
        <Separator />
        <div className="space-y-4">
          <div className="flex items-center justify-between"><div><Label className="text-foreground">Message sounds</Label><p className="text-sm text-muted-foreground">Play sound for incoming messages</p></div><Switch checked={settings?.message_sounds ?? true} onCheckedChange={(checked) => updateSettings.mutate({ message_sounds: checked })} /></div>
          <div className="flex items-center justify-between"><div><Label className="text-foreground">Do Not Disturb</Label><p className="text-sm text-muted-foreground">Silence all notifications</p></div><Switch checked={settings?.do_not_disturb ?? false} onCheckedChange={(checked) => updateSettings.mutate({ do_not_disturb: checked })} /></div>
        </div>
      </div>
    </div>
  );
}

function PrivacySettings({ settings, updateSettings }: { settings: any; updateSettings: any }) {
  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-foreground mb-2">Privacy and Security</h1>
      <p className="text-muted-foreground mb-8">Control who can see your information</p>
      <div className="space-y-6">
        <div className="space-y-4">
          <div><Label className="text-foreground mb-2 block">Who can see my profile photo</Label><select value={settings?.profile_photo_visibility || "everyone"} onChange={(e) => updateSettings.mutate({ profile_photo_visibility: e.target.value })} className="w-full px-4 py-2 rounded-lg bg-muted border border-border text-foreground"><option value="everyone">Everyone</option><option value="contacts">My contacts</option><option value="nobody">Nobody</option></select></div>
          <div><Label className="text-foreground mb-2 block">Who can see my status</Label><select value={settings?.status_visibility || "everyone"} onChange={(e) => updateSettings.mutate({ status_visibility: e.target.value })} className="w-full px-4 py-2 rounded-lg bg-muted border border-border text-foreground"><option value="everyone">Everyone</option><option value="contacts">My contacts</option><option value="nobody">Nobody</option></select></div>
        </div>
        <Separator />
        <div className="space-y-4">
          <div className="flex items-center justify-between"><div><Label className="text-foreground">Two-factor authentication</Label><p className="text-sm text-muted-foreground">Add an extra layer of security</p></div><Switch checked={settings?.two_factor_enabled ?? false} onCheckedChange={(checked) => updateSettings.mutate({ two_factor_enabled: checked })} /></div>
          <div className="flex items-center justify-between"><div><Label className="text-foreground">End-to-end encryption</Label><p className="text-sm text-muted-foreground">Encrypt all your messages</p></div><Switch checked={settings?.end_to_end_encryption ?? true} onCheckedChange={(checked) => updateSettings.mutate({ end_to_end_encryption: checked })} /></div>
        </div>
      </div>
    </div>
  );
}

function SessionsSettings() {
  const { data: sessions, isLoading } = useSessions();
  const revokeSession = useRevokeSession();
  const revokeAllOthers = useRevokeAllOtherSessions();

  const currentSession = sessions?.find(s => s.is_current);
  const otherSessions = sessions?.filter(s => !s.is_current) || [];

  const getDeviceIcon = (deviceType: string) => {
    switch (deviceType) {
      case "mobile": return <Smartphone className="w-5 h-5" />;
      case "tablet": return <Tablet className="w-5 h-5" />;
      default: return <Monitor className="w-5 h-5" />;
    }
  };

  const formatLastActive = (dateStr: string) => {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return "Just now";
    if (diffMins < 60) return `${diffMins} min ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  const SessionCard = ({ session, isCurrent }: { session: UserSession; isCurrent: boolean }) => {
    const deviceInfo = session.device_info as any;
    return (
      <div className={`p-4 rounded-lg border ${
        isCurrent ? "border-green-500/30 bg-green-500/5" : "border-border bg-muted/30"
      }`}>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <div className={`p-2 rounded-lg ${
              isCurrent ? "bg-green-500/10 text-green-600 dark:text-green-400" : "bg-muted text-muted-foreground"
            }`}>
              {getDeviceIcon(deviceInfo?.device_type || "desktop")}
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <p className="font-medium text-foreground truncate">
                  {deviceInfo?.browser || "Unknown Browser"} on {deviceInfo?.os || "Unknown OS"}
                </p>
                {isCurrent && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-500/10 text-green-600 dark:text-green-400 whitespace-nowrap">
                    This device
                  </span>
                )}
              </div>
              <p className="text-sm text-muted-foreground">
                IP: {maskIpAddress(session.ip_address)} · {formatLastActive(session.last_active)}
              </p>
              {deviceInfo?.screen_size && (
                <p className="text-xs text-muted-foreground mt-0.5">
                  Screen: {deviceInfo.screen_size}
                </p>
              )}
            </div>
          </div>
          {!isCurrent && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-destructive hover:text-destructive hover:bg-destructive/10 shrink-0"
                  disabled={revokeSession.isPending}
                >
                  <LogOut className="w-4 h-4" />
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Sign out this device?</AlertDialogTitle>
                  <AlertDialogDescription>
                    This will sign out {deviceInfo?.browser || "the browser"} on {deviceInfo?.os || "the device"}. They'll need to sign in again to use Loop.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    onClick={() => revokeSession.mutate(session.id)}
                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  >
                    Sign Out Device
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-foreground mb-2">Active Sessions</h1>
      <p className="text-muted-foreground mb-8">Manage your active login sessions across devices</p>

      {isLoading ? (
        <div className="space-y-4">
          <Skeleton className="h-20 w-full rounded-lg" />
          <Skeleton className="h-20 w-full rounded-lg" />
        </div>
      ) : (
        <div className="space-y-6">
          {/* Current Session */}
          {currentSession && (
            <div>
              <h3 className="text-lg font-semibold text-foreground mb-3">Current Session</h3>
              <SessionCard session={currentSession} isCurrent={true} />
            </div>
          )}

          <Separator />

          {/* Other Sessions */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-lg font-semibold text-foreground">
                Other Sessions {otherSessions.length > 0 && `(${otherSessions.length})`}
              </h3>
              {otherSessions.length > 1 && (
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-destructive border-destructive/30 hover:bg-destructive/10"
                      disabled={revokeAllOthers.isPending}
                    >
                      <AlertTriangle className="w-4 h-4 mr-1" />
                      Sign out all
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Sign out all other devices?</AlertDialogTitle>
                      <AlertDialogDescription>
                        This will sign out {otherSessions.length} other {otherSessions.length === 1 ? "session" : "sessions"}. Only your current device will remain signed in.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Cancel</AlertDialogCancel>
                      <AlertDialogAction
                        onClick={() => revokeAllOthers.mutate()}
                        className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                      >
                        Sign Out All Others
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              )}
            </div>

            {otherSessions.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                <Monitor className="w-10 h-10 mx-auto mb-2 opacity-50" />
                <p>No other active sessions</p>
                <p className="text-sm">You're only signed in on this device</p>
              </div>
            ) : (
              <div className="space-y-3">
                {otherSessions.map(session => (
                  <SessionCard key={session.id} session={session} isCurrent={false} />
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function LanguageSettings({ settings, updateSettings }: { settings: any; updateSettings: any }) {
  const languages = [{ code: "en", name: "English" }, { code: "es", name: "Spanish" }, { code: "fr", name: "French" }, { code: "de", name: "German" }, { code: "ja", name: "Japanese" }, { code: "zh", name: "Chinese" }];
  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-foreground mb-2">Language</h1>
      <p className="text-muted-foreground mb-8">Select your preferred language</p>
      <div className="space-y-2">
        {languages.map((lang) => (
          <label key={lang.code} className="flex items-center gap-3 p-3 rounded-lg hover:bg-muted cursor-pointer transition-colors">
            <input type="radio" name="language" value={lang.code} checked={settings?.language === lang.code} onChange={() => updateSettings.mutate({ language: lang.code })} className="w-4 h-4 text-primary" />
            <p className="text-foreground font-medium">{lang.name}</p>
          </label>
        ))}
      </div>
    </div>
  );
}