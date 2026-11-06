"use client";

import { FormEvent, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { CalendarCheck2, Gift, Search } from "lucide-react";
import { api, ApiError, PublicLoyalty, PublicLoyaltyBusiness } from "@/lib/api";
import { LoyaltyStamps } from "@/components/LoyaltyStatusPanel";
import PoweredByRatel from "@/components/PoweredByRatel";

// A business's public points page — the link in the SMS receipt. No login: the customer types the
// phone number they gave at the shop and sees their first name and points. Nothing else is shown.
export default function BusinessPointsPage() {
  const params = useParams<{ slug: string }>();
  const [business, setBusiness] = useState<PublicLoyaltyBusiness | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const [phone, setPhone] = useState("");
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PublicLoyalty | null>(null);

  useEffect(() => {
    api.getPublicLoyaltyBusiness(params.slug).then(setBusiness).catch(() => setUnavailable(true));
  }, [params.slug]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (checking) return;
    setChecking(true);
    setError(null);
    try {
      setResult(await api.lookupPublicLoyalty(params.slug, phone.trim()));
    } catch (err) {
      setResult(null);
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setChecking(false);
    }
  }

  if (unavailable) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-canvas px-4">
        <p className="max-w-xs text-center text-sm text-ink-500">This page isn&apos;t available right now.</p>
      </main>
    );
  }
  if (!business) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-canvas">
        <p className="text-sm text-ink-500">Loading...</p>
      </main>
    );
  }

  const remaining = result ? Math.max(result.targetPoints - result.points, 0) : 0;

  return (
    <main className="flex min-h-screen flex-col items-center bg-canvas px-4 py-10">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-surface p-6 text-center shadow-card">
        {business.logoUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={business.logoUrl} alt="" className="mx-auto mb-3 h-16 w-16 rounded-xl object-contain" />
        )}
        <h1 className="text-lg font-semibold text-ink-900">{business.businessName}</h1>

        {!result ? (
          <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-3 text-left">
            <p className="text-center text-sm text-ink-500">Enter your phone number to see your points.</p>
            <label htmlFor="points-phone" className="sr-only">
              Phone number
            </label>
            <input
              id="points-phone"
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              placeholder="e.g. 024 123 4567"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-3 text-base text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
            {error && <p className="text-sm text-danger">{error}</p>}
            <button
              type="submit"
              disabled={checking || phone.trim().length < 9}
              className="flex items-center justify-center gap-2 rounded-lg bg-accent px-4 py-3 text-sm font-medium text-white transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Search size={16} /> {checking ? "Checking..." : "Check my points"}
            </button>
            <p className="text-center text-xs text-ink-500">
              Reach {business.targetPoints} points to earn <strong className="text-ink-700">{business.rewardDescription}</strong>.
            </p>
          </form>
        ) : (
          <div className="mt-4 flex flex-col items-center gap-3">
            <p className="text-sm text-ink-500">{result.customerFirstName ? `Hi ${result.customerFirstName}, here are your points` : "Your points"}</p>
            <p className="text-4xl font-semibold text-ink-900">
              {Math.min(result.points, result.targetPoints)}
              <span className="text-xl font-normal text-ink-400"> / {result.targetPoints} points</span>
            </p>
            <div className="flex justify-center">
              <LoyaltyStamps visits={result.visits} target={result.target} />
            </div>
            {result.rewardDue ? (
              <div className="mt-2 flex items-start gap-2 rounded-lg bg-success-soft px-3 py-2 text-left text-sm text-success">
                <Gift size={16} className="mt-0.5 shrink-0" />
                <span>
                  You&apos;ve earned <strong>{result.rewardDescription}</strong>! Show this page on your next visit and we&apos;ll apply it.
                </span>
              </div>
            ) : (
              <p className="text-sm text-ink-500">
                Reach {result.targetPoints} points to earn <strong className="text-ink-700">{result.rewardDescription}</strong> ({remaining} to go).
              </p>
            )}
            {result.bookingPath && (
              <a
                href={result.bookingPath}
                className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg bg-accent px-4 py-3 text-sm font-medium text-white transition hover:bg-accent-hover"
              >
                <CalendarCheck2 size={16} /> Book an appointment
              </a>
            )}
            <button
              type="button"
              onClick={() => {
                setResult(null);
                setPhone("");
              }}
              className="text-xs font-medium text-accent-hover hover:underline"
            >
              Check another number
            </button>
          </div>
        )}
      </div>
      <div className="mt-6">
        <PoweredByRatel />
      </div>
    </main>
  );
}
