"use client";

import { useState } from "react";
import { ApiError, CustomerPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface CustomerFormProps {
  onSubmit: (payload: CustomerPayload) => Promise<void>;
}

export default function CustomerForm({ onSubmit }: CustomerFormProps) {
  const [form, setForm] = useState({ fullName: "", phone: "", email: "", notes: "" });
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

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : "Add customer"}
      </Button>
    </form>
  );
}
