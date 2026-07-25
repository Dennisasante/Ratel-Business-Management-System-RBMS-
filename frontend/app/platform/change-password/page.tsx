"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { usePlatformAuth } from "@/lib/platformAuth";
import { ApiError } from "@/lib/api";
import Button from "@/components/ui/Button";
import PlatformShell from "@/components/platform/PlatformShell";
import PasswordInput from "@/components/PasswordInput";

const LIGHT_INPUT_CLASS = "rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20";

export default function PlatformChangePasswordPage() {
  const { session, changePassword } = usePlatformAuth();
  const router = useRouter();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const forced = session?.mustChangePassword ?? false;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirm) {
      setError("New passwords don't match.");
      return;
    }

    setSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
      setDone(true);
      setTimeout(() => router.push("/platform"), 1200);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PlatformShell>
      <div className="mx-auto flex max-w-md flex-col gap-6">
        <div>
          <h1 className="text-xl font-semibold text-ink-900">{forced ? "Set a new password" : "Change password"}</h1>
          <p className="mt-1 text-sm text-ink-500">
            {forced
              ? "You're using a temporary password. Set your own before continuing."
              : "Update the password you use to log in."}
          </p>
        </div>

        <div className="rounded-xl border border-border bg-surface p-6 shadow-card">
          {done ? (
            <p className="text-sm text-ink-700">Password updated.</p>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <PasswordInput
                label={forced ? "Temporary password" : "Current password"}
                required
                value={currentPassword}
                onChange={setCurrentPassword}
                inputClassName={LIGHT_INPUT_CLASS}
              />
              <PasswordInput
                label="New password"
                required
                value={newPassword}
                onChange={setNewPassword}
                placeholder="At least 8 characters"
                inputClassName={LIGHT_INPUT_CLASS}
              />
              <PasswordInput
                label="Confirm new password"
                required
                value={confirm}
                onChange={setConfirm}
                inputClassName={LIGHT_INPUT_CLASS}
              />

              {error && <p className="text-sm text-danger">{error}</p>}

              <Button type="submit" disabled={submitting} className="mt-1 w-full">
                {submitting ? "Updating..." : "Update password"}
              </Button>
            </form>
          )}
        </div>
      </div>
    </PlatformShell>
  );
}
