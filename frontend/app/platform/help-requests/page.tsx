"use client";

import { useEffect, useState, useCallback } from "react";
import { LifeBuoy } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, ApiError, PlatformHelpRequest, HelpRequestCategory } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Modal from "@/components/Modal";

const CATEGORY_LABELS: Record<HelpRequestCategory, string> = {
  GENERAL: "General question",
  BUG: "Something's broken",
  BILLING: "Billing",
  FEATURE_REQUEST: "Feature request",
};

export default function PlatformHelpRequestsPage() {
  const { session } = usePlatformAuth();
  const [requests, setRequests] = useState<PlatformHelpRequest[]>([]);
  const [fetching, setFetching] = useState(true);
  const [statusFilter, setStatusFilter] = useState<"OPEN" | "RESOLVED" | "">("OPEN");
  const [active, setActive] = useState<PlatformHelpRequest | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const data = await api.listPlatformHelpRequests(session.token);
    setRequests(data);
  }, [session]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    load().finally(() => setFetching(false));
  }, [session, load]);

  const visible = statusFilter ? requests.filter((r) => r.status === statusFilter) : requests;

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Help Requests"
          subtitle="Support messages sent in by business owners and staff, across every business."
        />

        <div className="flex flex-wrap gap-2">
          <FilterChip label="Awaiting reply" active={statusFilter === "OPEN"} onClick={() => setStatusFilter("OPEN")} />
          <FilterChip label="Resolved" active={statusFilter === "RESOLVED"} onClick={() => setStatusFilter("RESOLVED")} />
          <FilterChip label="All" active={statusFilter === ""} onClick={() => setStatusFilter("")} />
        </div>

        <Card>
          {fetching ? (
            <TableSkeleton cols={5} />
          ) : visible.length === 0 ? (
            <EmptyState
              icon={LifeBuoy}
              title="Nothing here"
              description="Support requests from businesses will show up here."
            />
          ) : (
            <ul className="divide-y divide-border">
              {visible.map((r) => (
                <li key={r.id} className="px-5 py-4">
                  <button onClick={() => setActive(r)} className="flex w-full flex-col gap-1 text-left sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-ink-900">{r.subject}</p>
                      <p className="mt-0.5 text-xs text-ink-500">
                        {r.businessName} &middot; {r.requesterName} &middot; {CATEGORY_LABELS[r.category]}
                      </p>
                      <p className="mt-1 truncate text-sm text-ink-700">{r.message}</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <Badge tone={r.status === "OPEN" ? "info" : "success"}>
                        {r.status === "OPEN" ? "Awaiting reply" : "Resolved"}
                      </Badge>
                      <span className="whitespace-nowrap text-xs text-ink-500">
                        {new Date(r.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {active && (
        <RequestModal
          request={active}
          token={session!.token}
          onClose={() => setActive(null)}
          onResponded={(updated) => {
            setRequests((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
            setActive(null);
          }}
        />
      )}
    </PlatformShell>
  );
}

function RequestModal({
  request,
  token,
  onClose,
  onResponded,
}: {
  request: PlatformHelpRequest;
  token: string;
  onClose: () => void;
  onResponded: (updated: PlatformHelpRequest) => void;
}) {
  const [response, setResponse] = useState(request.adminResponse ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRespond(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const updated = await api.respondPlatformHelpRequest(token, request.id, response);
      onResponded(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't send that reply.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={request.subject} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <Badge tone={request.status === "OPEN" ? "info" : "success"}>
            {request.status === "OPEN" ? "Awaiting reply" : "Resolved"}
          </Badge>
          <span className="text-sm text-ink-500">{new Date(request.createdAt).toLocaleString()}</span>
        </div>

        <div className="flex flex-col gap-1 text-sm">
          <p className="font-medium text-ink-900">{request.businessName}</p>
          <p className="text-ink-500">
            {request.requesterName} &middot; {request.requesterEmail}
          </p>
          <p className="text-ink-500">{CATEGORY_LABELS[request.category]}</p>
        </div>

        <p className="rounded-lg border border-border p-3 text-sm text-ink-700 whitespace-pre-wrap">{request.message}</p>

        {error && <p className="text-sm text-danger">{error}</p>}

        <form onSubmit={handleRespond} className="flex flex-col gap-3">
          <label className="text-sm font-medium text-ink-700">
            {request.status === "RESOLVED" ? "Reply" : "Send a reply"}
          </label>
          <textarea
            required
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            placeholder="Your reply to this business"
            className="min-h-28 rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
          <Button type="submit" disabled={busy}>
            {busy ? "Sending..." : request.status === "RESOLVED" ? "Update reply" : "Send & mark resolved"}
          </Button>
        </form>
      </div>
    </Modal>
  );
}

function FilterChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
        active
          ? "border-accent bg-accent-soft text-accent-hover"
          : "border-border bg-surface text-ink-700 hover:border-border-strong"
      }`}
    >
      {label}
    </button>
  );
}
