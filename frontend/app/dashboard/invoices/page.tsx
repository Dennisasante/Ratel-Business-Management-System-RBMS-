"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { FileText, Plus, Download } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, Invoice, InvoicePayload, InvoiceStatus, InvoiceSummary } from "@/lib/api";
import Modal from "@/components/Modal";
import InvoiceForm from "@/components/InvoiceForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: "Draft",
  SENT: "Sent",
  PAID: "Paid",
  OVERDUE: "Overdue",
};

const STATUS_TONES: Record<InvoiceStatus, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  DRAFT: "neutral",
  SENT: "info",
  PAID: "success",
  OVERDUE: "danger",
};

const ALL_STATUSES: InvoiceStatus[] = ["DRAFT", "SENT", "PAID", "OVERDUE"];

export default function InvoicesPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [invoices, setInvoices] = useState<InvoiceSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | "">("");
  const [showForm, setShowForm] = useState(false);
  const [editingInvoice, setEditingInvoice] = useState<Invoice | null>(null);
  const [viewingInvoice, setViewingInvoice] = useState<Invoice | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  const loadInvoices = useCallback(async () => {
    if (!session) return;
    setInvoices(await api.listInvoices(session.token));
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadInvoices().finally(() => setFetching(false));
  }, [session, loadInvoices]);

  async function handleCreate(payload: InvoicePayload) {
    if (!session) return;
    await api.createInvoice(session.token, payload);
    setShowForm(false);
    await loadInvoices();
  }

  async function handleUpdate(payload: InvoicePayload) {
    if (!session || !editingInvoice) return;
    await api.updateInvoice(session.token, editingInvoice.id, payload);
    setEditingInvoice(null);
    await loadInvoices();
  }

  async function openView(id: string) {
    if (!session) return;
    setActionError(null);
    const full = await api.getInvoice(session.token, id);
    setViewingInvoice(full);
  }

  async function handleStatusChange(id: string, status: InvoiceStatus) {
    if (!session) return;
    setActionError(null);
    setBusyId(id);
    try {
      const updated = await api.updateInvoiceStatus(session.token, id, status);
      setViewingInvoice((v) => (v && v.id === id ? updated : v));
      await loadInvoices();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't update this invoice.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleDownload(invoice: Invoice | InvoiceSummary) {
    if (!session) return;
    await api.downloadInvoicePdf(session.token, invoice.id, invoice.invoiceNumber);
  }

  async function handleDelete(id: string) {
    if (!session) return;
    setActionError(null);
    setBusyId(id);
    try {
      await api.deleteInvoice(session.token, id);
      setConfirmDeleteId(null);
      setViewingInvoice(null);
      await loadInvoices();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't delete this invoice.");
    } finally {
      setBusyId(null);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const visibleInvoices = statusFilter ? invoices.filter((i) => i.status === statusFilter) : invoices;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Invoices"
        subtitle="Create and send professional invoices to your clients."
        actions={
          <Button onClick={() => setShowForm(true)}>
            <Plus size={16} /> New invoice
          </Button>
        }
      />

      <div className="flex flex-wrap gap-2">
        <FilterChip label="All statuses" active={statusFilter === ""} onClick={() => setStatusFilter("")} />
        {ALL_STATUSES.map((s) => (
          <FilterChip key={s} label={STATUS_LABELS[s]} active={statusFilter === s} onClick={() => setStatusFilter(s)} />
        ))}
      </div>

      <Card>
        {actionError && <p className="px-5 pt-4 text-sm text-danger">{actionError}</p>}
        {fetching ? (
          <TableSkeleton cols={6} />
        ) : visibleInvoices.length === 0 ? (
          <EmptyState
            icon={FileText}
            title="No invoices yet"
            description="Create your first invoice to send to a client."
            action={
              <Button onClick={() => setShowForm(true)}>
                <Plus size={16} /> New invoice
              </Button>
            }
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Invoice</Th>
                <Th>Customer</Th>
                <Th>Issue date</Th>
                <Th>Due date</Th>
                <Th>Status</Th>
                <Th className="text-right">Total</Th>
              </Tr>
            </THead>
            <TBody>
              {visibleInvoices.map((inv) => (
                <Tr key={inv.id}>
                  <Td className="tabular font-medium">
                    <button className="hover:underline" onClick={() => openView(inv.id)}>
                      #{inv.invoiceNumber}
                    </button>
                  </Td>
                  <Td className="text-ink-500">{inv.customerName ?? "—"}</Td>
                  <Td className="tabular text-ink-500">{inv.issueDate}</Td>
                  <Td className="tabular text-ink-500">{inv.dueDate ?? "—"}</Td>
                  <Td>
                    <Badge tone={STATUS_TONES[inv.status]}>{STATUS_LABELS[inv.status]}</Badge>
                  </Td>
                  <Td className="text-right">
                    <div className="flex items-center justify-end gap-3">
                      <span className="tabular font-medium">GH₵{inv.totalAmount.toFixed(2)}</span>
                      <button
                        onClick={() => handleDownload(inv)}
                        className="text-ink-500 hover:text-accent-hover"
                        title="Download PDF"
                        aria-label="Download PDF"
                      >
                        <Download size={15} />
                      </button>
                    </div>
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {showForm && (
        <Modal title="New invoice" onClose={() => setShowForm(false)}>
          <InvoiceForm token={session.token} onSubmit={handleCreate} />
        </Modal>
      )}

      {editingInvoice && (
        <Modal title={`Edit invoice #${editingInvoice.invoiceNumber}`} onClose={() => setEditingInvoice(null)}>
          <InvoiceForm token={session.token} invoice={editingInvoice} onSubmit={handleUpdate} />
        </Modal>
      )}

      {viewingInvoice && (
        <Modal title={`Invoice #${viewingInvoice.invoiceNumber}`} onClose={() => setViewingInvoice(null)}>
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <Badge tone={STATUS_TONES[viewingInvoice.status]}>{STATUS_LABELS[viewingInvoice.status]}</Badge>
              <span className="text-sm text-ink-500">{viewingInvoice.issueDate}</span>
            </div>

            <div className="text-sm">
              <p className="font-medium text-ink-900">{viewingInvoice.customerName ?? "—"}</p>
              {viewingInvoice.customerEmail && <p className="text-ink-500">{viewingInvoice.customerEmail}</p>}
              {viewingInvoice.customerPhone && <p className="text-ink-500">{viewingInvoice.customerPhone}</p>}
              {viewingInvoice.customerAddress && <p className="text-ink-500">{viewingInvoice.customerAddress}</p>}
            </div>

            <div className="flex flex-col gap-1.5 rounded-lg border border-border p-3">
              {viewingInvoice.items.map((item) => (
                <div key={item.id} className="flex items-center justify-between text-sm">
                  <span className="text-ink-700">
                    {item.description} × {item.quantity}
                  </span>
                  <span className="tabular text-ink-500">GH₵{item.subtotal.toFixed(2)}</span>
                </div>
              ))}
              <div className="flex items-center justify-between border-t border-border pt-2 text-sm font-semibold">
                <span>Total</span>
                <span className="tabular">GH₵{viewingInvoice.totalAmount.toFixed(2)}</span>
              </div>
            </div>

            {viewingInvoice.notes && (
              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-500">Notes</p>
                <p className="text-sm text-ink-700">{viewingInvoice.notes}</p>
              </div>
            )}

            {actionError && <p className="text-sm text-danger">{actionError}</p>}

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-ink-700">Change status</label>
              <select
                value={viewingInvoice.status}
                disabled={busyId === viewingInvoice.id}
                onChange={(e) => handleStatusChange(viewingInvoice.id, e.target.value as InvoiceStatus)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                {ALL_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_LABELS[s]}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => handleDownload(viewingInvoice)} className="flex-1">
                <Download size={15} className="mr-1.5" /> Download PDF
              </Button>
              {viewingInvoice.status === "DRAFT" && (
                <>
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setEditingInvoice(viewingInvoice);
                      setViewingInvoice(null);
                    }}
                  >
                    Edit
                  </Button>
                  <Button variant="danger" onClick={() => setConfirmDeleteId(viewingInvoice.id)}>
                    Delete
                  </Button>
                </>
              )}
            </div>
          </div>
        </Modal>
      )}

      {confirmDeleteId && (
        <Modal title="Delete this invoice?" onClose={() => setConfirmDeleteId(null)}>
          <p className="text-sm text-ink-700">This can&rsquo;t be undone.</p>
          <div className="mt-4 flex gap-2">
            <Button variant="danger" onClick={() => handleDelete(confirmDeleteId)} disabled={busyId === confirmDeleteId} className="flex-1">
              {busyId === confirmDeleteId ? "Deleting..." : "Delete"}
            </Button>
            <Button variant="secondary" onClick={() => setConfirmDeleteId(null)} className="flex-1">
              Cancel
            </Button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function FilterChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1.5 text-sm font-medium transition ${
        active ? "border-accent bg-accent-soft text-accent-hover" : "border-border bg-surface text-ink-700 hover:bg-canvas"
      }`}
    >
      {label}
    </button>
  );
}
