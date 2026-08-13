"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Plus, Wrench, BarChart3, Mail, ListTree, CalendarDays, MessageCircle, MoreVertical, Move, ChevronRight } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  ServiceCatalogItem,
  ServiceOrder,
  ServiceOrderPayload,
  ServiceOrderStatus,
  ServiceOrderUpdatePayload,
  ServiceType,
  StaffMember,
} from "@/lib/api";
import Modal from "@/components/Modal";
import ServiceOrderForm from "@/components/ServiceOrderForm";
import ServiceOrderEditForm from "@/components/ServiceOrderEditForm";
import PaymentCollectionPanel from "@/components/PaymentCollectionPanel";
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

// Mirrors ServiceOrderService.ALLOWED_TRANSITIONS exactly — every destination
// the backend actually accepts from a given status (no skipping a stage in
// either direction, e.g. Received can't jump straight to Picked up). Keep in
// sync if the backend graph ever changes.
const ALLOWED_TRANSITIONS: Record<ServiceOrderStatus, ServiceOrderStatus[]> = {
  RECEIVED: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["RECEIVED", "COMPLETED", "CANCELLED"],
  COMPLETED: ["IN_PROGRESS", "PICKED_UP", "CANCELLED"],
  PICKED_UP: ["COMPLETED"],
  CANCELLED: ["RECEIVED"],
};

// Display order for the "Move to stage" list — every stage is always shown
// (minus whichever one is current) so staff can see the whole pipeline, even
// though only the backend-allowed ones are actually clickable.
const ALL_STAGES: ServiceOrderStatus[] = ["RECEIVED", "IN_PROGRESS", "COMPLETED", "PICKED_UP", "CANCELLED"];

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  UNPAID: "Unpaid",
  PARTIALLY_PAID: "Partially paid",
  PAID: "Paid",
  FAILED: "Payment failed",
  REFUNDED: "Refunded",
};

const PAYMENT_STATUS_TONES: Record<string, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  UNPAID: "neutral",
  PARTIALLY_PAID: "info",
  REFUNDED: "violet",
  PAID: "success",
  FAILED: "danger",
};

type ModalState = { type: "none" } | { type: "add" } | { type: "edit"; order: ServiceOrder };

export default function ServiceOrdersPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [orders, setOrders] = useState<ServiceOrder[]>([]);
  const [serviceTypes, setServiceTypes] = useState<ServiceType[]>([]);
  const [catalog, setCatalog] = useState<ServiceCatalogItem[]>([]);
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [fetching, setFetching] = useState(true);
  const [modal, setModal] = useState<ModalState>({ type: "none" });

  const [typeFilter, setTypeFilter] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<ServiceOrderStatus | "">("");

  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<string | null>(null); // `${orderId}:${action}`

  const [paystackConfigured, setPaystackConfigured] = useState(false);
  const [collectingPaymentOrder, setCollectingPaymentOrder] = useState<ServiceOrder | null>(null);

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
    const [cat, s, gw] = await Promise.all([
      api.listServiceCatalog(session.token),
      api.listStaffMembers(session.token),
      api.getPaymentGatewayStatus(session.token),
    ]);
    setCatalog(cat);
    setStaff(s);
    setPaystackConfigured(gw.paystackConfigured);
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

  async function handleUpdate(orderId: string, payload: ServiceOrderUpdatePayload) {
    if (!session) return;
    await api.updateServiceOrder(session.token, orderId, payload);
    await loadOrders();
    setModal({ type: "none" });
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

  function handleOrderChanged(updated: ServiceOrder) {
    setCollectingPaymentOrder(updated);
    setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
    if (updated.paymentStatus === "PAID") {
      setCollectingPaymentOrder(null);
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
                <ListTree size={16} /> Services
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
          Add your first category under &ldquo;Services&rdquo; above before creating an order.
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
                const allowedFromHere = ALLOWED_TRANSITIONS[o.status];
                // Every stage except the current one, each flagged with
                // whether the backend will actually accept moving there
                // directly right now — shown either way so the whole
                // pipeline is visible, not just the one or two next steps.
                const moveOptions = ALL_STAGES.filter((s) => s !== o.status).map((s) => ({
                  status: s,
                  allowed: allowedFromHere.includes(s),
                }));
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
                      {o.items.length > 0 && (
                        <span className="block text-xs text-ink-400">
                          {o.items[0].serviceName}
                          {o.items.length > 1 && ` +${o.items.length - 1} more`}
                        </span>
                      )}
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
                        {!o.bookingPaymentStatus && (
                          <Badge tone={PAYMENT_STATUS_TONES[o.paymentStatus] ?? "neutral"}>
                            {PAYMENT_STATUS_LABELS[o.paymentStatus] ?? o.paymentStatus}
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
                      <div className="flex flex-nowrap items-center justify-end gap-3 whitespace-nowrap text-right">
                        <StageMenu
                          options={moveOptions}
                          pending={pendingAction === `${o.id}:status`}
                          onSelect={(status) => handleSetStatus(o, status)}
                        />
                        <Link
                          href={`/receipt/service-order/${o.id}`}
                          target="_blank"
                          className="text-sm font-medium text-accent-hover hover:underline"
                        >
                          View
                        </Link>
                        <button
                          onClick={() => setModal({ type: "edit", order: o })}
                          className="text-sm font-medium text-ink-700 hover:underline"
                        >
                          Edit
                        </button>
                        {!o.bookingPaymentStatus && o.paymentStatus !== "REFUNDED" && (
                          <button
                            onClick={() => setCollectingPaymentOrder(o)}
                            className="text-sm font-medium text-accent-hover hover:underline"
                          >
                            {o.paymentStatus === "PAID" ? "Refund" : "Collect payment"}
                          </button>
                        )}
                        {((o.bookingWhatsappLink || o.customerWhatsappLink) || canResend) && (
                          <ActionsMenu>
                            {(o.bookingWhatsappLink || o.customerWhatsappLink) && (
                              <a
                                href={o.bookingWhatsappLink ?? o.customerWhatsappLink ?? undefined}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-success hover:bg-canvas"
                              >
                                <MessageCircle size={14} />
                                Message on WhatsApp
                              </a>
                            )}
                            {canResend && (
                              <button
                                onClick={() => handleResendEmail(o)}
                                disabled={pendingAction === `${o.id}:resend`}
                                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-ink-700 hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                <Mail size={14} />
                                {pendingAction === `${o.id}:resend` ? "Sending..." : "Resend email"}
                              </button>
                            )}
                          </ActionsMenu>
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
            token={session.token}
            serviceTypes={serviceTypes}
            catalog={catalog.filter((c) => c.active)}
            staff={staff}
            submitLabel="Create order"
            onSubmit={handleCreate}
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

      {collectingPaymentOrder && (
        <Modal
          title={`Collect payment — Order #${collectingPaymentOrder.orderNumber}`}
          onClose={() => setCollectingPaymentOrder(null)}
        >
          <PaymentCollectionPanel<ServiceOrder>
            id={collectingPaymentOrder.id}
            amount={collectingPaymentOrder.price}
            balanceDue={collectingPaymentOrder.balanceDue}
            paymentStatus={collectingPaymentOrder.paymentStatus}
            paystackConfigured={paystackConfigured}
            onVerifyPayment={(reference) => api.verifyServiceOrderPayment(session.token, reference)}
            onMarkPaid={(id) => api.markServiceOrderPaid(session.token, id)}
            onChargeMobileMoney={(id, phone, provider) => api.chargeServiceOrderMobileMoney(session.token, id, phone, provider)}
            onRecordPayment={(id, payload) => api.recordServiceOrderPayment(session.token, id, payload)}
            onRefund={(id, note) => api.refundServiceOrder(session.token, id, note)}
            onSubmitOtp={(reference, otp) => api.submitServiceOrderMobileMoneyOtp(session.token, reference, otp)}
            onChanged={handleOrderChanged}
          />
        </Modal>
      )}
    </div>
  );
}

function ActionsMenu({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  return (
    <div ref={ref} className="relative inline-block text-left">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label="More actions"
        className="rounded-lg p-1.5 text-ink-500 hover:bg-canvas hover:text-ink-900"
      >
        <MoreVertical size={16} />
      </button>
      {open && (
        <div
          onClick={() => setOpen(false)}
          className="absolute right-0 z-10 mt-1 w-52 rounded-lg border border-border bg-surface py-1 text-left shadow-card"
        >
          {children}
        </div>
      )}
    </div>
  );
}

// Jira-style "Move work item ▸" pattern: an accordion entry inside the
// overflow menu rather than a hover flyout, since this menu is click-driven
// (works the same on touch). Unmounts (and so resets its own expanded state)
// whenever the parent ActionsMenu closes, since it only exists in `children`.
// The primary, always-visible way to change an order's stage — shows every
// stage (not just the immediately-next one) so staff can see the whole
// pipeline at a glance; stages the backend won't accept a direct jump to
// right now are visible but disabled, rather than hidden entirely.
function StageMenu({
  options,
  pending,
  onSelect,
}: {
  options: { status: ServiceOrderStatus; allowed: boolean }[];
  pending: boolean;
  onSelect: (status: ServiceOrderStatus) => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  return (
    <div ref={ref} className="relative inline-block text-left">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-1 text-sm font-medium text-accent-hover hover:underline"
      >
        <Move size={14} />
        Move to stage
        <ChevronRight size={14} className={`transition-transform ${open ? "rotate-90" : ""}`} />
      </button>
      {open && (
        <div className="absolute right-0 z-10 mt-1 w-52 rounded-lg border border-border bg-surface py-1 text-left shadow-card">
          {options.map(({ status, allowed }) => (
            <button
              key={status}
              type="button"
              onClick={() => {
                if (!allowed) return;
                onSelect(status);
                setOpen(false);
              }}
              disabled={!allowed || pending}
              title={allowed ? undefined : "Not reachable directly from the current stage"}
              className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent ${
                status === "CANCELLED" ? "text-danger" : "text-ink-700"
              }`}
            >
              {STATUS_LABELS[status]}
            </button>
          ))}
        </div>
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
