import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";

interface CallHistoryRecord {
  id: string;
  user_id: string;
  other_participant_name: string;
  other_participant_avatar?: string;
  call_type: "audio" | "video";
  duration: number;
  status: "completed" | "missed" | "rejected" | "ongoing";
  direction: "incoming" | "outgoing";
  created_at: string;
}

export const useCallHistory = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data: callHistory, isLoading } = useQuery({
    queryKey: ["call-history"],
    queryFn: async () => {
      const { data, error } = await supabase
        .from("call_history")
        .select("*")
        .order("created_at", { ascending: false });

      if (error) throw error;
      return data as CallHistoryRecord[];
    },
  });

  const createCallRecord = useMutation({
    mutationFn: async (callData: Omit<CallHistoryRecord, "id" | "user_id" | "created_at">) => {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) throw new Error("Not authenticated");

      const { data, error } = await supabase
        .from("call_history")
        .insert({
          ...callData,
          user_id: user.id,
        })
        .select()
        .single();

      if (error) throw error;
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["call-history"] });
    },
    onError: (error) => {
      toast({
        title: "Error",
        description: "Failed to save call record",
        variant: "destructive",
      });
      console.error("Call history error:", error);
    },
  });

  const updateCallRecord = useMutation({
    mutationFn: async ({ id, duration, status }: { id: string; duration: number; status: CallHistoryRecord["status"] }) => {
      const { data, error } = await supabase
        .from("call_history")
        .update({ duration, status })
        .eq("id", id)
        .select()
        .single();

      if (error) throw error;
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["call-history"] });
    },
  });

  return {
    callHistory,
    isLoading,
    createCallRecord: createCallRecord.mutate,
    updateCallRecord: updateCallRecord.mutate,
  };
};
