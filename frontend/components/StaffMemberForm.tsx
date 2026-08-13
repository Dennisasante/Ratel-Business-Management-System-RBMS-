"use client";

import { useState } from "react";
import { ApiError, StaffMember, StaffMemberPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface StaffMemberFormProps {
  staffMember?: StaffMember;
  onSubmit: (payload: StaffMemberPayload) => Promise<void>;
}

// Deliberately minimal — a name to assign work to, no login/email/role/
// password. See StaffMemberController on the backend for why.
export default function StaffMemberForm({ staffMember, onSubmit }: StaffMemberFormProps) {
  const [form, setForm] = useState({
    fullName: staffMember?.fullName ?? "",
    phone: staffMember?.phone ?? "",
    notes: staffMember?.notes ?? "",
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
        fullName: form.fullName,
        phone: form.phone || undefined,
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
      <FormField label="Phone (optional)" name="phone" value={form.phone} onChange={(v) => set("phone", v)} />
      <FormField label="Notes (optional)" name="notes" value={form.notes} onChange={(v) => set("notes", v)} />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : staffMember ? "Save changes" : "Add staff member"}
      </Button>
    </form>
  );
}
