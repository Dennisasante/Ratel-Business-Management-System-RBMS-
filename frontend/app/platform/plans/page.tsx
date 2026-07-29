"use client";

import { useEffect, useState, useCallback } from "react";
import { Layers, Plus } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, SubscriptionPlan, SubscriptionPlanPayload } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import Modal from "@/components/Modal";
import SubscriptionPlanForm from "@/components/SubscriptionPlanForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

export default function PlatformPlansPage() {
  const { session } = usePlatformAuth();
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [fetching, setFetching] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<SubscriptionPlan | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    setPlans(await api.listPlatformSubscriptionPlans(session.token));
  }, [session]);

  useEffect(() => {
    load().finally(() => setFetching(false));
  }, [load]);

  async function handleCreate(payload: SubscriptionPlanPayload) {
    if (!session) return;
    await api.createPlatformSubscriptionPlan(session.token, payload);
    await load();
    setShowAdd(false);
  }

  async function handleEdit(payload: SubscriptionPlanPayload) {
    if (!session || !editing) return;
    await api.updatePlatformSubscriptionPlan(session.token, editing.id, payload);
    await load();
    setEditing(null);
  }

  async function toggleActive(plan: SubscriptionPlan) {
    if (!session) return;
    setPendingId(plan.id);
    try {
      if (plan.active) {
        await api.archivePlatformSubscriptionPlan(session.token, plan.id);
      } else {
        await api.restorePlatformSubscriptionPlan(session.token, plan.id);
      }
      await load();
    } finally {
      setPendingId(null);
    }
  }

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Subscription Plans"
          subtitle={`${plans.length} plan${plans.length === 1 ? "" : "s"} — active plans are what businesses can choose from on their Billing page`}
          actions={
            <Button onClick={() => setShowAdd(true)}>
              <Plus size={16} /> Add plan
            </Button>
          }
        />

        <Card>
          {fetching ? (
            <TableSkeleton cols={5} />
          ) : plans.length === 0 ? (
            <p className="p-5 text-sm text-ink-500">No plans yet — add one so businesses have something to subscribe to.</p>
          ) : (
            <Table>
              <THead>
                <Tr>
                  <Th>Name</Th>
                  <Th>Price</Th>
                  <Th>Billing period</Th>
                  <Th>Status</Th>
                  <Th className="text-right">Actions</Th>
                </Tr>
              </THead>
              <TBody>
                {plans.map((p) => (
                  <Tr key={p.id}>
                    <Td className="font-medium">{p.name}</Td>
                    <Td className="tabular">
                      {p.currency} {p.price.toFixed(2)}
                    </Td>
                    <Td className="text-ink-500">every {p.billingPeriodDays} days</Td>
                    <Td>
                      <Badge tone={p.active ? "success" : "neutral"}>{p.active ? "Active" : "Archived"}</Badge>
                    </Td>
                    <Td className="text-right">
                      <div className="flex justify-end gap-3">
                        <button
                          onClick={() => setEditing(p)}
                          className="text-sm font-medium text-accent-hover hover:underline"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => toggleActive(p)}
                          disabled={pendingId === p.id}
                          className="text-sm font-medium text-ink-500 hover:text-ink-900 disabled:opacity-50"
                        >
                          {pendingId === p.id ? "..." : p.active ? "Archive" : "Restore"}
                        </button>
                      </div>
                    </Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
          )}
        </Card>

        <p className="flex items-center gap-1.5 text-xs text-ink-500">
          <Layers size={13} />
          Archiving a plan hides it from new checkouts but keeps it visible here for businesses still on it historically.
        </p>
      </div>

      {showAdd && (
        <Modal title="Add plan" onClose={() => setShowAdd(false)}>
          <SubscriptionPlanForm onSubmit={handleCreate} />
        </Modal>
      )}

      {editing && (
        <Modal title={`Edit ${editing.name}`} onClose={() => setEditing(null)}>
          <SubscriptionPlanForm initial={editing} onSubmit={handleEdit} />
        </Modal>
      )}
    </PlatformShell>
  );
}
