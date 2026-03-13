import { useQuery, useQueryClient } from "@tanstack/react-query";
import { supabase } from "@/integrations/supabase/client";
import { useEffect } from "react";

interface MessageSender {
  id: string;
  full_name: string;
  username: string;
  avatar_url: string | null;
}

interface Message {
  id: string;
  content: string;
  conversation_id: string;
  sender_id: string;
  created_at: string;
  sender: MessageSender | null;
}

export const useMessages = (conversationId: string | null) => {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["messages", conversationId],
    queryFn: async () => {
      if (!conversationId) return [];

      const { data, error } = await (supabase as any)
        .from("messages")
        .select(`
          *,
          sender:profiles!messages_sender_id_fkey (
            id,
            full_name,
            username,
            avatar_url
          )
        `)
        .eq("conversation_id", conversationId)
        .order("created_at", { ascending: true });

      if (error) throw error;
      return data as Message[];
    },
    enabled: !!conversationId,
  });

  // Subscribe to realtime updates
  useEffect(() => {
    if (!conversationId) return;

    const channel = supabase
      .channel(`messages:${conversationId}`)
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "public",
          table: "messages",
          filter: `conversation_id=eq.${conversationId}`,
        },
        async (payload) => {
          const newMessage = payload.new as Message;
          
          // Fetch the sender profile for the new message
          const { data: senderData } = await (supabase as any)
            .from("profiles")
            .select("id, full_name, username, avatar_url")
            .eq("user_id", newMessage.sender_id)
            .maybeSingle();

          // Update the cache with the new message including sender info
          queryClient.setQueryData<Message[]>(
            ["messages", conversationId],
            (oldMessages) => {
              if (!oldMessages) return [{ ...newMessage, sender: senderData }];
              
              // Check if message already exists to prevent duplicates
              const exists = oldMessages.some((m) => m.id === newMessage.id);
              if (exists) return oldMessages;
              
              return [...oldMessages, { ...newMessage, sender: senderData }];
            }
          );
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [conversationId, queryClient]);

  return query;
};
