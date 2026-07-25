"use client";

import { useState } from "react";
import { ApiError, SupplierPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

export default function SupplierForm({ onSubmit }: { onSubmit: (payload: SupplierPayload) => Promise<void> }) {
  const [form, setForm] = useState({ name: "", phone: "", email: "", notes: "" });
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
        phone: form.phone || undefined,
        email: form.email || undefined,
        notes: form.notes || undefined,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField label="Supplier name" name="name" required value={form.name} onChange={(v) => set("name", v)} />
      <FormField label="Phone" name="phone" value={form.phone} onChange={(v) => set("phone", v)} />
      <FormField label="Email" name="email" type="email" value={form.email} onChange={(v) => set("email", v)} />
      <FormField label="Notes" name="notes" value={form.notes} onChange={(v) => set("notes", v)} />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : "Add supplier"}
      </Button>
    </form>
  );
}
