"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Clock, Wallet } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, ServiceOrderReport, ServiceOrderStatus } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import StatCard from "@/components/ui/StatCard";
import CardSkeleton from "@/components/ui/CardSkeleton";

const STATUS_LABELS: Record<ServiceOrderStatus, string> = {
  RECEIVED: "Received",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  PICKED_UP: "Picked up",
  CANCELLED: "Cancelled",
};

const STATUS_TONES: Record<ServiceOrderStatus, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  RECEIVED: "info",
  IN_PROGRESS: "accent",
  COMPLETED: "success",
  PICKED_UP: "violet",
  CANCELLED: "danger",
};

function firstDayOfMonth(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function ServiceOrdersReportPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [from, setFrom] = useState(firstDayOfMonth());
  const [to, setTo] = useState(today());
  const [report, setReport] = useState<ServiceOrderReport | null>(null);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadReport = useCallback(async () => {
    if (!session) return;
    setError(null);
    try {
      const r = await api.getServiceOrderReport(session.token, from, to);
      setReport(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load the report.");
    }
  }, [session, from, to]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadReport().finally(() => setFetching(false));
  }, [session, loadReport]);

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const totalRevenue = report ? report.revenueByType.reduce((sum, r) => sum + r.revenue, 0) : 0;
  const totalReceived = report ? Object.values(report.statusCounts).reduce((a, b) => a + b, 0) : 0;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link href="/dashboard/service-orders" className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-900">
          <ArrowLeft size={14} /> Back to service orders
        </Link>
      </div>

      <PageHeader title="Service Orders Report" subtitle="Revenue, status mix and turnaround for any date range." />

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
        <Button onClick={() => { setFetching(true); loadReport().finally(() => setFetching(false)); }} disabled={fetching}>
          {fetching ? "Updating..." : "Update"}
        </Button>
      </Card>

      {error && <p className="text-sm text-danger">{error}</p>}

      {fetching ? (
        <CardSkeleton count={3} />
      ) : report ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <StatCard
              label="Revenue (picked up in range)"
              value={`GH₵${totalRevenue.toFixed(2)}`}
              hint={`${report.from} → ${report.to}`}
              icon={Wallet}
              tone="success"
            />
            <StatCard
              label="Avg turnaround"
              value={`${report.avgTurnaroundHours.toFixed(1)}h`}
              hint="Received to picked up"
              icon={Clock}
              tone="info"
            />
          </div>

          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Revenue by category</h2>
            <p className="text-xs text-ink-500">Orders picked up in this range.</p>
            {report.revenueByType.length === 0 ? (
              <p className="mt-3 text-sm text-ink-500">No categories set up yet.</p>
            ) : (
              <ul className="mt-4 flex flex-col gap-2">
                {report.revenueByType.map((r) => (
                  <li key={r.serviceTypeId} className="flex items-center justify-between border-b border-border py-2 text-sm last:border-0">
                    <span className="text-ink-700">{r.serviceTypeName ?? "—"}</span>
                    <span className="tabular font-medium text-ink-900">GH₵{r.revenue.toFixed(2)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Status mix</h2>
            <p className="text-xs text-ink-500">Of the {totalReceived} order{totalReceived === 1 ? "" : "s"} received in this range.</p>
            <div className="mt-4 flex flex-wrap gap-3">
              {(Object.keys(STATUS_LABELS) as ServiceOrderStatus[]).map((s) => (
                <div key={s} className="flex items-center gap-2 rounded-lg border border-border px-3 py-2">
                  <Badge tone={STATUS_TONES[s]}>{STATUS_LABELS[s]}</Badge>
                  <span className="tabular text-sm font-semibold text-ink-900">{report.statusCounts[s] ?? 0}</span>
                </div>
              ))}
            </div>
          </Card>
        </>
      ) : null}
    </div>
  );
}
