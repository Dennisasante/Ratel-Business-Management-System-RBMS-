"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { History } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ActivityLogEntry, UserSummary } from "@/lib/api";
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

export default function ActivityPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const [logs, setLogs] = useState<ActivityLogEntry[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [fetching, setFetching] = useState(true);

  const [userId, setUserId] = useState("");
  const [dateRange, setDateRange] = useState<DateRangeValue>(defaultDateRangeValue());

  const loadLogs = useCallback(async () => {
    if (!session) return;
    setLogs(
      await api.listActivityLogs(session.token, {
        userId: userId || undefined,
        from: dateRange.from || undefined,
        to: dateRange.to || undefined,
      })
    );
  }, [session, userId, dateRange]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    api.listUsers(session.token).then(setUsers);
  }, [session]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadLogs().finally(() => setFetching(false));
  }, [session, loadLogs]);

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Activity Log" subtitle="Everything happening on your team's account, most recent first." />

      <Card className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Staff member</label>
            <select
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              <option value="">Everyone</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName}
                </option>
              ))}
            </select>
          </div>
          {(userId || dateRange.preset !== "today") && (
            <button
              onClick={() => {
                setUserId("");
                setDateRange(defaultDateRangeValue());
              }}
              className="text-sm font-medium text-accent-hover hover:underline"
            >
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
          <EmptyState icon={History} title="No activity found" description="Try clearing the filters, or check back after your team's been active." />
        ) : (
          <ul className="divide-y divide-border">
            {logs.map((log) => (
              <li key={log.id} className="flex items-start justify-between gap-4 px-5 py-3 text-sm">
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
  );
}
