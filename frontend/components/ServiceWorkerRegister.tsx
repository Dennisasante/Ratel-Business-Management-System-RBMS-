"use client";

import { useEffect } from "react";

// Registers the no-op service worker (see public/sw.js) purely so Chrome/
// Android offer "Install app" / add-to-home-screen. Safari ignores this
// entirely — iOS installability comes from the apple-touch-icon and
// apple-mobile-web-app-* meta tags in layout.tsx instead.
export default function ServiceWorkerRegister() {
  useEffect(() => {
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {
        // Installability is a nice-to-have, not load-bearing — never block
        // or surface an error to the user over this.
      });
    }
  }, []);

  return null;
}
