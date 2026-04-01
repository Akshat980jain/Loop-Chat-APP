-- ============================================
-- PASSKEY (WebAuthn) SUPPORT
-- Run this in Supabase SQL Editor
-- ============================================
-- Stores the public keys for Passkey-based biometric login.
-- No biometric data is stored — only cryptographic verification keys.

-- 1. Passkey credentials table
CREATE TABLE IF NOT EXISTS public.user_passkeys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    credential_id TEXT NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    counter INTEGER DEFAULT 0,
    transports TEXT[] DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    device_name TEXT DEFAULT 'Unknown Device'
);

-- 2. Challenges table (short-lived, for WebAuthn request/response flow)
CREATE TABLE IF NOT EXISTS public.passkey_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    challenge TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('registration', 'authentication')),
    created_at TIMESTAMPTZ DEFAULT now(),
    expires_at TIMESTAMPTZ DEFAULT (now() + interval '2 minutes')
);

-- 3. Index for fast lookup
CREATE INDEX IF NOT EXISTS idx_user_passkeys_user_id ON public.user_passkeys(user_id);
CREATE INDEX IF NOT EXISTS idx_user_passkeys_credential_id ON public.user_passkeys(credential_id);
CREATE INDEX IF NOT EXISTS idx_passkey_challenges_user_id ON public.passkey_challenges(user_id);

-- 4. RLS Policies
ALTER TABLE public.user_passkeys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.passkey_challenges ENABLE ROW LEVEL SECURITY;

-- Users can read/delete their own passkeys
CREATE POLICY "Users can view own passkeys"
    ON public.user_passkeys FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own passkeys"
    ON public.user_passkeys FOR DELETE
    USING (auth.uid() = user_id);

-- Insert is done via service_role from edge functions only
-- (no direct INSERT policy for users — prevents spoofing)

-- Challenges are managed by service_role edge functions only
-- Users need SELECT to verify their own challenge exists
CREATE POLICY "Users can view own challenges"
    ON public.passkey_challenges FOR SELECT
    USING (auth.uid() = user_id);

-- 5. Auto-cleanup expired challenges (optional cron or trigger)
-- For now, edge functions will clean up before creating new ones.

-- 6. Verify
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'user_passkeys'
ORDER BY ordinal_position;
