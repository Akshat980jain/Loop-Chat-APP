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
    // Check for guest mode in localStorage
    const guestMode = localStorage.getItem("guestMode");
    if (guestMode === "true") {
      setIsGuest(true);
      setLoading(false);
      return;
    }

    // Set up auth state listener
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      async (event, session) => {
        setSession(session);
        if (!session && !isGuest) {
          navigate("/auth");
        }

        // Check if session was revoked from another device
        if (session && event === 'SIGNED_IN') {
          const revoked = await checkSessionRevoked();
          if (revoked) {
            toast.error("You were signed out from another device");
            await supabase.auth.signOut();
            navigate("/auth");
          }
        }
      }
    );

    // Check for existing session
    supabase.auth.getSession().then(async ({ data: { session } }) => {
      if (session) {
        // Check if session was revoked
        const revoked = await checkSessionRevoked();
        if (revoked) {
          toast.error("You were signed out from another device");
          await supabase.auth.signOut();
          navigate("/auth");
          setLoading(false);
          return;
        }
      }

      setSession(session);
      if (!session && !isGuest) {
        navigate("/auth");
      }
      setLoading(false);
    });

    return () => subscription.unsubscribe();
  }, [navigate, isGuest]);

  if (loading) {
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

