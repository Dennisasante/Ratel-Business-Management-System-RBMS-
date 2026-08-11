"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Building2, Users, TrendingUp, Wallet, CalendarDays, ShoppingBag, Sparkles, Wrench, ShieldCheck } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, PlatformBusinessSummary, PlatformStats } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import StatCard from "@/components/ui/StatCard";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import MiniBarChart from "@/components/ui/MiniBarChart";
import CardSkeleton from "@/components/ui/CardSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const BILLING_STATUS_LABEL: Record<string, string> = {
  TRIALING: "Trialing",
  ACTIVE: "Active",
  GRACE: "Grace period",
  READ_ONLY: "Read-only",
};

const BILLING_STATUS_TONE: Record<string, "success" | "info" | "danger"> = {
  ACTIVE: "success",
  TRIALING: "info",
  GRACE: "danger",
  READ_ONLY: "danger",
};

export default function PlatformOverviewPage() {
  const { session } = usePlatformAuth();
  const [businesses, setBusinesses] = useState<PlatformBusinessSummary[]>([]);
  const [stats, setStats] = useState<PlatformStats | null>(null);
  const [fetching, setFetching] = useState(true);

  useEffect(() => {
    if (!session) return;
    Promise.all([api.listPlatformBusinesses(session.token), api.getPlatformStats(session.token)])
      .then(([b, s]) => {
        setBusinesses(b);
        setStats(s);
      })
      .finally(() => setFetching(false));
  }, [session]);

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <div className="relative isolate overflow-hidden rounded-2xl border border-sidebar-border bg-sidebar px-6 py-7 shadow-panel sm:px-8 sm:py-8">
          <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
            <div className="animate-blob-drift absolute -right-12 -top-16 h-56 w-56 rounded-full bg-accent/25 blur-3xl" />
            <div className="animate-blob-drift absolute -bottom-20 left-1/4 h-48 w-48 rounded-full bg-info/20 blur-3xl [animation-delay:-6s]" />
          </div>
          <ShieldCheck size={72} strokeWidth={1} className="pointer-events-none absolute -right-2 -top-2 text-white/10 sm:h-24 sm:w-24" aria-hidden />
          <div className="inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#7fa5e8" }}>
            <ShieldCheck size={12} /> Restricted access
          </div>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-sidebar-text-active sm:text-3xl">Platform Overview</h1>
          <p className="mt-2 text-sm text-sidebar-text">
            {stats ? `${stats.totalBusinesses} businesses, ${stats.totalUsers} users, GH₵${stats.totalPlatformRevenue.toFixed(2)} in lifetime revenue.` : "Every business running on Ratel, at a glance."}
          </p>
        </div>

        {fetching || !stats ? (
          <CardSkeleton count={4} />
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard label="Businesses" value={stats.totalBusinesses} hint={`${stats.activeBusinesses} active`} icon={Building2} tone="accent" />
              <StatCard label="Total users" value={stats.totalUsers} icon={Users} tone="info" />
              <StatCard
                label="Platform revenue"
                value={`GH₵${stats.totalPlatformRevenue.toFixed(2)}`}
                hint="All businesses, all time"
                icon={Wallet}
                tone="success"
              />
              <StatCard label="Signups (30d)" value={stats.signupsByDay.reduce((s, d) => s + d.count, 0)} icon={TrendingUp} tone="danger" />
            </div>

            <div>
              <h2 className="mb-3 text-sm font-semibold text-ink-500">Feature usage, platform-wide</h2>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <StatCard label="Bookings" value={stats.totalBookings} icon={CalendarDays} tone="accent" />
                <StatCard label="E-commerce orders" value={stats.totalEcommerceOrders} icon={ShoppingBag} tone="info" />
                <StatCard label="Custom wig requests" value={stats.totalCustomWigRequests} icon={Sparkles} tone="danger" />
                <StatCard label="Service orders" value={stats.totalServiceOrders} icon={Wrench} tone="success" />
              </div>
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
              <Card className="p-5">
                <h2 className="text-base font-semibold text-ink-900">New businesses — last 30 days</h2>
                <div className="mt-3">
                  <MiniBarChart data={stats.signupsByDay} />
                </div>
              </Card>
              <Card className="p-5">
                <h2 className="text-base font-semibold text-ink-900">Activity — last 30 days</h2>
                <div className="mt-3">
                  <MiniBarChart data={stats.activityByDay} color="#0E7C86" />
                </div>
              </Card>
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
              <Card className="p-5">
                <h2 className="text-base font-semibold text-ink-900">Billing status</h2>
                <div className="mt-4 flex flex-col gap-3">
                  {stats.billingStatusBreakdown.length === 0 ? (
                    <p className="text-sm text-ink-500">No businesses yet.</p>
                  ) : (
                    stats.billingStatusBreakdown.map((b) => (
                      <div key={b.status} className="flex items-center justify-between">
                        <Badge tone={BILLING_STATUS_TONE[b.status] ?? "info"}>
                          {BILLING_STATUS_LABEL[b.status] ?? b.status}
                        </Badge>
                        <span className="tabular text-sm font-semibold text-ink-900">{b.count}</span>
                      </div>
                    ))
                  )}
                </div>
              </Card>
              <Card className="p-5">
                <h2 className="text-base font-semibold text-ink-900">Plan mix</h2>
                <p className="mt-1 text-xs text-ink-500">MRR reflects only currently-paying (Active) businesses on each plan.</p>
                {stats.planMix.length === 0 ? (
                  <p className="mt-4 text-sm text-ink-500">No businesses on a paid plan yet.</p>
                ) : (
                  <div className="mt-4 flex flex-col gap-3">
                    {stats.planMix.map((p) => (
                      <div key={p.planName} className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium text-ink-900">{p.planName}</p>
                          <p className="text-xs text-ink-500">{p.businessCount} business{p.businessCount === 1 ? "" : "es"}</p>
                        </div>
                        <span className="tabular text-sm font-semibold text-ink-900">GH₵{p.mrr.toFixed(2)}/mo</span>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            </div>

            <Card>
              <div className="flex items-center justify-between p-5 pb-0">
                <h2 className="text-base font-semibold text-ink-900">Recent businesses</h2>
                <Link href="/platform/businesses" className="text-sm font-medium text-accent-hover hover:underline">
                  View all
                </Link>
              </div>
              <div className="mt-3">
                <Table>
                  <THead>
                    <Tr>
                      <Th>Business</Th>
                      <Th>Industry</Th>
                      <Th>Owner</Th>
                      <Th>Users</Th>
                      <Th>Status</Th>
                    </Tr>
                  </THead>
                  <TBody>
                    {businesses.slice(0, 8).map((b) => (
                      <Tr key={b.id}>
                        <Td>
                          <Link href={`/platform/businesses/${b.id}`} className="font-medium text-ink-900 hover:underline">
                            {b.name}
                          </Link>
                        </Td>
                        <Td className="text-ink-500">{b.industry}</Td>
                        <Td className="text-ink-500">{b.ownerEmail}</Td>
                        <Td className="tabular text-ink-500">{b.userCount}</Td>
                        <Td>
                          <Badge tone={b.active ? "success" : "danger"}>{b.active ? "Active" : "Inactive"}</Badge>
                        </Td>
                      </Tr>
                    ))}
                  </TBody>
                </Table>
              </div>
            </Card>
          </>
        )}
      </div>
    </PlatformShell>
  );
}
