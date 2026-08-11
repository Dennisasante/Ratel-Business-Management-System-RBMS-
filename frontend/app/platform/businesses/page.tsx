"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { Building2, Search } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, PlatformBusinessSummary } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

type StatusFilter = "all" | "active" | "inactive";

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

export default function PlatformBusinessesPage() {
  const { session } = usePlatformAuth();
  const [businesses, setBusinesses] = useState<PlatformBusinessSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<StatusFilter>("all");

  const load = useCallback(async () => {
    if (!session) return;
    const active = status === "all" ? undefined : status === "active";
    setBusinesses(await api.listPlatformBusinesses(session.token, query || undefined, active));
  }, [session, query, status]);

  useEffect(() => {
    load().finally(() => setFetching(false));
  }, [load]);

  // Debounce search-as-you-type slightly so every keystroke doesn't fire a request.
  useEffect(() => {
    const t = setTimeout(() => {
      if (session) load();
    }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, status]);

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader title="Businesses" subtitle={`${businesses.length} matching`} />

        <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-500" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by business name..."
              className="w-full rounded-lg border border-border bg-surface py-2 pl-9 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as StatusFilter)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          >
            <option value="all">All statuses</option>
            <option value="active">Active only</option>
            <option value="inactive">Suspended only</option>
          </select>
        </Card>

        <Card>
          {fetching ? (
            <TableSkeleton cols={8} />
          ) : businesses.length === 0 ? (
            <EmptyState icon={Building2} title="No matching businesses" description="Try a different search or filter." />
          ) : (
            <Table>
              <THead>
                <Tr>
                  <Th>Business</Th>
                  <Th>Industry</Th>
                  <Th>Location</Th>
                  <Th>Owner</Th>
                  <Th>Users</Th>
                  <Th>Plan</Th>
                  <Th>Billing</Th>
                  <Th>Status</Th>
                </Tr>
              </THead>
              <TBody>
                {businesses.map((b) => (
                  <Tr key={b.id}>
                    <Td>
                      <Link href={`/platform/businesses/${b.id}`} className="font-medium text-ink-900 hover:underline">
                        {b.name}
                      </Link>
                    </Td>
                    <Td className="text-ink-500">{b.industry}</Td>
                    <Td className="text-ink-500">{b.location ?? "—"}</Td>
                    <Td className="text-ink-500">{b.ownerEmail}</Td>
                    <Td className="tabular text-ink-500">{b.userCount}</Td>
                    <Td className="text-ink-500">{b.subscriptionPlan}</Td>
                    <Td>
                      <Badge tone={BILLING_STATUS_TONE[b.billingStatus] ?? "info"}>
                        {BILLING_STATUS_LABEL[b.billingStatus] ?? b.billingStatus}
                      </Badge>
                    </Td>
                    <Td>
                      <Badge tone={b.active ? "success" : "danger"}>{b.active ? "Active" : "Suspended"}</Badge>
                    </Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
          )}
        </Card>
      </div>
    </PlatformShell>
  );
}
