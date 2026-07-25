"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ShoppingCart, Plus, X, Receipt, Search } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  Customer,
  PaymentMethod,
  Product,
  ProductCategory,
  Sale,
} from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

interface CartLine {
  product: Product;
  quantity: number;
  discount: string; // controlled input text, parsed to a number on submit
}

const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "MOBILE_MONEY", label: "Mobile Money" },
  { value: "CARD", label: "Card" },
  { value: "BANK_TRANSFER", label: "Bank Transfer" },
];

export default function SalesPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [sales, setSales] = useState<Sale[]>([]);
  const [fetching, setFetching] = useState(true);

  const [productSearch, setProductSearch] = useState("");
  const [productCategoryId, setProductCategoryId] = useState("");
  const [salesSearch, setSalesSearch] = useState("");

  const [cart, setCart] = useState<CartLine[]>([]);
  const [customerId, setCustomerId] = useState<string>("");
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    if (!session) return;
    const [p, cat, c, s] = await Promise.all([
      api.listProducts(session.token),
      api.listProductCategories(session.token),
      api.listCustomers(session.token),
      api.listSales(session.token),
    ]);
    setProducts(p);
    setCategories(cat);
    setCustomers(c);
    setSales(s);
  }, [session]);

  const visibleProducts = products.filter((p) => {
    if (productCategoryId && p.categoryId !== productCategoryId) return false;
    if (productSearch && !p.name.toLowerCase().includes(productSearch.toLowerCase()) && !(p.sku ?? "").toLowerCase().includes(productSearch.toLowerCase())) return false;
    return true;
  });

  const visibleSales = sales.filter((s) => {
    if (!salesSearch) return true;
    const q = salesSearch.toLowerCase();
    return (
      String(s.saleNumber).includes(q) ||
      (s.customerName ?? "walk-in").toLowerCase().includes(q) ||
      s.items.some((i) => i.productName.toLowerCase().includes(q))
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
      const existing = prev.find((l) => l.product.id === product.id);
      const currentQty = existing?.quantity ?? 0;
      if (currentQty + 1 > product.quantity) return prev;
      if (existing) {
        return prev.map((l) =>
          l.product.id === product.id ? { ...l, quantity: l.quantity + 1 } : l
        );
      }
      return [...prev, { product, quantity: 1, discount: "0" }];
    });
  }

  function updateQuantity(productId: string, quantity: number) {
    setCart((prev) =>
      prev
        .map((l) => (l.product.id === productId ? { ...l, quantity } : l))
        .filter((l) => l.quantity > 0)
    );
  }

  function updateDiscount(productId: string, discount: string) {
    setCart((prev) => prev.map((l) => (l.product.id === productId ? { ...l, discount } : l)));
  }

  function removeFromCart(productId: string) {
    setCart((prev) => prev.filter((l) => l.product.id !== productId));
  }

  function lineTotal(l: CartLine): number {
    const gross = l.product.sellingPrice * l.quantity;
    const discount = Math.min(Number(l.discount) || 0, gross);
    return gross - discount;
  }

  const total = cart.reduce((sum, l) => sum + lineTotal(l), 0);
  const totalDiscount = cart.reduce((sum, l) => sum + Math.min(Number(l.discount) || 0, l.product.sellingPrice * l.quantity), 0);

  async function completeSale() {
    if (!session || cart.length === 0) return;
    setError(null);
    setSubmitting(true);
    try {
      await api.createSale(session.token, {
        customerId: customerId || undefined,
        paymentMethod,
        items: cart.map((l) => ({
          productId: l.product.id,
          quantity: l.quantity,
          discountAmount: Number(l.discount) || 0,
        })),
      });
      setCart([]);
      setCustomerId("");
      await loadAll();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDownloadReceipt(saleId: string, saleNumber: number) {
    if (!session) return;
    setDownloadingId(saleId);
    try {
      await api.downloadReceipt(session.token, saleId, saleNumber);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't download that receipt.");
    } finally {
      setDownloadingId(null);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Sales / POS" subtitle="Build a cart, then complete the sale." />

      {fetching ? (
        <Card>
          <TableSkeleton cols={3} rows={4} />
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          {/* Product catalog */}
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
                  const inCart = cart.find((l) => l.product.id === p.id)?.quantity ?? 0;
                  const soldOut = p.quantity <= inCart;
                  return (
                    <button
                      key={p.id}
                      onClick={() => addToCart(p)}
                      disabled={soldOut}
                      className="flex items-center justify-between rounded-lg border border-border p-3 text-left text-sm transition hover:border-accent hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-border disabled:hover:bg-transparent"
                    >
                      <span>
                        <span className="block font-medium text-ink-900">{p.name}</span>
                        <span className="tabular block text-xs text-ink-500">
                          GH₵{p.sellingPrice.toFixed(2)} · {p.quantity - inCart} in stock
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

          {/* Cart / checkout */}
          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Cart</h2>

            {cart.length === 0 ? (
              <p className="mt-3 text-sm text-ink-500">Add products from the left to start a sale.</p>
            ) : (
              <ul className="mt-3 flex flex-col gap-3">
                {cart.map((l) => (
                  <li key={l.product.id} className="text-sm">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="font-medium text-ink-900">{l.product.name}</p>
                        <p className="tabular text-xs text-ink-500">
                          GH₵{l.product.sellingPrice.toFixed(2)} × {l.quantity}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min={1}
                          max={l.product.quantity}
                          value={l.quantity}
                          onChange={(e) => updateQuantity(l.product.id, Number(e.target.value))}
                          className="tabular w-14 rounded-md border border-border px-2 py-1 text-center focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                        />
                        <button
                          onClick={() => removeFromCart(l.product.id)}
                          className="rounded-md p-1 text-danger hover:bg-danger-soft"
                          aria-label="Remove"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    </div>
                    <div className="mt-1.5 flex items-center justify-between gap-2">
                      <div className="flex items-center gap-1.5">
                        <label className="text-xs text-ink-500">Discount</label>
                        <input
                          type="number"
                          min={0}
                          step="0.01"
                          value={l.discount}
                          onChange={(e) => updateDiscount(l.product.id, e.target.value)}
                          className="tabular w-20 rounded-md border border-border px-2 py-1 text-center text-xs focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                        />
                      </div>
                      <span className="tabular text-xs font-medium text-ink-700">GH₵{lineTotal(l).toFixed(2)}</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}

            <div className="mt-4 flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">Customer (optional)</label>
              <select
                value={customerId}
                onChange={(e) => setCustomerId(e.target.value)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                <option value="">Walk-in customer</option>
                {customers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.fullName}
                  </option>
                ))}
              </select>
            </div>

            <div className="mt-3 flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">Payment method</label>
              <select
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                {PAYMENT_METHODS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="mt-4 flex flex-col gap-1 border-t border-border pt-3">
              {totalDiscount > 0 && (
                <div className="flex items-center justify-between text-sm text-danger">
                  <span>Discount</span>
                  <span className="tabular">-GH₵{totalDiscount.toFixed(2)}</span>
                </div>
              )}
              <div className="flex items-center justify-between">
                <span className="font-medium text-ink-700">Total</span>
                <span className="tabular text-lg font-semibold text-ink-900">GH₵{total.toFixed(2)}</span>
              </div>
            </div>

            {error && <p className="mt-2 text-sm text-danger">{error}</p>}

            <Button onClick={completeSale} disabled={cart.length === 0 || submitting} className="mt-4 w-full">
              {submitting ? "Completing sale..." : "Complete sale"}
            </Button>
          </Card>
        </div>
      )}

      {/* Recent sales */}
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3 p-5 pb-0">
          <h2 className="text-base font-semibold text-ink-900">Recent sales</h2>
          {sales.length > 0 && (
            <div className="relative min-w-[200px]">
              <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
              <input
                value={salesSearch}
                onChange={(e) => setSalesSearch(e.target.value)}
                placeholder="Search sale #, customer, item"
                className="w-full rounded-lg border border-border bg-surface py-1.5 pl-8 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
            </div>
          )}
        </div>
        {fetching ? (
          <TableSkeleton cols={6} />
        ) : sales.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title="No sales recorded yet"
            description="Completed sales will show up here."
          />
        ) : visibleSales.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">No sales match this search.</p>
        ) : (
          <div className="mt-3">
            <Table>
              <THead>
                <Tr>
                  <Th>Sale</Th>
                  <Th>Customer</Th>
                  <Th>Items</Th>
                  <Th>Payment</Th>
                  <Th className="text-right">Total</Th>
                  <Th></Th>
                </Tr>
              </THead>
              <TBody>
                {visibleSales.map((s) => (
                  <Tr key={s.id}>
                    <Td className="tabular font-medium">#{s.saleNumber}</Td>
                    <Td className="text-ink-500">{s.customerName ?? "Walk-in"}</Td>
                    <Td className="text-ink-500">
                      {s.items.map((i) => `${i.productName} ×${i.quantity}`).join(", ")}
                    </Td>
                    <Td className="text-ink-500">{s.paymentMethod.replace("_", " ")}</Td>
                    <Td className="tabular text-right font-medium">GH₵{s.totalAmount.toFixed(2)}</Td>
                    <Td className="text-right">
                      <div className="flex justify-end gap-3">
                        <Link
                          href={`/receipt/sale/${s.id}`}
                          target="_blank"
                          className="text-sm font-medium text-accent-hover hover:underline"
                        >
                          Print Receipt
                        </Link>
                        <button
                          onClick={() => handleDownloadReceipt(s.id, s.saleNumber)}
                          disabled={downloadingId === s.id}
                          className="text-sm font-medium text-ink-700 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {downloadingId === s.id ? "Preparing..." : "PDF"}
                        </button>
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
