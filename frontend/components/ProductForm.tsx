"use client";

import { useState } from "react";
import { ApiError, Product, ProductCategory, ProductPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface ProductFormProps {
  initial?: Product;
  categories: ProductCategory[];
  submitLabel: string;
  onSubmit: (payload: ProductPayload) => Promise<void>;
}

export default function ProductForm({ initial, categories, submitLabel, onSubmit }: ProductFormProps) {
  const [form, setForm] = useState({
    name: initial?.name ?? "",
    categoryId: initial?.categoryId ?? "",
    sku: initial?.sku ?? "",
    costPrice: initial ? String(initial.costPrice) : "0",
    sellingPrice: initial ? String(initial.sellingPrice) : "0",
    quantity: initial ? String(initial.quantity) : "0",
    lowStockThreshold: initial ? String(initial.lowStockThreshold) : "5",
    supplierName: initial?.supplierName ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        name: form.name,
        categoryId: form.categoryId || undefined,
        sku: form.sku || undefined,
        costPrice: Number(form.costPrice) || 0,
        sellingPrice: Number(form.sellingPrice) || 0,
        quantity: initial ? undefined : Number(form.quantity) || 0,
        lowStockThreshold: Number(form.lowStockThreshold) || 5,
        supplierName: form.supplierName || undefined,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField label="Product name" name="name" required value={form.name} onChange={(v) => set("name", v)} />

      <div className="grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Category</label>
          <select
            value={form.categoryId}
            onChange={(e) => set("categoryId", e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          >
            <option value="">Uncategorized</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <FormField label="SKU" name="sku" value={form.sku} onChange={(v) => set("sku", v)} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <FormField
          label="Cost price"
          name="costPrice"
          type="number"
          value={form.costPrice}
          onChange={(v) => set("costPrice", v)}
        />
        <FormField
          label="Selling price"
          name="sellingPrice"
          type="number"
          value={form.sellingPrice}
          onChange={(v) => set("sellingPrice", v)}
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        {!initial && (
          <FormField
            label="Opening quantity"
            name="quantity"
            type="number"
            value={form.quantity}
            onChange={(v) => set("quantity", v)}
          />
        )}
        <FormField
          label="Low stock threshold"
          name="lowStockThreshold"
          type="number"
          value={form.lowStockThreshold}
          onChange={(v) => set("lowStockThreshold", v)}
        />
      </div>

      <FormField
        label="Supplier"
        name="supplierName"
        value={form.supplierName}
        onChange={(v) => set("supplierName", v)}
      />

      {initial && (
        <p className="text-xs text-ink-500">
          To change quantity, use &ldquo;Adjust stock&rdquo; instead — it keeps a history of every change.
        </p>
      )}

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : submitLabel}
      </Button>
    </form>
  );
}
