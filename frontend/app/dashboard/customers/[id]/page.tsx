"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ShoppingCart, Wrench, Wallet } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, Customer, Sale, ServiceOrder } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import StatCard from "@/components/ui/StatCard";
import CardSkeleton from "@/components/ui/CardSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const STATUS_TONES: Record<string, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  RECEIVED: "info",
  IN_PROGRESS: "accent",
  COMPLETED: "success",
  PICKED_UP: "violet",
  CANCELLED: "danger",
};

export default function CustomerDetailPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const params = useParams<{ id: string }>();

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [sales, setSales] = useState<Sale[]>([]);
  const [serviceOrders, setServiceOrders] = useState<ServiceOrder[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    setError(null);
    try {
      const [cust, allSales, allOrders] = await Promise.all([
        api.getCustomer(session.token, params.id),
        api.listSales(session.token),
        api.listServiceOrders(session.token),
      ]);
      setCustomer(cust);
      setSales(allSales.filter((s) => s.customerId === params.id));
      setServiceOrders(allOrders.filter((o) => o.customerId === params.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load this customer.");
    }
  }, [session, params.id]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load().finally(() => setFetching(false));
  }, [session, load]);

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link href="/dashboard/customers" className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-900">
          <ArrowLeft size={14} /> Back to customers
        </Link>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {fetching ? (
        <CardSkeleton count={3} />
      ) : customer ? (
        <>
          <PageHeader title={customer.fullName} subtitle={customer.phone ?? customer.email ?? "No contact on file"} />

          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard label="Total spent" value={`GH₵${customer.totalSpent.toFixed(2)}`} icon={Wallet} tone="success" />
            <StatCard label="Sales" value={sales.length} icon={ShoppingCart} tone="accent" />
            <StatCard label="Service orders" value={serviceOrders.length} icon={Wrench} tone="info" />
          </div>

          {customer.notes && (
            <Card className="p-4">
              <p className="text-sm text-ink-500">Notes</p>
              <p className="mt-1 text-sm text-ink-900">{customer.notes}</p>
            </Card>
          )}

          <Card>
            <h2 className="p-5 pb-0 text-base font-semibold text-ink-900">Sales history</h2>
            {sales.length === 0 ? (
              <p className="p-5 text-sm text-ink-500">No sales recorded for this customer yet.</p>
            ) : (
              <div className="mt-3">
                <Table>
                  <THead>
                    <Tr>
                      <Th>Sale</Th>
                      <Th>Items</Th>
                      <Th>Payment</Th>
                      <Th>Date</Th>
                      <Th className="text-right">Total</Th>
                    </Tr>
                  </THead>
                  <TBody>
                    {sales.map((s) => (
                      <Tr key={s.id}>
                        <Td className="tabular font-medium">#{s.saleNumber}</Td>
                        <Td className="text-ink-500">{s.items.map((i) => `${i.productName} ×${i.quantity}`).join(", ")}</Td>
                        <Td className="text-ink-500">{s.paymentMethod.replace("_", " ")}</Td>
                        <Td className="tabular text-ink-500">{new Date(s.createdAt).toLocaleDateString()}</Td>
                        <Td className="tabular text-right font-medium">GH₵{s.totalAmount.toFixed(2)}</Td>
                      </Tr>
                    ))}
                  </TBody>
                </Table>
              </div>
            )}
          </Card>

          <Card>
            <h2 className="p-5 pb-0 text-base font-semibold text-ink-900">Service order history</h2>
            {serviceOrders.length === 0 ? (
              <p className="p-5 text-sm text-ink-500">No service orders recorded for this customer yet.</p>
            ) : (
              <div className="mt-3">
                <Table>
                  <THead>
                    <Tr>
                      <Th>Order</Th>
                      <Th>Type</Th>
                      <Th>Status</Th>
                      <Th>Received</Th>
                      <Th className="text-right">Price</Th>
                    </Tr>
                  </THead>
                  <TBody>
                    {serviceOrders.map((o) => (
                      <Tr key={o.id}>
                        <Td className="tabular font-medium">#{o.orderNumber}</Td>
                        <Td className="text-ink-500">{o.serviceTypeName ?? "—"}</Td>
                        <Td>
                          <Badge tone={STATUS_TONES[o.status] ?? "neutral"}>{o.status.replace("_", " ")}</Badge>
                        </Td>
                        <Td className="tabular text-ink-500">{new Date(o.receivedAt).toLocaleDateString()}</Td>
                        <Td className="tabular text-right font-medium">GH₵{o.price.toFixed(2)}</Td>
                      </Tr>
                    ))}
                  </TBody>
                </Table>
              </div>
            )}
          </Card>
        </>
      ) : (
        <p className="text-sm text-ink-500">Customer not found.</p>
      )}
    </div>
  );
}
