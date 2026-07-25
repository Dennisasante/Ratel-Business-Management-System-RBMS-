"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { KeyRound } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import PageHeader from "@/components/ui/PageHeader";

export default function ChangePasswordPage() {
  const { session, loading, changePassword, logout } = useAuth();
  const router = useRouter();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

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
      setTimeout(() => router.push("/dashboard"), 1200);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const forced = session.mustChangePassword;

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6">
      <PageHeader
        title={forced ? "Set a new password" : "Change password"}
        subtitle={
          forced
            ? "You're using a temporary password. Set your own before continuing."
            : "Update the password you use to log in."
        }
      />

      <Card className="p-6">
        {done ? (
          <p className="text-sm text-ink-700">Password updated.</p>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <FormField
              label={forced ? "Temporary password" : "Current password"}
              name="currentPassword"
              type="password"
              required
              value={currentPassword}
              onChange={setCurrentPassword}
            />
            <FormField
              label="New password"
              name="newPassword"
              type="password"
              required
              value={newPassword}
              onChange={setNewPassword}
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

            {forced && (
              <button
                type="button"
                onClick={logout}
                className="text-center text-xs text-ink-500 hover:underline"
              >
                Not you? Log out
              </button>
            )}
          </form>
        )}
      </Card>

      {!forced && (
        <p className="flex items-center gap-1.5 text-xs text-ink-500">
          <KeyRound size={13} />
          You&apos;ll need your current password to confirm this change.
        </p>
      )}
    </div>
  );
}
