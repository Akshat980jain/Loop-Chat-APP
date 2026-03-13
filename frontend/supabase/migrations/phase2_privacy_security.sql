-- ============================================
-- Phase 2: Privacy, Security & Organization
-- Database Schema Migration
-- ============================================

-- ============================================
-- 1. PRIVACY SETTINGS
-- ============================================

-- User privacy settings
CREATE TABLE IF NOT EXISTS privacy_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    
    -- Last seen privacy
    last_seen_visibility TEXT NOT NULL DEFAULT 'everyone' CHECK (last_seen_visibility IN ('everyone', 'contacts', 'nobody')),
    
    -- Profile photo privacy
    profile_photo_visibility TEXT NOT NULL DEFAULT 'everyone' CHECK (profile_photo_visibility IN ('everyone', 'contacts', 'nobody')),
    
    -- About/bio privacy
    about_visibility TEXT NOT NULL DEFAULT 'everyone' CHECK (about_visibility IN ('everyone', 'contacts', 'nobody')),
    
    -- Status/story privacy
    status_visibility TEXT NOT NULL DEFAULT 'everyone' CHECK (status_visibility IN ('everyone', 'contacts', 'selected')),
    status_excluded_users UUID[] DEFAULT '{}',
    
    -- Read receipts
    read_receipts_enabled BOOLEAN DEFAULT true,
    
    -- Online status
    show_online_status BOOLEAN DEFAULT true,
    
    -- Groups
    who_can_add_to_groups TEXT NOT NULL DEFAULT 'everyone' CHECK (who_can_add_to_groups IN ('everyone', 'contacts')),
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(user_id)
);

-- ============================================
-- 2. BLOCKED USERS
-- ============================================

CREATE TABLE IF NOT EXISTS blocked_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    blocker_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    blocked_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(blocker_id, blocked_id),
    CHECK (blocker_id != blocked_id)
);

-- ============================================
-- 3. ARCHIVED CHATS
-- ============================================

CREATE TABLE IF NOT EXISTS archived_conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    archived_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(user_id, conversation_id)
);

-- ============================================
-- 4. PINNED CHATS
-- ============================================

CREATE TABLE IF NOT EXISTS pinned_conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    pin_order INTEGER NOT NULL DEFAULT 0,
    pinned_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(user_id, conversation_id)
);

-- ============================================
-- 5. MUTED CONVERSATIONS
-- ============================================

CREATE TABLE IF NOT EXISTS muted_conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    muted_until TIMESTAMPTZ, -- NULL means muted forever
    muted_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(user_id, conversation_id)
);

-- ============================================
-- 6. DISAPPEARING MESSAGES SETTINGS
-- ============================================

CREATE TABLE IF NOT EXISTS disappearing_message_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    enabled BOOLEAN DEFAULT false,
    duration_seconds INTEGER, -- NULL means disabled, 86400 = 24h, 604800 = 7d, 7776000 = 90d
    enabled_by UUID REFERENCES auth.users(id),
    enabled_at TIMESTAMPTZ,
    
    UNIQUE(conversation_id)
);

-- ============================================
-- 7. MESSAGE SEARCH INDEX
-- ============================================

-- Add full-text search column to messages
ALTER TABLE messages ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Create index for full-text search
CREATE INDEX IF NOT EXISTS messages_search_idx ON messages USING GIN(search_vector);

-- Function to update search vector
CREATE OR REPLACE FUNCTION messages_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', COALESCE(NEW.content, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update search vector
DROP TRIGGER IF EXISTS messages_search_vector_trigger ON messages;
CREATE TRIGGER messages_search_vector_trigger
    BEFORE INSERT OR UPDATE ON messages
    FOR EACH ROW
    EXECUTE FUNCTION messages_search_vector_update();

-- ============================================
-- 8. SECURITY SETTINGS
-- ============================================

CREATE TABLE IF NOT EXISTS security_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    
    -- Two-step verification
    two_step_enabled BOOLEAN DEFAULT false,
    two_step_pin_hash TEXT, -- Hashed PIN
    two_step_email TEXT,
    
    -- Biometric lock
    biometric_lock_enabled BOOLEAN DEFAULT false,
    
    -- Security notifications
    security_notifications_enabled BOOLEAN DEFAULT true,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(user_id)
);

-- ============================================
-- 9. DEVICE MANAGEMENT
-- ============================================

CREATE TABLE IF NOT EXISTS user_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_name TEXT NOT NULL,
    device_type TEXT NOT NULL, -- 'android', 'ios', 'web'
    device_token TEXT, -- FCM token
    last_active TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(user_id, device_token)
);

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================

-- Privacy settings
CREATE INDEX IF NOT EXISTS idx_privacy_settings_user ON privacy_settings(user_id);

-- Blocked users
CREATE INDEX IF NOT EXISTS idx_blocked_users_blocker ON blocked_users(blocker_id);
CREATE INDEX IF NOT EXISTS idx_blocked_users_blocked ON blocked_users(blocked_id);

-- Archived conversations
CREATE INDEX IF NOT EXISTS idx_archived_conversations_user ON archived_conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_archived_conversations_conv ON archived_conversations(conversation_id);

-- Pinned conversations
CREATE INDEX IF NOT EXISTS idx_pinned_conversations_user ON pinned_conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_pinned_conversations_order ON pinned_conversations(user_id, pin_order);

-- Muted conversations
CREATE INDEX IF NOT EXISTS idx_muted_conversations_user ON muted_conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_muted_conversations_conv ON muted_conversations(conversation_id);

-- Disappearing messages
CREATE INDEX IF NOT EXISTS idx_disappearing_settings_conv ON disappearing_message_settings(conversation_id);

-- Security settings
CREATE INDEX IF NOT EXISTS idx_security_settings_user ON security_settings(user_id);

-- User devices
CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_user_devices_active ON user_devices(user_id, last_active DESC);

-- ============================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================

-- Enable RLS on all tables
ALTER TABLE privacy_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE blocked_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE archived_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE pinned_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE muted_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE disappearing_message_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_devices ENABLE ROW LEVEL SECURITY;

-- Privacy settings policies
CREATE POLICY "Users can view own privacy settings"
    ON privacy_settings FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can update own privacy settings"
    ON privacy_settings FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own privacy settings"
    ON privacy_settings FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- Blocked users policies
CREATE POLICY "Users can view own blocks"
    ON blocked_users FOR SELECT
    USING (auth.uid() = blocker_id);

CREATE POLICY "Users can block others"
    ON blocked_users FOR INSERT
    WITH CHECK (auth.uid() = blocker_id);

CREATE POLICY "Users can unblock others"
    ON blocked_users FOR DELETE
    USING (auth.uid() = blocker_id);

-- Archived conversations policies
CREATE POLICY "Users can view own archives"
    ON archived_conversations FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can archive conversations"
    ON archived_conversations FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can unarchive conversations"
    ON archived_conversations FOR DELETE
    USING (auth.uid() = user_id);

-- Pinned conversations policies
CREATE POLICY "Users can view own pins"
    ON pinned_conversations FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can pin conversations"
    ON pinned_conversations FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update pin order"
    ON pinned_conversations FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can unpin conversations"
    ON pinned_conversations FOR DELETE
    USING (auth.uid() = user_id);

-- Muted conversations policies
CREATE POLICY "Users can view own mutes"
    ON muted_conversations FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can mute conversations"
    ON muted_conversations FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update mute duration"
    ON muted_conversations FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can unmute conversations"
    ON muted_conversations FOR DELETE
    USING (auth.uid() = user_id);

-- Disappearing messages policies
CREATE POLICY "Conversation participants can view settings"
    ON disappearing_message_settings FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM conversation_participants
            WHERE conversation_id = disappearing_message_settings.conversation_id
            AND user_id = auth.uid()
        )
    );

CREATE POLICY "Conversation participants can update settings"
    ON disappearing_message_settings FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM conversation_participants
            WHERE conversation_id = disappearing_message_settings.conversation_id
            AND user_id = auth.uid()
        )
    );

-- Security settings policies
CREATE POLICY "Users can view own security settings"
    ON security_settings FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can update own security settings"
    ON security_settings FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own security settings"
    ON security_settings FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- User devices policies
CREATE POLICY "Users can view own devices"
    ON user_devices FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can add devices"
    ON user_devices FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own devices"
    ON user_devices FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own devices"
    ON user_devices FOR DELETE
    USING (auth.uid() = user_id);

-- ============================================
-- HELPER FUNCTIONS
-- ============================================

-- Function to check if user is blocked
CREATE OR REPLACE FUNCTION is_user_blocked(checker_id UUID, target_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM blocked_users
        WHERE (blocker_id = checker_id AND blocked_id = target_id)
           OR (blocker_id = target_id AND blocked_id = checker_id)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to get user's privacy setting
CREATE OR REPLACE FUNCTION get_privacy_setting(target_user_id UUID, setting_name TEXT)
RETURNS TEXT AS $$
DECLARE
    setting_value TEXT;
BEGIN
    EXECUTE format('SELECT %I FROM privacy_settings WHERE user_id = $1', setting_name)
    INTO setting_value
    USING target_user_id;
    
    RETURN COALESCE(setting_value, 'everyone');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- Verify all tables exist
SELECT 
    'privacy_settings' as table_name,
    COUNT(*) as row_count
FROM privacy_settings
UNION ALL
SELECT 'blocked_users', COUNT(*) FROM blocked_users
UNION ALL
SELECT 'archived_conversations', COUNT(*) FROM archived_conversations
UNION ALL
SELECT 'pinned_conversations', COUNT(*) FROM pinned_conversations
UNION ALL
SELECT 'muted_conversations', COUNT(*) FROM muted_conversations
UNION ALL
SELECT 'disappearing_message_settings', COUNT(*) FROM disappearing_message_settings
UNION ALL
SELECT 'security_settings', COUNT(*) FROM security_settings
UNION ALL
SELECT 'user_devices', COUNT(*) FROM user_devices;

-- Verify RLS is enabled
SELECT 
    schemaname,
    tablename,
    rowsecurity
FROM pg_tables
WHERE tablename IN (
    'privacy_settings',
    'blocked_users',
    'archived_conversations',
    'pinned_conversations',
    'muted_conversations',
    'disappearing_message_settings',
    'security_settings',
    'user_devices'
)
ORDER BY tablename;

-- Verify indexes
SELECT 
    tablename,
    indexname
FROM pg_indexes
WHERE tablename IN (
    'privacy_settings',
    'blocked_users',
    'archived_conversations',
    'pinned_conversations',
    'muted_conversations',
    'disappearing_message_settings',
    'security_settings',
    'user_devices',
    'messages'
)
ORDER BY tablename, indexname;
