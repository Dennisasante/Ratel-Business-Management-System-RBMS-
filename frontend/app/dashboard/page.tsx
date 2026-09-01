"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Wrench, History, Sparkles, Link2, Copy, Check, ShoppingCart, Package, Receipt } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ActivityLogEntry,
  ServiceOrder,
  DashboardSummary,
  DashboardChart,
  DashboardAttention,
  TopProduct,
  TopProductRankMetric,
  ProductsNeedingAttention,
  InventorySnapshot,
  SalesBreakdown,
  SalesBreakdownDimension,
  Expense,
} from "@/lib/api";
import { DateRangeValue, defaultDateRangeValue } from "@/lib/dateRangePresets";
import DateRangeFilter from "@/components/ui/DateRangeFilter";
import Card from "@/components/ui/Card";
import StatCard from "@/components/ui/StatCard";
import CardSkeleton from "@/components/ui/CardSkeleton";
import BusinessOverviewGrid from "@/components/dashboard/BusinessOverviewGrid";
import SalesProfitChart from "@/components/dashboard/SalesProfitChart";
import NeedsAttentionCard from "@/components/dashboard/NeedsAttentionCard";
import TopProductsCard from "@/components/dashboard/TopProductsCard";
import ProductsNeedingAttentionCard from "@/components/dashboard/ProductsNeedingAttentionCard";
import InventorySnapshotCard from "@/components/dashboard/InventorySnapshotCard";
import SalesBreakdownCard from "@/components/dashboard/SalesBreakdownCard";
import ExpensesSummaryCard from "@/components/dashboard/ExpensesSummaryCard";
import ProfitabilitySummaryCard from "@/components/dashboard/ProfitabilitySummaryCard";

function timeOfDayGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

const QUICK_ACTIONS = [
  { href: "/dashboard/sales", label: "New sale", icon: ShoppingCart },
  { href: "/dashboard/service-orders", label: "New service order", icon: Wrench },
  { href: "/dashboard/inventory", label: "Add product", icon: Package },
  { href: "/dashboard/expenses", label: "Log expense", icon: Receipt },
];

export default function DashboardPage() {
  const { session, business, loading } = useAuth();
  const router = useRouter();
  const isStaff = session?.role === "STAFF";

  const [range, setRange] = useState<DateRangeValue>(defaultDateRangeValue());
  const [rankBy, setRankBy] = useState<TopProductRankMetric>("REVENUE");
  const [breakdownDimension, setBreakdownDimension] = useState<SalesBreakdownDimension>("PAYMENT_METHOD");

  // Date-filtered — refetched whenever `range` (or the Top Products/breakdown
  // selectors) changes.
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [chart, setChart] = useState<DashboardChart | null>(null);
  const [topProducts, setTopProducts] = useState<TopProduct[] | null>(null);
  const [salesBreakdown, setSalesBreakdown] = useState<SalesBreakdown | null>(null);
  const [expenses, setExpenses] = useState<Expense[] | null>(null);

  // Current-state — fetched once, never affected by the date filter.
  const [attention, setAttention] = useState<DashboardAttention | null>(null);
  const [productsNeedingAttention, setProductsNeedingAttention] = useState<ProductsNeedingAttention | null>(null);
  const [inventorySnapshot, setInventorySnapshot] = useState<InventorySnapshot | null>(null);
  const [recentActivity, setRecentActivity] = useState<ActivityLogEntry[]>([]);

  // STAFF-only
  const [activeServiceOrders, setActiveServiceOrders] = useState(0);

  const [fetching, setFetching] = useState(true);
  const [copiedLink, setCopiedLink] = useState(false);

  const loadStaffView = useCallback(async () => {
    if (!session) return;
    const orders = await api.listServiceOrders(session.token);
    setActiveServiceOrders(orders.filter((o: ServiceOrder) => o.status === "RECEIVED" || o.status === "IN_PROGRESS").length);
  }, [session]);

  // Everything that depends on the global date filter — one Promise.all,
  // not five separate round trips waiting on each other.
  const loadDateScoped = useCallback(async () => {
    if (!session) return;
    const [s, c, tp, sb, ex] = await Promise.all([
      api.getDashboardSummary(session.token, range.from, range.to),
      api.getDashboardChart(session.token, range.from, range.to),
      api.getTopProducts(session.token, range.from, range.to, rankBy, 10),
      api.getSalesBreakdown(session.token, range.from, range.to, breakdownDimension),
      api.listExpenses(session.token, { from: range.from ?? undefined, to: range.to ?? undefined }),
    ]);
    setSummary(s);
    setChart(c);
    setTopProducts(tp);
    setSalesBreakdown(sb);
    setExpenses(ex);
  }, [session, range, rankBy, breakdownDimension]);

  // Current-state sections — fetched once per page load, independent of the filter.
  const loadCurrentState = useCallback(async () => {
    if (!session) return;
    const [att, pna, inv, activity] = await Promise.all([
      api.getDashboardAttention(session.token),
      api.getProductsNeedingAttention(session.token),
      api.getInventorySnapshot(session.token),
      api.listActivityLogs(session.token),
    ]);
    setAttention(att);
    setProductsNeedingAttention(pna);
    setInventorySnapshot(inv);
    setRecentActivity(activity.slice(0, 6));
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  // Current-state sections load exactly once per page visit (tracked via a
  // ref, not state, so flipping it never itself triggers a re-render/effect
  // loop) — every later run of this effect (range/rankBy/dimension changes)
  // only refetches the date-scoped half.
  const loadedCurrentStateRef = useRef(false);
  useEffect(() => {
    if (!session) return;
    if (isStaff) {
      loadStaffView().finally(() => setFetching(false));
      return;
    }
    // Wait for the business record (plan features) before fetching, same as before.
    if (!business) return;

    const tasks: Promise<unknown>[] = [loadDateScoped()];
    if (!loadedCurrentStateRef.current) {
      loadedCurrentStateRef.current = true;
      tasks.push(loadCurrentState());
    }
    Promise.all(tasks).finally(() => setFetching(false));
    // loadDateScoped/loadCurrentState/loadStaffView are recreated from the
    // deps already listed below, so omitting the functions themselves is safe.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, isStaff, business, range, rankBy, breakdownDimension]);

  async function copyCustomerLink() {
    if (!business) return;
    const origin = typeof window !== "undefined" ? window.location.origin : "";
    await navigator.clipboard.writeText(`${origin}/start/${business.slug}`);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const origin = typeof window !== "undefined" ? window.location.origin : "";
  const customerLinkUrl = business ? `${origin}/start/${business.slug}` : "";

  return (
    <div className="flex flex-col gap-6">
      <div className="relative isolate overflow-hidden rounded-2xl bg-gradient-to-br from-accent to-[#00234f] px-6 py-7 shadow-panel sm:px-8 sm:py-8">
        <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
          <div className="animate-blob-drift absolute -right-12 -top-16 h-56 w-56 rounded-full bg-white/10 blur-3xl" />
          <div className="animate-blob-drift absolute -bottom-20 left-1/4 h-48 w-48 rounded-full bg-info/30 blur-3xl [animation-delay:-6s]" />
        </div>
        <Sparkles size={72} strokeWidth={1} className="pointer-events-none absolute -right-2 -top-2 text-white/10 sm:h-24 sm:w-24" aria-hidden />
        <p className="text-sm font-medium text-white/70">
          {business?.name ?? session.businessName} · {session.role}
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-white sm:text-3xl">
          {timeOfDayGreeting()}, {session.fullName.split(" ")[0]}!
        </h1>
        <p className="mt-2 text-sm text-white/80">Here&apos;s a quick look at how things are going.</p>
      </div>

      {isStaff ? (
        fetching ? (
          <CardSkeleton count={4} />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Your service orders"
              value={activeServiceOrders}
              hint="Received or in progress"
              icon={Wrench}
              tone="info"
            />
          </div>
        )
      ) : (
        <>
          {/* One global date filter drives every period-based metric below —
              current-state sections (Needs Attention, Products Needing
              Attention, Inventory Snapshot) are unaffected by it. */}
          <Card className="p-4">
            <DateRangeFilter value={range} onChange={setRange} />
          </Card>

          {fetching ? (
            <CardSkeleton count={4} />
          ) : (
            <>
              <BusinessOverviewGrid summary={summary!} />

              <SalesProfitChart chart={chart} />

              <NeedsAttentionCard attention={attention} />

              <TopProductsCard products={topProducts} rankBy={rankBy} onRankByChange={setRankBy} />

              <ProductsNeedingAttentionCard data={productsNeedingAttention} />

              <div className="grid gap-6 lg:grid-cols-2">
                <InventorySnapshotCard snapshot={inventorySnapshot} />
                <SalesBreakdownCard
                  breakdown={salesBreakdown}
                  dimension={breakdownDimension}
                  onDimensionChange={setBreakdownDimension}
                />
              </div>

              <div className="grid gap-6 lg:grid-cols-2">
                <ExpensesSummaryCard expenses={expenses} />
                <ProfitabilitySummaryCard summary={summary} />
              </div>

              {business && (
                <Card className="flex flex-col gap-3 border-accent/20 bg-accent-soft/40 p-5 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex items-start gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-accent-hover">
                      <Link2 size={16} />
                    </span>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ink-900">Your customer link</p>
                      <p className="mt-0.5 text-xs text-ink-500">
                        One link for booking, custom orders, and more — share it anywhere, WhatsApp bio included.
                      </p>
                      <code className="mt-2 block max-w-full truncate rounded-lg bg-surface px-2.5 py-1.5 text-xs font-medium text-ink-700 sm:hidden">
                        {customerLinkUrl}
                      </code>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <code className="hidden max-w-[240px] truncate rounded-lg bg-surface px-2.5 py-1.5 text-xs font-medium text-ink-700 sm:block">
                      {customerLinkUrl}
                    </code>
                    <button
                      onClick={copyCustomerLink}
                      className="flex items-center gap-1.5 whitespace-nowrap rounded-full bg-accent px-3.5 py-2 text-xs font-semibold text-white transition hover:opacity-90"
                    >
                      {copiedLink ? <Check size={13} /> : <Copy size={13} />}
                      {copiedLink ? "Copied" : "Copy link"}
                    </button>
                  </div>
                </Card>
              )}

              <div className="grid gap-6 lg:grid-cols-2">
                <Card className="p-5">
                  <h2 className="text-base font-semibold text-ink-900">Quick actions</h2>
                  <p className="mt-1 text-sm text-ink-500">Jump straight into your most common tasks.</p>
                  <div className="mt-4 grid grid-cols-2 gap-3">
                    {QUICK_ACTIONS.map((action) => (
                      <Link
                        key={action.href}
                        href={action.href}
                        className="flex flex-col items-start gap-2 rounded-lg border border-border p-3 text-sm transition hover:border-accent hover:bg-accent-soft"
                      >
                        <span className="flex h-8 w-8 items-center justify-center rounded-md bg-accent-soft text-accent-hover">
                          <action.icon size={16} />
                        </span>
                        <span className="font-medium text-ink-900">{action.label}</span>
                      </Link>
                    ))}
                  </div>
                </Card>

                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <h2 className="text-base font-semibold text-ink-900">Recent activity</h2>
                    <Link href="/dashboard/activity" className="flex items-center gap-1 text-xs font-medium text-accent-hover hover:underline">
                      <History size={12} /> View all
                    </Link>
                  </div>
                  {recentActivity.length === 0 ? (
                    <p className="mt-4 text-sm text-ink-500">Nothing yet — activity will show up here as your team works.</p>
                  ) : (
                    <ul className="mt-4 space-y-3">
                      {recentActivity.map((log) => (
                        <li key={log.id} className="flex items-start justify-between gap-3 border-b border-border pb-3 text-sm last:border-0 last:pb-0">
                          <div>
                            <p className="text-ink-900">{log.action}</p>
                            <p className="mt-0.5 text-xs text-ink-500">{log.userName}</p>
                          </div>
                          <span className="shrink-0 whitespace-nowrap text-xs text-ink-500">{timeAgo(log.createdAt)}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </Card>
              </div>

              <div className="flex items-center justify-between rounded-xl border border-border bg-surface px-5 py-4 shadow-card">
                <div>
                  <p className="text-sm font-medium text-ink-900">Want to update your business logo, contact info, or industry?</p>
                  <p className="text-xs text-ink-500">That all lives in your business profile now.</p>
                </div>
                <Link href="/dashboard/profile" className="shrink-0 text-sm font-medium text-accent-hover hover:underline">
                  Go to profile →
                </Link>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}
