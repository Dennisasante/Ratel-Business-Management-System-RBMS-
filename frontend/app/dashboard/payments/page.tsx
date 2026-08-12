"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Wallet, Banknote, ArrowDownCircle, ArrowUpCircle } from "lucide-react";
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

function firstDayOfMonth(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function PaymentsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [from, setFrom] = useState(firstDayOfMonth());
  const [to, setTo] = useState(today());
  const [direction, setDirection] = useState<"" | "INCOMING" | "OUTGOING">("");
  const [gateway, setGateway] = useState<"" | "PAYSTACK" | "MANUAL">("");

  const [transactions, setTransactions] = useState<PaymentTransaction[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    setError(null);
    try {
      const data = await api.listPaymentTransactions(session.token, {
        from,
        to,
        direction: direction || undefined,
        gateway: gateway || undefined,
      });
      setTransactions(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load payment transactions.");
    }
  }, [session, from, to, direction, gateway]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    load().finally(() => setFetching(false));
  }, [session, load]);

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

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Payments" subtitle="Every payment transaction across the system — gateway and manual." />

      <Card className="flex flex-wrap items-end gap-3 p-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">From</label>
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">To</label>
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
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
          <label className="text-sm font-medium text-ink-700">Gateway</label>
          <select
            value={gateway}
            onChange={(e) => setGateway(e.target.value as "" | "PAYSTACK" | "MANUAL")}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          >
            <option value="">All</option>
            <option value="PAYSTACK">Paystack</option>
            <option value="MANUAL">Manual / cash</option>
          </select>
        </div>
        <Button onClick={() => { setFetching(true); load().finally(() => setFetching(false)); }} disabled={fetching}>
          {fetching ? "Updating..." : "Update"}
        </Button>
      </Card>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Gateway revenue"
          value={`GH₵${gatewayRevenue.toFixed(2)}`}
          hint="Real money settled through Paystack"
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

      <Card>
        {fetching ? (
          <TableSkeleton cols={6} />
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
                  <Td className="text-ink-500">{t.method ? t.method.replace("_", " ") : "—"}</Td>
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
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
