"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import { FileText, Plus, Download, MoreVertical, Eye, Send, Pencil, CheckCircle2, Copy, Trash2 } from "lucide-react";
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
  const { session, business, loading } = useAuth();
  const router = useRouter();

  const [invoices, setInvoices] = useState<InvoiceSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | "">("");
  const [showForm, setShowForm] = useState(false);
  const [editingInvoice, setEditingInvoice] = useState<Invoice | null>(null);
  const [viewingInvoice, setViewingInvoice] = useState<Invoice | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionInfo, setActionInfo] = useState<string | null>(null);
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
    loadPreview(id);
  }

  // Fetches the actual rendered PDF as a blob and shows it inline via an
  // <iframe> — a real preview of what the client will see, not just a plain
  // list of the same numbers, and without triggering a save-to-disk the way
  // Download does.
  async function loadPreview(id: string) {
    if (!session) return;
    setPreviewLoading(true);
    setPreviewUrl((old) => {
      if (old) URL.revokeObjectURL(old);
      return null;
    });
    try {
      const url = await api.getInvoicePdfBlobUrl(session.token, id);
      setPreviewUrl(url);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't load the preview.");
    } finally {
      setPreviewLoading(false);
    }
  }

  function closeView() {
    setViewingInvoice(null);
    setPreviewUrl((old) => {
      if (old) URL.revokeObjectURL(old);
      return null;
    });
  }

  async function openEdit(id: string) {
    if (!session) return;
    setActionError(null);
    const full = await api.getInvoice(session.token, id);
    setEditingInvoice(full);
  }

  async function handleStatusChange(id: string, status: InvoiceStatus) {
    if (!session) return;
    setActionError(null);
    setBusyId(id);
    try {
      const updated = await api.updateInvoiceStatus(session.token, id, status);
      setViewingInvoice((v) => (v && v.id === id ? updated : v));
      // The status badge is part of the rendered PDF, so a stale preview
      // would show the old status right after changing it.
      if (viewingInvoice?.id === id) loadPreview(id);
      await loadInvoices();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't update this invoice.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleDownload(invoice: Invoice | InvoiceSummary) {
    if (!session) return;
    setActionError(null);
    try {
      await api.downloadInvoicePdf(session.token, invoice.id, invoice.invoiceNumber);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't download this invoice.");
      // The row we tried to download may no longer exist (e.g. deleted from
      // another tab/session) — refresh so the list reflects reality instead
      // of leaving a stale, now-broken row on screen.
      await loadInvoices();
    }
  }

  async function handleDelete(id: string) {
    if (!session) return;
    setActionError(null);
    setBusyId(id);
    try {
      await api.deleteInvoice(session.token, id);
      setConfirmDeleteId(null);
      closeView();
      await loadInvoices();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't delete this invoice.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleSend(id: string) {
    if (!session) return;
    setActionError(null);
    setActionInfo(null);
    setBusyId(id);
    try {
      const updated = await api.sendInvoice(session.token, id);
      setViewingInvoice((v) => (v && v.id === id ? updated : v));
      if (viewingInvoice?.id === id) loadPreview(id);
      setActionInfo(`Invoice #${updated.invoiceNumber} sent to ${updated.customerEmail}.`);
      await loadInvoices();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't send this invoice.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleDuplicate(id: string) {
    if (!session) return;
    setActionError(null);
    setActionInfo(null);
    setBusyId(id);
    try {
      const copy = await api.duplicateInvoice(session.token, id);
      await loadInvoices();
      closeView();
      setEditingInvoice(copy);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't duplicate this invoice.");
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
        {actionInfo && <p className="px-5 pt-4 text-sm text-info">{actionInfo}</p>}
        {fetching ? (
          <TableSkeleton cols={7} />
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
                <Th>Reference</Th>
                <Th>Customer</Th>
                <Th>Date</Th>
                <Th>Due date</Th>
                <Th>Status</Th>
                <Th className="text-right">Total</Th>
                <Th></Th>
              </Tr>
            </THead>
            <TBody>
              {visibleInvoices.map((inv) => (
                <Tr key={inv.id}>
                  <Td className="tabular font-medium">
                    <button className="text-accent-hover hover:underline" onClick={() => openView(inv.id)}>
                      Invoice #{inv.invoiceNumber}
                    </button>
                  </Td>
                  <Td className="text-ink-500">{inv.customerName ?? "—"}</Td>
                  <Td className="tabular text-ink-500">{inv.issueDate}</Td>
                  <Td className="tabular text-ink-500">{inv.dueDate ?? "—"}</Td>
                  <Td>
                    <Badge tone={STATUS_TONES[inv.status]}>{STATUS_LABELS[inv.status]}</Badge>
                  </Td>
                  <Td className="tabular text-right font-medium">GH₵{inv.totalAmount.toFixed(2)}</Td>
                  <Td className="text-right">
                    <ActionsMenu>
                      <button
                        onClick={() => openView(inv.id)}
                        className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-ink-700 hover:bg-canvas"
                      >
                        <Eye size={14} />
                        View
                      </button>
                      <button
                        onClick={() => handleDownload(inv)}
                        className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-ink-700 hover:bg-canvas"
                      >
                        <Download size={14} />
                        Download
                      </button>
                      <button
                        onClick={() => handleSend(inv.id)}
                        disabled={busyId === inv.id}
                        className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-ink-700 hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        <Send size={14} />
                        {busyId === inv.id ? "Sending..." : "Send"}
                      </button>
                      {inv.status === "DRAFT" && (
                        <button
                          onClick={() => openEdit(inv.id)}
                          className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-ink-700 hover:bg-canvas"
                        >
                          <Pencil size={14} />
                          Edit
                        </button>
                      )}
                      {inv.status !== "PAID" && (
                        <button
                          onClick={() => handleStatusChange(inv.id, "PAID")}
                          disabled={busyId === inv.id}
                          className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-success hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          <CheckCircle2 size={14} />
                          Mark as Paid
                        </button>
                      )}
                      <button
                        onClick={() => handleDuplicate(inv.id)}
                        disabled={busyId === inv.id}
                        className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-ink-700 hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        <Copy size={14} />
                        Duplicate
                      </button>
                      {inv.status === "DRAFT" && (
                        <button
                          onClick={() => setConfirmDeleteId(inv.id)}
                          className="flex w-full items-center gap-2 border-t border-border px-3 py-2 text-left text-sm font-medium text-danger hover:bg-canvas"
                        >
                          <Trash2 size={14} />
                          Delete
                        </button>
                      )}
                    </ActionsMenu>
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {showForm && (
        <Modal title="New invoice" onClose={() => setShowForm(false)}>
          <InvoiceForm token={session.token} defaultTerms={business?.defaultTermsAndConditions} onSubmit={handleCreate} />
        </Modal>
      )}

      {editingInvoice && (
        <Modal title={`Edit invoice #${editingInvoice.invoiceNumber}`} onClose={() => setEditingInvoice(null)}>
          <InvoiceForm token={session.token} invoice={editingInvoice} onSubmit={handleUpdate} />
        </Modal>
      )}

      {viewingInvoice && (
        <Modal title={`Invoice #${viewingInvoice.invoiceNumber}`} onClose={closeView} maxWidthClassName="max-w-2xl">
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <Badge tone={STATUS_TONES[viewingInvoice.status]}>{STATUS_LABELS[viewingInvoice.status]}</Badge>
              <span className="text-sm text-ink-500">{viewingInvoice.issueDate}</span>
            </div>

            {/* A real preview of the generated document — the same PDF
                Download produces, shown inline instead of triggering a save. */}
            <div className="overflow-hidden rounded-lg border border-border bg-canvas">
              {previewLoading ? (
                <div className="flex h-[65vh] items-center justify-center text-sm text-ink-500">Loading preview...</div>
              ) : previewUrl ? (
                <iframe src={previewUrl} title={`Invoice #${viewingInvoice.invoiceNumber} preview`} className="h-[65vh] w-full" />
              ) : (
                <div className="flex h-[65vh] items-center justify-center text-sm text-ink-500">Couldn&rsquo;t load the preview.</div>
              )}
            </div>
            {previewUrl && (
              // Some browsers (mobile Safari/Android WebView in particular)
              // don't reliably render an embedded PDF inside an <iframe> —
              // this guarantees a working fallback regardless.
              <a href={previewUrl} target="_blank" rel="noopener noreferrer" className="-mt-2 self-end text-xs font-medium text-accent-hover hover:underline">
                Open preview in a new tab
              </a>
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

            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => handleDownload(viewingInvoice)} className="flex-1">
                <Download size={15} className="mr-1.5" /> Download
              </Button>
              <Button variant="secondary" onClick={() => handleSend(viewingInvoice.id)} disabled={busyId === viewingInvoice.id} className="flex-1">
                <Send size={15} className="mr-1.5" /> {busyId === viewingInvoice.id ? "Sending..." : "Send"}
              </Button>
              <Button variant="secondary" onClick={() => handleDuplicate(viewingInvoice.id)} disabled={busyId === viewingInvoice.id}>
                <Copy size={15} className="mr-1.5" /> Duplicate
              </Button>
              {viewingInvoice.status === "DRAFT" && (
                <>
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setEditingInvoice(viewingInvoice);
                      closeView();
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

// Same "⋯" overflow-menu idea as the Service Orders page, but portaled to
// document.body instead of positioned `absolute` inside the row — the
// Invoices table sits in a `overflow-x-auto` wrapper (Table.tsx), and per
// the CSS spec that resolves overflow-y to `auto` too, which silently
// clipped the dropdown for any row near the table's bottom edge (worst case:
// a one-row table, where the menu had almost nowhere to render into). A
// portal + `position: fixed` computed from the trigger's own bounding rect
// escapes that ancestor entirely, same trick Modal.tsx already uses.
function ActionsMenu({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState({ top: 0, left: 0 });
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (
        menuRef.current && !menuRef.current.contains(e.target as Node) &&
        buttonRef.current && !buttonRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  function toggle() {
    if (!open && buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect();
      // Right-aligned to the trigger, same as the old `right-0` positioning —
      // 208px is this menu's own w-52.
      setCoords({ top: rect.bottom + 4, left: rect.right - 208 });
    }
    setOpen((o) => !o);
  }

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        onClick={toggle}
        aria-label="More actions"
        className="rounded-lg p-1.5 text-ink-500 hover:bg-canvas hover:text-ink-900"
      >
        <MoreVertical size={16} />
      </button>
      {open &&
        createPortal(
          <div
            ref={menuRef}
            onClick={() => setOpen(false)}
            style={{ position: "fixed", top: coords.top, left: coords.left }}
            className="z-50 w-52 rounded-lg border border-border bg-surface py-1 text-left shadow-card"
          >
            {children}
          </div>,
          document.body
        )}
    </>
  );
}
