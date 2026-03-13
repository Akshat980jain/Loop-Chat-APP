import { useState, useCallback, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { useServiceWorker } from './useServiceWorker';

// VAPID public key - should match the one in the edge function
const VAPID_PUBLIC_KEY = 'BFBTz5hBKlOvDrOqH-P6eGVNIJ8JBzxMWqAKFMCvcGZxJBVLk8k8aWpOPOZXFxnFbhpAhqWwXnFqUZvMGvJVJRo';

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64 = (base64String + padding)
    .replace(/-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

export const useWebPushSubscription = () => {
  const { registration, register: registerSW, isSupported: swSupported } = useServiceWorker();
  const [subscription, setSubscription] = useState<PushSubscription | null>(null);
  const [isSubscribed, setIsSubscribed] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const isPushSupported = swSupported && 'PushManager' in window;

  // Check if already subscribed
  useEffect(() => {
    const checkSubscription = async () => {
      if (!registration) return;

      try {
        const sub = await registration.pushManager.getSubscription();
        if (sub) {
          setSubscription(sub);
          setIsSubscribed(true);
        }
      } catch (error) {
        console.error('Error checking push subscription:', error);
      }
    };

    checkSubscription();
  }, [registration]);

  // Subscribe to push notifications
  const subscribe = useCallback(async (): Promise<boolean> => {
    if (!isPushSupported) {
      console.log('Push notifications not supported');
      return false;
    }

    setIsLoading(true);

    try {
      // Ensure service worker is registered
      let swReg = registration;
      if (!swReg) {
        swReg = await registerSW();
        if (!swReg) {
          throw new Error('Failed to register service worker');
        }
      }

      // Request notification permission
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        console.log('Notification permission denied');
        return false;
      }

      // Subscribe to push manager
      const applicationServerKey = urlBase64ToUint8Array(VAPID_PUBLIC_KEY);
      const sub = await swReg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: applicationServerKey.buffer as ArrayBuffer
      });

      // Save subscription to database
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) {
        throw new Error('User not authenticated');
      }

      const subscriptionJson = sub.toJSON();
      const keys = subscriptionJson.keys as { p256dh: string; auth: string };

      // Check if subscription already exists
      const { data: existing } = await supabase
        .from('push_subscriptions')
        .select('id')
        .eq('user_id', user.id)
        .eq('endpoint', sub.endpoint)
        .single();

      if (existing) {
        // Update existing subscription
        await supabase
          .from('push_subscriptions')
          .update({
            p256dh: keys.p256dh,
            auth: keys.auth,
            updated_at: new Date().toISOString()
          })
          .eq('id', existing.id);
      } else {
        // Insert new subscription
        await supabase
          .from('push_subscriptions')
          .insert({
            user_id: user.id,
            endpoint: sub.endpoint,
            p256dh: keys.p256dh,
            auth: keys.auth
          });
      }

      setSubscription(sub);
      setIsSubscribed(true);
      console.log('Push subscription saved to database');

      return true;
    } catch (error) {
      console.error('Error subscribing to push:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  }, [isPushSupported, registration, registerSW]);

  // Unsubscribe from push notifications
  const unsubscribe = useCallback(async (): Promise<boolean> => {
    if (!subscription) return false;

    setIsLoading(true);

    try {
      await subscription.unsubscribe();

      // Remove from database
      const { data: { user } } = await supabase.auth.getUser();
      if (user) {
        await supabase
          .from('push_subscriptions')
          .delete()
          .eq('user_id', user.id)
          .eq('endpoint', subscription.endpoint);
      }

      setSubscription(null);
      setIsSubscribed(false);
      console.log('Push subscription removed');

      return true;
    } catch (error) {
      console.error('Error unsubscribing from push:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  }, [subscription]);

  return {
    subscription,
    isSubscribed,
    isLoading,
    isPushSupported,
    subscribe,
    unsubscribe
  };
};
