import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { supabase } from "@/integrations/supabase/client";
import { toast } from "sonner";

interface Profile {
  id: string;
  user_id: string;
  full_name: string;
  username: string;
  phone: string | null;
  status: string | null;
  avatar_url: string | null;
  generated_avatar_url: string | null;
  bio: string | null;
  created_at: string;
  updated_at: string;
}

interface UserSettings {
  id: string;
  user_id: string;
  show_online_status: boolean;
  show_read_receipts: boolean;
  show_typing_indicator: boolean;
  profile_photo_visibility: string;
  status_visibility: string;
  group_add_permission: string;
  direct_message_notifications: boolean;
  group_message_notifications: boolean;
  mention_notifications: boolean;
  message_sounds: boolean;
  notification_sounds: boolean;
  do_not_disturb: boolean;
  two_factor_enabled: boolean;
  end_to_end_encryption: boolean;
  language: string;
  date_format: string;
  time_format: string;
  use_generated_avatar: boolean;
  generated_avatar_url: string | null;
  created_at: string;
  updated_at: string;
}

interface Statistics {
  messages_sent: number;
  conversations: number;
  calls_made: number;
}

interface ProfileData {
  profile: Profile | null;
  settings: UserSettings | null;
  email: string | null;
  statistics: Statistics | null;
}

export const useProfile = () => {
  return useQuery<ProfileData>({
    queryKey: ["profile"],
    queryFn: async () => {
      const { data: sessionData } = await supabase.auth.getSession();
      if (!sessionData.session) {
        // Return null data when not authenticated instead of throwing
        return { profile: null, settings: null, email: null, statistics: null };
      }

      const { data, error } = await supabase.functions.invoke("get-profile", {
        headers: {
          Authorization: `Bearer ${sessionData.session.access_token}`,
        },
      });

      if (error) {
        console.error("Error fetching profile:", error);
        throw error;
      }

      return data as ProfileData;
    },
    retry: false,
  });
};

export const useUpdateProfile = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (updates: Partial<Profile>) => {
      const { data, error } = await supabase.functions.invoke("update-profile", {
        body: updates,
      });
      
      if (error) {
        throw new Error(error.message);
      }
      
      if (data.error) {
        throw new Error(data.error);
      }
      
      return data.profile;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile"] });
      toast.success("Profile updated successfully");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to update profile");
    },
  });
};

export const useUpdateSettings = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (updates: Partial<UserSettings>) => {
      const { data, error } = await supabase.functions.invoke("update-settings", {
        body: updates,
      });
      
      if (error) {
        throw new Error(error.message);
      }
      
      if (data.error) {
        throw new Error(data.error);
      }
      
      return data.settings;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile"] });
      toast.success("Settings updated successfully");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to update settings");
    },
  });
};

export const useUploadAvatar = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      
      const { data: { session } } = await supabase.auth.getSession();
      
      if (!session) {
        throw new Error("Not authenticated");
      }
      
      const response = await fetch(
        `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/upload-avatar`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${session.access_token}`,
          },
          body: formData,
        }
      );
      
      const data = await response.json();
      
      if (!response.ok) {
        throw new Error(data.error || "Failed to upload avatar");
      }
      
      return data.avatar_url;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile"] });
      toast.success("Avatar updated successfully");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to upload avatar");
    },
  });
};

export const useRemoveAvatar = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async () => {
      const { data, error } = await supabase.functions.invoke("update-profile", {
        body: { avatar_url: null },
      });
      
      if (error) {
        throw new Error(error.message);
      }
      
      if (data.error) {
        throw new Error(data.error);
      }
      
      return data.profile;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile"] });
      toast.success("Avatar removed successfully");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to remove avatar");
    },
  });
};

export const useDeleteAccount = () => {
  return useMutation({
    mutationFn: async () => {
      const { data, error } = await supabase.functions.invoke("delete-account");
      
      if (error) {
        throw new Error(error.message);
      }
      
      if (data.error) {
        throw new Error(data.error);
      }
      
      // Sign out after deletion
      await supabase.auth.signOut();
      
      return data;
    },
    onSuccess: () => {
      toast.success("Account deleted successfully");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to delete account");
    },
  });
};