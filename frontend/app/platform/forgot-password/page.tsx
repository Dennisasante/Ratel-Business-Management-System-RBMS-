"use client";

import { useState } from "react";
import Link from "next/link";
import { ShieldCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import Button from "@/components/ui/Button";

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
    <main className="flex min-h-screen items-center justify-center bg-sidebar px-4 py-12">
      <div className="w-full max-w-sm rounded-xl border border-sidebar-border bg-[#20242E] p-8 shadow-panel">
        <div className="flex items-center gap-2 text-sidebar-text-active">
          <ShieldCheck size={20} />
          <span className="text-sm font-semibold tracking-wide">RATEL PLATFORM</span>
        </div>
        <h1 className="mt-4 text-xl font-semibold text-sidebar-text-active">Reset password</h1>

        {submitted ? (
          <p className="mt-4 text-sm text-sidebar-text">
            If that email has a Super Admin account, a reset link is on its way. It expires in 30 minutes.
          </p>
        ) : (
          <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
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
          <Link href="/platform/login" className="font-medium text-accent-hover hover:underline">
            Back to log in
          </Link>
        </p>
      </div>
    </main>
  );
}
