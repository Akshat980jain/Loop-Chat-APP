import { useState, useEffect } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useToast } from "@/hooks/use-toast";

type Contact = {
  id: string;
  user_id: string;
  contact_user_id: string;
  nickname: string | null;
  created_at: string;
  profile?: {
    full_name: string;
    username: string;
    avatar_url: string | null;
    status: string | null;
  };
};

export const useContacts = () => {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(true);
  const { toast } = useToast();

  const fetchContacts = async () => {
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      const { data, error } = await supabase
        .from("contacts")
        .select("*")
        .eq("user_id", user.id)
        .order("created_at", { ascending: false });

      if (error) throw error;

      // Fetch profiles for each contact
      const contactsWithProfiles = await Promise.all(
        (data || []).map(async (contact) => {
          const { data: profile } = await supabase
            .from("profiles")
            .select("full_name, username, avatar_url, status")
            .eq("user_id", contact.contact_user_id)
            .single();
          
          return { ...contact, profile: profile || undefined };
        })
      );

      setContacts(contactsWithProfiles);
    } catch (error) {
      console.error("Error fetching contacts:", error);
    } finally {
      setLoading(false);
    }
  };

  const addContact = async (contactUserId: string, nickname?: string) => {
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) throw new Error("Not authenticated");

      const { error } = await supabase
        .from("contacts")
        .insert({
          user_id: user.id,
          contact_user_id: contactUserId,
          nickname: nickname || null,
        });

      if (error) {
        if (error.code === "23505") {
          toast({
            title: "Already added",
            description: "This user is already in your contacts",
          });
          return false;
        }
        throw error;
      }

      toast({
        title: "Contact added",
        description: "User has been added to your contacts",
      });

      await fetchContacts();
      return true;
    } catch (error) {
      console.error("Error adding contact:", error);
      toast({
        title: "Error",
        description: "Failed to add contact",
        variant: "destructive",
      });
      return false;
    }
  };

  const removeContact = async (contactId: string) => {
    try {
      const { error } = await supabase
        .from("contacts")
        .delete()
        .eq("id", contactId);

      if (error) throw error;

      toast({
        title: "Contact removed",
        description: "User has been removed from your contacts",
      });

      setContacts((prev) => prev.filter((c) => c.id !== contactId));
      return true;
    } catch (error) {
      console.error("Error removing contact:", error);
      toast({
        title: "Error",
        description: "Failed to remove contact",
        variant: "destructive",
      });
      return false;
    }
  };

  const updateNickname = async (contactId: string, nickname: string) => {
    try {
      const { error } = await supabase
        .from("contacts")
        .update({ nickname })
        .eq("id", contactId);

      if (error) throw error;

      setContacts((prev) =>
        prev.map((c) => (c.id === contactId ? { ...c, nickname } : c))
      );
      return true;
    } catch (error) {
      console.error("Error updating nickname:", error);
      return false;
    }
  };

  useEffect(() => {
    fetchContacts();
  }, []);

  return {
    contacts,
    loading,
    addContact,
    removeContact,
    updateNickname,
    refetch: fetchContacts,
  };
};
