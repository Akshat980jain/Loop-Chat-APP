-- Auth rate limits table for brute-force protection
CREATE TABLE public.auth_rate_limits (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  key TEXT NOT NULL,
  endpoint TEXT NOT NULL,
  attempt_count INT DEFAULT 1,
  window_start TIMESTAMPTZ DEFAULT NOW(),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- No user-facing RLS needed — only accessed by Edge Functions with service role
ALTER TABLE public.auth_rate_limits ENABLE ROW LEVEL SECURITY;

-- Unique constraint for upsert operations
CREATE UNIQUE INDEX idx_rate_limits_key_endpoint ON public.auth_rate_limits(key, endpoint);

-- Cleanup function: remove entries older than 1 hour
CREATE OR REPLACE FUNCTION cleanup_expired_rate_limits()
RETURNS void AS $$
BEGIN
  DELETE FROM public.auth_rate_limits
  WHERE window_start < NOW() - INTERVAL '1 hour';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
