"use client";

import { Suspense, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import Button from "@/components/ui/Button";
import PasswordInput from "@/components/PasswordInput";
import PlatformAuthShell from "@/components/auth/PlatformAuthShell";

const DARK_INPUT_CLASS =
  "rounded-lg border border-sidebar-border bg-sidebar px-3 py-2 text-sm text-sidebar-text-active focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30";
const DARK_LABEL_CLASS = "text-sm font-medium text-sidebar-text";
const DARK_ICON_CLASS = "text-sidebar-text hover:text-sidebar-text-active";

function ResetForm() {
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

    setSubmitting(true);
    try {
      await api.platformResetPassword(token, password);
      setDone(true);
      setTimeout(() => router.push("/platform/login"), 2000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return <p className="text-sm text-sidebar-text">Password updated. Redirecting you to log in...</p>;
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <PasswordInput
        label="New password"
        required
        value={password}
        onChange={setPassword}
        labelClassName={DARK_LABEL_CLASS}
        inputClassName={DARK_INPUT_CLASS}
        iconClassName={DARK_ICON_CLASS}
      />
      <PasswordInput
        label="Confirm new password"
        required
        value={confirm}
        onChange={setConfirm}
        labelClassName={DARK_LABEL_CLASS}
        inputClassName={DARK_INPUT_CLASS}
        iconClassName={DARK_ICON_CLASS}
      />

      {error && <p className="text-sm text-red-400">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Updating..." : "Update password"}
      </Button>
    </form>
  );
}

export default function PlatformResetPasswordPage() {
  return (
    <PlatformAuthShell title="Set a new password">
      <Suspense fallback={<p className="text-sm text-sidebar-text">Loading...</p>}>
        <ResetForm />
      </Suspense>

      <p className="mt-5 text-center text-sm text-sidebar-text">
        <Link href="/platform/login" className="font-medium hover:underline" style={{ color: "#7fa5e8" }}>
          Back to log in
        </Link>
      </p>
    </PlatformAuthShell>
  );
}
