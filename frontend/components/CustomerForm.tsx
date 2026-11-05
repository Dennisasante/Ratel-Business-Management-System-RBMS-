"use client";

import { useState } from "react";
import { ApiError, CustomerPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

const CUSTOMER_SOURCES = ["Walk-in", "Instagram", "WhatsApp", "Facebook", "Referral", "Website", "Other"];

interface CustomerFormProps {
  onSubmit: (payload: CustomerPayload) => Promise<void>;
  // Pre-fills the form when editing an existing customer.
  initial?: {
    fullName: string;
    phone?: string | null;
    email?: string | null;
    notes?: string | null;
    source?: string | null;
    smsOptOut?: boolean;
  };
  submitLabel?: string;
}

export default function CustomerForm({ onSubmit, initial, submitLabel = "Add customer" }: CustomerFormProps) {
  const [form, setForm] = useState({
    fullName: initial?.fullName ?? "",
    phone: initial?.phone ?? "",
    email: initial?.email ?? "",
    notes: initial?.notes ?? "",
    source: initial?.source ?? "",
  });
  const [smsOptOut, setSmsOptOut] = useState(initial?.smsOptOut ?? false);
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
        fullName: form.fullName,
        phone: form.phone || undefined,
        email: form.email || undefined,
        notes: form.notes || undefined,
        source: form.source || undefined,
        smsOptOut,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField label="Full name" name="fullName" required value={form.fullName} onChange={(v) => set("fullName", v)} />
      <FormField label="Phone" name="phone" value={form.phone} onChange={(v) => set("phone", v)} />
      <FormField label="Email" name="email" type="email" value={form.email} onChange={(v) => set("email", v)} />
      <FormField label="Notes" name="notes" value={form.notes} onChange={(v) => set("notes", v)} />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="source" className="text-sm font-medium text-ink-700">
          How did they find us?
        </label>
        <select
          id="source"
          value={form.source}
          onChange={(e) => set("source", e.target.value)}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          <option value="">Not specified</option>
          {CUSTOMER_SOURCES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      <label className="flex items-start gap-2 rounded-lg bg-canvas px-3 py-2 text-sm text-ink-700">
        <input
          type="checkbox"
          checked={smsOptOut}
          onChange={(e) => setSmsOptOut(e.target.checked)}
          className="mt-0.5 rounded border-border text-accent focus:ring-accent/20"
        />
        <span>
          Doesn&apos;t want promotional texts
          <span className="block text-xs text-ink-500">Campaigns skip them. Receipts still go out.</span>
        </span>
      </label>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : submitLabel}
      </Button>
    </form>
  );
}
