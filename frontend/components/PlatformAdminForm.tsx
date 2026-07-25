"use client";

import { useState } from "react";
import { ApiError, CreatePlatformAdminPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

const PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

function generatePassword(): string {
  let out = "";
  for (let i = 0; i < 10; i++) {
    out += PASSWORD_CHARS[Math.floor(Math.random() * PASSWORD_CHARS.length)];
  }
  return out;
}

export default function PlatformAdminForm({ onSubmit }: { onSubmit: (payload: CreatePlatformAdminPayload) => Promise<void> }) {
  const [form, setForm] = useState({
    fullName: "",
    email: "",
    temporaryPassword: generatePassword(),
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
      await onSubmit(form);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField label="Full name" name="fullName" required value={form.fullName} onChange={(v) => set("fullName", v)} />
      <FormField label="Email" name="email" type="email" required value={form.email} onChange={(v) => set("email", v)} />

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between">
          <label className="text-sm font-medium text-ink-700">Temporary password</label>
          <button
            type="button"
            onClick={() => set("temporaryPassword", generatePassword())}
            className="text-xs font-medium text-accent-hover hover:underline"
          >
            Generate new
          </button>
        </div>
        <input
          value={form.temporaryPassword}
          onChange={(e) => set("temporaryPassword", e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 font-mono text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
        <p className="text-xs text-ink-500">
          Share this with them directly. They&apos;ll be asked to set their own password on first login.
        </p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Adding..." : "Add Super Admin"}
      </Button>
    </form>
  );
}
