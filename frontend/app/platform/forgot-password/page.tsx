"use client";

import { useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import Button from "@/components/ui/Button";
import PlatformAuthShell from "@/components/auth/PlatformAuthShell";

export default function PlatformForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.platformForgotPassword(email);
      setSubmitted(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PlatformAuthShell title="Reset password">
      {submitted ? (
        <p className="text-sm text-sidebar-text">
          If that email has a Super Admin account, a reset link is on its way. It expires in 30 minutes.
        </p>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-sidebar-text">Email</label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="rounded-lg border border-sidebar-border bg-sidebar px-3 py-2 text-sm text-sidebar-text-active focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
            />
          </div>

          {error && <p className="text-sm text-red-400">{error}</p>}

          <Button type="submit" disabled={submitting} className="mt-1 w-full">
            {submitting ? "Sending..." : "Send reset link"}
          </Button>
        </form>
      )}

      <p className="mt-5 text-center text-sm text-sidebar-text">
        <Link href="/platform/login" className="font-medium hover:underline" style={{ color: "#7fa5e8" }}>
          Back to log in
        </Link>
      </p>
    </PlatformAuthShell>
  );
}
