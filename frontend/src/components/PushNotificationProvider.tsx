import { useEffect } from 'react';
import { usePushNotifications } from '@/hooks/usePushNotifications';
import { useWebPushSubscription } from '@/hooks/useWebPushSubscription';
import { useCapacitorPush } from '@/hooks/useCapacitorPush';
import { useServiceWorker } from '@/hooks/useServiceWorker';
import { supabase } from '@/integrations/supabase/client';

export const PushNotificationProvider = () => {
  const { requestPermission, isSupported, permission } = usePushNotifications();
  const { subscribe: subscribeWebPush, isPushSupported, isSubscribed } = useWebPushSubscription();
  const { isNative, register: registerNativePush, isRegistered } = useCapacitorPush();
  const { register: registerServiceWorker, isSupported: swSupported } = useServiceWorker();

  // Register service worker on mount
  useEffect(() => {
    if (swSupported) {
      registerServiceWorker();
    }
  }, [swSupported, registerServiceWorker]);

  // Request notification permission and subscribe on mount
  useEffect(() => {
    const setupPushNotifications = async () => {
      // Check if user is logged in
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      // Delay the permission request slightly to not block initial render
      const timer = setTimeout(async () => {
        if (isNative && !isRegistered) {
          // Native mobile - use Capacitor push
          await registerNativePush();
        } else if (isPushSupported && !isSubscribed) {
          // Web - use Web Push
          await subscribeWebPush();
        } else if (isSupported && permission === 'default') {
          // Fallback to basic notifications
          await requestPermission();
        }
      }, 3000);

      return () => clearTimeout(timer);
    };

    setupPushNotifications();
  }, [isSupported, permission, requestPermission, isPushSupported, isSubscribed, subscribeWebPush, isNative, isRegistered, registerNativePush]);

  // Re-subscribe when user logs in
  useEffect(() => {
    const { data: { subscription } } = supabase.auth.onAuthStateChange(async (event, session) => {
      if (event === 'SIGNED_IN' && session) {
        // Small delay to ensure user profile is ready
        setTimeout(async () => {
          if (isNative && !isRegistered) {
            await registerNativePush();
          } else if (isPushSupported && !isSubscribed) {
            await subscribeWebPush();
          }
        }, 1000);
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [isNative, isRegistered, registerNativePush, isPushSupported, isSubscribed, subscribeWebPush]);

  // This component doesn't render anything visible
  return null;
};