import { useQuery } from "@tanstack/react-query";
import { supabase } from "@/integrations/supabase/client";

export const useConversations = (userId: string | undefined) => {
  return useQuery({
    queryKey: ["conversations", userId],
    queryFn: async () => {
      if (!userId) return [];

      console.log("Fetching conversations for user:", userId);

      // Step 1: Get all conversation IDs the user is part of
      const { data: participantData, error: participantError } = await supabase
        .from("conversation_participants")
        .select("conversation_id")
        .eq("user_id", userId);

      console.log("User's conversations:", { participantData, participantError });

      if (participantError) {
        console.error("Error fetching user's conversations:", participantError);
        throw participantError;
      }

      if (!participantData || participantData.length === 0) {
        console.log("No conversations found for user");
        return [];
      }

      const conversationIds = participantData.map(p => p.conversation_id);

      // Step 2: Get conversation details and other participants
      const conversations = await Promise.all(
        conversationIds.map(async (convId) => {
          // Get conversation details
          const { data: convData, error: convError } = await supabase
            .from("conversations")
            .select("id, updated_at")
            .eq("id", convId)
            .single();

          if (convError || !convData) {
            console.error("Error fetching conversation:", convError);
            return null;
          }

          // Get other participant's user_id
          const { data: otherParticipantData, error: otherParticipantError } = await supabase
            .from("conversation_participants")
            .select("user_id")
            .eq("conversation_id", convId)
            .neq("user_id", userId)
            .single();

          if (otherParticipantError || !otherParticipantData) {
            console.error("Error fetching other participant:", otherParticipantError);
            return {
              id: convData.id,
              updated_at: convData.updated_at,
              participant: null,
            };
          }

          // Get profile for the other participant
          const { data: profileData, error: profileError } = await supabase
            .from("profiles")
            .select("id, user_id, full_name, username, avatar_url, status")
            .eq("user_id", otherParticipantData.user_id)
            .single();

          if (profileError) {
            console.error("Error fetching profile:", profileError);
          }

          console.log("Conversation:", convId, "Other participant profile:", profileData);

          return {
            id: convData.id,
            updated_at: convData.updated_at,
            participant: profileData || null,
          };
        })
      );

      // Filter out any null entries and return
      const validConversations = conversations.filter(c => c !== null);
      console.log("Final conversations list:", validConversations);
      return validConversations;
    },
    enabled: !!userId,
  });
};
