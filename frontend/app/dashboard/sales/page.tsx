"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ShoppingCart, Plus, X, Receipt, Search, ChevronDown, ChevronLeft, ChevronRight } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  PaymentMethod,
  Product,
  ProductCategory,
  Sale,
  ServiceCatalogItem,
  ServiceCatalogItemPayload,
  ServiceType,
  UserSummary,
} from "@/lib/api";
import Modal from "@/components/Modal";
import CustomerPicker from "@/components/CustomerPicker";
import QuickServiceCatalogForm from "@/components/QuickServiceCatalogForm";
import PaymentCollectionPanel from "@/components/PaymentCollectionPanel";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";
import DateRangeFilter from "@/components/ui/DateRangeFilter";
import { DateRangeValue, defaultDateRangeValue } from "@/lib/dateRangePresets";

interface ProductCartLine {
  kind: "product";
  product: Product;
  quantity: number;
  discount: string; // controlled input text, parsed to a number on submit
  gift: boolean;
}

interface ServiceCartLine {
  kind: "service";
  service: ServiceCatalogItem;
  quantity: number;
  discount: string;
  gift: boolean;
}

type CartLine = ProductCartLine | ServiceCartLine;

function cartLineKey(l: CartLine): string {
  return `${l.kind}:${l.kind === "product" ? l.product.id : l.service.id}`;
}

function cartLineName(l: CartLine): string {
  return l.kind === "product" ? l.product.name : l.service.name;
}

function cartLineUnitPrice(l: CartLine): number {
  return l.kind === "product" ? l.product.sellingPrice : l.service.price;
}

const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "MOBILE_MONEY_DIRECT", label: "Direct Mobile Money" },
  { value: "MOBILE_MONEY", label: "Online Payment" },
];

export default function SalesPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [services, setServices] = useState<ServiceCatalogItem[]>([]);
  const [serviceTypes, setServiceTypes] = useState<ServiceType[]>([]);
  const [sales, setSales] = useState<Sale[]>([]);
  const [staffMembers, setStaffMembers] = useState<UserSummary[]>([]);
  const [fetching, setFetching] = useState(true);

  const [catalogOpen, setCatalogOpen] = useState(false);
  const [catalogTab, setCatalogTab] = useState<"products" | "services">("products");
  const [productSearch, setProductSearch] = useState("");
  const [productCategoryId, setProductCategoryId] = useState("");
  const [serviceSearch, setServiceSearch] = useState("");
  const [salesSearch, setSalesSearch] = useState("");
  const [salesDateRange, setSalesDateRange] = useState<DateRangeValue>(defaultDateRangeValue());
  const [salesFetching, setSalesFetching] = useState(true);
  const [salesCashierFilter, setSalesCashierFilter] = useState("");
  const [salesPaymentFilter, setSalesPaymentFilter] = useState<PaymentMethod | "">("");
  const [salesPage, setSalesPage] = useState(0);
  const SALES_PAGE_SIZE = 10;

  const [cart, setCart] = useState<CartLine[]>([]);
  const [customerId, setCustomerId] = useState<string>("");
  // Bumped after every completed sale to force CustomerPicker to remount and
  // reset back to its unselected search state.
  const [customerPickerKey, setCustomerPickerKey] = useState(0);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [showAddService, setShowAddService] = useState(false);

  const [paystackConfigured, setPaystackConfigured] = useState(false);
  // Whether this business has a receipt printer configured (Profile →
  // Integrations → Receipt Printer) — gates both showing the "Print" link
  // per row and auto-opening the print dialog right after checkout below.
  const [printerEnabled, setPrinterEnabled] = useState(false);
  // Any sale currently open in the "Collect payment" modal — set right after
  // checkout for a CARD/MOBILE_MONEY sale, or by clicking "Collect payment"
  // on an older still-unpaid row. Either way, payment never requires leaving
  // this page or opening the printable receipt.
  const [collectingPaymentSale, setCollectingPaymentSale] = useState<Sale | null>(null);

  const loadAll = useCallback(async () => {
    if (!session) return;
    const [p, cat, svc, svcTypes, gw, users] = await Promise.all([
      api.listProducts(session.token),
      api.listProductCategories(session.token),
      api.listServiceCatalog(session.token, true),
      api.listServiceTypes(session.token),
      api.getPaymentGatewayStatus(session.token),
      api.listUsers(session.token),
    ]);
    setProducts(p);
    setCategories(cat);
    setServices(svc);
    setServiceTypes(svcTypes);
    setPaystackConfigured(gw.paystackConfigured);
    setPrinterEnabled(gw.receiptPrinterEnabled);
    setStaffMembers(users);
  }, [session]);

  // Opens the receipt in a new tab with auto-print requested — the receipt
  // page itself re-checks the printer setting before actually calling
  // window.print(), so this is safe to call unconditionally once a sale is
  // confirmed PAID; it's a no-op (still just opens the receipt tab) if the
  // setting happens to be off.
  function openReceiptForPrint(saleId: string) {
    if (!printerEnabled) return;
    window.open(`/receipt/sale/${saleId}?autoprint=1`, "_blank");
  }

  // Server-side date + staff filter, refetched on its own whenever either
  // changes — separate from loadAll() so switching "Today" -> "This month"
  // doesn't also re-fetch the whole product/service catalog. Defaults to
  // Today (see defaultDateRangeValue) rather than loading every sale the
  // business has ever made.
  const loadSales = useCallback(async () => {
    if (!session) return;
    const s = await api.listSales(session.token, {
      cashierId: salesCashierFilter || undefined,
      from: salesDateRange.from ?? undefined,
      to: salesDateRange.to ?? undefined,
    });
    setSales(s);
  }, [session, salesCashierFilter, salesDateRange]);

  const visibleProducts = products.filter((p) => {
    if (productCategoryId && p.categoryId !== productCategoryId) return false;
    if (productSearch && !p.name.toLowerCase().includes(productSearch.toLowerCase()) && !(p.sku ?? "").toLowerCase().includes(productSearch.toLowerCase())) return false;
    return true;
  });

  const visibleServices = services.filter((s) => {
    if (serviceSearch && !s.name.toLowerCase().includes(serviceSearch.toLowerCase())) return false;
    return true;
  });

  const visibleSales = sales.filter((s) => {
    if (salesPaymentFilter && s.paymentMethod !== salesPaymentFilter) return false;
    if (!salesSearch) return true;
    const q = salesSearch.toLowerCase();
    return (
      String(s.saleNumber).includes(q) ||
      (s.customerName ?? "walk-in").toLowerCase().includes(q) ||
      s.items.some((i) => i.productName.toLowerCase().includes(q))
    );
  });

  const salesPageCount = Math.max(1, Math.ceil(visibleSales.length / SALES_PAGE_SIZE));
  const pagedSales = visibleSales.slice(salesPage * SALES_PAGE_SIZE, salesPage * SALES_PAGE_SIZE + SALES_PAGE_SIZE);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadAll().finally(() => setFetching(false));
  }, [session, loadAll]);

  useEffect(() => {
    loadSales().finally(() => setSalesFetching(false));
  }, [loadSales]);

  useEffect(() => {
    setSalesPage(0);
  }, [salesSearch, salesDateRange, salesCashierFilter, salesPaymentFilter]);

  useEffect(() => {
    setSalesPage((p) => Math.min(p, salesPageCount - 1));
  }, [salesPageCount]);

  function addProductToCart(product: Product) {
    setCart((prev) => {
      const existing = prev.find((l) => l.kind === "product" && l.product.id === product.id);
      const currentQty = existing?.quantity ?? 0;
      if (currentQty + 1 > product.quantity) return prev;
      if (existing) {
        return prev.map((l) =>
          l.kind === "product" && l.product.id === product.id ? { ...l, quantity: l.quantity + 1 } : l
        );
      }
      return [...prev, { kind: "product", product, quantity: 1, discount: "0", gift: false }];
    });
  }

  function addServiceToCart(service: ServiceCatalogItem) {
    setCart((prev) => {
      const existing = prev.find((l) => l.kind === "service" && l.service.id === service.id);
      if (existing) {
        return prev.map((l) =>
          l.kind === "service" && l.service.id === service.id ? { ...l, quantity: l.quantity + 1 } : l
        );
      }
      return [...prev, { kind: "service", service, quantity: 1, discount: "0", gift: false }];
    });
  }

  function updateQuantity(key: string, quantity: number) {
    setCart((prev) =>
      prev
        .map((l) => (cartLineKey(l) === key ? { ...l, quantity } : l))
        .filter((l) => l.quantity > 0)
    );
  }

  function updateDiscount(key: string, discount: string) {
    setCart((prev) => prev.map((l) => (cartLineKey(l) === key ? { ...l, discount } : l)));
  }

  function toggleGift(key: string, gift: boolean) {
    setCart((prev) => prev.map((l) => (cartLineKey(l) === key ? { ...l, gift } : l)));
  }

  function removeFromCart(key: string) {
    setCart((prev) => prev.filter((l) => cartLineKey(l) !== key));
  }

  function lineDiscount(l: CartLine): number {
    const gross = cartLineUnitPrice(l) * l.quantity;
    return l.gift ? gross : Math.min(Number(l.discount) || 0, gross);
  }

  function lineTotal(l: CartLine): number {
    const gross = cartLineUnitPrice(l) * l.quantity;
    return gross - lineDiscount(l);
  }

  const total = cart.reduce((sum, l) => sum + lineTotal(l), 0);
  const totalDiscount = cart.reduce((sum, l) => sum + lineDiscount(l), 0);

  async function completeSale() {
    if (!session || cart.length === 0) return;
    if (!customerId) {
      setError("Pick a customer first.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const sale = await api.createSale(session.token, {
        customerId,
        paymentMethod,
        items: cart.map((l) =>
          l.kind === "product"
            ? { productId: l.product.id, quantity: l.quantity, discountAmount: Number(l.discount) || 0, gift: l.gift }
            : { serviceCatalogId: l.service.id, quantity: l.quantity, discountAmount: Number(l.discount) || 0, gift: l.gift }
        ),
      });
      setCart([]);
      setCustomerId("");
      setCustomerPickerKey((k) => k + 1);
      if (sale.paymentStatus !== "PAID") {
        // Card/mobile money — open the payment modal right away instead of
        // leaving the cashier to hunt for it afterward. Printing (if
        // enabled) happens once it's actually confirmed PAID — see
        // handleSaleChanged below — not before money is actually in.
        setCollectingPaymentSale(sale);
      } else {
        // Cash/direct mobile money are PAID the instant they're rung up.
        openReceiptForPrint(sale.id);
      }
      // loadAll() refreshes stock quantities; loadSales() picks up the new
      // sale itself — but only if it actually falls inside the currently
      // selected date range (e.g. still shows if "Today" is selected, which
      // it always will be immediately after a fresh sale).
      await Promise.all([loadAll(), loadSales()]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function handleSaleChanged(updated: Sale) {
    // Genuine unpaid -> PAID transition (not just a re-render of an
    // already-settled sale) — this is the actual "money just arrived"
    // moment for a card/mobile money sale, so it's when printing (if
    // enabled) should fire, same as the instant-PAID cash path above.
    const justPaid = collectingPaymentSale?.id === updated.id && collectingPaymentSale.paymentStatus !== "PAID" && updated.paymentStatus === "PAID";
    setCollectingPaymentSale(updated);
    setSales((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
    if (updated.paymentStatus === "PAID") {
      setCollectingPaymentSale(null);
      if (justPaid) openReceiptForPrint(updated.id);
    }
  }

  async function handleAddService(payload: ServiceCatalogItemPayload) {
    if (!session) return;
    const service = await api.createServiceCatalogItem(session.token, payload);
    setServices((prev) => [...prev, service].sort((a, b) => a.name.localeCompare(b.name)));
    setShowAddService(false);
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
          {/* Product / service catalog */}
          <Card className="p-5 lg:col-span-2">
            <button
              type="button"
              onClick={() => setCatalogOpen((o) => !o)}
              className="flex w-full items-center justify-between text-left"
            >
              <div>
                <h2 className="text-base font-semibold text-ink-900">Products &amp; services</h2>
                <p className="text-xs text-ink-500">{catalogOpen ? "Tap to collapse" : "Tap to add items to the cart"}</p>
              </div>
              <ChevronDown
                size={18}
                className={`shrink-0 text-ink-400 transition-transform ${catalogOpen ? "rotate-180" : ""}`}
              />
            </button>

            {catalogOpen && (
            <div className="mt-4">
            <div className="flex items-center justify-between">
              <div className="flex gap-1 rounded-lg bg-canvas p-1">
                <button
                  onClick={() => setCatalogTab("products")}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                    catalogTab === "products" ? "bg-surface text-ink-900 shadow-sm" : "text-ink-500 hover:text-ink-700"
                  }`}
                >
                  Products
                </button>
                <button
                  onClick={() => setCatalogTab("services")}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                    catalogTab === "services" ? "bg-surface text-ink-900 shadow-sm" : "text-ink-500 hover:text-ink-700"
                  }`}
                >
                  Services
                </button>
              </div>
              {catalogTab === "services" && (
                <button
                  type="button"
                  onClick={() => setShowAddService(true)}
                  className="text-xs font-medium text-accent-hover hover:underline"
                >
                  + New service
                </button>
              )}
            </div>

            {catalogTab === "products" ? (
              products.length === 0 ? (
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
                        const inCart = cart.find((l) => l.kind === "product" && l.product.id === p.id)?.quantity ?? 0;
                        const soldOut = p.quantity <= inCart;
                        return (
                          <button
                            key={p.id}
                            onClick={() => addProductToCart(p)}
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
              )
            ) : services.length === 0 ? (
              <p className="mt-3 text-sm text-ink-500">
                No services yet. Use &quot;+ New service&quot; above, or add some in the{" "}
                <Link href="/dashboard/service-orders/catalog" className="font-medium text-accent-hover hover:underline">
                  Services
                </Link>
                .
              </p>
            ) : (
              <>
                <div className="relative mt-3 min-w-[160px]">
                  <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    value={serviceSearch}
                    onChange={(e) => setServiceSearch(e.target.value)}
                    placeholder="Search services"
                    className="w-full rounded-lg border border-border bg-surface py-1.5 pl-8 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                </div>
                {visibleServices.length === 0 ? (
                  <p className="mt-3 text-sm text-ink-500">No services match this search.</p>
                ) : (
                  <div className="mt-3 grid gap-3 sm:grid-cols-2">
                    {visibleServices.map((s) => (
                      <button
                        key={s.id}
                        onClick={() => addServiceToCart(s)}
                        className="flex items-center justify-between rounded-lg border border-border p-3 text-left text-sm transition hover:border-accent hover:bg-accent-soft"
                      >
                        <span>
                          <span className="block font-medium text-ink-900">{s.name}</span>
                          <span className="tabular block text-xs text-ink-500">GH₵{s.price.toFixed(2)}</span>
                        </span>
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-accent-soft text-accent-hover">
                          <Plus size={14} />
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </>
            )}
            </div>
            )}
          </Card>

          {/* Cart / checkout */}
          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Cart</h2>

            {cart.length === 0 ? (
              <p className="mt-3 text-sm text-ink-500">Add products or services from the left to start a sale.</p>
            ) : (
              <ul className="mt-3 flex flex-col gap-3">
                {cart.map((l) => {
                  const key = cartLineKey(l);
                  return (
                  <li key={key} className="text-sm">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="font-medium text-ink-900">
                          {cartLineName(l)}
                          {l.kind === "service" && <span className="ml-1.5 text-xs font-normal text-ink-400">Service</span>}
                        </p>
                        <p className="tabular text-xs text-ink-500">
                          GH₵{cartLineUnitPrice(l).toFixed(2)} × {l.quantity}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min={1}
                          max={l.kind === "product" ? l.product.quantity : undefined}
                          value={l.quantity}
                          onChange={(e) => updateQuantity(key, Number(e.target.value))}
                          className="tabular w-14 rounded-md border border-border px-2 py-1 text-center focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                        />
                        <button
                          onClick={() => removeFromCart(key)}
                          className="rounded-md p-1 text-danger hover:bg-danger-soft"
                          aria-label="Remove"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    </div>
                    <div className="mt-1.5 flex items-center justify-between gap-2">
                      <div className="flex items-center gap-3">
                        <div className="flex items-center gap-1.5">
                          <label className="text-xs text-ink-500">Discount</label>
                          <input
                            type="number"
                            min={0}
                            step="0.01"
                            value={l.discount}
                            disabled={l.gift}
                            onChange={(e) => updateDiscount(key, e.target.value)}
                            className="tabular w-20 rounded-md border border-border px-2 py-1 text-center text-xs focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20 disabled:opacity-50"
                          />
                        </div>
                        <label className="flex items-center gap-1.5 text-xs text-ink-500">
                          <input
                            type="checkbox"
                            checked={l.gift}
                            onChange={(e) => toggleGift(key, e.target.checked)}
                            className="rounded border-border text-accent focus:ring-accent/20"
                          />
                          Gift
                        </label>
                      </div>
                      <span className="tabular text-xs font-medium text-ink-700">
                        {l.gift ? <Badge tone="violet">Gift</Badge> : `GH₵${lineTotal(l).toFixed(2)}`}
                      </span>
                    </div>
                  </li>
                  );
                })}
              </ul>
            )}

            <div className="mt-4 flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">
                Customer <span className="text-danger">*</span>
              </label>
              <CustomerPicker
                key={customerPickerKey}
                token={session.token}
                onSelect={(c) => setCustomerId(c.id)}
              />
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

            <Button onClick={completeSale} disabled={cart.length === 0 || !customerId || submitting} className="mt-4 w-full">
              {submitting ? "Completing sale..." : "Complete sale"}
            </Button>
          </Card>
        </div>
      )}

      {/* Recent sales */}
      <Card>
        <div className="flex flex-col gap-3 p-5 pb-0">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-ink-900">Sales</h2>
            <div className="flex flex-wrap items-center gap-2">
              <select
                value={salesCashierFilter}
                onChange={(e) => setSalesCashierFilter(e.target.value)}
                className="rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                <option value="">Everyone</option>
                {staffMembers.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName}
                  </option>
                ))}
              </select>
              <select
                value={salesPaymentFilter}
                onChange={(e) => setSalesPaymentFilter(e.target.value as PaymentMethod | "")}
                className="rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                <option value="">All payment methods</option>
                {PAYMENT_METHODS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
              <div className="relative min-w-[200px]">
                <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
                <input
                  value={salesSearch}
                  onChange={(e) => setSalesSearch(e.target.value)}
                  placeholder="Search sale #, customer, item"
                  className="w-full rounded-lg border border-border bg-surface py-1.5 pl-8 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
              </div>
            </div>
          </div>
          <DateRangeFilter value={salesDateRange} onChange={setSalesDateRange} />
        </div>
        {salesFetching ? (
          <TableSkeleton cols={7} />
        ) : sales.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title="No sales in this range"
            description="Try a wider date range, or complete a sale above."
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
                  <Th>Sold by</Th>
                  <Th>Payment</Th>
                  <Th className="text-right">Total</Th>
                  <Th></Th>
                </Tr>
              </THead>
              <TBody>
                {pagedSales.map((s) => (
                  <Tr key={s.id}>
                    <Td className="tabular font-medium">#{s.saleNumber}</Td>
                    <Td className="text-ink-500">{s.customerName ?? "Walk-in"}</Td>
                    <Td className="text-ink-500">
                      {s.items.map((i) => `${i.productName} ×${i.quantity}${i.gift ? " (gift)" : ""}`).join(", ")}
                    </Td>
                    <Td className="text-ink-500">{s.cashierName}</Td>
                    <Td className="text-ink-500">
                      <span className="block">{s.paymentMethod.replaceAll("_", " ")}</span>
                      {s.paymentStatus !== "PAID" && (
                        <Badge
                          tone={
                            s.paymentStatus === "FAILED"
                              ? "danger"
                              : s.paymentStatus === "PARTIALLY_PAID"
                              ? "info"
                              : s.paymentStatus === "REFUNDED"
                              ? "violet"
                              : "neutral"
                          }
                        >
                          {s.paymentStatus === "FAILED"
                            ? "Payment failed"
                            : s.paymentStatus === "PARTIALLY_PAID"
                            ? "Partially paid"
                            : s.paymentStatus === "REFUNDED"
                            ? "Refunded"
                            : "Unpaid"}
                        </Badge>
                      )}
                    </Td>
                    <Td className="tabular text-right font-medium">GH₵{s.totalAmount.toFixed(2)}</Td>
                    <Td className="text-right">
                      <div className="flex justify-end gap-3">
                        {s.paymentStatus !== "REFUNDED" && (
                          <button
                            onClick={() => setCollectingPaymentSale(s)}
                            className="text-sm font-medium text-accent-hover hover:underline"
                          >
                            {s.paymentStatus === "PAID" ? "Refund" : "Collect payment"}
                          </button>
                        )}
                        {printerEnabled && (
                          <Link
                            href={`/receipt/sale/${s.id}`}
                            target="_blank"
                            className="text-sm font-medium text-ink-700 hover:underline"
                          >
                            Print
                          </Link>
                        )}
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
            <div className="flex items-center justify-between gap-3 p-5 pt-4">
              <p className="text-xs text-ink-500">
                Showing {salesPage * SALES_PAGE_SIZE + 1}–{Math.min(visibleSales.length, (salesPage + 1) * SALES_PAGE_SIZE)} of {visibleSales.length}
              </p>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setSalesPage((p) => Math.max(0, p - 1))}
                  disabled={salesPage === 0}
                  className="flex items-center gap-1 rounded-lg border border-border px-2.5 py-1.5 text-sm font-medium text-ink-700 transition hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <ChevronLeft size={14} />
                  Previous
                </button>
                <span className="text-xs text-ink-500">
                  Page {salesPage + 1} of {salesPageCount}
                </span>
                <button
                  type="button"
                  onClick={() => setSalesPage((p) => Math.min(salesPageCount - 1, p + 1))}
                  disabled={salesPage >= salesPageCount - 1}
                  className="flex items-center gap-1 rounded-lg border border-border px-2.5 py-1.5 text-sm font-medium text-ink-700 transition hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Next
                  <ChevronRight size={14} />
                </button>
              </div>
            </div>
          </div>
        )}
      </Card>

      {showAddService && (
        <Modal title="Add service" onClose={() => setShowAddService(false)}>
          <QuickServiceCatalogForm serviceTypes={serviceTypes} onSubmit={handleAddService} />
        </Modal>
      )}

      {collectingPaymentSale && (
        <Modal title={`Collect payment — Sale #${collectingPaymentSale.saleNumber}`} onClose={() => setCollectingPaymentSale(null)}>
          <PaymentCollectionPanel<Sale>
            id={collectingPaymentSale.id}
            amount={collectingPaymentSale.totalAmount}
            balanceDue={collectingPaymentSale.balanceDue}
            paymentStatus={collectingPaymentSale.paymentStatus}
            paystackConfigured={paystackConfigured}
            onVerifyPayment={(reference) => api.verifySalePayment(session.token, reference)}
            onMarkPaid={(id) => api.markSalePaid(session.token, id)}
            onChargeMobileMoney={(id, phone, provider) => api.chargeSaleMobileMoney(session.token, id, phone, provider)}
            onSubmitOtp={(reference, otp) => api.submitSaleMobileMoneyOtp(session.token, reference, otp)}
            onRecordPayment={(id, payload) => api.recordSalePayment(session.token, id, payload)}
            onRefund={(id, note) => api.refundSale(session.token, id, note)}
            onChanged={handleSaleChanged}
          />
        </Modal>
      )}
    </div>
  );
}
