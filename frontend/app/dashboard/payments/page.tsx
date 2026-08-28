"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { Wallet, Banknote, ArrowDownCircle, ArrowUpCircle, RotateCw } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, PaymentTransaction } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import StatCard from "@/components/ui/StatCard";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";
import DateRangeFilter from "@/components/ui/DateRangeFilter";
import { DateRangeValue, defaultDateRangeValue } from "@/lib/dateRangePresets";

const STATUS_TONES: Record<string, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  PENDING: "neutral",
  SUCCESS: "success",
  FAILED: "danger",
};

const SOURCE_LABELS: Record<string, string> = {
  SERVICE_ORDER: "Service order",
  SALE: "Sale",
  BOOKING: "Booking",
  PURCHASE_ORDER: "Purchase order",
};

export default function PaymentsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [dateRange, setDateRange] = useState<DateRangeValue>(defaultDateRangeValue());
  const [direction, setDirection] = useState<"" | "INCOMING" | "OUTGOING">("");
  const [gateway, setGateway] = useState<"" | "PAYSTACK" | "MANUAL">("");

  const [transactions, setTransactions] = useState<PaymentTransaction[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [verifyingId, setVerifyingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    setError(null);
    try {
      const data = await api.listPaymentTransactions(session.token, {
        from: dateRange.from ?? undefined,
        to: dateRange.to ?? undefined,
        direction: direction || undefined,
        gateway: gateway || undefined,
      });
      setTransactions(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load payment transactions.");
    }
  }, [session, dateRange, direction, gateway]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    load().finally(() => setFetching(false));
  }, [session, load]);

  async function handleVerify(id: string) {
    if (!session) return;
    setVerifyingId(id);
    setError(null);
    try {
      await api.verifyPaymentTransaction(session.token, id);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't verify that payment.");
    } finally {
      setVerifyingId(null);
    }
  }

  // Daily granularity for the common case (the page defaults to "this
  // month"); a wider pick (a quarter, a year) collapses to monthly rows so
  // the breakdown table stays readable instead of growing to 90+ rows.
  // Computed above the loading/session early return — like every other hook
  // in this component — so useMemo below is never skipped on some renders
  // and not others (React's hooks must run in the same order every render).
  // "All time" (no from/to at all) has no finite width — treat it the same
  // as any other very wide range: monthly buckets, with the same "narrow
  // this down" nudge.
  const rangeDays =
    dateRange.from && dateRange.to
      ? Math.round((new Date(dateRange.to).getTime() - new Date(dateRange.from).getTime()) / 86_400_000) + 1
      : Infinity;
  const granularity: "day" | "month" = rangeDays <= 45 ? "day" : "month";
  const rangeTooWide = rangeDays > 366;

  const breakdown = useMemo(() => {
    const buckets = new Map<string, { cash: number; online: number }>();
    for (const t of transactions) {
      if (t.direction !== "INCOMING" || t.status !== "SUCCESS") continue;
      if (t.gateway !== "PAYSTACK" && t.gateway !== "MANUAL") continue;
      const key = (t.paidAt ?? t.createdAt).slice(0, granularity === "day" ? 10 : 7);
      const bucket = buckets.get(key) ?? { cash: 0, online: 0 };
      if (t.gateway === "MANUAL") bucket.cash += t.amount;
      else bucket.online += t.amount;
      buckets.set(key, bucket);
    }
    return Array.from(buckets.entries())
      .map(([date, b]) => ({ date, ...b, total: b.cash + b.online }))
      .sort((a, b) => (a.date < b.date ? 1 : -1));
  }, [transactions, granularity]);

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  // "Every live money that came onto the system" — real Paystack-settled
  // incoming payments.
  const gatewayRevenue = transactions
    .filter((t) => t.direction === "INCOMING" && t.gateway === "PAYSTACK" && t.status === "SUCCESS")
    .reduce((sum, t) => sum + t.amount, 0);

  // "Virtual cash accumulated" — cash/manually recorded incoming payments,
  // never touched a gateway.
  const manualCash = transactions
    .filter((t) => t.direction === "INCOMING" && t.gateway === "MANUAL" && t.status === "SUCCESS")
    .reduce((sum, t) => sum + t.amount, 0);

  const outgoing = transactions
    .filter((t) => t.direction === "OUTGOING" && t.status === "SUCCESS")
    .reduce((sum, t) => sum + t.amount, 0);

  const breakdownTotals = breakdown.reduce(
    (acc, row) => ({ cash: acc.cash + row.cash, online: acc.online + row.online, total: acc.total + row.total }),
    { cash: 0, online: 0, total: 0 }
  );

  function formatBucketDate(key: string): string {
    return granularity === "day"
      ? new Date(`${key}T00:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })
      : new Date(`${key}-01T00:00:00`).toLocaleDateString(undefined, { month: "long", year: "numeric" });
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Payments" subtitle="Every payment transaction across the system — online and manual." />

      <Card className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Direction</label>
            <select
              value={direction}
              onChange={(e) => setDirection(e.target.value as "" | "INCOMING" | "OUTGOING")}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              <option value="">All</option>
              <option value="INCOMING">Incoming</option>
              <option value="OUTGOING">Outgoing</option>
            </select>
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Type</label>
            <select
              value={gateway}
              onChange={(e) => setGateway(e.target.value as "" | "PAYSTACK" | "MANUAL")}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              <option value="">All</option>
              <option value="PAYSTACK">Online payments</option>
              <option value="MANUAL">Manual / cash</option>
            </select>
          </div>
          <Button onClick={() => { setFetching(true); load().finally(() => setFetching(false)); }} disabled={fetching}>
            {fetching ? "Updating..." : "Update"}
          </Button>
        </div>
        <DateRangeFilter value={dateRange} onChange={setDateRange} />
      </Card>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Online payments revenue"
          value={`GH₵${gatewayRevenue.toFixed(2)}`}
          hint="Real money collected online, settled through Paystack"
          icon={Wallet}
          tone="success"
        />
        <StatCard
          label="Cash / manually recorded"
          value={`GH₵${manualCash.toFixed(2)}`}
          hint="Virtual cash accumulated in the system"
          icon={Banknote}
          tone="accent"
        />
        <StatCard
          label="Paid out to suppliers"
          value={`GH₵${outgoing.toFixed(2)}`}
          hint="Purchase orders settled in this range"
          icon={ArrowUpCircle}
          tone="info"
        />
      </div>

      {!fetching && breakdown.length > 0 && (
        <Card>
          <div className="flex flex-wrap items-center justify-between gap-2 p-5 pb-0">
            <h2 className="text-base font-semibold text-ink-900">
              Revenue by {granularity === "day" ? "day" : "month"}
            </h2>
            {rangeTooWide && (
              <p className="text-xs text-ink-500">Narrow the date range for a faster breakdown.</p>
            )}
          </div>
          <div className="mt-3">
            <Table>
              <THead>
                <Tr>
                  <Th>{granularity === "day" ? "Date" : "Month"}</Th>
                  <Th className="text-right">Cash</Th>
                  <Th className="text-right">Online</Th>
                  <Th className="text-right">Total</Th>
                </Tr>
              </THead>
              <TBody>
                {breakdown.map((row) => (
                  <Tr key={row.date}>
                    <Td className="text-ink-700">{formatBucketDate(row.date)}</Td>
                    <Td className="tabular text-right text-ink-500">GH₵{row.cash.toFixed(2)}</Td>
                    <Td className="tabular text-right text-ink-500">GH₵{row.online.toFixed(2)}</Td>
                    <Td className="tabular text-right font-medium text-ink-900">GH₵{row.total.toFixed(2)}</Td>
                  </Tr>
                ))}
                <Tr>
                  <Td className="font-semibold text-ink-900">Total</Td>
                  <Td className="tabular text-right font-semibold text-ink-900">GH₵{breakdownTotals.cash.toFixed(2)}</Td>
                  <Td className="tabular text-right font-semibold text-ink-900">GH₵{breakdownTotals.online.toFixed(2)}</Td>
                  <Td className="tabular text-right font-semibold text-ink-900">GH₵{breakdownTotals.total.toFixed(2)}</Td>
                </Tr>
              </TBody>
            </Table>
          </div>
        </Card>
      )}

      <Card>
        {fetching ? (
          <TableSkeleton cols={7} />
        ) : transactions.length === 0 ? (
          <EmptyState
            icon={Wallet}
            title="No transactions in this range"
            description="Payments collected on service orders, sales, bookings, and purchase orders will show up here."
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>When</Th>
                <Th>For</Th>
                <Th>Customer</Th>
                <Th>Method</Th>
                <Th>Status</Th>
                <Th className="text-right">Amount</Th>
                <Th className="text-right">Actions</Th>
              </Tr>
            </THead>
            <TBody>
              {transactions.map((t) => (
                <Tr key={t.id}>
                  <Td className="tabular text-ink-500">{new Date(t.createdAt).toLocaleString()}</Td>
                  <Td className="text-ink-700">
                    <span className="block font-medium">{t.sourceLabel ?? SOURCE_LABELS[t.sourceType]}</span>
                    <span className="block text-xs text-ink-400">
                      {t.gateway === "PAYSTACK" ? "Paystack" : "Manual"}
                      {t.note ? ` · ${t.note}` : ""}
                    </span>
                  </Td>
                  <Td className="text-ink-500">{t.customerName ?? t.customerPhone ?? "—"}</Td>
                  <Td className="text-ink-500">{t.method ? t.method.replaceAll("_", " ") : "—"}</Td>
                  <Td>
                    <Badge tone={STATUS_TONES[t.status] ?? "neutral"}>{t.status}</Badge>
                  </Td>
                  <Td className="tabular text-right font-medium">
                    <span className="inline-flex items-center gap-1">
                      {t.direction === "INCOMING" ? (
                        <ArrowDownCircle size={13} className="text-success" />
                      ) : (
                        <ArrowUpCircle size={13} className="text-danger" />
                      )}
                      GH₵{t.amount.toFixed(2)}
                    </span>
                  </Td>
                  <Td className="text-right">
                    {t.status === "PENDING" && t.gateway === "PAYSTACK" && (
                      <button
                        onClick={() => handleVerify(t.id)}
                        disabled={verifyingId === t.id}
                        className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs font-medium text-ink-700 hover:bg-canvas disabled:opacity-50"
                        title="Cross-check with Paystack directly"
                      >
                        <RotateCw size={12} className={verifyingId === t.id ? "animate-spin" : ""} />
                        {verifyingId === t.id ? "Checking..." : "Verify"}
                      </button>
                    )}
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
