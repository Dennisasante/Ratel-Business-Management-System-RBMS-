"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { ShieldCheck, Check, X as XIcon } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, PendingApproval } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";
import Modal from "@/components/Modal";

const SOURCE_LABELS: Record<PendingApproval["sourceType"], string> = {
  SALE: "Sale",
  SERVICE_ORDER: "Service order",
  CUSTOM_WIG_REQUEST: "Custom wig request",
};

const ACTION_LABELS: Record<PendingApproval["actionType"], string> = {
  EDIT_PRICE: "Price edit",
  REFUND: "Refund",
};

export default function PendingApprovalsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [items, setItems] = useState<PendingApproval[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [decidingId, setDecidingId] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<PendingApproval | null>(null);
  const [rejectNote, setRejectNote] = useState("");

  const load = useCallback(async () => {
    if (!session) return;
    setError(null);
    try {
      const data = await api.listPendingApprovals(session.token);
      setItems(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load pending approvals.");
    }
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!loading && session && session.role !== "OWNER") router.push("/dashboard");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    load().finally(() => setFetching(false));
  }, [session, load]);

  async function handleApprove(id: string) {
    if (!session) return;
    setDecidingId(id);
    setError(null);
    try {
      await api.approvePendingApproval(session.token, id);
      setItems((prev) => prev.filter((i) => i.id !== id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't approve this request.");
    } finally {
      setDecidingId(null);
    }
  }

  async function handleReject() {
    if (!session || !rejecting) return;
    setDecidingId(rejecting.id);
    setError(null);
    try {
      await api.rejectPendingApproval(session.token, rejecting.id, rejectNote.trim() || undefined);
      setItems((prev) => prev.filter((i) => i.id !== rejecting.id));
      setRejecting(null);
      setRejectNote("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't reject this request.");
    } finally {
      setDecidingId(null);
    }
  }

  if (loading || !session || session.role !== "OWNER") {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Pending Approvals"
        subtitle="Price edits and refunds submitted by your team, waiting on your sign-off."
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Card>
        {fetching ? (
          <TableSkeleton cols={5} />
        ) : items.length === 0 ? (
          <EmptyState
            icon={ShieldCheck}
            title="Nothing waiting on you"
            description="When a Manager, Sales Person, or Accountant edits a price or issues a refund, it'll show up here for your approval."
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Requested</Th>
                <Th>What</Th>
                <Th>By</Th>
                <Th>Details</Th>
                <Th className="text-right">Actions</Th>
              </Tr>
            </THead>
            <TBody>
              {items.map((item) => (
                <Tr key={item.id}>
                  <Td className="tabular text-ink-500">{new Date(item.requestedAt).toLocaleString()}</Td>
                  <Td>
                    <span className="block font-medium text-ink-900">{SOURCE_LABELS[item.sourceType]}</span>
                    <Badge tone={item.actionType === "REFUND" ? "danger" : "info"}>
                      {ACTION_LABELS[item.actionType]}
                    </Badge>
                  </Td>
                  <Td className="text-ink-700">{item.requestedByName}</Td>
                  <Td className="max-w-sm text-ink-700">{item.summary}</Td>
                  <Td className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="secondary"
                        className="!px-2 !py-1 text-xs"
                        disabled={decidingId === item.id}
                        onClick={() => {
                          setRejecting(item);
                          setRejectNote("");
                        }}
                      >
                        <XIcon size={13} /> Reject
                      </Button>
                      <Button
                        variant="primary"
                        className="!px-2 !py-1 text-xs"
                        disabled={decidingId === item.id}
                        onClick={() => handleApprove(item.id)}
                      >
                        <Check size={13} /> {decidingId === item.id ? "Approving..." : "Approve"}
                      </Button>
                    </div>
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {rejecting && (
        <Modal title="Reject this request?" onClose={() => setRejecting(null)}>
          <div className="flex flex-col gap-4">
            <p className="text-sm text-ink-700">{rejecting.summary}</p>
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">Note (optional)</label>
              <textarea
                value={rejectNote}
                onChange={(e) => setRejectNote(e.target.value)}
                rows={3}
                placeholder="Let them know why..."
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setRejecting(null)}>
                Cancel
              </Button>
              <Button variant="danger" onClick={handleReject} disabled={decidingId === rejecting.id}>
                {decidingId === rejecting.id ? "Rejecting..." : "Reject request"}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
