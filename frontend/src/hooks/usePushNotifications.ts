import { useEffect, useCallback, useRef } from 'react';
import { supabase } from '@/integrations/supabase/client';

interface PushNotificationOptions {
  onIncomingCall?: (callerName: string, callType: string) => void;
}

export const usePushNotifications = (options: PushNotificationOptions = {}) => {
  const { onIncomingCall } = options;
  const notificationRef = useRef<Notification | null>(null);

  // Request notification permission
  const requestPermission = useCallback(async (): Promise<boolean> => {
    if (!('Notification' in window)) {
      console.log('This browser does not support notifications');
      return false;
    }

    if (Notification.permission === 'granted') {
      return true;
    }

    if (Notification.permission !== 'denied') {
      const permission = await Notification.requestPermission();
      return permission === 'granted';
    }

    return false;
  }, []);

  // Show a notification
  const showNotification = useCallback(async (
    title: string,
    body: string,
    options?: {
      icon?: string;
      tag?: string;
      requireInteraction?: boolean;
      vibrate?: number[];
      actions?: { action: string; title: string }[];
    }
  ): Promise<Notification | null> => {
    const hasPermission = await requestPermission();
    
    if (!hasPermission) {
      console.log('Notification permission not granted');
      return null;
    }

    try {
      // Close any existing notification with the same tag
      if (notificationRef.current && options?.tag) {
        notificationRef.current.close();
      }

      const notification = new Notification(title, {
        body,
        icon: options?.icon || '/favicon.ico',
        tag: options?.tag,
        requireInteraction: options?.requireInteraction ?? false,
      });

      notification.onclick = () => {
        window.focus();
        notification.close();
      };

      notificationRef.current = notification;
      return notification;
    } catch (error) {
      console.error('Error showing notification:', error);
      return null;
    }
  }, [requestPermission]);

  // Show incoming call notification
  const showIncomingCallNotification = useCallback(async (
    callerName: string,
    callerAvatar?: string,
    callType: 'audio' | 'video' = 'audio'
  ) => {
    const icon = callerAvatar || '/favicon.ico';
    const title = `Incoming ${callType === 'video' ? 'Video' : 'Voice'} Call`;
    const body = `${callerName} is calling...`;

    const notification = await showNotification(title, body, {
      icon,
      tag: 'incoming-call',
      requireInteraction: true,
      vibrate: [300, 100, 300, 100, 300],
    });

    if (notification && onIncomingCall) {
      notification.onclick = () => {
        onIncomingCall(callerName, callType);
        notification.close();
      };
    }

    return notification;
  }, [showNotification, onIncomingCall]);

  // Close incoming call notification
  const closeIncomingCallNotification = useCallback(() => {
    if (notificationRef.current) {
      notificationRef.current.close();
      notificationRef.current = null;
    }
  }, []);

  // Listen for incoming calls when app is in background
  useEffect(() => {
    let cleanup: (() => void) | undefined;

    const setupCallListener = async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      const channel = supabase
        .channel('push-notification-calls')
        .on(
          'postgres_changes',
          {
            event: 'INSERT',
            schema: 'public',
            table: 'calls',
            filter: `callee_id=eq.${user.id}`,
          },
          async (payload) => {
            const call = payload.new as {
              caller_id: string;
              call_type: string;
              status: string;
            };

            if (call.status === 'ringing' && document.hidden) {
              // App is in background, show notification
              const { data: profile } = await supabase
                .from('profiles')
                .select('full_name, avatar_url')
                .eq('user_id', call.caller_id)
                .single();

              if (profile) {
                await showIncomingCallNotification(
                  profile.full_name,
                  profile.avatar_url || undefined,
                  call.call_type as 'audio' | 'video'
                );
              }
            }
          }
        )
        .on(
          'postgres_changes',
          {
            event: 'UPDATE',
            schema: 'public',
            table: 'calls',
          },
          (payload) => {
            const call = payload.new as { status: string };
            // Close notification if call ended or was rejected
            if (['ended', 'rejected', 'cancelled', 'connected'].includes(call.status)) {
              closeIncomingCallNotification();
            }
          }
        )
        .subscribe();

      cleanup = () => {
        supabase.removeChannel(channel);
      };
    };

    // Request permission on mount
    requestPermission();
    setupCallListener();

    return () => {
      cleanup?.();
    };
  }, [requestPermission, showIncomingCallNotification, closeIncomingCallNotification]);

  return {
    requestPermission,
    showNotification,
    showIncomingCallNotification,
    closeIncomingCallNotification,
    isSupported: 'Notification' in window,
    permission: 'Notification' in window ? Notification.permission : 'denied',
  };
};