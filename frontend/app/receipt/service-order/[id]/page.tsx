"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { CheckCircle2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessIntegrations, BusinessSummary, ServiceOrder } from "@/lib/api";
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

export default function ServiceOrderReceiptPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const params = useParams<{ id: string }>();

  const [order, setOrder] = useState<ServiceOrder | null>(null);
  const [business, setBusiness] = useState<BusinessSummary | null>(null);
  const [integrations, setIntegrations] = useState<BusinessIntegrations | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [payError, setPayError] = useState<string | null>(null);
  const [markingPaid, setMarkingPaid] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      const [o, b, i] = await Promise.all([
        api.getServiceOrder(session.token, params.id),
        api.getMyBusiness(session.token),
        api.getBusinessIntegrations(session.token),
      ]);
      setOrder(o);
      setBusiness(b);
      setIntegrations(i);
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
              {integrations?.paystackSecretConfigured && (
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
