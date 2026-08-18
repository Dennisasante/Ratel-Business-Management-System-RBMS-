"use client";

import { useState } from "react";
import Link from "next/link";
import { usePlatformAuth } from "@/lib/platformAuth";
import { ApiError } from "@/lib/api";
import Button from "@/components/ui/Button";
import PasswordInput from "@/components/PasswordInput";
import PlatformAuthShell from "@/components/auth/PlatformAuthShell";

const DARK_INPUT_CLASS =
  "rounded-lg border border-sidebar-border bg-sidebar px-3 py-2 text-sm text-sidebar-text-active focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30";
const DARK_LABEL_CLASS = "text-sm font-medium text-sidebar-text";
const DARK_ICON_CLASS = "text-sidebar-text hover:text-sidebar-text-active";

export default function PlatformLoginPage() {
  const { login } = usePlatformAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PlatformAuthShell title="Super Admin" subtitle="Sign in to manage the Tallia platform.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label className={DARK_LABEL_CLASS}>Email</label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={DARK_INPUT_CLASS}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <PasswordInput
            label="Password"
            required
            value={password}
            onChange={setPassword}
            labelClassName={DARK_LABEL_CLASS}
            inputClassName={DARK_INPUT_CLASS}
            iconClassName={DARK_ICON_CLASS}
          />
          <Link
            href="/platform/forgot-password"
            className="mt-1.5 inline-block text-xs font-medium hover:underline"
            style={{ color: "#7fa5e8" }}
          >
            Forgot password?
          </Link>
        </div>

        {error && <p className="text-sm text-red-400">{error}</p>}

        <Button type="submit" disabled={submitting} className="mt-1 w-full">
          {submitting ? "Logging in..." : "Log in"}
        </Button>
      </form>
    </PlatformAuthShell>
  );
}
