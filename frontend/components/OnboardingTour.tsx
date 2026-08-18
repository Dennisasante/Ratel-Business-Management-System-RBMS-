"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Sparkles, LayoutDashboard, ShoppingCart, Wallet, LifeBuoy, ChevronRight, ChevronLeft } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api } from "@/lib/api";

const STEPS: { icon: typeof Sparkles; title: string; body: string }[] = [
  {
    icon: Sparkles,
    title: "Welcome to Tallia",
    body: "A quick look at where everything lives, so you're not hunting for it on your first real day.",
  },
  {
    icon: LayoutDashboard,
    title: "Everything's in the sidebar",
    body: "Bookings, Sales, Service Orders, Inventory and more are grouped by what they're for. What you see depends on your role — staff get a lighter menu than an Owner.",
  },
  {
    icon: ShoppingCart,
    title: "Sales, Service Orders & Bookings",
    body: "Ring up a sale or start a service order, then collect payment right from the list — no need to open a receipt first. Bookings has its own hosted page customers can use themselves.",
  },
  {
    icon: Wallet,
    title: "Payments, all in one ledger",
    body: "Every payment — card, mobile money, cash, or money paid out to a supplier — lands on the Payments page, whichever screen it started from.",
  },
  {
    icon: LifeBuoy,
    title: "Stuck? Help & Support has you covered",
    body: "A full guide to every feature, plus a direct line to us if something's not working. Find it near the bottom of the sidebar any time.",
  },
];

// Fixed key, not per-user — the real per-user "seen it" state lives server-side
// (User.onboardingCompletedAt) and is fetched fresh on every dashboard load.
// This only guards against re-fetching that status mid-session after a skip.
const SESSION_DISMISS_KEY = "rbms_onboarding_dismissed";

export default function OnboardingTour() {
  return (
    <Suspense fallback={null}>
      <OnboardingTourInner />
    </Suspense>
  );
}

function OnboardingTourInner() {
  const { session } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [visible, setVisible] = useState(false);
  const [step, setStep] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!session) return;
    const forced = searchParams.get("tour") === "1";

    if (forced) {
      setStep(0);
      setVisible(true);
      router.replace("/dashboard");
      return;
    }

    if (window.sessionStorage.getItem(SESSION_DISMISS_KEY)) return;

    api
      .getOnboardingStatus(session.token)
      .then((status) => {
        if (!status.completed) setVisible(true);
      })
      .catch(() => {});
    // Only re-check when the session identity changes, not on every route change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.token]);

  async function finish() {
    if (!session || busy) return;
    setBusy(true);
    try {
      await api.completeOnboarding(session.token);
    } catch {
      // best-effort — worst case the tour just shows again next load
    } finally {
      window.sessionStorage.setItem(SESSION_DISMISS_KEY, "1");
      setBusy(false);
      setVisible(false);
    }
  }

  if (!visible) return null;

  const current = STEPS[step];
  const Icon = current.icon;
  const isLast = step === STEPS.length - 1;

  return (
    <div className="animate-overlay-in fixed inset-0 z-[60] flex items-center justify-center bg-black/40 px-4">
      <div className="animate-modal-in w-full max-w-sm rounded-xl border border-border bg-surface p-6 shadow-panel">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-accent-soft text-accent-hover">
          <Icon size={22} strokeWidth={1.75} />
        </div>
        <h2 className="mt-4 text-base font-semibold text-ink-900">{current.title}</h2>
        <p className="mt-1.5 text-sm text-ink-500">{current.body}</p>

        <div className="mt-5 flex items-center justify-center gap-1.5">
          {STEPS.map((_, i) => (
            <span
              key={i}
              className={`h-1.5 rounded-full transition-all ${i === step ? "w-5 bg-accent" : "w-1.5 bg-border"}`}
            />
          ))}
        </div>

        <div className="mt-5 flex items-center justify-between gap-2">
          <button
            onClick={finish}
            disabled={busy}
            className="text-sm font-medium text-ink-500 hover:text-ink-900 disabled:opacity-50"
          >
            Skip
          </button>
          <div className="flex items-center gap-2">
            {step > 0 && (
              <button
                onClick={() => setStep((s) => s - 1)}
                className="inline-flex items-center gap-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-ink-900 hover:bg-canvas"
              >
                <ChevronLeft size={15} /> Back
              </button>
            )}
            <button
              onClick={() => (isLast ? finish() : setStep((s) => s + 1))}
              disabled={busy}
              className="inline-flex items-center gap-1 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card hover:bg-accent-hover disabled:opacity-50"
            >
              {isLast ? (busy ? "Finishing..." : "Get started") : "Next"}
              {!isLast && <ChevronRight size={15} />}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
