"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2, CreditCard, X } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BillingStatus, SubscriptionPlan, SubscriptionPaymentSummary } from "@/lib/api";
import PaystackCheckoutButton from "@/components/PaystackCheckoutButton";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import CardSkeleton from "@/components/ui/CardSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const STATUS_TONES: Record<string, "info" | "success" | "danger"> = {
  TRIALING: "info",
  ACTIVE: "success",
  GRACE: "danger",
  READ_ONLY: "danger",
};

const STATUS_LABELS: Record<string, string> = {
  TRIALING: "Free trial",
  ACTIVE: "Active",
  GRACE: "Renew soon",
  READ_ONLY: "Read-only",
};

// Mirrors BillingService.DISCOUNT_BY_MONTHS exactly — keep both in sync if
// these tiers ever change. Used only for pre-checkout price display; the
// backend recomputes the actual charge and is the source of truth.
const MONTH_OPTIONS: { months: 1 | 3 | 6 | 12; discountPct: number }[] = [
  { months: 1, discountPct: 0 },
  { months: 3, discountPct: 5 },
  { months: 6, discountPct: 10 },
  { months: 12, discountPct: 20 },
];

export default function BillingPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [status, setStatus] = useState<BillingStatus | null>(null);
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [history, setHistory] = useState<SubscriptionPaymentSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [showUsd, setShowUsd] = useState(false);
  const [saveCard, setSaveCard] = useState(false);
  const [savingCardPref, setSavingCardPref] = useState(false);
  const [monthsByPlan, setMonthsByPlan] = useState<Record<string, 1 | 3 | 6 | 12>>({});

  const load = useCallback(async () => {
    if (!session) return;
    const [s, p, h] = await Promise.all([
      api.getBillingStatus(session.token),
      api.listBillingPlans(session.token),
      api.getBillingHistory(session.token),
    ]);
    setStatus(s);
    setPlans(p);
    setHistory(h);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!loading && session && session.role !== "OWNER") router.push("/dashboard");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load().finally(() => setFetching(false));
  }, [session, load]);

  const activePlans = useMemo(() => plans.filter((p) => p.active), [plans]);

  async function handleVerify(reference: string): Promise<boolean> {
    if (!session) return false;
    setError(null);
    setNotice(null);
    try {
      const result = await api.verifyBillingPayment(session.token, reference);
      if (result.success) {
        setNotice("Payment confirmed — thanks! Your subscription is now active.");
      } else {
        setError(result.message || "Payment wasn't completed yet — if you just paid, wait a few seconds and try again.");
      }
      await load();
      return result.success;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't confirm this payment. If you were charged, contact support.");
      return false;
    }
  }

  async function handleToggleAutoRenew(enabled: boolean) {
    if (!session) return;
    setError(null);
    setSavingCardPref(true);
    try {
      await api.setBillingAutoRenew(session.token, enabled);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update that setting.");
    } finally {
      setSavingCardPref(false);
    }
  }

  async function handleRemoveCard() {
    if (!session) return;
    setError(null);
    setSavingCardPref(true);
    try {
      await api.removeSavedCard(session.token);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't remove that card.");
    } finally {
      setSavingCardPref(false);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Billing" subtitle="Your plan, payment history, and subscription status." />

      {fetching || !status ? (
        <CardSkeleton count={2} />
      ) : (
        <>
          <Card className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-3">
              <Badge tone={STATUS_TONES[status.billingStatus] ?? "neutral"}>
                {STATUS_LABELS[status.billingStatus] ?? status.billingStatus}
              </Badge>
              <div>
                <p className="text-sm font-medium text-ink-900">
                  {status.plan ? status.plan.name : "No plan yet"}
                </p>
                <p className="text-xs text-ink-500">
                  {status.billingStatus === "READ_ONLY"
                    ? "Renew below to keep creating and editing."
                    : status.billingStatus === "GRACE"
                    ? `Your subscription lapsed — ${status.daysRemaining} day${status.daysRemaining === 1 ? "" : "s"} left to renew before you lose access.`
                    : status.daysRemaining >= 0
                    ? `${status.daysRemaining} day${status.daysRemaining === 1 ? "" : "s"} remaining`
                    : "Expired"}
                </p>
              </div>
            </div>
          </Card>

          {error && <p className="text-sm text-danger">{error}</p>}
          {notice && (
            <p className="flex items-center gap-1.5 text-sm text-success">
              <CheckCircle2 size={15} /> {notice}
            </p>
          )}

          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-ink-900">Plans</h2>
            {status.usdDisplayRate != null && (
              <div className="flex items-center gap-1 rounded-lg border border-border bg-surface p-0.5 text-xs font-medium">
                <button
                  onClick={() => setShowUsd(false)}
                  className={`rounded-md px-2.5 py-1 transition ${!showUsd ? "bg-accent text-white" : "text-ink-500"}`}
                >
                  GHS
                </button>
                <button
                  onClick={() => setShowUsd(true)}
                  className={`rounded-md px-2.5 py-1 transition ${showUsd ? "bg-accent text-white" : "text-ink-500"}`}
                >
                  USD
                </button>
              </div>
            )}
          </div>

          {activePlans.length === 0 ? (
            <p className="text-sm text-ink-500">
              No plans are available to subscribe to yet — check back soon, or reach out if you&apos;d like to upgrade now.
            </p>
          ) : (
          <>
          <label className="flex items-center gap-2 text-sm text-ink-700">
            <input
              type="checkbox"
              checked={saveCard}
              onChange={(e) => setSaveCard(e.target.checked)}
              className="rounded border-border text-accent focus:ring-accent/20"
            />
            Save this card for automatic renewal
          </label>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {activePlans.map((plan) => {
              const isCurrent = status.plan?.id === plan.id && status.billingStatus === "ACTIVE";
              const months = monthsByPlan[plan.id] ?? 1;
              // Their real rate (custom override, if any) only applies to the
              // plan they're actually on — every other plan card shows its own
              // list price, since a negotiated rate is tied to a specific plan.
              const monthlyRate = isCurrent && status.effectiveMonthlyRate != null ? status.effectiveMonthlyRate : plan.price;
              const discountPct = MONTH_OPTIONS.find((o) => o.months === months)?.discountPct ?? 0;
              const fullPriceNoDiscount = monthlyRate * months;
              const total = fullPriceNoDiscount * (1 - discountPct / 100);
              const savings = fullPriceNoDiscount - total;
              const usdPrice = status.usdDisplayRate ? total / status.usdDisplayRate : null;
              return (
                <Card key={plan.id} className={`flex flex-col gap-3 p-5 ${isCurrent ? "border-accent ring-1 ring-accent" : ""}`}>
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-ink-900">{plan.name}</h3>
                    {isCurrent && <Badge tone="accent">Current plan</Badge>}
                  </div>

                  <div className="flex items-center gap-1 rounded-lg border border-border bg-canvas p-0.5 text-xs font-medium">
                    {MONTH_OPTIONS.map((o) => (
                      <button
                        key={o.months}
                        onClick={() => setMonthsByPlan((prev) => ({ ...prev, [plan.id]: o.months }))}
                        className={`flex-1 rounded-md px-2 py-1 transition ${
                          months === o.months ? "bg-accent text-white" : "text-ink-500 hover:text-ink-900"
                        }`}
                      >
                        {o.months}mo
                      </button>
                    ))}
                  </div>

                  <div>
                    <p className="text-2xl font-semibold text-ink-900">
                      {showUsd && usdPrice != null ? (
                        <>
                          ≈ ${usdPrice.toFixed(2)}
                        </>
                      ) : (
                        <>
                          {plan.currency} {total.toFixed(2)}
                        </>
                      )}
                    </p>
                    <p className="text-xs text-ink-500">
                      {months === 1
                        ? `every ${plan.billingPeriodDays} days`
                        : `${months} months (${plan.billingPeriodDays * months} days)`}
                    </p>
                    {discountPct > 0 && (
                      <p className="mt-1 text-xs text-success">
                        {plan.currency} {fullPriceNoDiscount.toFixed(2)} — save {plan.currency} {savings.toFixed(2)} ({discountPct}% off)
                      </p>
                    )}
                    {showUsd && usdPrice != null && (
                      <p className="mt-1 text-xs text-ink-500">
                        Display only — checkout always charges {plan.currency} {total.toFixed(2)}.
                      </p>
                    )}
                  </div>
                  <PaystackCheckoutButton
                    planId={plan.id}
                    months={months}
                    buttonLabel="Pay with Paystack"
                    className="mt-1 w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                    onStartCheckout={(planId, checkoutMonths) => api.startBillingCheckout(session.token, planId, checkoutMonths, saveCard)}
                    onVerify={handleVerify}
                    onError={setError}
                  />
                </Card>
              );
            })}
          </div>
          </>
          )}

          {status.cardLast4 && (
            <Card className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <CreditCard size={20} className="text-ink-500" />
                <div>
                  <p className="text-sm font-medium text-ink-900">
                    {status.cardBrand ? `${status.cardBrand} · ` : ""}•••• {status.cardLast4}
                  </p>
                  <p className="text-xs text-ink-500">
                    {status.autoRenewEnabled ? "Auto-renewal is on." : "Saved, but auto-renewal is off."}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-2 text-sm text-ink-700">
                  <input
                    type="checkbox"
                    checked={status.autoRenewEnabled}
                    disabled={savingCardPref}
                    onChange={(e) => handleToggleAutoRenew(e.target.checked)}
                    className="rounded border-border text-accent focus:ring-accent/20"
                  />
                  Auto-renew
                </label>
                <button
                  onClick={handleRemoveCard}
                  disabled={savingCardPref}
                  className="flex items-center gap-1 text-sm font-medium text-danger hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <X size={14} /> Remove card
                </button>
              </div>
            </Card>
          )}

          <Card>
            <h2 className="p-5 pb-0 text-base font-semibold text-ink-900">Payment history</h2>
            {history.length === 0 ? (
              <p className="p-5 text-sm text-ink-500">No payments yet.</p>
            ) : (
              <div className="mt-3">
                <Table>
                  <THead>
                    <Tr>
                      <Th>Date</Th>
                      <Th>Plan</Th>
                      <Th>Months</Th>
                      <Th>Status</Th>
                      <Th className="text-right">Amount</Th>
                    </Tr>
                  </THead>
                  <TBody>
                    {history.map((h) => (
                      <Tr key={h.id}>
                        <Td className="tabular text-ink-500">{new Date(h.createdAt).toLocaleDateString()}</Td>
                        <Td className="font-medium">{h.planName ?? "—"}</Td>
                        <Td className="tabular text-ink-500">{h.months}</Td>
                        <Td>
                          <Badge tone={h.status === "SUCCESS" ? "success" : h.status === "FAILED" ? "danger" : "neutral"}>
                            {h.status}
                          </Badge>
                        </Td>
                        <Td className="tabular text-right font-medium">
                          {h.currency} {h.amount.toFixed(2)}
                        </Td>
                      </Tr>
                    ))}
                  </TBody>
                </Table>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
