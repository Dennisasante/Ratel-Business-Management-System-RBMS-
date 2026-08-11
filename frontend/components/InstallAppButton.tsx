"use client";

import { useEffect, useState } from "react";
import { Download } from "lucide-react";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

// Chrome/Edge/Android fire beforeinstallprompt and let us trigger the native
// install UI directly. iOS Safari has no such event — the only path there is
// the manual Share -> Add to Home Screen flow, so we just point people at it.
export default function InstallAppButton({ className }: { className?: string }) {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isIos, setIsIos] = useState(false);
  const [showIosHint, setShowIosHint] = useState(false);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const standalone =
      window.matchMedia("(display-mode: standalone)").matches ||
      (window.navigator as unknown as { standalone?: boolean }).standalone === true;
    if (standalone) return;

    const ios = /iphone|ipad|ipod/i.test(window.navigator.userAgent);
    setIsIos(ios);
    setVisible(ios);

    function onBeforeInstallPrompt(e: Event) {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
      setVisible(true);
    }
    function onInstalled() {
      setVisible(false);
      setDeferredPrompt(null);
    }
    window.addEventListener("beforeinstallprompt", onBeforeInstallPrompt);
    window.addEventListener("appinstalled", onInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstallPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);

  if (!visible) return null;

  async function handleClick() {
    if (deferredPrompt) {
      await deferredPrompt.prompt();
      const choice = await deferredPrompt.userChoice;
      if (choice.outcome !== "dismissed") setVisible(false);
      setDeferredPrompt(null);
      return;
    }
    setShowIosHint((v) => !v);
  }

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={handleClick}
        className={className ?? "flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-ink-700 hover:bg-canvas"}
      >
        <Download size={15} /> Install app
      </button>
      {showIosHint && isIos && (
        <div className="absolute right-0 z-20 mt-2 w-56 rounded-lg border border-border bg-surface p-3 text-xs text-ink-700 shadow-panel">
          Tap the Share icon in Safari, then &quot;Add to Home Screen&quot;.
        </div>
      )}
    </div>
  );
}
