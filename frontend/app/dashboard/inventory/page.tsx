"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Package, Plus, AlertTriangle, Search, Archive as ArchiveIcon, Tags, Upload } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  Product,
  ProductCategory,
  ProductCategoryPayload,
  ProductPayload,
  StockAdjustmentPayload,
} from "@/lib/api";
import Modal from "@/components/Modal";
import ProductForm from "@/components/ProductForm";
import ProductCategoryManager from "@/components/ProductCategoryManager";
import StockAdjustForm from "@/components/StockAdjustForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Toast from "@/components/ui/Toast";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

type ModalState =
  | { type: "none" }
  | { type: "add" }
  | { type: "edit"; product: Product }
  | { type: "stock"; product: Product }
  | { type: "categories" };

export default function InventoryPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [fetching, setFetching] = useState(true);
  const [modal, setModal] = useState<ModalState>({ type: "none" });

  const [search, setSearch] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [tab, setTab] = useState<"active" | "archived">("active");

  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; undo: () => void } | null>(null);

  const loadProducts = useCallback(async () => {
    if (!session) return;
    const data = await api.listProducts(session.token, {
      search: search || undefined,
      categoryId: categoryId || undefined,
      includeArchived: tab === "archived",
    });
    setProducts(tab === "archived" ? data.filter((p) => !p.active) : data);
  }, [session, search, categoryId, tab]);

  const loadCategories = useCallback(async () => {
    if (!session) return;
    const data = await api.listProductCategories(session.token);
    setCategories(data);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadCategories();
  }, [session, loadCategories]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadProducts().finally(() => setFetching(false));
  }, [session, loadProducts]);

  async function handleCreate(payload: ProductPayload) {
    if (!session) return;
    await api.createProduct(session.token, payload);
    await loadProducts();
    setModal({ type: "none" });
  }

  async function handleUpdate(productId: string, payload: ProductPayload) {
    if (!session) return;
    await api.updateProduct(session.token, productId, payload);
    await loadProducts();
    setModal({ type: "none" });
  }

  async function handleUploadPhoto(productId: string, file: File) {
    if (!session) return;
    const updated = await api.uploadProductPhoto(session.token, productId, file);
    await loadProducts();
    setModal((m) => (m.type === "edit" && m.product.id === productId ? { type: "edit", product: updated } : m));
  }

  async function handleAdjustStock(productId: string, payload: StockAdjustmentPayload) {
    if (!session) return;
    await api.adjustStock(session.token, productId, payload);
    await loadProducts();
    setModal({ type: "none" });
  }

  async function handleArchive(product: Product) {
    if (!session) return;
    setPendingAction(product.id);
    try {
      await api.archiveProduct(session.token, product.id);
      await loadProducts();
      setToast({
        message: `"${product.name}" archived.`,
        undo: async () => {
          await api.restoreProduct(session.token, product.id);
          await loadProducts();
        },
      });
    } finally {
      setPendingAction(null);
    }
  }

  async function handleRestore(product: Product) {
    if (!session) return;
    setPendingAction(product.id);
    try {
      await api.restoreProduct(session.token, product.id);
      await loadProducts();
    } finally {
      setPendingAction(null);
    }
  }

  async function handleCreateCategory(payload: ProductCategoryPayload) {
    if (!session) return;
    await api.createProductCategory(session.token, payload);
    await loadCategories();
  }

  async function handleRenameCategory(id: string, payload: ProductCategoryPayload) {
    if (!session) return;
    await api.renameProductCategory(session.token, id, payload);
    await loadCategories();
  }

  async function handleDeleteCategory(id: string) {
    if (!session) return;
    await api.deleteProductCategory(session.token, id);
    await loadCategories();
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const lowStockCount = products.filter((p) => p.lowStock).length;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Inventory"
        subtitle={
          tab === "active" && lowStockCount > 0 ? (
            <span className="flex items-center gap-1.5 text-accent-hover">
              <AlertTriangle size={14} />
              {lowStockCount} product{lowStockCount > 1 ? "s" : ""} at or below their low stock threshold
            </span>
          ) : (
            `${products.length} product${products.length === 1 ? "" : "s"}`
          )
        }
        actions={
          <>
            <Link href="/dashboard/inventory/import">
              <Button variant="secondary">
                <Upload size={16} /> Import
              </Button>
            </Link>
            <Button variant="secondary" onClick={() => setModal({ type: "categories" })}>
              <Tags size={16} /> Categories
            </Button>
            <Button onClick={() => setModal({ type: "add" })}>
              <Plus size={16} /> Add product
            </Button>
          </>
        }
      />

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex rounded-lg border border-border bg-surface p-0.5 text-sm">
          <button
            onClick={() => setTab("active")}
            className={`rounded-md px-3 py-1.5 font-medium transition ${
              tab === "active" ? "bg-accent-soft text-accent-hover" : "text-ink-500 hover:text-ink-900"
            }`}
          >
            Active
          </button>
          <button
            onClick={() => setTab("archived")}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium transition ${
              tab === "archived" ? "bg-accent-soft text-accent-hover" : "text-ink-500 hover:text-ink-900"
            }`}
          >
            <ArchiveIcon size={13} /> Archived
          </button>
        </div>

        <div className="relative min-w-[200px] flex-1">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or SKU"
            className="w-full rounded-lg border border-border bg-surface py-2 pl-9 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>

        <select
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      <Card>
        {fetching ? (
          <TableSkeleton cols={5} />
        ) : products.length === 0 ? (
          tab === "archived" ? (
            <EmptyState icon={ArchiveIcon} title="No archived products" description="Products you archive will show up here for restoring later." />
          ) : (
            <EmptyState
              icon={Package}
              title="No products yet"
              description="Add your first product to start tracking stock."
              action={
                <Button onClick={() => setModal({ type: "add" })}>
                  <Plus size={16} /> Add product
                </Button>
              }
            />
          )
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Product</Th>
                <Th>Category</Th>
                <Th>Stock</Th>
                <Th>Cost / Sell</Th>
                <Th>Margin</Th>
                <Th></Th>
              </Tr>
            </THead>
            <TBody>
              {products.map((p) => (
                <Tr key={p.id}>
                  <Td>
                    <p className="font-medium text-ink-900">{p.name}</p>
                    {p.sku && <p className="text-xs text-ink-500">SKU: {p.sku}</p>}
                  </Td>
                  <Td className="text-ink-500">{categories.find((c) => c.id === p.categoryId)?.name ?? "—"}</Td>
                  <Td>
                    <div className="flex items-center gap-2">
                      <span className="tabular font-medium">{p.quantity}</span>
                      {p.lowStock && p.active && <Badge tone="danger">Low stock</Badge>}
                    </div>
                  </Td>
                  <Td className="tabular text-ink-500">
                    GH₵{p.costPrice.toFixed(2)} / GH₵{p.sellingPrice.toFixed(2)}
                  </Td>
                  <Td className="tabular text-ink-500">
                    {p.profitMarginPercent === null ? "—" : `${p.profitMarginPercent.toFixed(1)}%`}
                  </Td>
                  <Td>
                    <div className="flex justify-end gap-4 whitespace-nowrap text-right">
                      {tab === "archived" ? (
                        <button
                          onClick={() => handleRestore(p)}
                          disabled={pendingAction === p.id}
                          className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {pendingAction === p.id ? "Restoring..." : "Restore"}
                        </button>
                      ) : (
                        <>
                          <button
                            onClick={() => setModal({ type: "stock", product: p })}
                            className="text-sm font-medium text-accent-hover hover:underline"
                          >
                            Adjust stock
                          </button>
                          <button
                            onClick={() => setModal({ type: "edit", product: p })}
                            className="text-sm font-medium text-ink-700 hover:underline"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => handleArchive(p)}
                            disabled={pendingAction === p.id}
                            className="text-sm font-medium text-danger hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                          >
                            {pendingAction === p.id ? "Removing..." : "Remove"}
                          </button>
                        </>
                      )}
                    </div>
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {modal.type === "add" && (
        <Modal title="Add product" onClose={() => setModal({ type: "none" })}>
          <ProductForm categories={categories} submitLabel="Add product" onSubmit={handleCreate} />
        </Modal>
      )}

      {modal.type === "edit" && (
        <Modal title="Edit product" onClose={() => setModal({ type: "none" })}>
          <ProductForm
            initial={modal.product}
            categories={categories}
            submitLabel="Save changes"
            onSubmit={(payload) => handleUpdate(modal.product.id, payload)}
            onUploadPhoto={(file) => handleUploadPhoto(modal.product.id, file)}
          />
        </Modal>
      )}

      {modal.type === "stock" && (
        <Modal title={`Adjust stock — ${modal.product.name}`} onClose={() => setModal({ type: "none" })}>
          <StockAdjustForm
            product={modal.product}
            onSubmit={(payload) => handleAdjustStock(modal.product.id, payload)}
          />
        </Modal>
      )}

      {modal.type === "categories" && (
        <Modal title="Product categories" onClose={() => setModal({ type: "none" })}>
          <ProductCategoryManager
            categories={categories}
            onCreate={handleCreateCategory}
            onRename={handleRenameCategory}
            onDelete={handleDeleteCategory}
          />
        </Modal>
      )}

      {toast && (
        <Toast
          message={toast.message}
          actionLabel="Undo"
          onAction={toast.undo}
          onDismiss={() => setToast(null)}
        />
      )}
    </div>
  );
}
