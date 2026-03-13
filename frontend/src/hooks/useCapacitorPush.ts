import { useEffect, useState, useCallback } from 'react';
import { Capacitor } from '@capacitor/core';
import { supabase } from '@/integrations/supabase/client';

// Lazy load the push notifications plugin only on native platforms
let PushNotifications: typeof import('@capacitor/push-notifications').PushNotifications | null = null;

export const useCapacitorPush = () => {
  const [token, setToken] = useState<string | null>(null);
  const [isRegistered, setIsRegistered] = useState(false);
  const [isNative, setIsNative] = useState(false);

  useEffect(() => {
    const isNativePlatform = Capacitor.isNativePlatform();
    setIsNative(isNativePlatform);

    if (isNativePlatform) {
      // Dynamically import the push notifications plugin
      import('@capacitor/push-notifications').then((module) => {
        PushNotifications = module.PushNotifications;
        setupPushNotifications();
      }).catch((error) => {
        console.error('Error loading push notifications plugin:', error);
      });
    }
  }, []);

  const setupPushNotifications = async () => {
    if (!PushNotifications) return;

    try {
      // Request permission
      const permResult = await PushNotifications.requestPermissions();
      
      if (permResult.receive === 'granted') {
        // Register for push notifications
        await PushNotifications.register();
      }

      // Listen for registration success
      PushNotifications.addListener('registration', async (token) => {
        console.log('Push registration success, token:', token.value);
        setToken(token.value);
        setIsRegistered(true);

        // Save token to database
        await saveTokenToDatabase(token.value);
      });

      // Listen for registration errors
      PushNotifications.addListener('registrationError', (error) => {
        console.error('Push registration error:', error);
      });

      // Listen for incoming push notifications
      PushNotifications.addListener('pushNotificationReceived', (notification) => {
        console.log('Push notification received:', notification);
        
        // Handle incoming call notification
        if (notification.data?.type === 'incoming_call') {
          // Dispatch custom event for the app to handle
          window.dispatchEvent(new CustomEvent('incomingCallPush', {
            detail: notification.data
          }));
        }
      });

      // Listen for notification actions
      PushNotifications.addListener('pushNotificationActionPerformed', (notification) => {
        console.log('Push notification action performed:', notification);
        
        const { actionId, notification: notif } = notification;
        
        if (notif.data?.callId) {
          if (actionId === 'accept') {
            window.dispatchEvent(new CustomEvent('callAccepted', {
              detail: { callId: notif.data.callId }
            }));
          } else if (actionId === 'reject') {
            window.dispatchEvent(new CustomEvent('callRejected', {
              detail: { callId: notif.data.callId }
            }));
          }
        }
      });

    } catch (error) {
      console.error('Error setting up push notifications:', error);
    }
  };

  const saveTokenToDatabase = async (pushToken: string) => {
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      // For native push, we store the FCM/APNs token differently
      // Using a special endpoint format for native tokens
      const endpoint = `native://${Capacitor.getPlatform()}/${pushToken}`;

      const { data: existing } = await supabase
        .from('push_subscriptions')
        .select('id')
        .eq('user_id', user.id)
        .eq('endpoint', endpoint)
        .single();

      if (existing) {
        await supabase
          .from('push_subscriptions')
          .update({ updated_at: new Date().toISOString() })
          .eq('id', existing.id);
      } else {
        await supabase
          .from('push_subscriptions')
          .insert({
            user_id: user.id,
            endpoint: endpoint,
            p256dh: 'native',
            auth: 'native'
          });
      }

      console.log('Native push token saved to database');
    } catch (error) {
      console.error('Error saving push token:', error);
    }
  };

  const register = useCallback(async () => {
    if (!PushNotifications) return false;

    try {
      const permResult = await PushNotifications.requestPermissions();
      
      if (permResult.receive === 'granted') {
        await PushNotifications.register();
        return true;
      }
      
      return false;
    } catch (error) {
      console.error('Error registering for push:', error);
      return false;
    }
  }, []);

  return {
    token,
    isRegistered,
    isNative,
    register
  };
};
