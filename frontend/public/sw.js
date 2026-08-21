// Exists only so Chrome/Android will treat Ratel as installable (a fetch
// handler is part of their install-criteria check). Deliberately does no
// caching — this is a thin "open like an app" shell, not an offline-first
// app, so nothing here should ever serve stale business data. Requests just
// pass straight through to the network, same as if there were no service
// worker at all.
self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", () => {
  // No-op: default network behavior, no interception, no cache.
});

// Payload is the plain {title, body} JSON PushNotificationService sends —
// see backend PushNotificationService.notifyBusinessOwnersAndManagers().
self.addEventListener("push", (event) => {
  let title = "Tallia";
  let body = "You have a new notification.";
  if (event.data) {
    try {
      const data = event.data.json();
      title = data.title || title;
      body = data.body || body;
    } catch {
      body = event.data.text();
    }
  }
  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      icon: "/icons/icon-192.png",
      badge: "/icons/icon-192.png",
    })
  );
});

// Focuses an already-open dashboard tab if one exists, otherwise opens one —
// same "just get them to the dashboard" behavior regardless of which
// notification type this was for.
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes("/dashboard") && "focus" in client) {
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow("/dashboard");
      }
    })
  );
});
