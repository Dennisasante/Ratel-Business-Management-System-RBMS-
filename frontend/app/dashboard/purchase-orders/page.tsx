"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Plus, X, ClipboardList, Search } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, Product, ProductCategory, PurchaseOrder, Supplier } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

interface CartLine {
  product: Product;
  quantity: number;
  unitCost: number;
}

export default function PurchaseOrdersPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [fetching, setFetching] = useState(true);

  const [productSearch, setProductSearch] = useState("");
  const [productCategoryId, setProductCategoryId] = useState("");
  const [orderSearch, setOrderSearch] = useState("");

  const [cart, setCart] = useState<CartLine[]>([]);
  const [supplierId, setSupplierId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<string | null>(null); // `${orderId}:${action}`

  const loadAll = useCallback(async () => {
    if (!session) return;
    const [p, cat, s, o] = await Promise.all([
      api.listProducts(session.token),
      api.listProductCategories(session.token),
      api.listSuppliers(session.token),
      api.listPurchaseOrders(session.token),
    ]);
    setProducts(p);
    setCategories(cat);
    setSuppliers(s);
    setOrders(o);
  }, [session]);

  const visibleProducts = products.filter((p) => {
    if (productCategoryId && p.categoryId !== productCategoryId) return false;
    if (productSearch && !p.name.toLowerCase().includes(productSearch.toLowerCase()) && !(p.sku ?? "").toLowerCase().includes(productSearch.toLowerCase())) return false;
    return true;
  });

  const visibleOrders = orders.filter((o) => {
    if (!orderSearch) return true;
    const q = orderSearch.toLowerCase();
    return (
      String(o.poNumber).includes(q) ||
      (o.supplierName ?? "").toLowerCase().includes(q) ||
      o.status.toLowerCase().includes(q) ||
      o.items.some((i) => i.productName.toLowerCase().includes(q))
    );
  });

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadAll().finally(() => setFetching(false));
  }, [session, loadAll]);

  function addToCart(product: Product) {
    setCart((prev) => {
      if (prev.some((l) => l.product.id === product.id)) return prev;
      return [...prev, { product, quantity: 1, unitCost: product.costPrice }];
    });
  }

  function updateLine(productId: string, field: "quantity" | "unitCost", value: number) {
    setCart((prev) => prev.map((l) => (l.product.id === productId ? { ...l, [field]: value } : l)));
  }

  function removeFromCart(productId: string) {
    setCart((prev) => prev.filter((l) => l.product.id !== productId));
  }

  const total = cart.reduce((sum, l) => sum + l.unitCost * l.quantity, 0);

  async function submitOrder() {
    if (!session || cart.length === 0) return;
    setError(null);
    setSubmitting(true);
    try {
      await api.createPurchaseOrder(session.token, {
        supplierId: supplierId || undefined,
        items: cart.map((l) => ({ productId: l.product.id, quantity: l.quantity, unitCost: l.unitCost })),
      });
      setCart([]);
      setSupplierId("");
      await loadAll();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReceive(id: string) {
    if (!session) return;
    setActionError(null);
    setPendingAction(`${id}:receive`);
    try {
      await api.receivePurchaseOrder(session.token, id);
      await loadAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't mark this as received.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleCancel(id: string) {
    if (!session) return;
    setActionError(null);
    setPendingAction(`${id}:cancel`);
    try {
      await api.cancelPurchaseOrder(session.token, id);
      await loadAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't cancel this order.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleMarkPaid(id: string) {
    if (!session) return;
    setActionError(null);
    setPendingAction(`${id}:markPaid`);
    try {
      await api.markPurchaseOrderPaid(session.token, id);
      await loadAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't mark this order as paid.");
    } finally {
      setPendingAction(null);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Purchase Orders" subtitle="Restock inventory — stock only updates once an order is marked received." />

      {fetching ? (
        <Card>
          <TableSkeleton cols={3} rows={4} />
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card className="p-5 lg:col-span-2">
            <h2 className="text-base font-semibold text-ink-900">Products</h2>
            {products.length === 0 ? (
              <p className="mt-3 text-sm text-ink-500">
                No products yet.{" "}
                <Link href="/dashboard/inventory" className="font-medium text-accent-hover hover:underline">
                  Add some in Inventory
                </Link>
                .
              </p>
            ) : (
              <>
                <div className="mt-3 flex flex-wrap gap-2">
                  <div className="relative min-w-[160px] flex-1">
                    <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
                    <input
                      value={productSearch}
                      onChange={(e) => setProductSearch(e.target.value)}
                      placeholder="Search products"
                      className="w-full rounded-lg border border-border bg-surface py-1.5 pl-8 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                    />
                  </div>
                  <select
                    value={productCategoryId}
                    onChange={(e) => setProductCategoryId(e.target.value)}
                    className="rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  >
                    <option value="">All categories</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                {visibleProducts.length === 0 ? (
                  <p className="mt-3 text-sm text-ink-500">No products match this search.</p>
                ) : (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {visibleProducts.map((p) => {
                  const inCart = cart.some((l) => l.product.id === p.id);
                  return (
                    <button
                      key={p.id}
                      onClick={() => addToCart(p)}
                      disabled={inCart}
                      className="flex items-center justify-between rounded-lg border border-border p-3 text-left text-sm transition hover:border-accent hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <span>
                        <span className="block font-medium text-ink-900">{p.name}</span>
                        <span className="tabular block text-xs text-ink-500">
                          Cost GH₵{p.costPrice.toFixed(2)} · {p.quantity} on hand
                        </span>
                      </span>
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-accent-soft text-accent-hover">
                        <Plus size={14} />
                      </span>
                    </button>
                  );
                })}
              </div>
                )}
              </>
            )}
          </Card>

          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">New order</h2>

            {cart.length === 0 ? (
              <p className="mt-3 text-sm text-ink-500">Add products from the left to build a purchase order.</p>
            ) : (
              <ul className="mt-3 flex flex-col gap-3">
                {cart.map((l) => (
                  <li key={l.product.id} className="text-sm">
                    <div className="flex items-center justify-between">
                      <p className="font-medium text-ink-900">{l.product.name}</p>
                      <button
                        onClick={() => removeFromCart(l.product.id)}
                        className="rounded-md p-1 text-danger hover:bg-danger-soft"
                        aria-label="Remove"
                      >
                        <X size={14} />
                      </button>
                    </div>
                    <div className="mt-1 flex items-center gap-2">
                      <label className="text-xs text-ink-500">Qty</label>
                      <input
                        type="number"
                        min={1}
                        value={l.quantity}
                        onChange={(e) => updateLine(l.product.id, "quantity", Number(e.target.value))}
                        className="tabular w-16 rounded-md border border-border px-2 py-1 text-center text-xs"
                      />
                      <label className="text-xs text-ink-500">Unit cost</label>
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={l.unitCost}
                        onChange={(e) => updateLine(l.product.id, "unitCost", Number(e.target.value))}
                        className="tabular w-20 rounded-md border border-border px-2 py-1 text-center text-xs"
                      />
                    </div>
                  </li>
                ))}
              </ul>
            )}

            <div className="mt-4 flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">Supplier (optional)</label>
              <select
                value={supplierId}
                onChange={(e) => setSupplierId(e.target.value)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                <option value="">No supplier on file</option>
                {suppliers.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="mt-4 flex items-center justify-between border-t border-border pt-3">
              <span className="font-medium text-ink-700">Total</span>
              <span className="tabular text-lg font-semibold text-ink-900">GH₵{total.toFixed(2)}</span>
            </div>

            {error && <p className="mt-2 text-sm text-danger">{error}</p>}

            <Button onClick={submitOrder} disabled={cart.length === 0 || submitting} className="mt-4 w-full">
              {submitting ? "Creating order..." : "Create purchase order"}
            </Button>
          </Card>
        </div>
      )}

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3 p-5 pb-0">
          <h2 className="text-base font-semibold text-ink-900">Orders</h2>
          {orders.length > 0 && (
            <div className="relative min-w-[200px]">
              <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
              <input
                value={orderSearch}
                onChange={(e) => setOrderSearch(e.target.value)}
                placeholder="Search PO #, supplier, item"
                className="w-full rounded-lg border border-border bg-surface py-1.5 pl-8 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
            </div>
          )}
        </div>
        {actionError && <p className="px-5 pt-2 text-sm text-danger">{actionError}</p>}
        {fetching ? (
          <TableSkeleton cols={6} />
        ) : orders.length === 0 ? (
          <EmptyState icon={ClipboardList} title="No purchase orders yet" description="Orders you create will show up here." />
        ) : visibleOrders.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">No orders match this search.</p>
        ) : (
          <div className="mt-3">
            <Table>
              <THead>
                <Tr>
                  <Th>PO</Th>
                  <Th>Supplier</Th>
                  <Th>Items</Th>
                  <Th>Total</Th>
                  <Th>Status</Th>
                  <Th className="text-right">Actions</Th>
                </Tr>
              </THead>
              <TBody>
                {visibleOrders.map((o) => (
                  <Tr key={o.id}>
                    <Td className="tabular font-medium">#{o.poNumber}</Td>
                    <Td className="text-ink-500">{o.supplierName ?? "—"}</Td>
                    <Td className="text-ink-500">
                      {o.items.map((i) => `${i.productName} ×${i.quantity}`).join(", ")}
                    </Td>
                    <Td className="tabular font-medium">GH₵{o.totalAmount.toFixed(2)}</Td>
                    <Td>
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Badge tone={o.status === "RECEIVED" ? "success" : o.status === "CANCELLED" ? "danger" : "neutral"}>
                          {o.status}
                        </Badge>
                        {o.paymentStatus !== "PAID" && o.status !== "CANCELLED" && <Badge tone="neutral">Unpaid</Badge>}
                      </div>
                    </Td>
                    <Td className="text-right">
                      <div className="flex flex-wrap justify-end gap-3">
                        {o.status === "PENDING" && (
                          <>
                            <button
                              onClick={() => handleReceive(o.id)}
                              disabled={pendingAction === `${o.id}:receive` || pendingAction === `${o.id}:cancel`}
                              className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              {pendingAction === `${o.id}:receive` ? "Marking..." : "Mark received"}
                            </button>
                            <button
                              onClick={() => handleCancel(o.id)}
                              disabled={pendingAction === `${o.id}:receive` || pendingAction === `${o.id}:cancel`}
                              className="text-sm font-medium text-danger hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              {pendingAction === `${o.id}:cancel` ? "Cancelling..." : "Cancel"}
                            </button>
                          </>
                        )}
                        {o.paymentStatus !== "PAID" && o.status !== "CANCELLED" && (
                          <button
                            onClick={() => handleMarkPaid(o.id)}
                            disabled={pendingAction === `${o.id}:markPaid`}
                            className="text-sm font-medium text-success hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                          >
                            {pendingAction === `${o.id}:markPaid` ? "Marking..." : "Mark paid"}
                          </button>
                        )}
                      </div>
                    </Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
          </div>
        )}
      </Card>
    </div>
  );
}
