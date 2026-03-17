-- Migration for User Presence & Discovery (Phase 1.2)

-- Ensure presence_status enum exists
DO $$ BEGIN
    CREATE TYPE presence_status_enum AS ENUM ('offline', 'online', 'away', 'dnd');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Update profiles table to support rich presence
ALTER TABLE public.profiles
ADD COLUMN IF NOT EXISTS "presence_status" presence_status_enum DEFAULT 'offline',
ADD COLUMN IF NOT EXISTS "is_online" BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS "last_seen" TIMESTAMPTZ DEFAULT NOW();

-- Create an index to quickly find online users (for discovery)
CREATE INDEX IF NOT EXISTS idx_profiles_presence ON public.profiles ("presence_status", "last_seen");
