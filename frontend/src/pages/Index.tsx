import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { ChatLayout } from "@/components/ChatLayout";
import { Session } from "@supabase/supabase-js";
import { checkSessionRevoked } from "@/hooks/useSessionManagement";
import { toast } from "sonner";

const Index = () => {
  const [session, setSession] = useState<Session | null>(null);
  const [isGuest, setIsGuest] = useState(false);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    console.log("[Index] useEffect triggered. isGuest:", isGuest);

    // Self-healing: Unregister all service workers on localhost to resolve SSL caching freezes
    if (window.location.hostname === 'localhost' && 'serviceWorker' in navigator) {
      navigator.serviceWorker.getRegistrations().then((registrations) => {
        if (registrations.length > 0) {
          console.log("[Index] Found active service workers on localhost. Cleaning up...", registrations);
          for (const reg of registrations) {
            reg.unregister().then((success) => {
              console.log("[Index] Service worker unregistration success:", success);
            });
          }
        }
      });
    }

    // Check for guest mode in localStorage
    const guestMode = localStorage.getItem("guestMode");
    if (guestMode === "true") {
      console.log("[Index] Guest mode detected via localStorage");
      setIsGuest(true);
      setLoading(false);
      return;
    }

    // Set up auth state listener
    console.log("[Index] Setting up auth state change listener");
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      async (event, session) => {
        console.log("[Index] Auth state change event:", event, "User ID:", session?.user?.id);
        setSession(session);
        if (!session && !isGuest) {
          console.log("[Index] No session. Navigating to /auth");
          navigate("/auth");
        }
      }
    );

    // Check for existing session
    console.log("[Index] Checking current session via getSession()");
    supabase.auth.getSession().then(async ({ data: { session } }) => {
      console.log("[Index] getSession() resolved. Session present:", !!session);
      setSession(session);
      if (!session && !isGuest) {
        console.log("[Index] getSession() found no user. Navigating to /auth");
        navigate("/auth");
      }
      setLoading(false);
      console.log("[Index] setLoading(false) executed. Screen should render layout.");

      // Check session revocation asynchronously without blocking the UI load
      if (session) {
        console.log("[Index] Triggering async revocation check in background");
        checkSessionRevoked().then(async (revoked) => {
          console.log("[Index] Revocation check result:", revoked);
          if (revoked) {
            toast.error("You were signed out from another device");
            await supabase.auth.signOut();
            navigate("/auth");
          }
        }).catch((err) => {
          console.error("[Index] Background session revocation check failed:", err);
        });
      }
    }).catch((err) => {
      console.error("[Index] getSession() rejected:", err);
      setLoading(false);
    });

    return () => {
      console.log("[Index] useEffect cleanup. Unsubscribing listener.");
      subscription.unsubscribe();
    };
  }, [navigate, isGuest]);

  if (loading) {
    console.log("[Index] Rendering loading view...");
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-muted-foreground">Loading...</div>
      </div>
    );
  }

  if (!session && !isGuest) {
    return null;
  }

  return <ChatLayout isGuest={isGuest} />;
};

export default Index;

