"use client";

import { useState } from "react";
import {
  ApiError,
  Customer,
  ServiceCatalogItem,
  ServiceOrder,
  ServiceOrderPayload,
  ServiceType,
  UserSummary,
} from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface ServiceOrderFormProps {
  initial?: ServiceOrder;
  serviceTypes: ServiceType[];
  customers: Customer[];
  catalog: ServiceCatalogItem[];
  staff: UserSummary[];
  submitLabel: string;
  onSubmit: (payload: ServiceOrderPayload) => Promise<void>;
}

export default function ServiceOrderForm({
  initial,
  serviceTypes,
  customers,
  catalog,
  staff,
  submitLabel,
  onSubmit,
}: ServiceOrderFormProps) {
  const [form, setForm] = useState({
    serviceTypeId: initial?.serviceTypeId ?? serviceTypes[0]?.id ?? "",
    customerId: initial?.customerId ?? "",
    serviceCatalogId: initial?.serviceCatalogId ?? "",
    notes: initial?.notes ?? "",
    // basePrice reconstructs the pre-discount amount so the discount field starts
    // consistent; it isn't sent to the server, only price + discount are.
    basePrice: initial ? String(initial.price + initial.discountAmount) : "0",
    price: initial ? String(initial.price) : "0",
    discount: initial ? String(initial.discountAmount) : "0",
    assignedStaffId: initial?.assignedStaffId ?? "",
    scheduledAt: initial?.scheduledAt ? initial.scheduledAt.slice(0, 16) : "",
  });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function pickCatalogItem(id: string) {
    setForm((f) => {
      const item = catalog.find((c) => c.id === id);
      const base = item ? item.price : Number(f.basePrice) || 0;
      return { ...f, serviceCatalogId: id, basePrice: String(base), price: String(base), discount: "0" };
    });
  }

  function setDiscount(value: string) {
    setForm((f) => {
      const base = Number(f.basePrice) || 0;
      const discount = Math.min(Number(value) || 0, base);
      return { ...f, discount: value, price: String(Math.max(base - discount, 0)) };
    });
  }

  function setPriceManually(value: string) {
    // Typing a price directly overrides everything — the discount conversation restarts.
    setForm((f) => ({ ...f, price: value, basePrice: value, discount: "0" }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        serviceTypeId: form.serviceTypeId,
        customerId: form.customerId || undefined,
        serviceCatalogId: form.serviceCatalogId || undefined,
        notes: form.notes || undefined,
        price: Number(form.price) || 0,
        discountAmount: Number(form.discount) || 0,
        assignedStaffId: form.assignedStaffId || undefined,
        scheduledAt: form.scheduledAt ? new Date(form.scheduledAt).toISOString() : undefined,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Category</label>
        <select
          value={form.serviceTypeId}
          onChange={(e) => set("serviceTypeId", e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          {serviceTypes.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Customer (optional)</label>
        <select
          value={form.customerId}
          onChange={(e) => set("customerId", e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          <option value="">No customer on file</option>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.fullName}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Catalog price (optional)</label>
        <select
          value={form.serviceCatalogId}
          onChange={(e) => pickCatalogItem(e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          <option value="">Custom price</option>
          {catalog.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} — GH₵{c.price.toFixed(2)}
            </option>
          ))}
        </select>
        <p className="text-xs text-ink-500">Picking one fills the price below — still editable per order.</p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Price" name="price" type="number" value={form.price} onChange={setPriceManually} />
        <FormField
          label="Discount (optional)"
          name="discount"
          type="number"
          value={form.discount}
          onChange={setDiscount}
        />
      </div>
      <p className="-mt-2 text-xs text-ink-500">
        At your discretion — knocking off a discount here reduces the price above and shows on the receipt.
      </p>

      <FormField
        label="Schedule for (optional)"
        name="scheduledAt"
        type="datetime-local"
        value={form.scheduledAt}
        onChange={(v) => set("scheduledAt", v)}
      />

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Assigned staff (optional, tracking only)</label>
        <select
          value={form.assignedStaffId}
          onChange={(e) => set("assignedStaffId", e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          <option value="">Unassigned</option>
          {staff.map((s) => (
            <option key={s.id} value={s.id}>
              {s.fullName}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Notes</label>
        <textarea
          value={form.notes}
          onChange={(e) => set("notes", e.target.value)}
          rows={3}
          placeholder="What's wrong / what's needed"
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : submitLabel}
      </Button>
    </form>
  );
}
