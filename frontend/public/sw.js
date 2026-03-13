// Service Worker for Push Notifications
const CACHE_NAME = 'loop-chat-v1';

// Install event
self.addEventListener('install', (event) => {
  console.log('[SW] Service Worker installed');
  self.skipWaiting();
});

// Activate event
self.addEventListener('activate', (event) => {
  console.log('[SW] Service Worker activated');
  event.waitUntil(clients.claim());
});

// Push event - handles incoming push notifications
self.addEventListener('push', (event) => {
  console.log('[SW] Push received:', event);
  
  let data = {
    title: 'Incoming Call',
    body: 'Someone is calling you',
    icon: '/favicon.png',
    badge: '/favicon.png',
    tag: 'incoming-call',
    requireInteraction: true,
    vibrate: [300, 100, 300, 100, 300],
    data: {}
  };

  if (event.data) {
    try {
      const payload = event.data.json();
      data = {
        ...data,
        ...payload
      };
    } catch (e) {
      console.error('[SW] Error parsing push data:', e);
    }
  }

  const options = {
    body: data.body,
    icon: data.icon || '/favicon.png',
    badge: data.badge || '/favicon.png',
    tag: data.tag || 'incoming-call',
    requireInteraction: data.requireInteraction !== false,
    vibrate: data.vibrate || [300, 100, 300, 100, 300],
    data: data.data || {},
    actions: data.actions || [
      { action: 'accept', title: 'Accept' },
      { action: 'reject', title: 'Reject' }
    ]
  };

  event.waitUntil(
    self.registration.showNotification(data.title, options)
  );
});

// Notification click event
self.addEventListener('notificationclick', (event) => {
  console.log('[SW] Notification clicked:', event.action);
  
  event.notification.close();

  const data = event.notification.data || {};
  const callId = data.callId;
  const action = event.action;

  // Handle action buttons
  if (action === 'accept' && callId) {
    event.waitUntil(
      clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
        // Try to focus an existing window
        for (const client of clientList) {
          if ('focus' in client) {
            client.focus();
            client.postMessage({
              type: 'CALL_ACCEPTED',
              callId: callId
            });
            return;
          }
        }
        // Open a new window if none exists
        if (clients.openWindow) {
          return clients.openWindow(`/incoming-call?callId=${callId}&autoAccept=true`);
        }
      })
    );
  } else if (action === 'reject' && callId) {
    event.waitUntil(
      clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
        for (const client of clientList) {
          client.postMessage({
            type: 'CALL_REJECTED',
            callId: callId
          });
        }
      })
    );
  } else {
    // Default click - open the app
    event.waitUntil(
      clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
        for (const client of clientList) {
          if ('focus' in client) {
            return client.focus();
          }
        }
        if (clients.openWindow) {
          const url = callId ? `/incoming-call?callId=${callId}` : '/';
          return clients.openWindow(url);
        }
      })
    );
  }
});

// Notification close event
self.addEventListener('notificationclose', (event) => {
  console.log('[SW] Notification closed');
});

// Message event - receive messages from the main app
self.addEventListener('message', (event) => {
  console.log('[SW] Message received:', event.data);
  
  if (event.data && event.data.type === 'CLOSE_CALL_NOTIFICATION') {
    // Close any incoming call notifications
    self.registration.getNotifications({ tag: 'incoming-call' }).then((notifications) => {
      notifications.forEach((notification) => notification.close());
    });
  }
});
