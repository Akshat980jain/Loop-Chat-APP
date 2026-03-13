import { Dialog, DialogContent, DialogTitle } from "./ui/dialog";
import { VisuallyHidden } from "@radix-ui/react-visually-hidden";
import { Avatar, AvatarFallback, AvatarImage } from "./ui/avatar";
import { X, ChevronLeft, ChevronRight, Eye, Loader2, Send } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { useState, useEffect } from "react";
import { formatDistanceToNow } from "date-fns";
import { useStories, useStoryViews, UserWithStories } from "@/hooks/useStories";
import { supabase } from "@/integrations/supabase/client";
import { ScrollArea } from "./ui/scroll-area";
import { useToast } from "@/hooks/use-toast";

interface StoryViewerProps {
  userStories: UserWithStories | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const StoryViewer = ({ userStories, open, onOpenChange }: StoryViewerProps) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [progress, setProgress] = useState(0);
  const [showViewers, setShowViewers] = useState(false);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");
  const [isSendingReply, setIsSendingReply] = useState(false);
  const [isReplyFocused, setIsReplyFocused] = useState(false);
  const { recordView, refetch } = useStories();
  const { toast } = useToast();

  const stories = userStories?.stories || [];
  const currentStory = stories[currentIndex];
  const profile = userStories?.profile;
  const isOwnStory = userStories?.user_id === currentUserId;

  // Fetch viewers only when viewing own story and viewers panel is open
  const { data: viewsData, isLoading: viewsLoading } = useStoryViews(
    showViewers && isOwnStory ? currentStory?.id : null
  );

  // Get current user id
  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setCurrentUserId(data?.session?.user?.id || null);
    });
  }, []);

  // Record view when viewing someone else's story
  useEffect(() => {
    if (open && currentStory && !isOwnStory && currentUserId) {
      recordView.mutate(currentStory.id);
    }
  }, [open, currentStory?.id, isOwnStory, currentUserId]);

  // Auto-advance stories (pause when viewing viewers list or typing reply)
  useEffect(() => {
    if (!open || stories.length === 0 || showViewers || isReplyFocused) return;

    const interval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) {
          // Move to next story
          if (currentIndex < stories.length - 1) {
            setCurrentIndex(currentIndex + 1);
            return 0;
          } else {
            onOpenChange(false);
            return 0;
          }
        }
        return prev + 2; // 2% every 100ms = 5 seconds per story
      });
    }, 100);

    return () => clearInterval(interval);
  }, [open, currentIndex, stories.length, onOpenChange, showViewers, isReplyFocused]);

  // Reset on open
  useEffect(() => {
    if (open) {
      setCurrentIndex(0);
      setProgress(0);
      setShowViewers(false);
      setReplyText("");
      setIsReplyFocused(false);
    }
  }, [open]);

  // Refetch stories when closing to update viewed status
  useEffect(() => {
    if (!open) {
      refetch();
    }
  }, [open, refetch]);

  const handlePrev = () => {
    if (currentIndex > 0) {
      setCurrentIndex(currentIndex - 1);
      setProgress(0);
      setShowViewers(false);
    }
  };

  const handleNext = () => {
    if (currentIndex < stories.length - 1) {
      setCurrentIndex(currentIndex + 1);
      setProgress(0);
      setShowViewers(false);
    } else {
      onOpenChange(false);
    }
  };

  const handleSendReply = async () => {
    if (!replyText.trim() || !userStories || !currentUserId) return;

    setIsSendingReply(true);
    try {
      const storyOwnerId = userStories.user_id;

      // Find or create a conversation with the story owner
      // First, check if a conversation already exists between these two users
      const { data: existingParticipations } = await supabase
        .from('conversation_participants')
        .select('conversation_id')
        .eq('user_id', currentUserId);

      let conversationId: string | null = null;

      if (existingParticipations && existingParticipations.length > 0) {
        // Check if any of these conversations include the story owner
        const conversationIds = existingParticipations.map(p => p.conversation_id);

        const { data: sharedConversation } = await supabase
          .from('conversation_participants')
          .select('conversation_id')
          .eq('user_id', storyOwnerId)
          .in('conversation_id', conversationIds)
          .limit(1)
          .single();

        if (sharedConversation) {
          conversationId = sharedConversation.conversation_id;
        }
      }

      // If no existing conversation, create a new one
      if (!conversationId) {
        const { data: newConversation, error: convError } = await supabase
          .from('conversations')
          .insert({})
          .select()
          .single();

        if (convError) throw convError;
        conversationId = newConversation.id;

        // Add both participants
        const { error: partError } = await supabase
          .from('conversation_participants')
          .insert([
            { conversation_id: conversationId, user_id: currentUserId },
            { conversation_id: conversationId, user_id: storyOwnerId }
          ]);

        if (partError) throw partError;
      }

      // Send the reply message with story context
      const messageContent = `Replied to your story: "${replyText.trim()}"`;

      const { error: msgError } = await supabase
        .from('messages')
        .insert({
          conversation_id: conversationId,
          sender_id: currentUserId,
          content: messageContent
        });

      if (msgError) throw msgError;

      toast({
        title: "Reply sent",
        description: `Your reply was sent to ${profile?.full_name}`,
      });

      setReplyText("");
      setIsReplyFocused(false);
    } catch (error) {
      console.error('Error sending reply:', error);
      toast({
        title: "Failed to send reply",
        description: "Please try again",
        variant: "destructive",
      });
    } finally {
      setIsSendingReply(false);
    }
  };

  if (!userStories || stories.length === 0) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md p-0 bg-black border-0 overflow-hidden" aria-describedby={undefined}>
        <VisuallyHidden>
          <DialogTitle>{profile?.full_name}'s Story</DialogTitle>
        </VisuallyHidden>
        {/* Progress bars */}
        <div className="absolute top-0 left-0 right-0 z-20 flex gap-1 p-2">
          {stories.map((_, index) => (
            <div key={index} className="flex-1 h-0.5 bg-white/30 rounded-full overflow-hidden">
              <div
                className="h-full bg-white transition-all duration-100"
                style={{
                  width: index < currentIndex ? '100%' :
                    index === currentIndex ? `${progress}%` : '0%'
                }}
              />
            </div>
          ))}
        </div>

        {/* Header */}
        <div className="absolute top-4 left-0 right-0 z-20 flex items-center justify-between px-4 pt-2">
          <div className="flex items-center gap-3">
            <Avatar className="w-10 h-10 border-2 border-white">
              <AvatarImage src={profile?.avatar_url || undefined} />
              <AvatarFallback className="bg-primary text-primary-foreground">
                {profile?.full_name?.[0] || 'U'}
              </AvatarFallback>
            </Avatar>
            <div>
              <p className="text-white font-semibold text-sm">{profile?.full_name}</p>
              <p className="text-white/70 text-xs">
                {currentStory && formatDistanceToNow(new Date(currentStory.created_at), { addSuffix: true })}
              </p>
            </div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => onOpenChange(false)}
            className="text-white hover:bg-white/20"
          >
            <X className="w-6 h-6" />
          </Button>
        </div>

        {/* Story content */}
        <div className="relative w-full aspect-[9/16] bg-black flex items-center justify-center">
          {currentStory?.media_type === 'image' ? (
            <img
              src={currentStory.media_url}
              alt="Story"
              className="w-full h-full object-contain"
            />
          ) : currentStory?.media_type === 'video' ? (
            <video
              src={currentStory.media_url}
              className="w-full h-full object-contain"
              autoPlay
              muted
              loop
            />
          ) : null}

          {/* Caption */}
          {currentStory?.caption && !showViewers && (
            <div className="absolute bottom-24 left-4 right-4 text-center">
              <p className="text-white text-lg drop-shadow-lg">{currentStory.caption}</p>
            </div>
          )}

          {/* Views section for own stories */}
          {isOwnStory && (
            <div className="absolute bottom-0 left-0 right-0 z-20">
              {showViewers ? (
                // Viewers list panel
                <div className="bg-black/90 backdrop-blur-sm rounded-t-2xl max-h-[50%]">
                  <div className="flex items-center justify-between p-4 border-b border-white/10">
                    <div className="flex items-center gap-2">
                      <Eye className="w-5 h-5 text-white" />
                      <span className="text-white font-medium">
                        {viewsData?.count || 0} views
                      </span>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setShowViewers(false)}
                      className="text-white hover:bg-white/20"
                    >
                      Close
                    </Button>
                  </div>
                  <ScrollArea className="max-h-[200px]">
                    {viewsLoading ? (
                      <div className="flex items-center justify-center p-4">
                        <Loader2 className="w-6 h-6 animate-spin text-white" />
                      </div>
                    ) : viewsData?.views && viewsData.views.length > 0 ? (
                      <div className="p-2">
                        {viewsData.views.map((view) => (
                          <div
                            key={view.id}
                            className="flex items-center gap-3 p-2 rounded-lg hover:bg-white/10"
                          >
                            <Avatar className="w-10 h-10">
                              <AvatarImage src={view.viewer.avatar_url || undefined} />
                              <AvatarFallback className="bg-primary text-primary-foreground">
                                {view.viewer.full_name?.[0] || 'U'}
                              </AvatarFallback>
                            </Avatar>
                            <div className="flex-1 min-w-0">
                              <p className="text-white text-sm font-medium truncate">
                                {view.viewer.full_name}
                              </p>
                              <p className="text-white/60 text-xs">
                                {formatDistanceToNow(new Date(view.viewed_at), { addSuffix: true })}
                              </p>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="p-4 text-center text-white/60 text-sm">
                        No views yet
                      </div>
                    )}
                  </ScrollArea>
                </div>
              ) : (
                // Views count button
                <button
                  onClick={() => setShowViewers(true)}
                  className="flex items-center gap-2 p-4 text-white hover:bg-white/10 transition-colors w-full"
                >
                  <Eye className="w-5 h-5" />
                  <span className="text-sm">
                    {currentStory?.view_count || 0} views
                  </span>
                </button>
              )}
            </div>
          )}

          {/* Reply input for other users' stories */}
          {!isOwnStory && currentUserId && (
            <div className="absolute bottom-0 left-0 right-0 z-20 p-3 bg-gradient-to-t from-black/80 to-transparent">
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleSendReply();
                }}
                className="flex items-center gap-2"
              >
                <Input
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  onFocus={() => setIsReplyFocused(true)}
                  onBlur={() => !replyText && setIsReplyFocused(false)}
                  placeholder={`Reply to ${profile?.full_name?.split(' ')[0]}...`}
                  className="flex-1 bg-white/10 border-white/20 text-white placeholder:text-white/50 focus:bg-white/20"
                  disabled={isSendingReply}
                />
                <Button
                  type="submit"
                  size="icon"
                  variant="ghost"
                  disabled={!replyText.trim() || isSendingReply}
                  className="text-white hover:bg-white/20 shrink-0"
                >
                  {isSendingReply ? (
                    <Loader2 className="w-5 h-5 animate-spin" />
                  ) : (
                    <Send className="w-5 h-5" />
                  )}
                </Button>
              </form>
            </div>
          )}

          {/* Navigation areas - only when not showing viewers or focused on reply */}
          {!showViewers && !isReplyFocused && (
            <>
              <button
                onClick={handlePrev}
                className="absolute left-0 top-0 bottom-20 w-1/3 z-10"
                aria-label="Previous story"
              />
              <button
                onClick={handleNext}
                className="absolute right-0 top-0 bottom-20 w-1/3 z-10"
                aria-label="Next story"
              />
            </>
          )}

          {/* Navigation arrows */}
          {!showViewers && !isReplyFocused && currentIndex > 0 && (
            <Button
              variant="ghost"
              size="icon"
              onClick={handlePrev}
              className="absolute left-2 top-1/2 -translate-y-1/2 text-white hover:bg-white/20 z-20"
            >
              <ChevronLeft className="w-8 h-8" />
            </Button>
          )}
          {!showViewers && !isReplyFocused && currentIndex < stories.length - 1 && (
            <Button
              variant="ghost"
              size="icon"
              onClick={handleNext}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-white hover:bg-white/20 z-20"
            >
              <ChevronRight className="w-8 h-8" />
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
};
