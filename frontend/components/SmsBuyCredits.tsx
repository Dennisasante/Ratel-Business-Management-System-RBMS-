"use client";

import { useEffect, useState } from "react";
import { api, SmsPricing } from "@/lib/api";
import Card from "@/components/ui/Card";
import PaystackCheckoutButton from "@/components/PaystackCheckoutButton";

const QUICK_AMOUNTS = [10, 20, 50, 100, 200];

/**
 * Buy SMS credits by typing an amount: the business sees how many credits that buys
 * (amount ÷ price per credit, rounded down) before paying with Paystack.
 */
export default function SmsBuyCredits({
  token,
  onVerify,
  onError,
}: {
  token: string;
  onVerify: (reference: string) => Promise<boolean>;
  onError: (message: string) => void;
}) {
  const [pricing, setPricing] = useState<SmsPricing | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [amount, setAmount] = useState("50");

  useEffect(() => {
    api
      .getSmsPricing(token)
      .then(setPricing)
      .catch(() => setPricing(null))
      .finally(() => setLoaded(true));
  }, [token]);

  if (!loaded) return <p className="text-sm text-ink-500">Loading...</p>;

  if (!pricing || pricing.pricePerCredit == null) {
    return <p className="text-sm text-ink-500">Credits can&apos;t be bought just yet. Please contact support.</p>;
  }

  const price = pricing.pricePerCredit;
  const value = Number(amount);
  const valid = Number.isFinite(value) && value >= pricing.minAmount && value <= pricing.maxAmount;
  // Whole credits only; the same rounding the server uses.
  const credits = valid ? Math.floor(Math.round(value * 100) / 100 / price) : 0;

  return (
    <Card className="flex max-w-md flex-col gap-4 p-5">
      <div>
        <label htmlFor="sms-buy-amount" className="text-sm font-medium text-ink-900">
          How much do you want to spend?
        </label>
        <div className="mt-1.5 flex items-center gap-2">
          <span className="text-sm text-ink-500">{pricing.currency}</span>
          <input
            id="sms-buy-amount"
            type="number"
            inputMode="decimal"
            min={pricing.minAmount}
            max={pricing.maxAmount}
            step="1"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-32 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
        <div className="mt-2 flex flex-wrap gap-2">
          {QUICK_AMOUNTS.map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => setAmount(String(q))}
              className={`rounded-full border px-3 py-1 text-xs transition ${
                Number(amount) === q ? "border-accent bg-accent/10 text-accent" : "border-border text-ink-700 hover:bg-canvas"
              }`}
            >
              {q}
            </button>
          ))}
        </div>
      </div>

      <div className="rounded-lg bg-canvas px-4 py-3">
        {valid ? (
          <>
            <p className="text-2xl font-semibold text-ink-900">{credits.toLocaleString()} credits</p>
            <p className="text-xs text-ink-500">
              {pricing.currency} {price.toFixed(3)} per credit. One credit sends one text of up to 160 characters.
            </p>
          </>
        ) : (
          <p className="text-sm text-ink-500">
            Enter an amount between {pricing.currency} {pricing.minAmount} and {pricing.currency} {pricing.maxAmount.toLocaleString()}.
          </p>
        )}
      </div>

      {/* The checkout button is shared with subscriptions and calls its first field a plan id —
          here it carries the typed amount (months is unused). */}
      {valid ? (
        <PaystackCheckoutButton
          key={amount}
          planId={amount}
          months={1}
          buttonLabel={`Pay ${pricing.currency} ${value.toFixed(2)} with Paystack`}
          className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
          onStartCheckout={(typed) => api.startSmsPurchaseByAmount(token, Number(typed))}
          onVerify={onVerify}
          onError={onError}
        />
      ) : (
        <button
          type="button"
          disabled
          className="w-full cursor-not-allowed rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white opacity-60"
        >
          Pay with Paystack
        </button>
      )}
    </Card>
  );
}
