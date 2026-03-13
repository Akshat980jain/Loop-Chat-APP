-- Enable realtime for calls table so incoming calls are detected on other devices
ALTER PUBLICATION supabase_realtime ADD TABLE public.calls;