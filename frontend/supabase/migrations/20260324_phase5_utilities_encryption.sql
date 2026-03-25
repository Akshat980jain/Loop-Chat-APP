-- ============================================
-- Phase 5: Utilities & Encryption
-- Database Schema Migration
-- ============================================

-- ============================================
-- 1. SCHEDULED MESSAGES
-- ============================================
DROP TABLE IF EXISTS scheduled_messages CASCADE;

CREATE TABLE scheduled_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    message_type VARCHAR(50) DEFAULT 'text',
    media_url TEXT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) DEFAULT 'pending', -- pending, sent, cancelled
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scheduled_sender ON scheduled_messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_pending ON scheduled_messages(status, scheduled_at)
    WHERE status = 'pending';

ALTER TABLE scheduled_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own scheduled messages"
    ON scheduled_messages FOR ALL
    USING (auth.uid() = sender_id)
    WITH CHECK (auth.uid() = sender_id);

-- ============================================
-- Function to send due scheduled messages
-- Moves rows from scheduled_messages -> messages
-- ============================================
CREATE OR REPLACE FUNCTION send_scheduled_messages()
RETURNS void AS $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT * FROM scheduled_messages
        WHERE status = 'pending' AND scheduled_at <= NOW()
    LOOP
        INSERT INTO messages (id, conversation_id, sender_id, content, message_type, media_url, created_at)
        VALUES (uuid_generate_v4(), rec.conversation_id, rec.sender_id, rec.content, rec.message_type, rec.media_url, NOW());

        UPDATE scheduled_messages SET status = 'sent' WHERE id = rec.id;

        UPDATE conversations SET last_message = rec.content, updated_at = NOW()
        WHERE id = rec.conversation_id;
    END LOOP;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Schedule cron job to run every minute (requires pg_cron)
SELECT cron.schedule('send_scheduled_messages_cron', '* * * * *', 'SELECT send_scheduled_messages();');

-- ============================================
-- 2. PUBLIC KEYS TABLE (E2EE Key Exchange)
-- ============================================
DROP TABLE IF EXISTS public_keys CASCADE;

CREATE TABLE public_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL,
    public_key TEXT NOT NULL, -- Base64-encoded RSA public key
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_public_keys_user ON public_keys(user_id);

ALTER TABLE public_keys ENABLE ROW LEVEL SECURITY;

-- Anyone can read public keys (needed for encryption)
CREATE POLICY "Anyone can read public keys"
    ON public_keys FOR SELECT
    USING (true);

-- Users can only manage their own keys
CREATE POLICY "Users can insert their own public keys"
    ON public_keys FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own public keys"
    ON public_keys FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete their own public keys"
    ON public_keys FOR DELETE
    USING (auth.uid() = user_id);

-- ============================================
-- 3. Add phone column to profiles (for contact sync)
-- ============================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'profiles' AND column_name = 'phone'
    ) THEN
        ALTER TABLE profiles ADD COLUMN phone TEXT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_profiles_phone ON profiles(phone) WHERE phone IS NOT NULL;
