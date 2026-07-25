"use client";

import { Suspense, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";

function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const token = searchParams.get("token") ?? "";

  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (password !== confirm) {
      setError("Passwords don't match.");
      return;
    }
    if (!token) {
      setError("This reset link is missing its token. Request a new one.");
      return;
    }

    setSubmitting(true);
    try {
      await api.resetPassword(token, password);
      setDone(true);
      setTimeout(() => router.push("/login"), 2000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return <p className="mt-4 text-sm text-ink-700">Password updated. Redirecting you to log in...</p>;
  }

  return (
    <>
      <p className="mt-1 text-sm text-ink-500">Choose a new password for your account.</p>
      <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
        <FormField
          label="New password"
          name="password"
          type="password"
          required
          value={password}
          onChange={setPassword}
          placeholder="At least 8 characters"
        />
        <FormField
          label="Confirm new password"
          name="confirm"
          type="password"
          required
          value={confirm}
          onChange={setConfirm}
        />

        {error && <p className="text-sm text-danger">{error}</p>}

        <Button type="submit" disabled={submitting} className="mt-1 w-full">
          {submitting ? "Updating..." : "Update password"}
        </Button>
      </form>
    </>
  );
}

export default function ResetPasswordPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-12">
      <Card className="w-full max-w-sm p-8">
        <Link href="/" className="text-sm font-semibold text-ink-900">
          Ratel
        </Link>
        <h1 className="mt-4 text-xl font-semibold text-ink-900">Set a new password</h1>

        <Suspense fallback={<p className="mt-4 text-sm text-ink-500">Loading...</p>}>
          <ResetPasswordForm />
        </Suspense>

        <p className="mt-5 text-center text-sm text-ink-500">
          <Link href="/login" className="font-medium text-accent-hover hover:underline">
            Back to log in
          </Link>
        </p>
      </Card>
    </main>
  );
}
