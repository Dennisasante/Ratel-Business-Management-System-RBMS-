"use client";

import { useState } from "react";
import { ApiError, BusinessSummary, BusinessUpdatePayload, Industry } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

const INDUSTRIES: { value: Industry; label: string }[] = [
  { value: "RETAIL", label: "Retail / Handmade Products" },
  { value: "SALON", label: "Salon / Hair & Beauty" },
  { value: "RESTAURANT", label: "Restaurant / Food" },
  { value: "SCHOOL", label: "School" },
  { value: "OTHER", label: "Other" },
];

export default function BusinessProfileForm({
  initial,
  onSubmit,
}: {
  initial: BusinessSummary;
  onSubmit: (payload: BusinessUpdatePayload) => Promise<void>;
}) {
  const [form, setForm] = useState({
    name: initial.name,
    industry: initial.industry as Industry,
    location: initial.location ?? "",
    contactEmail: initial.contactEmail ?? "",
    contactPhone: initial.contactPhone ?? "",
    taxId: initial.taxId ?? "",
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
        industry: form.industry,
        location: form.location || undefined,
        contactEmail: form.contactEmail || undefined,
        contactPhone: form.contactPhone || undefined,
        taxId: form.taxId || undefined,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField label="Business name" name="name" required value={form.name} onChange={(v) => set("name", v)} />

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">
          Industry <span className="text-danger">*</span>
        </label>
        <select
          value={form.industry}
          onChange={(e) => set("industry", e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          {INDUSTRIES.map((i) => (
            <option key={i.value} value={i.value}>
              {i.label}
            </option>
          ))}
        </select>
      </div>

      <FormField label="Location" name="location" value={form.location} onChange={(v) => set("location", v)} />
      <FormField label="Contact email" name="contactEmail" type="email" value={form.contactEmail} onChange={(v) => set("contactEmail", v)} />
      <FormField label="Contact phone" name="contactPhone" value={form.contactPhone} onChange={(v) => set("contactPhone", v)} />
      <FormField
        label="Tax ID (optional)"
        name="taxId"
        value={form.taxId}
        onChange={(v) => set("taxId", v)}
        placeholder="TIN or VAT registration number"
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : "Save changes"}
      </Button>
    </form>
  );
}
