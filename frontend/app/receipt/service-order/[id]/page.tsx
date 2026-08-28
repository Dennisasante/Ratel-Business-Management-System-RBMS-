"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessSummary, ServiceOrder } from "@/lib/api";
import ReceiptView from "@/components/ReceiptView";
import PaymentCollectionPanel from "@/components/PaymentCollectionPanel";

export default function ServiceOrderReceiptPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const params = useParams<{ id: string }>();

  const [order, setOrder] = useState<ServiceOrder | null>(null);
  const [business, setBusiness] = useState<BusinessSummary | null>(null);
  const [paystackConfigured, setPaystackConfigured] = useState(false);
  const [printerPaperWidth, setPrinterPaperWidth] = useState<"58" | "80">("80");
  const [error, setError] = useState<string | null>(null);

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
      setPrinterPaperWidth(i.receiptPrinterPaperWidth);
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
        <div className="mb-4 w-full max-w-xs print:hidden">
          <PaymentCollectionPanel<ServiceOrder>
            id={order.id}
            amount={order.price}
            balanceDue={order.balanceDue}
            paymentStatus={order.paymentStatus}
            paystackConfigured={paystackConfigured}
            onVerifyPayment={(reference) => api.verifyServiceOrderPayment(session.token, reference)}
            onMarkPaid={(id) => api.markServiceOrderPaid(session.token, id)}
            onChargeMobileMoney={(id, phone, provider) => api.chargeServiceOrderMobileMoney(session.token, id, phone, provider)}
            onSubmitOtp={(reference, otp) => api.submitServiceOrderMobileMoneyOtp(session.token, reference, otp)}
            onRecordPayment={(id, payload) => api.recordServiceOrderPayment(session.token, id, payload)}
            onRefund={(id, note) => api.refundServiceOrder(session.token, id, note)}
            onChanged={setOrder}
          />
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
        defaultPaperWidth={printerPaperWidth}
      />
    </div>
  );
}
