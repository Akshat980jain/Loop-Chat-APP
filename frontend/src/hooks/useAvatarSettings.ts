import { useMutation, useQueryClient } from "@tanstack/react-query";
import { supabase } from "@/integrations/supabase/client";

interface GenerateAvatarParams {
  style: 'modern' | 'cartoon' | 'abstract' | 'nature' | 'pixel';
}

export const useGenerateAvatar = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ style }: GenerateAvatarParams) => {
      const { data: session } = await supabase.auth.getSession();
      if (!session?.session?.access_token) {
        throw new Error('You must be logged in to generate an avatar');
      }

      const { data, error } = await supabase.functions.invoke('generate-avatar', {
        body: { style },
        headers: {
          Authorization: `Bearer ${session.session.access_token}`
        }
      });

      if (error) throw error;
      if (data?.error) throw new Error(data.error);
      
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile"] });
    },
  });
};

export const useToggleAvatarPreference = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (useGenerated: boolean) => {
      const { data: session } = await supabase.auth.getSession();
      if (!session?.session?.user) {
        throw new Error('You must be logged in');
      }

      const { error } = await supabase
        .from('user_settings')
        .update({ use_generated_avatar: useGenerated })
        .eq('user_id', session.session.user.id);

      if (error) throw error;
      return { use_generated_avatar: useGenerated };
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile"] });
    },
  });
};
