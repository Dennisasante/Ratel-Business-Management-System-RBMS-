"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Users,
  Wallet,
  Wrench,
  AlertTriangle,
  ShoppingCart,
  ShoppingBag,
  Package,
  Receipt,
  History,
  Sparkles,
  ChevronRight,
  Link2,
  Copy,
  Check,
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ActivityLogEntry, ServiceOrder, UserSummary } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import StatCard from "@/components/ui/StatCard";
import CardSkeleton from "@/components/ui/CardSkeleton";

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

function AttentionRow({
  href,
  icon: Icon,
  label,
  count,
  zeroHint,
}: {
  href: string;
  icon: typeof AlertTriangle;
  label: string;
  count: number;
  zeroHint: string;
}) {
  return (
    <Link
      href={href}
      className="flex items-center justify-between gap-3 border-b border-border py-3 text-sm last:border-0 transition hover:bg-canvas -mx-1 px-1 rounded-md"
    >
      <div className="flex items-center gap-3">
        <span
          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${
            count > 0 ? "bg-danger-soft text-danger" : "bg-canvas text-ink-500"
          }`}
        >
          <Icon size={15} />
        </span>
        <div>
          <p className="font-medium text-ink-900">{label}</p>
          <p className="text-xs text-ink-500">{count > 0 ? `${count} need${count === 1 ? "s" : ""} your attention` : zeroHint}</p>
        </div>
      </div>
      <div className="flex items-center gap-1.5">
        {count > 0 && (
          <span className="rounded-full bg-danger px-2 py-0.5 text-xs font-semibold text-white">{count}</span>
        )}
        <ChevronRight size={15} className="text-ink-500" />
      </div>
    </Link>
  );
}

export default function DashboardPage() {
  const { session, business, loading } = useAuth();
  const router = useRouter();
  const isStaff = session?.role === "STAFF";
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [todayRevenue, setTodayRevenue] = useState(0);
  const [todaySalesCount, setTodaySalesCount] = useState(0);
  const [activeServiceOrders, setActiveServiceOrders] = useState(0);
  const [lowStockCount, setLowStockCount] = useState(0);
  const [newCustomWigCount, setNewCustomWigCount] = useState(0);
  const [pendingEcommerceCount, setPendingEcommerceCount] = useState(0);
  const [recentActivity, setRecentActivity] = useState<ActivityLogEntry[]>([]);
  const [fetching, setFetching] = useState(true);
  const [copiedLink, setCopiedLink] = useState(false);

  const features = business?.planFeatures ?? [];
  const hasCustomWig = features.includes("CUSTOM_WIG_REQUESTS");
  const hasEcommerce = features.includes("WOOCOMMERCE_SYNC");

  // STAFF only ever sees their own scheduled work — the business-wide
  // financial/staffing/audit stats below aren't theirs to see, so skip
  // fetching them entirely rather than fetch-then-hide.
  const loadDashboard = useCallback(async () => {
    if (!session) return;
    if (isStaff) {
      const orders = await api.listServiceOrders(session.token);
      setActiveServiceOrders(orders.filter((o: ServiceOrder) => o.status === "RECEIVED" || o.status === "IN_PROGRESS").length);
      return;
    }
    const today = new Date().toISOString().slice(0, 10);
    const [u, summary, orders, lowStock, activity] = await Promise.all([
      api.listUsers(session.token),
      api.getReportSummary(session.token, today, today),
      api.listServiceOrders(session.token),
      api.listLowStockProducts(session.token),
      api.listActivityLogs(session.token),
    ]);
    setUsers(u);
    setTodayRevenue(summary.revenue);
    setTodaySalesCount(summary.salesCount);
    setActiveServiceOrders(orders.filter((o: ServiceOrder) => o.status === "RECEIVED" || o.status === "IN_PROGRESS").length);
    setLowStockCount(lowStock.length);
    setRecentActivity(activity.slice(0, 6));

    const featureFetches: Promise<void>[] = [];
    if (hasCustomWig) {
      featureFetches.push(
        api.listCustomWigRequests(session.token).then((reqs) => {
          setNewCustomWigCount(reqs.filter((r) => r.status === "SUBMITTED").length);
        })
      );
    }
    if (hasEcommerce) {
      featureFetches.push(
        api.listEcommerceOrders(session.token).then((ecomOrders) => {
          setPendingEcommerceCount(ecomOrders.filter((o) => o.status === "RECEIVED").length);
        })
      );
    }
    await Promise.all(featureFetches);
  }, [session, isStaff, hasCustomWig, hasEcommerce]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    // Wait for the business record (and its plan features) to load before
    // fetching owner-facing stats, so the feature-gated fetches below run on
    // the first pass instead of a silent, panel-flashing second one.
    if (!isStaff && !business) return;
    loadDashboard().finally(() => setFetching(false));
  }, [session, isStaff, business, loadDashboard]);

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
      <PageHeader
        title={`${timeOfDayGreeting()}, ${session.fullName.split(" ")[0]}`}
        subtitle={`${business?.name ?? session.businessName} · ${session.role}`}
      />

      {fetching ? (
        <CardSkeleton count={4} />
      ) : isStaff ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Your service orders"
            value={activeServiceOrders}
            hint="Received or in progress"
            icon={Wrench}
            tone="info"
          />
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <StatCard label="Team members" value={users.length} icon={Users} tone="accent" />
            <StatCard
              label="Today's revenue"
              value={`GH₵${todayRevenue.toFixed(2)}`}
              hint={`${todaySalesCount} sale${todaySalesCount === 1 ? "" : "s"} today`}
              icon={Wallet}
              tone="success"
            />
            <StatCard
              label="Active service orders"
              value={activeServiceOrders}
              hint="Received or in progress"
              icon={Wrench}
              tone="info"
            />
          </div>

          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Needs your attention</h2>
            <p className="mt-1 text-sm text-ink-500">A running list of what's waiting on you today.</p>
            <div className="mt-3 flex flex-col">
              <AttentionRow
                href="/dashboard/inventory"
                icon={AlertTriangle}
                label="Low stock alerts"
                count={lowStockCount}
                zeroHint="All good"
              />
              {hasCustomWig && (
                <AttentionRow
                  href="/dashboard/custom-wig-requests"
                  icon={Sparkles}
                  label="New custom wig requests"
                  count={newCustomWigCount}
                  zeroHint="All caught up"
                />
              )}
              {hasEcommerce && (
                <AttentionRow
                  href="/dashboard/ecommerce-orders"
                  icon={ShoppingBag}
                  label="E-commerce orders to fulfill"
                  count={pendingEcommerceCount}
                  zeroHint="All caught up"
                />
              )}
            </div>
          </Card>

          {business && (
            <Card className="flex flex-col gap-3 border-accent/20 bg-accent-soft/40 p-5 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-accent-hover">
                  <Link2 size={16} />
                </span>
                <div>
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
    </div>
  );
}
