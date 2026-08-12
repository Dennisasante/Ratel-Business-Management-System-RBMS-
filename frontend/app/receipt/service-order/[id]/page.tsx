"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { CheckCircle2, Smartphone } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessSummary, MobileMoneyProvider, ServiceOrder } from "@/lib/api";
import ReceiptView from "@/components/ReceiptView";
import Badge from "@/components/ui/Badge";
import PaystackCheckoutButton from "@/components/PaystackCheckoutButton";

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  UNPAID: "Unpaid",
  PAID: "Paid",
  FAILED: "Payment failed",
};

const PAYMENT_STATUS_TONES: Record<string, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  UNPAID: "neutral",
  PAID: "success",
  FAILED: "danger",
};

const MOMO_PROVIDERS: { value: MobileMoneyProvider; label: string }[] = [
  { value: "mtn", label: "MTN Mobile Money" },
  { value: "atl", label: "AirtelTigo Money" },
  { value: "vod", label: "Telecel Cash (Vodafone)" },
];

export default function ServiceOrderReceiptPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const params = useParams<{ id: string }>();

  const [order, setOrder] = useState<ServiceOrder | null>(null);
  const [business, setBusiness] = useState<BusinessSummary | null>(null);
  const [paystackConfigured, setPaystackConfigured] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [payError, setPayError] = useState<string | null>(null);
  const [markingPaid, setMarkingPaid] = useState(false);

  const [showMomoForm, setShowMomoForm] = useState(false);
  const [momoPhone, setMomoPhone] = useState("");
  const [momoProvider, setMomoProvider] = useState<MobileMoneyProvider>("mtn");
  const [chargingMomo, setChargingMomo] = useState(false);
  const [momoPending, setMomoPending] = useState<{ reference: string; message: string; status: string } | null>(null);
  const [verifyingMomo, setVerifyingMomo] = useState(false);
  const [otpCode, setOtpCode] = useState("");
  const [submittingOtp, setSubmittingOtp] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      const [o, b, i] = await Promise.all([
        api.getServiceOrder(session.token, params.id),
        api.getMyBusiness(session.token),
        api.getPaymentGatewayStatus(session.token),
      ]);
      setOrder(o);
      setBusiness(b);
      setPaystackConfigured(i.paystackConfigured);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load this receipt.");
    }
  }, [session, params.id]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load();
  }, [session, load]);

  async function handleMarkPaid() {
    if (!session || !order) return;
    setMarkingPaid(true);
    setPayError(null);
    try {
      const updated = await api.markServiceOrderPaid(session.token, order.id);
      setOrder(updated);
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't mark this order as paid.");
    } finally {
      setMarkingPaid(false);
    }
  }

  async function handleChargeMobileMoney(e: React.FormEvent) {
    e.preventDefault();
    if (!session || !order || !momoPhone.trim()) return;
    setChargingMomo(true);
    setPayError(null);
    try {
      const result = await api.chargeServiceOrderMobileMoney(session.token, order.id, momoPhone.trim(), momoProvider);
      if (result.status.toLowerCase() === "success") {
        await load();
        setShowMomoForm(false);
        setMomoPending(null);
      } else {
        setMomoPending({
          reference: result.reference,
          status: result.status,
          message:
            result.displayText ||
            (result.status.toLowerCase() === "send_otp"
              ? "A code was texted to the customer's phone."
              : "Ask the customer to check their phone and approve the payment."),
        });
      }
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't start the mobile money charge.");
    } finally {
      setChargingMomo(false);
    }
  }

  async function handleVerifyMobileMoney() {
    if (!session || !momoPending) return;
    setVerifyingMomo(true);
    setPayError(null);
    try {
      const updated = await api.verifyServiceOrderPayment(session.token, momoPending.reference);
      setOrder(updated);
      if (updated.paymentStatus === "PAID") {
        setMomoPending(null);
        setShowMomoForm(false);
      } else {
        setPayError("Not confirmed yet — ask the customer to approve it on their phone, then try again.");
      }
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't verify this payment.");
    } finally {
      setVerifyingMomo(false);
    }
  }

  async function handleSubmitOtp(e: React.FormEvent) {
    e.preventDefault();
    if (!session || !momoPending || !otpCode.trim()) return;
    setSubmittingOtp(true);
    setPayError(null);
    try {
      const updated = await api.submitServiceOrderMobileMoneyOtp(session.token, momoPending.reference, otpCode.trim());
      setOrder(updated);
      if (updated.paymentStatus === "PAID") {
        setMomoPending(null);
        setShowMomoForm(false);
        setOtpCode("");
      } else {
        setPayError("That code didn't work — check with the customer and try again.");
      }
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't submit that code.");
    } finally {
      setSubmittingOtp(false);
    }
  }

  if (loading || !session) {
    return <p className="p-6 text-sm text-ink-500">Loading...</p>;
  }

  if (error) {
    return <p className="p-6 text-sm text-danger">{error}</p>;
  }

  if (!order || !business) {
    return <p className="p-6 text-sm text-ink-500">Loading receipt...</p>;
  }

  const items =
    order.items.length > 0
      ? order.items.map((item) => ({
          name: item.serviceTypeName ? `${item.serviceName} (${item.serviceTypeName})` : item.serviceName,
          quantity: 1,
          unitPrice: item.price + item.discountAmount,
          discountAmount: item.discountAmount,
          subtotal: item.price,
        }))
      : [
          {
            name: order.serviceCatalogName ?? order.serviceTypeName ?? "Service",
            quantity: 1,
            unitPrice: order.price + order.discountAmount,
            discountAmount: order.discountAmount,
            subtotal: order.price,
          },
        ];

  // Booking-originated orders already have their own payment flow (the
  // customer's manage-booking link) — don't offer a second, conflicting one here.
  const showPaymentPanel = !order.bookingPaymentStatus;

  return (
    <div className="flex flex-col items-center">
      {showPaymentPanel && (
        <div className="mb-4 flex w-full max-w-xs flex-col gap-2 print:hidden">
          <div className="flex items-center justify-between rounded-lg border border-border bg-surface px-3 py-2">
            <span className="text-sm font-medium text-ink-700">Payment</span>
            <Badge tone={PAYMENT_STATUS_TONES[order.paymentStatus] ?? "neutral"}>
              {PAYMENT_STATUS_LABELS[order.paymentStatus] ?? order.paymentStatus}
            </Badge>
          </div>
          {order.paymentStatus !== "PAID" && (
            <div className="flex flex-col gap-2">
              {payError && <p className="text-xs text-danger">{payError}</p>}
              {paystackConfigured && (
                <PaystackCheckoutButton
                  planId={order.id}
                  buttonLabel={`Pay GH₵${order.price.toFixed(2)} with Paystack`}
                  onStartCheckout={async (id) => {
                    const result = await api.startServiceOrderPayment(session.token, id);
                    return result;
                  }}
                  onVerify={async (reference) => {
                    try {
                      const updated = await api.verifyServiceOrderPayment(session.token, reference);
                      setOrder(updated);
                      return updated.paymentStatus === "PAID";
                    } catch (err) {
                      setPayError(err instanceof ApiError ? err.message : "Couldn't verify this payment.");
                      return false;
                    }
                  }}
                  onError={(msg) => setPayError(msg)}
                  className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover"
                />
              )}
              <button
                onClick={handleMarkPaid}
                disabled={markingPaid}
                className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-ink-700 transition hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
              >
                <CheckCircle2 size={15} />
                {markingPaid ? "Marking..." : "Mark as paid"}
              </button>

              {paystackConfigured && !momoPending && (
                <button
                  type="button"
                  onClick={() => setShowMomoForm((s) => !s)}
                  className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-ink-700 transition hover:bg-canvas"
                >
                  <Smartphone size={15} />
                  {showMomoForm ? "Cancel mobile money charge" : "Charge mobile money"}
                </button>
              )}

              {showMomoForm && !momoPending && (
                <form onSubmit={handleChargeMobileMoney} className="flex flex-col gap-2 rounded-lg border border-border p-3">
                  <select
                    value={momoProvider}
                    onChange={(e) => setMomoProvider(e.target.value as MobileMoneyProvider)}
                    className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  >
                    {MOMO_PROVIDERS.map((p) => (
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                  </select>
                  <input
                    value={momoPhone}
                    onChange={(e) => setMomoPhone(e.target.value)}
                    placeholder="Customer's mobile money number"
                    className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                  <button
                    type="submit"
                    disabled={chargingMomo || !momoPhone.trim()}
                    className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {chargingMomo ? "Sending prompt..." : `Send GH₵${order.price.toFixed(2)} prompt`}
                  </button>
                </form>
              )}

              {momoPending && momoPending.status.toLowerCase() === "send_otp" && (
                <form onSubmit={handleSubmitOtp} className="flex flex-col gap-2 rounded-lg border border-border p-3">
                  <p className="text-xs text-ink-500">{momoPending.message}</p>
                  <input
                    value={otpCode}
                    onChange={(e) => setOtpCode(e.target.value)}
                    placeholder="Code from the customer's SMS"
                    className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                  <button
                    type="submit"
                    disabled={submittingOtp || !otpCode.trim()}
                    className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {submittingOtp ? "Submitting..." : "Submit code"}
                  </button>
                </form>
              )}

              {momoPending && momoPending.status.toLowerCase() !== "send_otp" && (
                <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
                  <p className="text-xs text-ink-500">{momoPending.message}</p>
                  <button
                    onClick={handleVerifyMobileMoney}
                    disabled={verifyingMomo}
                    className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {verifyingMomo ? "Checking..." : "They've approved it — verify now"}
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      <ReceiptView
        businessName={business.name}
        businessLogoUrl={business.logoUrl}
        businessLocation={business.location}
        businessContactPhone={business.contactPhone}
        documentLabel={`Service Order #${order.orderNumber}`}
        timestamp={order.pickedUpAt ?? order.receivedAt}
        metaLines={[`Customer: ${order.customerName ?? "Walk-in"}`]}
        items={items}
        total={order.price}
        footerLines={order.status !== "PICKED_UP" ? ["Not yet picked up"] : []}
      />
    </div>
  );
}
