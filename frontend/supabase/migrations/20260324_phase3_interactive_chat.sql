-- ============================================
-- Phase 3: Interactive Chat Features
-- Database Schema Migration (Polls & Vanish Mode)
-- ============================================

-- ============================================
-- ============================================
-- 1. POLLS
-- ============================================

-- Drop existing tables to ensure a clean schema deployment (since there was a schema mismatch)
DROP TABLE IF EXISTS poll_votes CASCADE;
DROP TABLE IF EXISTS poll_options CASCADE;
DROP TABLE IF EXISTS polls CASCADE;

CREATE TABLE IF NOT EXISTS polls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    multiple_answers BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(message_id)
);

-- ============================================
-- 2. POLL OPTIONS
-- ============================================

CREATE TABLE IF NOT EXISTS poll_options (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- 3. POLL VOTES
-- ============================================

CREATE TABLE IF NOT EXISTS poll_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    option_id UUID NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(option_id, user_id)
);

-- ============================================
-- 4. VANISH MODE (DISAPPEARING MESSAGES)
-- ============================================

-- Add expires_at column to messages table
ALTER TABLE messages ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Index for efficient cleanup
CREATE INDEX IF NOT EXISTS idx_messages_expires_at ON messages(expires_at) WHERE expires_at IS NOT NULL;

-- Enable pg_cron extension if not exists
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Create function to delete expired messages
CREATE OR REPLACE FUNCTION delete_expired_messages() RETURNS void AS $$
BEGIN
    DELETE FROM messages WHERE expires_at IS NOT NULL AND expires_at < NOW();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Schedule the cleanup job to run every minute
SELECT cron.schedule(
    'vanish-mode-message-cleanup',
    '* * * * *',
    'SELECT delete_expired_messages();'
);

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================

CREATE INDEX IF NOT EXISTS idx_polls_message ON polls(message_id);
CREATE INDEX IF NOT EXISTS idx_poll_options_poll ON poll_options(poll_id);
CREATE INDEX IF NOT EXISTS idx_poll_options_order ON poll_options(poll_id, order_index);
CREATE INDEX IF NOT EXISTS idx_poll_votes_option ON poll_votes(option_id);
CREATE INDEX IF NOT EXISTS idx_poll_votes_user ON poll_votes(user_id);

-- ============================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================

-- Enable RLS
ALTER TABLE polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE poll_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE poll_votes ENABLE ROW LEVEL SECURITY;

-- Polls policies
CREATE POLICY "Users can view polls in their conversations"
    ON polls FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM messages m
            JOIN conversation_participants cp ON m.conversation_id = cp.conversation_id
            WHERE m.id = polls.message_id
            AND cp.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can insert polls in their conversations"
    ON polls FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM messages m
            JOIN conversation_participants cp ON m.conversation_id = cp.conversation_id
            WHERE m.id = polls.message_id
            AND cp.user_id = auth.uid()
        )
    );

-- Poll options policies
CREATE POLICY "Users can view poll options in their conversations"
    ON poll_options FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM polls p
            JOIN messages m ON p.message_id = m.id
            JOIN conversation_participants cp ON m.conversation_id = cp.conversation_id
            WHERE p.id = poll_options.poll_id
            AND cp.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can insert poll options for their polls"
    ON poll_options FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM polls p
            JOIN messages m ON p.message_id = m.id
            WHERE p.id = poll_options.poll_id
            AND m.sender_id = auth.uid()
        )
    );

-- Poll votes policies
CREATE POLICY "Users can view votes in their conversations"
    ON poll_votes FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM poll_options po
            JOIN polls p ON po.poll_id = p.id
            JOIN messages m ON p.message_id = m.id
            JOIN conversation_participants cp ON m.conversation_id = cp.conversation_id
            WHERE po.id = poll_votes.option_id
            AND cp.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can vote on polls in their conversations"
    ON poll_votes FOR INSERT
    WITH CHECK (
        auth.uid() = user_id AND
        EXISTS (
            SELECT 1 FROM poll_options po
            JOIN polls p ON po.poll_id = p.id
            JOIN messages m ON p.message_id = m.id
            JOIN conversation_participants cp ON m.conversation_id = cp.conversation_id
            WHERE po.id = poll_votes.option_id
            AND cp.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can remove their own votes"
    ON poll_votes FOR DELETE
    USING (auth.uid() = user_id);
