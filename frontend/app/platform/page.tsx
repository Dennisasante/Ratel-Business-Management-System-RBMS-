"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Building2, Users, TrendingUp, Wallet } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, PlatformBusinessSummary, PlatformStats } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import StatCard from "@/components/ui/StatCard";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import MiniBarChart from "@/components/ui/MiniBarChart";
import CardSkeleton from "@/components/ui/CardSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

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
        <PageHeader title="Platform Overview" subtitle="Every business running on Ratel, at a glance." />

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
