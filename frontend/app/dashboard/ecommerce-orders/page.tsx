"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { ShoppingBag, MessageCircle, XCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, EcommerceOrder, EcommerceOrderDetail, EcommerceOrderStatus } from "@/lib/api";
import Modal from "@/components/Modal";
import UpsellBanner from "@/components/UpsellBanner";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const STATUS_LABELS: Record<EcommerceOrderStatus, string> = {
  RECEIVED: "Received",
  PROCESSING: "Processing",
  READY: "Ready",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

const STATUS_TONES: Record<EcommerceOrderStatus, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  RECEIVED: "info",
  PROCESSING: "accent",
  READY: "violet",
  COMPLETED: "success",
  CANCELLED: "danger",
};

const NEXT_STATUS: Partial<Record<EcommerceOrderStatus, EcommerceOrderStatus>> = {
  RECEIVED: "PROCESSING",
  PROCESSING: "READY",
  READY: "COMPLETED",
};

const NEXT_STATUS_LABEL: Partial<Record<EcommerceOrderStatus, string>> = {
  RECEIVED: "Mark processing",
  PROCESSING: "Mark ready",
  READY: "Mark completed",
};

export default function EcommerceOrdersPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [orders, setOrders] = useState<EcommerceOrder[]>([]);
  const [fetching, setFetching] = useState(true);
  const [upsellMessage, setUpsellMessage] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<EcommerceOrderStatus | "">("");
  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [detail, setDetail] = useState<EcommerceOrderDetail | null>(null);

  const loadOrders = useCallback(async () => {
    if (!session) return;
    try {
      const data = await api.listEcommerceOrders(session.token);
      setOrders(data);
      setUpsellMessage(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setUpsellMessage(err.message);
      } else {
        throw err;
      }
    }
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!loading && session?.role === "STAFF") router.push("/dashboard");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadOrders().finally(() => setFetching(false));
  }, [session, loadOrders]);

  async function handleAdvanceStatus(order: EcommerceOrder) {
    const next = NEXT_STATUS[order.status];
    if (!session || !next) return;
    const key = `${order.id}:status`;
    setPendingAction(key);
    setActionError(null);
    try {
      await api.updateEcommerceOrderStatus(session.token, order.id, next);
      await loadOrders();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't update this order's status.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleCancel(order: EcommerceOrder) {
    if (!session) return;
    const key = `${order.id}:cancel`;
    setPendingAction(key);
    setActionError(null);
    try {
      await api.updateEcommerceOrderStatus(session.token, order.id, "CANCELLED");
      await loadOrders();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't cancel this order.");
    } finally {
      setPendingAction(null);
    }
  }

  async function openDetail(orderId: string) {
    if (!session) return;
    const full = await api.getEcommerceOrder(session.token, orderId);
    setDetail(full);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const visibleOrders = statusFilter ? orders.filter((o) => o.status === statusFilter) : orders;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="E-commerce Orders" subtitle="Orders synced in automatically from your WooCommerce store." />

      {upsellMessage && <UpsellBanner message={upsellMessage} />}

      {!upsellMessage && (
        <>
          <div className="flex flex-wrap gap-2">
            <FilterChip label="All statuses" active={statusFilter === ""} onClick={() => setStatusFilter("")} />
            {(Object.keys(STATUS_LABELS) as EcommerceOrderStatus[]).map((s) => (
              <FilterChip key={s} label={STATUS_LABELS[s]} active={statusFilter === s} onClick={() => setStatusFilter(s)} />
            ))}
          </div>

          {actionError && <p className="text-sm text-danger">{actionError}</p>}

          <Card>
            {fetching ? (
              <TableSkeleton cols={7} />
            ) : visibleOrders.length === 0 ? (
              <EmptyState
                icon={ShoppingBag}
                title="No orders yet"
                description="Orders placed on your WooCommerce store will show up here automatically."
              />
            ) : (
              <Table>
                <THead>
                  <Tr>
                    <Th>Order</Th>
                    <Th>Customer</Th>
                    <Th>Items</Th>
                    <Th>Total</Th>
                    <Th>Status</Th>
                    <Th>Received</Th>
                    <Th className="text-right">Actions</Th>
                  </Tr>
                </THead>
                <TBody>
                  {visibleOrders.map((o) => {
                    const next = NEXT_STATUS[o.status];
                    const canCancel = o.status !== "COMPLETED" && o.status !== "CANCELLED";
                    return (
                      <Tr key={o.id}>
                        <Td className="tabular font-medium">
                          <button className="hover:underline" onClick={() => openDetail(o.id)}>
                            #{o.orderNumber}
                          </button>
                        </Td>
                        <Td className="text-ink-500">{o.customerName ?? "—"}</Td>
                        <Td className="tabular text-ink-500">{o.itemCount}</Td>
                        <Td className="tabular font-medium">
                          {o.currency} {o.totalAmount.toFixed(2)}
                        </Td>
                        <Td>
                          <Badge tone={STATUS_TONES[o.status]}>{STATUS_LABELS[o.status]}</Badge>
                        </Td>
                        <Td className="tabular text-ink-500">{new Date(o.createdAt).toLocaleDateString()}</Td>
                        <Td>
                          <div className="flex flex-wrap items-center justify-end gap-3 whitespace-nowrap text-right">
                            {o.whatsappLink && (
                              <a
                                href={o.whatsappLink}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="flex items-center gap-1 text-sm font-medium text-success hover:underline"
                              >
                                <MessageCircle size={14} /> Notify
                              </a>
                            )}
                            {next && (
                              <button
                                onClick={() => handleAdvanceStatus(o)}
                                disabled={pendingAction === `${o.id}:status`}
                                className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {pendingAction === `${o.id}:status` ? "Updating..." : NEXT_STATUS_LABEL[o.status]}
                              </button>
                            )}
                            {canCancel && (
                              <button
                                onClick={() => handleCancel(o)}
                                disabled={pendingAction === `${o.id}:cancel`}
                                className="flex items-center gap-1 text-sm font-medium text-danger hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                <XCircle size={14} />
                                {pendingAction === `${o.id}:cancel` ? "Cancelling..." : "Cancel"}
                              </button>
                            )}
                          </div>
                        </Td>
                      </Tr>
                    );
                  })}
                </TBody>
              </Table>
            )}
          </Card>
        </>
      )}

      {detail && (
        <Modal title={`Order #${detail.orderNumber}`} onClose={() => setDetail(null)}>
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <Badge tone={STATUS_TONES[detail.status]}>{STATUS_LABELS[detail.status]}</Badge>
              <span className="text-sm text-ink-500">{new Date(detail.createdAt).toLocaleString()}</span>
            </div>

            <div className="flex flex-col gap-1 text-sm">
              <p className="font-medium text-ink-900">{detail.customerName ?? "Unknown customer"}</p>
              {detail.customerEmail && <p className="text-ink-500">{detail.customerEmail}</p>}
              {detail.customerPhone && <p className="text-ink-500">{detail.customerPhone}</p>}
            </div>

            <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
              {detail.items.map((item, i) => (
                <div key={i} className="flex items-center justify-between text-sm">
                  <span className="text-ink-700">
                    {item.quantity} × {item.productName}
                  </span>
                  <span className="tabular font-medium">
                    {detail.currency} {(item.unitPrice * item.quantity).toFixed(2)}
                  </span>
                </div>
              ))}
              <div className="flex items-center justify-between border-t border-border pt-2 text-sm font-semibold">
                <span>Total</span>
                <span className="tabular">
                  {detail.currency} {detail.totalAmount.toFixed(2)}
                </span>
              </div>
            </div>

            {detail.whatsappLink && (
              <a
                href={detail.whatsappLink}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-2 rounded-lg bg-success px-4 py-2 text-sm font-medium text-white hover:bg-success/90"
              >
                <MessageCircle size={15} />
                Message customer on WhatsApp
              </a>
            )}
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
