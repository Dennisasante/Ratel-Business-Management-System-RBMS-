"use client";

import { useState } from "react";
import { ApiError, PlanFeature, SubscriptionPlan, SubscriptionPlanPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

const FEATURE_OPTIONS: { code: PlanFeature; label: string }[] = [
  { code: "BOOKING_WIDGET", label: "Booking widget (website embed)" },
  { code: "WOOCOMMERCE_SYNC", label: "WooCommerce sync" },
  { code: "CUSTOM_WIG_REQUESTS", label: "Custom wig requests" },
];

export default function SubscriptionPlanForm({
  initial,
  onSubmit,
}: {
  initial?: SubscriptionPlan;
  onSubmit: (payload: SubscriptionPlanPayload) => Promise<void>;
}) {
  const [form, setForm] = useState({
    name: initial?.name ?? "",
    price: initial ? String(initial.price) : "",
    currency: initial?.currency ?? "GHS",
    billingPeriodDays: initial ? String(initial.billingPeriodDays) : "30",
    sortOrder: initial ? String(initial.sortOrder) : "0",
  });
  const [features, setFeatures] = useState<PlanFeature[]>(initial?.features ?? []);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function toggleFeature(code: PlanFeature) {
    setFeatures((f) => (f.includes(code) ? f.filter((c) => c !== code) : [...f, code]));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        name: form.name,
        price: Number(form.price) || 0,
        currency: form.currency,
        billingPeriodDays: Number(form.billingPeriodDays) || 30,
        sortOrder: Number(form.sortOrder) || 0,
        features,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField label="Plan name" name="name" required value={form.name} onChange={(v) => set("name", v)} placeholder="e.g. Basic" />

      <div className="grid grid-cols-2 gap-4">
        <FormField label="Price" name="price" type="number" required value={form.price} onChange={(v) => set("price", v)} />
        <FormField label="Currency" name="currency" required value={form.currency} onChange={(v) => set("currency", v)} />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <FormField
          label="Billing period (days)"
          name="billingPeriodDays"
          type="number"
          required
          value={form.billingPeriodDays}
          onChange={(v) => set("billingPeriodDays", v)}
        />
        <FormField
          label="Sort order"
          name="sortOrder"
          type="number"
          value={form.sortOrder}
          onChange={(v) => set("sortOrder", v)}
        />
      </div>

      <div className="flex flex-col gap-2">
        <label className="text-sm font-medium text-ink-700">Features included</label>
        {FEATURE_OPTIONS.map((opt) => (
          <label key={opt.code} className="flex items-center gap-2 text-sm text-ink-700">
            <input
              type="checkbox"
              checked={features.includes(opt.code)}
              onChange={() => toggleFeature(opt.code)}
              className="h-4 w-4 rounded border-border text-accent focus:ring-accent/20"
            />
            {opt.label}
          </label>
        ))}
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : initial ? "Save changes" : "Add plan"}
      </Button>
    </form>
  );
}
