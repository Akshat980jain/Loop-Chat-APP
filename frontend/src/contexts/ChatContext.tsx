import { createContext, useContext, useState, ReactNode } from "react";

type ChatContextType = {
  selectedConversationId: string | null;
  setSelectedConversationId: (id: string | null) => void;
  isGuest: boolean;
};

const ChatContext = createContext<ChatContextType | undefined>(undefined);

export const ChatProvider = ({ children, isGuest = false }: { children: ReactNode; isGuest?: boolean }) => {
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);

  return (
    <ChatContext.Provider value={{ selectedConversationId, setSelectedConversationId, isGuest }}>
      {children}
    </ChatContext.Provider>
  );
};

export const useChatContext = () => {
  const context = useContext(ChatContext);
  if (!context) {
    throw new Error("useChatContext must be used within ChatProvider");
  }
  return context;
};
