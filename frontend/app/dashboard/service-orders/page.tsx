"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Plus, Wrench, BarChart3, Mail, ListTree, CalendarDays, MessageCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  Customer,
  CustomerPayload,
  ServiceCatalogItem,
  ServiceOrder,
  ServiceOrderPayload,
  ServiceOrderStatus,
  ServiceOrderUpdatePayload,
  ServiceType,
  UserSummary,
} from "@/lib/api";
import Modal from "@/components/Modal";
import ServiceOrderForm from "@/components/ServiceOrderForm";
import ServiceOrderEditForm from "@/components/ServiceOrderEditForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const STATUS_LABELS: Record<ServiceOrderStatus, string> = {
  RECEIVED: "Received",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  PICKED_UP: "Picked up",
  CANCELLED: "Cancelled",
};

const STATUS_TONES: Record<ServiceOrderStatus, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  RECEIVED: "info",
  IN_PROGRESS: "accent",
  COMPLETED: "success",
  PICKED_UP: "violet",
  CANCELLED: "danger",
};

const NEXT_STATUS: Partial<Record<ServiceOrderStatus, ServiceOrderStatus>> = {
  RECEIVED: "IN_PROGRESS",
  IN_PROGRESS: "COMPLETED",
  COMPLETED: "PICKED_UP",
};

const NEXT_STATUS_LABEL: Partial<Record<ServiceOrderStatus, string>> = {
  RECEIVED: "Mark in progress",
  IN_PROGRESS: "Mark completed",
  COMPLETED: "Mark picked up",
};

// Mirrors ServiceOrderService.ALLOWED_TRANSITIONS' one-step-back edges — lets
// a mistaken status change be corrected without allowing an arbitrary jump.
const PREVIOUS_STATUS: Partial<Record<ServiceOrderStatus, ServiceOrderStatus>> = {
  IN_PROGRESS: "RECEIVED",
  COMPLETED: "IN_PROGRESS",
  PICKED_UP: "COMPLETED",
  CANCELLED: "RECEIVED",
};

const PREVIOUS_STATUS_LABEL: Partial<Record<ServiceOrderStatus, string>> = {
  IN_PROGRESS: "Move back to received",
  COMPLETED: "Move back to in progress",
  PICKED_UP: "Move back to completed",
  CANCELLED: "Reopen",
};

type ModalState = { type: "none" } | { type: "add" } | { type: "edit"; order: ServiceOrder };

export default function ServiceOrdersPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [orders, setOrders] = useState<ServiceOrder[]>([]);
  const [serviceTypes, setServiceTypes] = useState<ServiceType[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [catalog, setCatalog] = useState<ServiceCatalogItem[]>([]);
  const [staff, setStaff] = useState<UserSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [modal, setModal] = useState<ModalState>({ type: "none" });

  const [typeFilter, setTypeFilter] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<ServiceOrderStatus | "">("");

  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<string | null>(null); // `${orderId}:${action}`

  const loadOrders = useCallback(async () => {
    if (!session) return;
    const data = await api.listServiceOrders(session.token, {
      serviceTypeId: typeFilter || undefined,
      status: statusFilter || undefined,
    });
    setOrders(data);
  }, [session, typeFilter, statusFilter]);

  const loadServiceTypes = useCallback(async () => {
    if (!session) return;
    const data = await api.listServiceTypes(session.token);
    setServiceTypes(data);
  }, [session]);

  const loadSupportingData = useCallback(async () => {
    if (!session) return;
    const [c, cat, s] = await Promise.all([
      api.listCustomers(session.token),
      api.listServiceCatalog(session.token),
      api.listUsers(session.token),
    ]);
    setCustomers(c);
    setCatalog(cat);
    setStaff(s);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadServiceTypes();
    loadSupportingData();
  }, [session, loadServiceTypes, loadSupportingData]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadOrders().finally(() => setFetching(false));
  }, [session, loadOrders]);

  async function handleCreate(payload: ServiceOrderPayload) {
    if (!session) return;
    await api.createServiceOrder(session.token, payload);
    await loadOrders();
    setModal({ type: "none" });
  }

  async function handleCreateCustomer(payload: CustomerPayload): Promise<Customer> {
    if (!session) throw new Error("Not signed in.");
    const customer = await api.createCustomer(session.token, payload);
    setCustomers((prev) => [...prev, customer].sort((a, b) => a.fullName.localeCompare(b.fullName)));
    return customer;
  }

  async function handleUpdate(orderId: string, payload: ServiceOrderUpdatePayload) {
    if (!session) return;
    await api.updateServiceOrder(session.token, orderId, payload);
    await loadOrders();
    setModal({ type: "none" });
  }

  async function handleAdvanceStatus(order: ServiceOrder) {
    const next = NEXT_STATUS[order.status];
    if (!session || !next) return;
    await handleSetStatus(order, next);
  }

  async function handleMoveBack(order: ServiceOrder) {
    const previous = PREVIOUS_STATUS[order.status];
    if (!session || !previous) return;
    await handleSetStatus(order, previous);
  }

  async function handleSetStatus(order: ServiceOrder, status: ServiceOrderStatus) {
    if (!session) return;
    const key = `${order.id}:status`;
    setActionError(null);
    setPendingAction(key);
    try {
      await api.updateServiceOrderStatus(session.token, order.id, status);
      await loadOrders();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't update this order's status.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleCancel(order: ServiceOrder) {
    if (!session) return;
    const key = `${order.id}:cancel`;
    setActionError(null);
    setPendingAction(key);
    try {
      await api.updateServiceOrderStatus(session.token, order.id, "CANCELLED");
      await loadOrders();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't cancel this order.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleResendEmail(order: ServiceOrder) {
    if (!session) return;
    const key = `${order.id}:resend`;
    setActionError(null);
    setPendingAction(key);
    try {
      await api.resendServiceOrderReadyEmail(session.token, order.id);
      await loadOrders();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't resend the email.");
    } finally {
      setPendingAction(null);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Service Orders"
        subtitle="Installations, revamps and more — from drop-off to pickup."
        actions={
          <>
            <Link href="/dashboard/service-orders/catalog">
              <Button variant="secondary">
                <ListTree size={16} /> Service Catalog
              </Button>
            </Link>
            <Link href="/dashboard/service-orders/calendar">
              <Button variant="secondary">
                <CalendarDays size={16} /> Calendar
              </Button>
            </Link>
            <Link href="/dashboard/service-orders/report">
              <Button variant="secondary">
                <BarChart3 size={16} /> Report
              </Button>
            </Link>
            <Button onClick={() => setModal({ type: "add" })} disabled={serviceTypes.length === 0}>
              <Plus size={16} /> New order
            </Button>
          </>
        }
      />

      {serviceTypes.length === 0 && !fetching && (
        <p className="text-sm text-ink-500">
          Add your first category under &ldquo;Service Catalog&rdquo; above before creating an order.
        </p>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <FilterChip label="All types" active={typeFilter === ""} onClick={() => setTypeFilter("")} />
        {serviceTypes.map((t) => (
          <FilterChip key={t.id} label={t.name} active={typeFilter === t.id} onClick={() => setTypeFilter(t.id)} />
        ))}
        <span className="mx-1 h-4 w-px bg-border" />
        <FilterChip label="All statuses" active={statusFilter === ""} onClick={() => setStatusFilter("")} />
        {(Object.keys(STATUS_LABELS) as ServiceOrderStatus[]).map((s) => (
          <FilterChip key={s} label={STATUS_LABELS[s]} active={statusFilter === s} onClick={() => setStatusFilter(s)} />
        ))}
      </div>

      <Card>
        {actionError && <p className="px-5 pt-4 text-sm text-danger">{actionError}</p>}
        {fetching ? (
          <TableSkeleton cols={7} />
        ) : orders.length === 0 ? (
          <EmptyState
            icon={Wrench}
            title="No service orders yet"
            description="Orders you create will show up here."
            action={
              serviceTypes.length > 0 ? (
                <Button onClick={() => setModal({ type: "add" })}>
                  <Plus size={16} /> New order
                </Button>
              ) : undefined
            }
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Order</Th>
                <Th>Type</Th>
                <Th>Customer</Th>
                <Th>Status</Th>
                <Th>Price</Th>
                <Th>Received</Th>
                <Th>Picked up</Th>
                <Th className="text-right">Actions</Th>
              </Tr>
            </THead>
            <TBody>
              {orders.map((o) => {
                const nextStatus = NEXT_STATUS[o.status];
                const previousStatus = PREVIOUS_STATUS[o.status];
                const canCancel = o.status !== "PICKED_UP" && o.status !== "CANCELLED";
                const canResend = o.status === "COMPLETED" || o.status === "PICKED_UP";
                return (
                  <Tr key={o.id}>
                    <Td className="tabular font-medium">
                      <Link href={`/receipt/service-order/${o.id}`} target="_blank" className="text-accent-hover hover:underline">
                        #{o.orderNumber}
                      </Link>
                    </Td>
                    <Td className="text-ink-500">
                      <span className="block">{o.serviceTypeName ?? "—"}</span>
                      {o.serviceCatalogName && <span className="block text-xs text-ink-400">{o.serviceCatalogName}</span>}
                    </Td>
                    <Td className="text-ink-500">{o.customerName ?? "Walk-in"}</Td>
                    <Td>
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Badge tone={STATUS_TONES[o.status]}>{STATUS_LABELS[o.status]}</Badge>
                        {o.bookingPaymentStatus && o.bookingPaymentStatus !== "PAID" && (
                          <Badge tone={o.bookingPaymentStatus === "FAILED" ? "danger" : "neutral"}>
                            {o.bookingPaymentStatus === "FAILED" ? "Payment failed" : "Awaiting payment"}
                          </Badge>
                        )}
                      </div>
                    </Td>
                    <Td className="tabular font-medium">GH₵{o.price.toFixed(2)}</Td>
                    <Td className="tabular text-ink-500">{new Date(o.receivedAt).toLocaleDateString()}</Td>
                    <Td className="tabular text-ink-500">
                      {o.pickedUpAt ? new Date(o.pickedUpAt).toLocaleDateString() : "—"}
                    </Td>
                    <Td>
                      <div className="flex flex-wrap justify-end gap-3 whitespace-nowrap text-right">
                        {(o.bookingWhatsappLink || o.customerWhatsappLink) && (
                          <a
                            href={o.bookingWhatsappLink ?? o.customerWhatsappLink ?? undefined}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center gap-1 text-sm font-medium text-success hover:underline"
                          >
                            <MessageCircle size={13} />
                            WhatsApp
                          </a>
                        )}
                        {nextStatus && (
                          <button
                            onClick={() => handleAdvanceStatus(o)}
                            disabled={pendingAction === `${o.id}:status`}
                            className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                          >
                            {pendingAction === `${o.id}:status` ? "Updating..." : NEXT_STATUS_LABEL[o.status]}
                          </button>
                        )}
                        {previousStatus && (
                          <button
                            onClick={() => handleMoveBack(o)}
                            disabled={pendingAction === `${o.id}:status`}
                            className="text-sm font-medium text-ink-500 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                          >
                            {pendingAction === `${o.id}:status` ? "Updating..." : PREVIOUS_STATUS_LABEL[o.status]}
                          </button>
                        )}
                        <Link
                          href={`/receipt/service-order/${o.id}`}
                          target="_blank"
                          className="text-sm font-medium text-accent-hover hover:underline"
                        >
                          View / Print
                        </Link>
                        {canResend && (
                          <button
                            onClick={() => handleResendEmail(o)}
                            disabled={pendingAction === `${o.id}:resend`}
                            className="flex items-center gap-1 text-sm font-medium text-ink-700 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                          >
                            <Mail size={13} />
                            {pendingAction === `${o.id}:resend` ? "Sending..." : "Resend email"}
                          </button>
                        )}
                        <button
                          onClick={() => setModal({ type: "edit", order: o })}
                          className="text-sm font-medium text-ink-700 hover:underline"
                        >
                          Edit
                        </button>
                        {canCancel && (
                          <button
                            onClick={() => handleCancel(o)}
                            disabled={pendingAction === `${o.id}:cancel`}
                            className="text-sm font-medium text-danger hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                          >
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

      {modal.type === "add" && (
        <Modal title="New service order" onClose={() => setModal({ type: "none" })}>
          <ServiceOrderForm
            serviceTypes={serviceTypes}
            customers={customers}
            catalog={catalog.filter((c) => c.active)}
            staff={staff}
            submitLabel="Create order"
            onSubmit={handleCreate}
            onCreateCustomer={handleCreateCustomer}
          />
        </Modal>
      )}

      {modal.type === "edit" && (
        <Modal title={`Edit order #${modal.order.orderNumber}`} onClose={() => setModal({ type: "none" })}>
          <ServiceOrderEditForm
            order={modal.order}
            staff={staff}
            onSubmit={(payload) => handleUpdate(modal.order.id, payload)}
          />
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
