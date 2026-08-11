"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessSummary, ServiceOrder } from "@/lib/api";
import ReceiptView from "@/components/ReceiptView";

export default function ServiceOrderReceiptPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const params = useParams<{ id: string }>();

  const [order, setOrder] = useState<ServiceOrder | null>(null);
  const [business, setBusiness] = useState<BusinessSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      const [o, b] = await Promise.all([
        api.getServiceOrder(session.token, params.id),
        api.getMyBusiness(session.token),
      ]);
      setOrder(o);
      setBusiness(b);
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

  return (
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
  );
}
