"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Sparkles, MessageCircle, Settings, CheckCircle2, Plus } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  CreateStaffCustomWigRequestPayload,
  CustomWigRequest,
  CustomWigRequestDetail,
  CustomWigRequestStatus,
} from "@/lib/api";
import Modal from "@/components/Modal";
import UpsellBanner from "@/components/UpsellBanner";
import PaymentCollectionPanel from "@/components/PaymentCollectionPanel";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const STATUS_LABELS: Record<CustomWigRequestStatus, string> = {
  SUBMITTED: "Submitted",
  QUOTED: "Quoted",
  ACCEPTED: "Accepted",
  DECLINED: "Declined",
};

const STATUS_TONES: Record<CustomWigRequestStatus, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  SUBMITTED: "info",
  QUOTED: "violet",
  ACCEPTED: "success",
  DECLINED: "danger",
};

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
  PAID: "success",
  FAILED: "danger",
  REFUNDED: "violet",
};

const PAYMENT_METHOD_LABELS: Record<string, string> = {
  CASH: "Cash",
  MOBILE_MONEY_DIRECT: "Direct Mobile Money",
  MOBILE_MONEY: "Online Payment",
};

export default function CustomWigRequestsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [requests, setRequests] = useState<CustomWigRequest[]>([]);
  const [fetching, setFetching] = useState(true);
  const [upsellMessage, setUpsellMessage] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<CustomWigRequestStatus | "">("");
  const [detail, setDetail] = useState<CustomWigRequestDetail | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [showNewRequest, setShowNewRequest] = useState(false);
  const [paystackConfigured, setPaystackConfigured] = useState(false);
  const [collectingPaymentRequest, setCollectingPaymentRequest] = useState<CustomWigRequest | null>(null);

  const loadRequests = useCallback(async () => {
    if (!session) return;
    try {
      const data = await api.listCustomWigRequests(session.token);
      setRequests(data);
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
    Promise.all([loadRequests(), api.getPaymentGatewayStatus(session.token).then((gw) => setPaystackConfigured(gw.paystackConfigured))]).finally(
      () => setFetching(false)
    );
  }, [session, loadRequests]);

  async function openDetail(id: string) {
    if (!session) return;
    setActionError(null);
    const full = await api.getCustomWigRequest(session.token, id);
    setDetail(full);
  }

  async function refreshDetail(id: string) {
    if (!session) return;
    const full = await api.getCustomWigRequest(session.token, id);
    setDetail(full);
    await loadRequests();
  }

  function handleRequestPaymentChanged(updated: CustomWigRequest) {
    setCollectingPaymentRequest(updated);
    setRequests((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    if (updated.paymentStatus === "PAID") {
      setCollectingPaymentRequest(null);
    }
  }

  async function handleCreate(payload: CreateStaffCustomWigRequestPayload, photo: File | null) {
    if (!session) return;
    await api.createStaffCustomWigRequest(session.token, payload, photo);
    setShowNewRequest(false);
    await loadRequests();
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const visibleRequests = statusFilter ? requests.filter((r) => r.status === statusFilter) : requests;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <PageHeader title="Custom Wig Requests" subtitle="Requests submitted through your custom configurator — or logged by you." />
        <div className="flex items-center gap-2">
          <Button onClick={() => setShowNewRequest(true)}>
            <Plus size={15} className="mr-1.5" /> New request
          </Button>
          <Link href="/dashboard/custom-wig-requests/attributes">
            <Button variant="secondary">
              <Settings size={15} className="mr-1.5" /> Pricing rules
            </Button>
          </Link>
        </div>
      </div>

      {upsellMessage && <UpsellBanner message={upsellMessage} />}

      {!upsellMessage && (
        <>
          <div className="flex flex-wrap gap-2">
            <FilterChip label="All statuses" active={statusFilter === ""} onClick={() => setStatusFilter("")} />
            {(Object.keys(STATUS_LABELS) as CustomWigRequestStatus[]).map((s) => (
              <FilterChip key={s} label={STATUS_LABELS[s]} active={statusFilter === s} onClick={() => setStatusFilter(s)} />
            ))}
          </div>

          <Card>
            {fetching ? (
              <TableSkeleton cols={6} />
            ) : visibleRequests.length === 0 ? (
              <EmptyState
                icon={Sparkles}
                title="No requests yet"
                description="Custom wig requests submitted through your configurator will show up here."
              />
            ) : (
              <Table>
                <THead>
                  <Tr>
                    <Th>Request</Th>
                    <Th>Customer</Th>
                    <Th>Estimate</Th>
                    <Th>Status</Th>
                    <Th>Received</Th>
                    <Th className="text-right">Quote</Th>
                  </Tr>
                </THead>
                <TBody>
                  {visibleRequests.map((r) => (
                    <Tr key={r.id}>
                      <Td className="tabular font-medium">
                        <button className="hover:underline" onClick={() => openDetail(r.id)}>
                          #{r.requestNumber}
                        </button>
                      </Td>
                      <Td className="text-ink-500">
                        <span>
                          {r.customerName}
                          {r.source && <span className="ml-1.5 text-xs text-ink-400">via {r.source}</span>}
                        </span>
                        {r.description && (
                          <p className="mt-0.5 max-w-xs truncate text-xs text-ink-400" title={r.description}>
                            {r.description}
                          </p>
                        )}
                      </Td>
                      <Td className="tabular font-medium">GHS {r.estimatedPrice.toFixed(2)}</Td>
                      <Td>
                        <div className="flex flex-wrap items-center gap-1.5">
                          <Badge tone={STATUS_TONES[r.status]}>{STATUS_LABELS[r.status]}</Badge>
                          {r.finalPrice != null && (
                            <Badge tone={PAYMENT_STATUS_TONES[r.paymentStatus] ?? "neutral"}>
                              {PAYMENT_STATUS_LABELS[r.paymentStatus] ?? r.paymentStatus}
                            </Badge>
                          )}
                        </div>
                      </Td>
                      <Td className="tabular text-ink-500">{new Date(r.createdAt).toLocaleDateString()}</Td>
                      <Td className="text-right">
                        {r.finalPrice != null ? (
                          <div className="flex flex-col items-end gap-1">
                            <span className="tabular font-medium">GHS {r.finalPrice.toFixed(2)}</span>
                            {r.paymentStatus !== "REFUNDED" && (
                              <button
                                onClick={() => setCollectingPaymentRequest(r)}
                                className="text-xs font-medium text-accent-hover hover:underline"
                              >
                                {r.paymentStatus === "PAID" ? "Refund" : "Collect payment"}
                              </button>
                            )}
                          </div>
                        ) : (
                          <button onClick={() => openDetail(r.id)} className="text-sm font-medium text-accent-hover hover:underline">
                            Review
                          </button>
                        )}
                      </Td>
                    </Tr>
                  ))}
                </TBody>
              </Table>
            )}
          </Card>
        </>
      )}

      {detail && session && (
        <RequestDetailModal
          detail={detail}
          token={session.token}
          onClose={() => setDetail(null)}
          onChanged={() => refreshDetail(detail.id)}
          error={actionError}
          setError={setActionError}
        />
      )}

      {showNewRequest && <NewRequestModal onClose={() => setShowNewRequest(false)} onSubmit={handleCreate} />}

      {collectingPaymentRequest && session && (
        <Modal
          title={`Collect payment — Request #${collectingPaymentRequest.requestNumber}`}
          onClose={() => setCollectingPaymentRequest(null)}
        >
          <PaymentCollectionPanel<CustomWigRequest>
            id={collectingPaymentRequest.id}
            amount={collectingPaymentRequest.finalPrice ?? 0}
            balanceDue={collectingPaymentRequest.balanceDue ?? 0}
            paymentStatus={collectingPaymentRequest.paymentStatus}
            paystackConfigured={paystackConfigured}
            onVerifyPayment={(reference) => api.verifyCustomWigRequestPayment(session.token, reference)}
            onMarkPaid={(id) => api.markCustomWigRequestPaid(session.token, id)}
            onChargeMobileMoney={(id, phone, provider) => api.chargeCustomWigRequestMobileMoney(session.token, id, phone, provider)}
            onSubmitOtp={(reference, otp) => api.submitCustomWigRequestMobileMoneyOtp(session.token, reference, otp)}
            onRecordPayment={(id, payload) => api.recordCustomWigRequestPayment(session.token, id, payload)}
            onRefund={(id, note) => api.refundCustomWigRequest(session.token, id, note)}
            onChanged={handleRequestPaymentChanged}
          />
        </Modal>
      )}
    </div>
  );
}

function RequestDetailModal({
  detail,
  token,
  onClose,
  onChanged,
  error,
  setError,
}: {
  detail: CustomWigRequestDetail;
  token: string;
  onClose: () => void;
  onChanged: () => void;
  error: string | null;
  setError: (e: string | null) => void;
}) {
  const [finalPrice, setFinalPrice] = useState(detail.estimatedPrice.toFixed(2));
  const [message, setMessage] = useState("");
  const [declineMessage, setDeclineMessage] = useState("");
  const [confirmingDecline, setConfirmingDecline] = useState(false);
  const [busy, setBusy] = useState(false);

  async function handleQuote(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.quoteCustomWigRequest(token, detail.id, Number(finalPrice) || 0, message);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't send that quote.");
    } finally {
      setBusy(false);
    }
  }

  async function handleDecline() {
    setError(null);
    setBusy(true);
    try {
      await api.declineCustomWigRequest(token, detail.id, declineMessage);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't decline this request.");
    } finally {
      setBusy(false);
    }
  }

  async function handleAccept() {
    setError(null);
    setBusy(true);
    try {
      await api.acceptCustomWigRequest(token, detail.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update this request.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Request #${detail.requestNumber}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <Badge tone={STATUS_TONES[detail.status]}>{STATUS_LABELS[detail.status]}</Badge>
          <span className="text-sm text-ink-500">{new Date(detail.createdAt).toLocaleString()}</span>
        </div>

        <div className="flex flex-col gap-1 text-sm">
          <p className="font-medium text-ink-900">{detail.customerName}</p>
          {detail.customerEmail && <p className="text-ink-500">{detail.customerEmail}</p>}
          {detail.customerWhatsapp && <p className="text-ink-500">{detail.customerWhatsapp}</p>}
          {detail.source && <p className="text-xs text-ink-400">via {detail.source}</p>}
        </div>

        {detail.description && (
          <div>
            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-500">What they want</p>
            <p className="text-sm text-ink-700">{detail.description}</p>
          </div>
        )}

        <div className="flex flex-col gap-1.5 rounded-lg border border-border p-3">
          {detail.selections.map((sel, i) => (
            <div key={i} className="flex items-center justify-between text-sm">
              <span className="text-ink-700">
                {sel.attributeName}: {sel.optionLabel}
                {sel.requiresManualQuote && <span className="ml-1.5 text-xs font-medium text-accent-hover">(manual quote)</span>}
              </span>
              <span className="tabular text-ink-500">+{sel.priceModifier.toFixed(2)}</span>
            </div>
          ))}
          <div className="flex items-center justify-between border-t border-border pt-2 text-sm font-semibold">
            <span>Estimated</span>
            <span className="tabular">GHS {detail.estimatedPrice.toFixed(2)}</span>
          </div>
          {detail.amountPaid > 0 && (
            <div className="flex items-center justify-between text-sm text-ink-500">
              <span>Paid{detail.paymentMethod ? ` via ${PAYMENT_METHOD_LABELS[detail.paymentMethod] ?? detail.paymentMethod}` : ""}</span>
              <span className="tabular">GHS {detail.amountPaid.toFixed(2)}</span>
            </div>
          )}
        </div>

        {detail.inspirationPhotoUrl && (
          <div>
            <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-ink-500">Inspiration photo</p>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={detail.inspirationPhotoUrl} alt="" className="max-h-64 w-full rounded-lg object-cover" />
          </div>
        )}

        {detail.notes && (
          <div>
            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-500">Notes</p>
            <p className="text-sm text-ink-700">{detail.notes}</p>
          </div>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}

        {detail.status === "SUBMITTED" && !confirmingDecline && (
          <form onSubmit={handleQuote} className="flex flex-col gap-3 rounded-lg bg-canvas p-3">
            <p className="text-sm font-medium text-ink-900">Send a quote</p>
            <div className="grid grid-cols-2 gap-2">
              <input
                type="number"
                step="0.01"
                value={finalPrice}
                onChange={(e) => setFinalPrice(e.target.value)}
                placeholder="Final price"
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
            </div>
            <textarea
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="A note for the customer (optional)"
              className="min-h-16 rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
            <div className="flex gap-2">
              <Button type="submit" disabled={busy} className="flex-1">
                {busy ? "Sending..." : "Send quote"}
              </Button>
              <Button type="button" variant="ghost" onClick={() => setConfirmingDecline(true)}>
                Decline
              </Button>
            </div>
          </form>
        )}

        {detail.status === "SUBMITTED" && confirmingDecline && (
          <div className="flex flex-col gap-3 rounded-lg bg-danger-soft p-3">
            <p className="text-sm font-medium text-ink-900">Decline this request</p>
            <textarea
              value={declineMessage}
              onChange={(e) => setDeclineMessage(e.target.value)}
              placeholder="A reason for the customer (optional)"
              className="min-h-16 rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
            <div className="flex gap-2">
              <Button variant="danger" onClick={handleDecline} disabled={busy} className="flex-1">
                {busy ? "Declining..." : "Confirm decline"}
              </Button>
              <Button variant="ghost" onClick={() => setConfirmingDecline(false)}>
                Cancel
              </Button>
            </div>
          </div>
        )}

        {detail.status === "QUOTED" && (
          <div className="flex flex-col gap-2 rounded-lg bg-canvas p-3">
            <p className="text-sm text-ink-700">
              Quoted at <span className="font-semibold">GHS {detail.finalPrice?.toFixed(2)}</span>
              {detail.ownerMessage && <> — {detail.ownerMessage}</>}
            </p>
            <Button onClick={handleAccept} disabled={busy} variant="secondary">
              <CheckCircle2 size={15} className="mr-1.5" />
              {busy ? "Updating..." : "Mark accepted"}
            </Button>
          </div>
        )}

        {detail.status === "DECLINED" && detail.ownerMessage && (
          <p className="rounded-lg bg-canvas p-3 text-sm text-ink-700">{detail.ownerMessage}</p>
        )}

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
  );
}

function NewRequestModal({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (payload: CreateStaffCustomWigRequestPayload, photo: File | null) => Promise<void>;
}) {
  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [customerWhatsapp, setCustomerWhatsapp] = useState("");
  const [source, setSource] = useState("");
  const [description, setDescription] = useState("");
  const [price, setPrice] = useState("");
  const [notes, setNotes] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!customerName.trim()) {
      setError("Customer name is required.");
      return;
    }
    if (!description.trim()) {
      setError("Describe what the customer wants.");
      return;
    }
    const parsedPrice = Number(price);
    if (!parsedPrice || parsedPrice <= 0) {
      setError("Enter a price greater than zero.");
      return;
    }

    setBusy(true);
    try {
      await onSubmit(
        {
          customerName: customerName.trim(),
          customerEmail: customerEmail.trim() || undefined,
          customerWhatsapp: customerWhatsapp.trim() || undefined,
          source: source.trim() || undefined,
          description: description.trim(),
          price: parsedPrice,
          notes: notes.trim() || undefined,
        },
        photo
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't log that request.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Log a custom wig request" onClose={onClose}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-500">For a request that came in outside the site — Instagram, WhatsApp, a phone call.</p>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Customer name *</label>
            <input
              required
              value={customerName}
              onChange={(e) => setCustomerName(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Where it came from</label>
            <input
              value={source}
              onChange={(e) => setSource(e.target.value)}
              placeholder="e.g. Instagram DM"
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Email (optional)</label>
            <input
              type="email"
              value={customerEmail}
              onChange={(e) => setCustomerEmail(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">WhatsApp / phone (optional)</label>
            <input
              value={customerWhatsapp}
              onChange={(e) => setCustomerWhatsapp(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">What do they want? *</label>
          <textarea
            required
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder='e.g. "24 inches HD lace wig, bone straight, natural black"'
            className="min-h-20 rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Price *</label>
          <input
            required
            type="number"
            min="0.01"
            step="0.01"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            placeholder="0.00"
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Notes (optional)</label>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Anything else the customer mentioned"
            className="min-h-16 rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Inspiration photo (optional)</label>
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp"
            onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
            className="text-sm text-ink-700"
          />
        </div>

        {error && <p className="text-sm text-danger">{error}</p>}

        <Button type="submit" disabled={busy}>
          {busy ? "Logging..." : "Log request"}
        </Button>
      </form>
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
