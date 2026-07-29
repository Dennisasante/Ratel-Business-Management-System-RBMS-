"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2 } from "lucide-react";
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
  READ_ONLY: "danger",
};

const STATUS_LABELS: Record<string, string> = {
  TRIALING: "Free trial",
  ACTIVE: "Active",
  READ_ONLY: "Read-only",
};

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

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {activePlans.map((plan) => {
              const isCurrent = status.plan?.id === plan.id && status.billingStatus === "ACTIVE";
              const usdPrice = status.usdDisplayRate ? plan.price / status.usdDisplayRate : null;
              return (
                <Card key={plan.id} className={`flex flex-col gap-3 p-5 ${isCurrent ? "border-accent ring-1 ring-accent" : ""}`}>
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-ink-900">{plan.name}</h3>
                    {isCurrent && <Badge tone="accent">Current plan</Badge>}
                  </div>
                  <div>
                    <p className="text-2xl font-semibold text-ink-900">
                      {showUsd && usdPrice != null ? (
                        <>
                          ≈ ${usdPrice.toFixed(2)}
                        </>
                      ) : (
                        <>
                          {plan.currency} {plan.price.toFixed(2)}
                        </>
                      )}
                    </p>
                    <p className="text-xs text-ink-500">every {plan.billingPeriodDays} days</p>
                    {showUsd && usdPrice != null && (
                      <p className="mt-1 text-xs text-ink-500">
                        Display only — checkout always charges {plan.currency} {plan.price.toFixed(2)}.
                      </p>
                    )}
                  </div>
                  <PaystackCheckoutButton
                    planId={plan.id}
                    buttonLabel="Pay with Paystack"
                    className="mt-1 w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                    onStartCheckout={(planId) => api.startBillingCheckout(session.token, planId)}
                    onVerify={handleVerify}
                    onError={setError}
                  />
                </Card>
              );
            })}
          </div>

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
                      <Th>Status</Th>
                      <Th className="text-right">Amount</Th>
                    </Tr>
                  </THead>
                  <TBody>
                    {history.map((h) => (
                      <Tr key={h.id}>
                        <Td className="tabular text-ink-500">{new Date(h.createdAt).toLocaleDateString()}</Td>
                        <Td className="font-medium">{h.planName ?? "—"}</Td>
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
