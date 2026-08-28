"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessSummary, Sale } from "@/lib/api";
import ReceiptView from "@/components/ReceiptView";
import PaymentCollectionPanel from "@/components/PaymentCollectionPanel";

export default function SaleReceiptPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  // Set only by the Sales page right after a sale completes, and only when
  // the business has a receipt printer configured — see completeSale() there.
  // The gateway-status check below is the real authority either way: a
  // stale/bookmarked "?autoprint=1" URL can never trigger printing on its
  // own if the business's printer setting is off.
  const requestedAutoPrint = searchParams.get("autoprint") === "1";

  const [sale, setSale] = useState<Sale | null>(null);
  const [business, setBusiness] = useState<BusinessSummary | null>(null);
  const [paystackConfigured, setPaystackConfigured] = useState(false);
  const [printerEnabled, setPrinterEnabled] = useState(false);
  const [printerPaperWidth, setPrinterPaperWidth] = useState<"58" | "80">("80");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      const [s, b, i] = await Promise.all([
        api.getSale(session.token, params.id),
        api.getMyBusiness(session.token),
        api.getPaymentGatewayStatus(session.token),
      ]);
      setSale(s);
      setBusiness(b);
      setPaystackConfigured(i.paystackConfigured);
      setPrinterEnabled(i.receiptPrinterEnabled);
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

  if (!sale || !business) {
    return <p className="p-6 text-sm text-ink-500">Loading receipt...</p>;
  }

  // Cash/direct-mobile-money sales are PAID the instant they're rung up —
  // nothing further to collect, so the payment panel only matters for Online
  // Payment sales that started out UNPAID.
  const showPaymentPanel = sale.paymentStatus !== "PAID" || sale.paymentMethod === "MOBILE_MONEY";

  return (
    <div className="flex flex-col items-center">
      {showPaymentPanel && (
        <div className="mb-4 w-full max-w-xs print:hidden">
          <PaymentCollectionPanel<Sale>
            id={sale.id}
            amount={sale.totalAmount}
            balanceDue={sale.balanceDue}
            paymentStatus={sale.paymentStatus}
            paystackConfigured={paystackConfigured}
            onVerifyPayment={(reference) => api.verifySalePayment(session.token, reference)}
            onMarkPaid={(id) => api.markSalePaid(session.token, id)}
            onChargeMobileMoney={(id, phone, provider) => api.chargeSaleMobileMoney(session.token, id, phone, provider)}
            onSubmitOtp={(reference, otp) => api.submitSaleMobileMoneyOtp(session.token, reference, otp)}
            onRecordPayment={(id, payload) => api.recordSalePayment(session.token, id, payload)}
            onRefund={(id, note) => api.refundSale(session.token, id, note)}
            onChanged={setSale}
          />
        </div>
      )}

      <ReceiptView
        businessName={business.name}
        businessLogoUrl={business.logoUrl}
        businessLocation={business.location}
        businessContactPhone={business.contactPhone}
        documentLabel={`Receipt #${sale.saleNumber}`}
        timestamp={sale.createdAt}
        metaLines={[
          `Served by: ${sale.cashierName}`,
          `Customer: ${sale.customerName ?? "Walk-in"}`,
        ]}
        items={sale.items.map((i) => ({
          name: i.productName,
          quantity: i.quantity,
          unitPrice: i.unitPrice,
          discountAmount: i.discountAmount,
          subtotal: i.subtotal,
          gift: i.gift,
        }))}
        total={sale.totalAmount}
        footerLines={[`Payment: ${sale.paymentMethod.replaceAll("_", " ")}`]}
        defaultPaperWidth={printerPaperWidth}
        autoPrint={requestedAutoPrint && printerEnabled}
      />
    </div>
  );
}
