"use client";

import { useState } from "react";
import { CreditCard, CheckCircle } from "lucide-react";

declare global {
  interface Window {
    PaystackPop?: new () => {
      resumeTransaction: (accessCode: string) => void;
    };
  }
}

const SCRIPT_ID = "paystack-inline-js";

function loadPaystackScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.PaystackPop) {
      resolve();
      return;
    }
    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("Couldn't load Paystack")));
      return;
    }
    const script = document.createElement("script");
    script.id = SCRIPT_ID;
    script.src = "https://js.paystack.co/v2/inline.js";
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Couldn't load Paystack"));
    document.body.appendChild(script);
  });
}

/**
 * Paystack's resumeTransaction(accessCode) — the "initialize on the server,
 * complete in the browser" flow Paystack's own docs recommend — takes only
 * the access code and hands back no completion callback. Paystack's own
 * guidance for this flow is to confirm via "webhooks or the verify
 * endpoint," so this is a genuine two-step UI: Pay opens the popup, Verify
 * (a separate action, with its own pending state) asks our backend to
 * re-check with Paystack directly once the customer is done. The webhook
 * (built server-side) is usually already ahead of this by the time someone
 * clicks Verify — this button just makes sure it's reflected here too.
 */
export default function PaystackCheckoutButton({
  planId,
  className,
  buttonLabel,
  onStartCheckout,
  onVerify,
  onError,
}: {
  planId: string;
  className?: string;
  buttonLabel: React.ReactNode;
  onStartCheckout: (planId: string) => Promise<{ accessCode: string; reference: string }>;
  onVerify: (reference: string) => Promise<boolean>;
  onError: (message: string) => void;
}) {
  const [starting, setStarting] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [reference, setReference] = useState<string | null>(null);

  async function handlePay() {
    if (starting) return;
    setStarting(true);
    try {
      await loadPaystackScript();
      const result = await onStartCheckout(planId);
      if (!window.PaystackPop) {
        throw new Error("Paystack didn't load correctly. Please try again.");
      }
      const popup = new window.PaystackPop();
      popup.resumeTransaction(result.accessCode);
      setReference(result.reference);
    } catch (err) {
      onError(err instanceof Error ? err.message : "Payment couldn't be started. Please try again.");
    } finally {
      setStarting(false);
    }
  }

  async function handleVerify() {
    if (!reference || verifying) return;
    setVerifying(true);
    try {
      const success = await onVerify(reference);
      if (success) setReference(null);
    } finally {
      setVerifying(false);
    }
  }

  if (reference) {
    return (
      <div className="flex flex-col gap-2">
        <p className="text-xs text-ink-500">Finished paying in the popup? Confirm it here.</p>
        <button onClick={handleVerify} disabled={verifying} className={className}>
          <CheckCircle size={15} className="mr-1.5 inline" />
          {verifying ? "Checking..." : "Verify payment"}
        </button>
        <button onClick={() => setReference(null)} className="text-xs font-medium text-ink-500 hover:text-ink-900">
          Start over
        </button>
      </div>
    );
  }

  return (
    <button onClick={handlePay} disabled={starting} className={className}>
      <CreditCard size={15} className="mr-1.5 inline" />
      {starting ? "Starting..." : buttonLabel}
    </button>
  );
}
