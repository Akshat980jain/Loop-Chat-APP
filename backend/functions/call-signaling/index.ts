// WebRTC Call Signaling
// This is a reference copy. The actual deployed function is in supabase/functions/
// 
// Note: Call signaling is handled via Supabase Realtime subscriptions directly
// from the client using the useCallSignaling hook. No edge function is needed
// as the signaling data (SDP offers/answers and ICE candidates) are stored
// in the call_signals table and synced in real-time between peers.
//
// Database tables used:
// - calls: Stores active call records (caller_id, callee_id, status, etc.)
// - call_signals: Stores WebRTC signaling data (SDP and ICE candidates)
//
// See: src/hooks/useCallSignaling.ts for the client-side implementation
