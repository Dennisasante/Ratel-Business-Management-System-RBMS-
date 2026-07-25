"use client";

import { useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.forgotPassword(email);
      setSubmitted(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-12">
      <Card className="w-full max-w-sm p-8">
        <Link href="/" className="text-sm font-semibold text-ink-900">
          Ratel
        </Link>
        <h1 className="mt-4 text-xl font-semibold text-ink-900">Reset your password</h1>

        {submitted ? (
          <p className="mt-4 text-sm text-ink-700">
            If an account exists for <span className="font-medium text-ink-900">{email}</span>, we&apos;ve sent a
            link to reset your password. It expires in 30 minutes.
          </p>
        ) : (
          <>
            <p className="mt-1 text-sm text-ink-500">
              Enter the email on your account and we&apos;ll send you a reset link.
            </p>
            <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
              <FormField label="Email" name="email" type="email" required value={email} onChange={setEmail} />

              {error && <p className="text-sm text-danger">{error}</p>}

              <Button type="submit" disabled={submitting} className="mt-1 w-full">
                {submitting ? "Sending..." : "Send reset link"}
              </Button>
            </form>
          </>
        )}

        <p className="mt-5 text-center text-sm text-ink-500">
          <Link href="/login" className="font-medium text-accent-hover hover:underline">
            Back to log in
          </Link>
        </p>
      </Card>
    </main>
  );
}
