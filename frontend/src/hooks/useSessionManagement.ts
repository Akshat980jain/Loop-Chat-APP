import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { supabase } from "@/integrations/supabase/client";
import { toast } from "sonner";

// --- Types ---

interface DeviceInfo {
  browser: string;
  os: string;
  device_type: "desktop" | "mobile" | "tablet";
  screen_size: string;
}

export interface UserSession {
  id: string;
  user_id: string;
  session_token_hash: string;
  device_info: DeviceInfo;
  ip_address: string | null;
  last_active: string;
  is_revoked: boolean;
  created_at: string;
  is_current?: boolean;
}

// --- User Agent Parser ---

function parseUserAgent(ua: string): DeviceInfo {
  let browser = "Unknown Browser";
  let os = "Unknown OS";
  let device_type: DeviceInfo["device_type"] = "desktop";

  // Detect browser
  if (ua.includes("Firefox/")) {
    browser = "Firefox";
  } else if (ua.includes("Edg/")) {
    browser = "Microsoft Edge";
  } else if (ua.includes("OPR/") || ua.includes("Opera/")) {
    browser = "Opera";
  } else if (ua.includes("Chrome/") && !ua.includes("Edg/")) {
    browser = "Chrome";
  } else if (ua.includes("Safari/") && !ua.includes("Chrome/")) {
    browser = "Safari";
  } else if (ua.includes("MSIE") || ua.includes("Trident/")) {
    browser = "Internet Explorer";
  }

  // Detect OS
  if (ua.includes("Windows NT 10")) {
    os = "Windows";
  } else if (ua.includes("Windows")) {
    os = "Windows";
  } else if (ua.includes("Mac OS X")) {
    os = "macOS";
  } else if (ua.includes("Android")) {
    os = "Android";
  } else if (ua.includes("iPhone") || ua.includes("iPad")) {
    os = "iOS";
  } else if (ua.includes("Linux")) {
    os = "Linux";
  } else if (ua.includes("CrOS")) {
    os = "Chrome OS";
  }

  // Detect device type
  if (ua.includes("Mobile") || ua.includes("Android") || ua.includes("iPhone")) {
    device_type = "mobile";
  } else if (ua.includes("iPad") || ua.includes("Tablet")) {
    device_type = "tablet";
  }

  const screen_size = `${window.screen.width}x${window.screen.height}`;

  return { browser, os, device_type, screen_size };
}

// --- SHA-256 Hash ---

async function sha256(message: string): Promise<string> {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

// --- Hooks ---

/**
 * Fetch all active (non-revoked) sessions for the current user.
 */
export const useSessions = () => {
  return useQuery<UserSession[]>({
    queryKey: ["user-sessions"],
    queryFn: async () => {
      const { data: sessionData } = await supabase.auth.getSession();
      if (!sessionData.session) {
        return [];
      }

      // Get current token hash to identify the current session
      const currentTokenHash = await sha256(sessionData.session.access_token);

      // Query sessions directly (RLS ensures only user's own sessions are returned)
      const { data, error } = await supabase
        .from("user_sessions")
        .select("*")
        .eq("is_revoked", false)
        .order("last_active", { ascending: false });

      if (error) {
        console.error("Error fetching sessions:", error);
        throw error;
      }

      // Mark the current session
      return (data || []).map((session: any) => ({
        ...session,
        is_current: session.session_token_hash === currentTokenHash,
      })) as UserSession[];
    },
    retry: false,
  });
};

/**
 * Track the current login session (called after login).
 */
export const useTrackSession = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const { data: sessionData } = await supabase.auth.getSession();
      if (!sessionData.session) {
        throw new Error("Not authenticated");
      }

      const deviceInfo = parseUserAgent(navigator.userAgent);

      const { data, error } = await supabase.functions.invoke("track-session", {
        body: { device_info: deviceInfo },
        headers: {
          Authorization: `Bearer ${sessionData.session.access_token}`,
        },
      });

      if (error) {
        throw new Error(error.message);
      }

      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user-sessions"] });
    },
    onError: (error: Error) => {
      console.error("Failed to track session:", error);
      // Silent failure — session tracking is non-critical
    },
  });
};

/**
 * Revoke a specific session (sign out from another device).
 */
export const useRevokeSession = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (sessionId: string) => {
      const { data: sessionData } = await supabase.auth.getSession();
      if (!sessionData.session) {
        throw new Error("Not authenticated");
      }

      const { data, error } = await supabase.functions.invoke("revoke-session", {
        body: { sessionId },
        headers: {
          Authorization: `Bearer ${sessionData.session.access_token}`,
        },
      });

      if (error) {
        throw new Error(error.message);
      }

      if (data?.error) {
        throw new Error(data.error);
      }

      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user-sessions"] });
      toast.success("Session revoked successfully");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to revoke session");
    },
  });
};

/**
 * Revoke all sessions except the current one.
 */
export const useRevokeAllOtherSessions = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const { data: sessionData } = await supabase.auth.getSession();
      if (!sessionData.session) {
        throw new Error("Not authenticated");
      }

      const { data, error } = await supabase.functions.invoke("revoke-session", {
        body: { revokeAllOthers: true },
        headers: {
          Authorization: `Bearer ${sessionData.session.access_token}`,
        },
      });

      if (error) {
        throw new Error(error.message);
      }

      if (data?.error) {
        throw new Error(data.error);
      }

      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user-sessions"] });
      toast.success("All other sessions signed out");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to revoke sessions");
    },
  });
};

/**
 * Check if the current session has been revoked by another device.
 * Returns true if the session is revoked and the user should be signed out.
 */
export const checkSessionRevoked = async (): Promise<boolean> => {
  try {
    const { data: sessionData } = await supabase.auth.getSession();
    if (!sessionData.session) {
      return false;
    }

    const currentTokenHash = await sha256(sessionData.session.access_token);

    const { data, error } = await supabase
      .from("user_sessions")
      .select("is_revoked")
      .eq("session_token_hash", currentTokenHash)
      .maybeSingle();

    if (error) {
      console.error("Error checking session revocation:", error);
      return false;
    }

    return data?.is_revoked === true;
  } catch {
    return false;
  }
};

/**
 * Mask IP address for display (e.g., "192.168.1.100" → "192.168.x.x")
 */
export const maskIpAddress = (ip: string | null): string => {
  if (!ip || ip === "unknown") return "Unknown";
  const parts = ip.split(".");
  if (parts.length === 4) {
    return `${parts[0]}.${parts[1]}.x.x`;
  }
  // IPv6 or other format — just show first part
  return ip.substring(0, 12) + "...";
};
