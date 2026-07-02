import { useEffect, useCallback, useState } from 'react';

export const useServiceWorker = () => {
  const [registration, setRegistration] = useState<ServiceWorkerRegistration | null>(null);
  const [isSupported, setIsSupported] = useState(false);

  useEffect(() => {
    setIsSupported('serviceWorker' in navigator);
  }, []);

  const register = useCallback(async (): Promise<ServiceWorkerRegistration | null> => {
    if (!isSupported) {
      console.log('Service Workers not supported');
      return null;
    }

    try {
      const reg = await navigator.serviceWorker.register('/sw.js', {
        scope: '/'
      });

      console.log('Service Worker registered:', reg);
      setRegistration(reg);

      // Check for updates
      reg.addEventListener('updatefound', () => {
        console.log('Service Worker update found');
      });

      return reg;
    } catch (error) {
      if (window.location.hostname === 'localhost') {
        console.warn(
          'Service Worker registration failed: This is normal in local development if using a self-signed SSL certificate without browser trust. ' +
          'To enable Service Workers locally with HTTPS: \n' +
          '1. Open chrome://flags/#allow-insecure-localhost in Chrome\n' +
          '2. Enable the flag\n' +
          '3. Relaunch Chrome.\n' +
          'Original Error:', error
        );
      } else {
        console.error('Service Worker registration failed:', error);
      }
      return null;
    }
  }, [isSupported]);

  const unregister = useCallback(async (): Promise<boolean> => {
    if (!registration) return false;

    try {
      const success = await registration.unregister();
      if (success) {
        setRegistration(null);
      }
      return success;
    } catch (error) {
      console.error('Service Worker unregistration failed:', error);
      return false;
    }
  }, [registration]);

  const getRegistration = useCallback(async (): Promise<ServiceWorkerRegistration | null> => {
    if (!isSupported) return null;

    try {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg) {
        setRegistration(reg);
      }
      return reg || null;
    } catch (error) {
      console.error('Error getting Service Worker registration:', error);
      return null;
    }
  }, [isSupported]);

  const sendMessage = useCallback((message: unknown) => {
    if (navigator.serviceWorker.controller) {
      navigator.serviceWorker.controller.postMessage(message);
    }
  }, []);

  // Close call notification
  const closeCallNotification = useCallback(() => {
    sendMessage({ type: 'CLOSE_CALL_NOTIFICATION' });
  }, [sendMessage]);

  return {
    registration,
    isSupported,
    register,
    unregister,
    getRegistration,
    sendMessage,
    closeCallNotification
  };
};
