"use client";

import { useEffect, useState } from "react";
import { Bell, BellOff } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import Button from "@/components/ui/Button";

// Standard Web Push boilerplate — pushManager.subscribe() needs the VAPID
// public key as a raw Uint8Array, not the base64url string it's shipped as.
function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i++) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

type Status = "checking" | "unsupported" | "subscribed" | "unsubscribed" | "denied" | "ios-not-installed";

// iOS Safari only supports Web Push once the site has been added to the
// Home Screen and reopened from there (iOS 16.4+) — from a regular browser
// tab, PushManager exists (so the earlier feature-detection passes) but
// subscribe() always fails with an opaque error. Checked separately so the
// UI can explain the actual fix instead of a dead-end failure message.
function isIosBrowserTab(): boolean {
  if (typeof window === "undefined") return false;
  const isIos = /iPad|iPhone|iPod/.test(navigator.userAgent) && !("MSStream" in window);
  const isStandalone =
    window.matchMedia("(display-mode: standalone)").matches ||
    (window.navigator as unknown as { standalone?: boolean }).standalone === true;
  return isIos && !isStandalone;
}

// Never auto-triggered — permission prompts are one-shot and burning it on
// page load (before the person knows what they're agreeing to) tends to get
// an instant "Block" that can't be undone without a manual browser-settings
// trip. This only ever fires from an explicit click here.
export default function PushNotificationToggle({ token }: { token: string }) {
  const [status, setStatus] = useState<Status>("checking");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function check() {
      if (typeof window === "undefined" || !("serviceWorker" in navigator) || !("PushManager" in window)) {
        setStatus("unsupported");
        return;
      }
      if (isIosBrowserTab()) {
        setStatus("ios-not-installed");
        return;
      }
      if (Notification.permission === "denied") {
        setStatus("denied");
        return;
      }
      const registration = await navigator.serviceWorker.ready;
      const existing = await registration.pushManager.getSubscription();
      setStatus(existing ? "subscribed" : "unsubscribed");
    }
    check().catch(() => setStatus("unsupported"));
  }, []);

  async function handleEnable() {
    setError(null);
    setBusy(true);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        setStatus(permission === "denied" ? "denied" : "unsubscribed");
        return;
      }
      const vapidKey = process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY;
      if (!vapidKey) {
        setError("Push notifications aren't set up on this server yet.");
        return;
      }
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKey) as BufferSource,
      });
      const json = subscription.toJSON();
      await api.subscribeToPush(token, {
        endpoint: json.endpoint!,
        p256dh: json.keys!.p256dh,
        auth: json.keys!.auth,
      });
      setStatus("subscribed");
    } catch (err) {
      // Surfaces the real DOMException name/message (e.g. "NotAllowedError:
      // Registration failed - push service error") rather than a generic
      // fallback — pushManager.subscribe() fails in enough different,
      // device-specific ways (iOS requiring the PWA be installed first,
      // a misconfigured VAPID key, a blocked push service, etc.) that the
      // real message is the fastest way to tell which one it is.
      const message = err instanceof ApiError ? err.message : err instanceof Error ? err.message : String(err);
      setError(`Couldn't enable push notifications on this device: ${message}`);
    } finally {
      setBusy(false);
    }
  }

  async function handleDisable() {
    setError(null);
    setBusy(true);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        const endpoint = subscription.endpoint;
        await subscription.unsubscribe();
        await api.unsubscribeFromPush(token, endpoint);
      }
      setStatus("unsubscribed");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't disable push notifications on this device.");
    } finally {
      setBusy(false);
    }
  }

  if (status === "checking") return null;

  if (status === "unsupported") {
    return <p className="text-xs text-ink-500">Push notifications aren&apos;t supported on this browser/device.</p>;
  }

  if (status === "ios-not-installed") {
    return (
      <p className="text-xs text-ink-500">
        On iPhone/iPad, push notifications only work once this app is added to your Home Screen: tap the Share icon in
        Safari, choose &quot;Add to Home Screen,&quot; then open the app from there and try again.
      </p>
    );
  }

  if (status === "denied") {
    return (
      <p className="text-xs text-ink-500">
        Notifications are blocked for this site in your browser settings. Allow them there, then reload this page.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      {status === "subscribed" ? (
        <Button variant="secondary" onClick={handleDisable} disabled={busy} className="w-fit">
          <BellOff size={15} />
          {busy ? "Working..." : "Disable push notifications on this device"}
        </Button>
      ) : (
        <Button onClick={handleEnable} disabled={busy} className="w-fit">
          <Bell size={15} />
          {busy ? "Working..." : "Enable push notifications on this device"}
        </Button>
      )}
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  );
}
