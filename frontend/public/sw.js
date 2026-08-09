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
