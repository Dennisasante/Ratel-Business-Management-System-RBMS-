"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { History } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, ActivityLogEntry, PlatformBusinessSummary } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import DateRangeFilter from "@/components/ui/DateRangeFilter";
import { DateRangeValue, defaultDateRangeValue } from "@/lib/dateRangePresets";

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

export default function PlatformActivityPage() {
  const { session } = usePlatformAuth();
  const [logs, setLogs] = useState<ActivityLogEntry[]>([]);
  const [options, setOptions] = useState<ActivityLogEntry[]>([]);
  const [allBusinesses, setAllBusinesses] = useState<PlatformBusinessSummary[]>([]);
  const [fetching, setFetching] = useState(true);

  const [businessId, setBusinessId] = useState("");
  const [userId, setUserId] = useState("");
  const [dateRange, setDateRange] = useState<DateRangeValue>(defaultDateRangeValue());

  const load = useCallback(async () => {
    if (!session) return;
    setLogs(
      await api.listPlatformActivityLogs(session.token, {
        businessId: businessId || undefined,
        userId: userId || undefined,
        from: dateRange.from || undefined,
        to: dateRange.to || undefined,
      })
    );
  }, [session, businessId, userId, dateRange]);

  // Unfiltered pull once, just to populate the staff dropdown options.
  useEffect(() => {
    if (!session) return;
    api.listPlatformActivityLogs(session.token).then(setOptions);
  }, [session]);

  // Every business, not just ones with recent activity — a capped recent-logs
  // sample would silently omit a quiet business from the dropdown, making it
  // impossible to ever select (looked like "the business filter doesn't work").
  useEffect(() => {
    if (!session) return;
    api.listPlatformBusinesses(session.token).then(setAllBusinesses);
  }, [session]);

  useEffect(() => {
    load().finally(() => setFetching(false));
  }, [load]);

  const businesses = useMemo(
    () =>
      allBusinesses
        .map((b) => [b.id, b.name] as [string, string])
        .sort((a, b) => a[1].localeCompare(b[1])),
    [allBusinesses]
  );

  const staff = useMemo(() => {
    const map = new Map<string, string>();
    options.forEach((l) => l.userId && map.set(l.userId, l.userName));
    return Array.from(map.entries());
  }, [options]);

  function clearFilters() {
    setBusinessId("");
    setUserId("");
    setDateRange(defaultDateRangeValue());
  }

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader title="Activity Log" subtitle="Every logged action across every business, most recent first." />

        <Card className="flex flex-col gap-3 p-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">Business</label>
              <select
                value={businessId}
                onChange={(e) => setBusinessId(e.target.value)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                <option value="">All businesses</option>
                {businesses.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">Staff member</label>
              <select
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                <option value="">Everyone</option>
                {staff.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
            {(businessId || userId || dateRange.preset !== "today") && (
              <button onClick={clearFilters} className="text-sm font-medium text-accent-hover hover:underline">
                Clear filters
              </button>
            )}
          </div>
          <DateRangeFilter value={dateRange} onChange={setDateRange} />
        </Card>

        <Card>
          {fetching ? (
            <TableSkeleton cols={2} />
          ) : logs.length === 0 ? (
            <EmptyState icon={History} title="No activity found" description="Try clearing the filters." />
          ) : (
            <ul className="divide-y divide-border">
              {logs.map((log) => (
                <li key={log.id} className="flex items-start justify-between gap-4 px-5 py-3 text-sm">
                  <div>
                    <p className="text-ink-900">{log.action}</p>
                    <p className="mt-0.5 text-xs text-ink-500">
                      {log.businessName} · {log.userName}
                    </p>
                  </div>
                  <span className="shrink-0 whitespace-nowrap text-xs text-ink-500">{timeAgo(log.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </PlatformShell>
  );
}
