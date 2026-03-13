import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { supabase } from "@/integrations/supabase/client";

interface Story {
  id: string;
  media_url: string;
  media_type: string;
  caption: string | null;
  created_at: string;
  expires_at: string;
  view_count?: number;
  is_viewed?: boolean;
}

export interface UserWithStories {
  user_id: string;
  profile: {
    full_name: string;
    username: string;
    avatar_url: string | null;
    status: string | null;
  };
  stories: Story[];
  has_unviewed?: boolean;
}

interface StoryViewer {
  id: string;
  viewed_at: string;
  viewer: {
    user_id: string;
    full_name: string;
    username: string;
    avatar_url: string | null;
  };
}

export const useStories = () => {
  const queryClient = useQueryClient();

  const { data: storiesData, isLoading, error, refetch } = useQuery({
    queryKey: ["stories"],
    queryFn: async (): Promise<UserWithStories[]> => {
      const { data: { session } } = await supabase.auth.getSession();

      // In guest mode / logged-out state, don't call the function (it requires a valid user JWT)
      if (!session?.access_token) {
        return [];
      }

      const { data, error } = await supabase.functions.invoke('get-stories', {
        headers: {
          Authorization: `Bearer ${session.access_token}`,
        },
      });

      if (error) {
        // Avoid blank-screen loops on auth issues
        const msg = (error as any)?.message || "Failed to fetch stories";
        if (msg.includes("Invalid JWT") || (error as any)?.context?.status === 401) {
          throw new Error("Session expired. Please sign in again.");
        }
        throw error;
      }

      return data?.stories || [];
    },
    refetchInterval: 60000, // Refetch every minute
    retry: false,
  });

  const createStory = useMutation({
    mutationFn: async ({ media_url, media_type = 'image', caption }: { 
      media_url: string; 
      media_type?: string;
      caption?: string;
    }) => {
      const { data: session } = await supabase.auth.getSession();
      if (!session?.session?.access_token) {
        throw new Error('You must be logged in to create a story');
      }

      const { data, error } = await supabase.functions.invoke('create-story', {
        body: { media_url, media_type, caption },
        headers: {
          Authorization: `Bearer ${session.session.access_token}`
        }
      });

      if (error) throw error;
      return data.story;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["stories"] });
    },
  });

  const recordView = useMutation({
    mutationFn: async (storyId: string) => {
      const { data: session } = await supabase.auth.getSession();
      if (!session?.session?.access_token) {
        return; // Not logged in, can't record view
      }

      // Don't manually override Authorization; let the Supabase client attach the current token.
      const { error } = await supabase.functions.invoke('record-story-view', {
        body: { story_id: storyId },
      });

      if (error) {
        console.error('Error recording view:', error);
      }
    },
  });

  return {
    stories: storiesData || [],
    isLoading,
    error,
    refetch,
    createStory,
    recordView,
  };
};

export const useStoryViews = (storyId: string | null) => {
  return useQuery({
    queryKey: ["story-views", storyId],
    queryFn: async (): Promise<{ views: StoryViewer[]; count: number }> => {
      if (!storyId) return { views: [], count: 0 };

      const { data: session } = await supabase.auth.getSession();
      if (!session?.session?.access_token) {
        throw new Error('You must be logged in to view story stats');
      }

      const { data, error } = await supabase.functions.invoke('get-story-views', {
        body: { story_id: storyId },
        headers: {
          Authorization: `Bearer ${session.session.access_token}`
        }
      });

      if (error) throw error;
      return { views: data?.views || [], count: data?.count || 0 };
    },
    enabled: !!storyId,
  });
};
