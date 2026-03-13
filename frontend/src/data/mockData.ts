export const mockUsers = [
  {
    id: "1",
    full_name: "Jane Cooper",
    username: "jane_cooper",
    avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jane",
    status: "online",
  },
  {
    id: "2",
    full_name: "Jenny Wilson",
    username: "jenny_wilson",
    avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jenny",
    status: "online",
  },
  {
    id: "3",
    full_name: "Bessie Cooper",
    username: "bessie_cooper",
    avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Bessie",
    status: "offline",
  },
  {
    id: "4",
    full_name: "Guy Hawkins",
    username: "guy_hawkins",
    avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Guy",
    status: "offline",
  },
  {
    id: "5",
    full_name: "Ralph Edwards",
    username: "ralph_edwards",
    avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Ralph",
    status: "online",
  },
];

export const mockConversations = [
  {
    id: "conv-1",
    updated_at: new Date(Date.now() - 1000 * 60 * 30).toISOString(), // 30 min ago
    participant: mockUsers[0],
  },
  {
    id: "conv-2",
    updated_at: new Date(Date.now() - 1000 * 60 * 60).toISOString(), // 1 hour ago
    participant: mockUsers[1],
  },
  {
    id: "conv-3",
    updated_at: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(), // 2 hours ago
    participant: mockUsers[2],
  },
  {
    id: "conv-4",
    updated_at: new Date(Date.now() - 1000 * 60 * 60 * 5).toISOString(), // 5 hours ago
    participant: mockUsers[3],
  },
  {
    id: "conv-5",
    updated_at: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(), // 1 day ago
    participant: mockUsers[4],
  },
];

export const mockMessages: Record<string, any[]> = {
  "conv-1": [
    {
      id: "msg-1",
      conversation_id: "conv-1",
      sender_id: "1",
      content: "Hello! How are you doing today?",
      created_at: new Date(Date.now() - 1000 * 60 * 35).toISOString(),
      sender: mockUsers[0],
    },
    {
      id: "msg-2",
      conversation_id: "conv-1",
      sender_id: "guest",
      content: "Hi! I'm doing great, thanks for asking!",
      created_at: new Date(Date.now() - 1000 * 60 * 32).toISOString(),
      sender: {
        id: "guest",
        full_name: "Guest User",
        username: "guest",
        avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=guest",
      },
    },
    {
      id: "msg-3",
      conversation_id: "conv-1",
      sender_id: "1",
      content: "That's wonderful! Don't forget to check out all the features in the app.",
      created_at: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
      sender: mockUsers[0],
    },
  ],
  "conv-2": [
    {
      id: "msg-4",
      conversation_id: "conv-2",
      sender_id: "2",
      content: "Hi there! Nice to meet you 👋",
      created_at: new Date(Date.now() - 1000 * 60 * 70).toISOString(),
      sender: mockUsers[1],
    },
    {
      id: "msg-5",
      conversation_id: "conv-2",
      sender_id: "guest",
      content: "Nice to meet you too!",
      created_at: new Date(Date.now() - 1000 * 60 * 65).toISOString(),
      sender: {
        id: "guest",
        full_name: "Guest User",
        username: "guest",
        avatar_url: "https://api.dicebear.com/7.x/avataaars/svg?seed=guest",
      },
    },
    {
      id: "msg-6",
      conversation_id: "conv-2",
      sender_id: "2",
      content: "This is a sample conversation to show how the chat works!",
      created_at: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
      sender: mockUsers[1],
    },
  ],
  "conv-3": [
    {
      id: "msg-7",
      conversation_id: "conv-3",
      sender_id: "3",
      content: "How are you, my friend?",
      created_at: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
      sender: mockUsers[2],
    },
  ],
  "conv-4": [
    {
      id: "msg-8",
      conversation_id: "conv-4",
      sender_id: "4",
      content: "Where are you right now?",
      created_at: new Date(Date.now() - 1000 * 60 * 60 * 5).toISOString(),
      sender: mockUsers[3],
    },
  ],
  "conv-5": [
    {
      id: "msg-9",
      conversation_id: "conv-5",
      sender_id: "5",
      content: "Hello, I'm looking forward to chatting with you!",
      created_at: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
      sender: mockUsers[4],
    },
  ],
};
