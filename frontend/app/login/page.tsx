"use client";

import { useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import FormField from "@/components/FormField";
import GoogleButton from "@/components/GoogleButton";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";

export default function LoginPage() {
  const { login, loginWithGoogle } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login({ email, password });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGoogleCredential(idToken: string) {
    setError(null);
    setSubmitting(true);
    try {
      await loginWithGoogle(idToken);
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
        <h1 className="mt-4 text-xl font-semibold text-ink-900">Log in</h1>
        <p className="mt-1 text-sm text-ink-500">Welcome back.</p>

        <div className="mt-6">
          <GoogleButton onCredential={handleGoogleCredential} />
        </div>

        <div className="my-5 flex items-center gap-3 text-xs text-ink-500">
          <span className="h-px flex-1 bg-border" />
          or log in with email
          <span className="h-px flex-1 bg-border" />
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <FormField label="Email" name="email" type="email" required value={email} onChange={setEmail} />
          <div>
            <FormField label="Password" name="password" type="password" required value={password} onChange={setPassword} />
            <Link href="/forgot-password" className="mt-1.5 inline-block text-xs font-medium text-accent-hover hover:underline">
              Forgot password?
            </Link>
          </div>

          {error && <p className="text-sm text-danger">{error}</p>}

          <Button type="submit" disabled={submitting} className="mt-1 w-full">
            {submitting ? "Logging in..." : "Log in"}
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-ink-500">
          Don&apos;t have a business account yet?{" "}
          <Link href="/register" className="font-medium text-accent-hover hover:underline">
            Register
          </Link>
        </p>
      </Card>
    </main>
  );
}
