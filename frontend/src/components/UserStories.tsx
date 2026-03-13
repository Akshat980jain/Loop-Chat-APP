import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { useState, useEffect } from "react";
import { useHaptics } from "@/hooks/useHaptics";
import { useStories, UserWithStories } from "@/hooks/useStories";
import { StoryViewer } from "./StoryViewer";
import { Plus, Loader2 } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";

export const UserStories = () => {
  const [selectedUserStories, setSelectedUserStories] = useState<UserWithStories | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const haptics = useHaptics();
  const { stories, isLoading, error: storiesError, createStory } = useStories();
  const { toast } = useToast();

  useEffect(() => {
    if (storiesError instanceof Error) {
      toast({
        title: "Couldn't load stories",
        description: storiesError.message,
        variant: "destructive",
      });
    }
  }, [storiesError, toast]);

  // Get current user id
  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setCurrentUserId(data?.session?.user?.id || null);
    });
  }, []);

  // Separate current user's stories from others
  const myStories = stories.find((s) => s.user_id === currentUserId);
  const otherStories = stories.filter((s) => s.user_id !== currentUserId);

  const handleStoryClick = (userStories: UserWithStories) => {
    haptics.light();
    setSelectedUserStories(userStories);
  };

  const handleMyStatusClick = () => {
    haptics.light();
    if (myStories && myStories.stories.length > 0) {
      // If user has stories, show them
      setSelectedUserStories(myStories);
    } else {
      // Otherwise, trigger add story flow
      handleAddStory();
    }
  };

  const handleAddStory = async () => {
    // Check if user is authenticated
    const { data: session } = await supabase.auth.getSession();
    if (!session?.session) {
      toast({
        title: "Sign in required",
        description: "Please sign in to add a story",
        variant: "destructive",
      });
      return;
    }

    // Create a file input and trigger it
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*,video/*';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;

      setIsUploading(true);
      try {
        // Upload to storage - use avatars/{user_id}/stories/ path to match storage RLS policy
        const fileExt = file.name.split('.').pop();
        const fileName = `avatars/${session.session.user.id}/stories/${Date.now()}.${fileExt}`;
        
        const { error: uploadError } = await supabase.storage
          .from('chat-attachments')
          .upload(fileName, file);

        if (uploadError) throw uploadError;

        // Get public URL
        const { data: urlData } = supabase.storage
          .from('chat-attachments')
          .getPublicUrl(fileName);

        const mediaType = file.type.startsWith('video/') ? 'video' : 'image';

        // Create story
        await createStory.mutateAsync({
          media_url: urlData.publicUrl,
          media_type: mediaType,
        });

        toast({
          title: "Story added",
          description: "Your story has been posted for 24 hours",
        });
      } catch (error) {
        console.error('Error adding story:', error);
        toast({
          title: "Error",
          description: "Failed to add story. Please try again.",
          variant: "destructive",
        });
      } finally {
        setIsUploading(false);
      }
    };
    input.click();
  };

  return (
    <>
      <div className="p-4 border-b border-border">
        <h3 className="text-sm font-semibold text-foreground mb-3">Status</h3>
        <div className="flex gap-3 overflow-x-auto scrollbar-hide">
          {/* My Status Button - shows own story or add option */}
          <button
            onClick={handleMyStatusClick}
            disabled={isUploading}
            className="flex-shrink-0 flex flex-col items-center gap-2 hover-scale"
          >
            <div className="relative">
              {myStories && myStories.stories.length > 0 ? (
                // Show user's own story preview with gradient ring
                <div className="p-0.5 rounded-full bg-gradient-to-tr from-primary via-accent to-primary">
                  <div className="w-16 h-16 rounded-full border-2 border-background overflow-hidden bg-muted">
                    {myStories.stories[0]?.media_type === 'video' ? (
                      <video 
                        src={myStories.stories[0]?.media_url} 
                        className="w-full h-full object-cover"
                        muted
                      />
                    ) : (
                      <img 
                        src={myStories.stories[0]?.media_url} 
                        alt="My story preview"
                        className="w-full h-full object-cover"
                      />
                    )}
                  </div>
                  {/* Add more button overlay */}
                  <div
                    role="button"
                    tabIndex={0}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleAddStory();
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.stopPropagation();
                        handleAddStory();
                      }
                    }}
                    className="absolute -bottom-1 -right-1 w-6 h-6 bg-primary rounded-full border-2 border-background flex items-center justify-center cursor-pointer"
                  >
                    <Plus className="w-3 h-3 text-primary-foreground" />
                  </div>
                </div>
              ) : (
                // Show add status placeholder
                <div className="w-16 h-16 rounded-full bg-muted flex items-center justify-center border-2 border-dashed border-muted-foreground/50">
                  {isUploading ? (
                    <Loader2 className="w-6 h-6 text-muted-foreground animate-spin" />
                  ) : (
                    <Plus className="w-6 h-6 text-muted-foreground" />
                  )}
                </div>
              )}
            </div>
            <span className="text-xs text-muted-foreground">
              {myStories && myStories.stories.length > 0 ? "My status" : "Add status"}
            </span>
          </button>

          {/* Loading state */}
          {isLoading && (
            <div className="flex items-center justify-center px-4">
              <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            </div>
          )}

          {/* Other User Stories (excluding current user) */}
          {otherStories.map((userStories) => (
            <button
              key={userStories.user_id}
              onClick={() => handleStoryClick(userStories)}
              className="flex-shrink-0 flex flex-col items-center gap-2 hover-scale"
            >
              <div className="relative">
                {/* Ring color: gradient for unviewed, muted for viewed */}
                <div className={`p-0.5 rounded-full ${
                  userStories.has_unviewed 
                    ? 'bg-gradient-to-tr from-primary via-accent to-primary' 
                    : 'bg-muted-foreground/30'
                }`}>
                  <div className="w-16 h-16 rounded-full border-2 border-background overflow-hidden bg-muted">
                    {userStories.stories[0]?.media_type === 'video' ? (
                      <video 
                        src={userStories.stories[0]?.media_url} 
                        className="w-full h-full object-cover"
                        muted
                      />
                    ) : (
                      <img 
                        src={userStories.stories[0]?.media_url} 
                        alt="Story preview"
                        className="w-full h-full object-cover"
                      />
                    )}
                  </div>
                </div>
                {userStories.profile.status === 'online' && (
                  <div className="absolute bottom-0 right-0 w-4 h-4 bg-green-500 rounded-full border-2 border-background" />
                )}
              </div>
              <span className="text-xs text-foreground truncate max-w-[70px]">
                {userStories.profile.full_name.split(' ')[0]}
              </span>
            </button>
          ))}

          {/* Empty state - only show if no other users have stories */}
          {!isLoading && otherStories.length === 0 && !myStories && (
            <div className="flex items-center px-4 text-muted-foreground text-sm">
              No status updates yet
            </div>
          )}
        </div>
      </div>

      <StoryViewer 
        userStories={selectedUserStories} 
        open={!!selectedUserStories} 
        onOpenChange={(open) => !open && setSelectedUserStories(null)} 
      />
    </>
  );
};
