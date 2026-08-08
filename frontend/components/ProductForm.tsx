"use client";

import { useState } from "react";
import { Globe, Upload } from "lucide-react";
import { ApiError, Product, ProductCategory, ProductPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface ProductFormProps {
  initial?: Product;
  categories: ProductCategory[];
  submitLabel: string;
  onSubmit: (payload: ProductPayload) => Promise<void>;
  onUploadPhoto?: (file: File) => Promise<void>;
}

export default function ProductForm({ initial, categories, submitLabel, onSubmit, onUploadPhoto }: ProductFormProps) {
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
  const [publishToWebsite, setPublishToWebsite] = useState(initial?.publishToWebsite ?? false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);

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
        publishToWebsite,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handlePhotoSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !onUploadPhoto) return;
    setPhotoError(null);
    setUploadingPhoto(true);
    try {
      await onUploadPhoto(file);
    } catch (err) {
      setPhotoError(err instanceof ApiError ? err.message : "Couldn't upload that photo.");
    } finally {
      setUploadingPhoto(false);
      e.target.value = "";
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

      {initial && onUploadPhoto && (
        <div className="flex items-center gap-3 rounded-lg border border-border p-3">
          {initial.imageUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={initial.imageUrl} alt="" className="h-14 w-14 rounded-lg object-cover" />
          ) : (
            <div className="flex h-14 w-14 items-center justify-center rounded-lg bg-canvas text-ink-300">
              <Upload size={18} />
            </div>
          )}
          <div>
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={handlePhotoSelected}
              className="hidden"
              id="product-photo-upload"
            />
            <label
              htmlFor="product-photo-upload"
              className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-canvas"
            >
              <Upload size={13} />
              {uploadingPhoto ? "Uploading..." : initial.imageUrl ? "Change photo" : "Upload photo"}
            </label>
            <p className="mt-1 text-xs text-ink-500">PNG, JPEG, or WEBP</p>
            {photoError && <p className="mt-1 text-xs text-danger">{photoError}</p>}
          </div>
        </div>
      )}

      <div className="flex items-center gap-2 rounded-lg bg-canvas px-3 py-2">
        <input
          id="publishToWebsite"
          type="checkbox"
          checked={publishToWebsite}
          onChange={(e) => setPublishToWebsite(e.target.checked)}
          className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
        />
        <label htmlFor="publishToWebsite" className="flex items-center gap-1.5 text-sm text-ink-700">
          <Globe size={14} className="text-ink-500" />
          Publish to your website
        </label>
      </div>
      {initial?.publishToWebsite && (
        <p className="-mt-2 text-xs text-ink-500">
          {initial.syncedToWebsite ? "Synced to WooCommerce." : "Will sync to WooCommerce once connected in Integrations."}
        </p>
      )}

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : submitLabel}
      </Button>
    </form>
  );
}
