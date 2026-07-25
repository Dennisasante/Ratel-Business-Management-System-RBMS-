"use client";

import { useEffect, useState } from "react";
import { ShieldAlert } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, PlatformAuditLogEntry } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";

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

export default function PlatformAdminActionsPage() {
  const { session } = usePlatformAuth();
  const [logs, setLogs] = useState<PlatformAuditLogEntry[]>([]);
  const [fetching, setFetching] = useState(true);

  useEffect(() => {
    if (!session) return;
    api.listPlatformAuditLogs(session.token)
      .then(setLogs)
      .finally(() => setFetching(false));
  }, [session]);

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Admin Actions"
          subtitle="Every action taken as Super Admin — suspensions, deletions, password resets. Survives even if the business itself is later deleted."
        />

        <Card>
          {fetching ? (
            <TableSkeleton cols={2} />
          ) : logs.length === 0 ? (
            <EmptyState icon={ShieldAlert} title="No admin actions yet" description="Suspensions, deletions, and password resets will show up here." />
          ) : (
            <ul className="divide-y divide-border">
              {logs.map((log) => (
                <li key={log.id} className="flex items-start justify-between gap-4 px-5 py-3 text-sm">
                  <div>
                    <p className="text-ink-900">{log.action}</p>
                    <p className="mt-0.5 text-xs text-ink-500">{log.adminName}</p>
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
