"use client";

import { useEffect, useRef, useState } from "react";

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: { client_id: string; callback: (response: { credential: string }) => void }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

const SCRIPT_ID = "google-identity-services";
// Google's button only accepts a fixed pixel width (no percentage), and only
// renders correctly between roughly 200-400px — so instead of a fixed 320px
// (which overflows on narrow phones), measure the actual wrapper and clamp
// into that range, re-rendering the button on resize.
const MIN_WIDTH = 220;
const MAX_WIDTH = 380;

export default function GoogleButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLDivElement>(null);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  // Starts unmeasured (null) rather than defaulting to MAX_WIDTH — rendering
  // Google's button once at a guessed width and then trying to re-render it
  // at the real width doesn't reliably resize the already-mounted iframe, so
  // on narrow phones it stayed stuck at 380px and overflowed the card. Safer
  // to just wait for the real measurement before rendering at all.
  const [width, setWidth] = useState<number | null>(null);

  useEffect(() => {
    if (!wrapperRef.current) return;
    if (typeof ResizeObserver === "undefined") {
      setWidth(MAX_WIDTH);
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const measured = entries[0]?.contentRect.width ?? MAX_WIDTH;
      setWidth(Math.round(Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, measured))));
    });
    observer.observe(wrapperRef.current);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!clientId || width === null) return;

    function render() {
      if (!window.google || !buttonRef.current) return;
      buttonRef.current.innerHTML = ""; // clear before re-rendering at a new width
      window.google.accounts.id.initialize({
        client_id: clientId as string,
        callback: (response) => onCredential(response.credential),
      });
      window.google.accounts.id.renderButton(buttonRef.current, {
        theme: "outline",
        size: "large",
        width,
        text: "continue_with",
      });
    }

    if (document.getElementById(SCRIPT_ID)) {
      render();
      return;
    }

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.id = SCRIPT_ID;
    script.async = true;
    script.defer = true;
    script.onload = render;
    document.body.appendChild(script);
  }, [clientId, onCredential, width]);

  if (!clientId) {
    return (
      <div className="rounded-lg border border-dashed border-border p-3 text-center text-xs text-ink-500">
        Google sign-in isn&apos;t configured yet — set NEXT_PUBLIC_GOOGLE_CLIENT_ID.
      </div>
    );
  }

  return (
    <div ref={wrapperRef} className="w-full">
      <div ref={buttonRef} className="flex justify-center" />
    </div>
  );
}
