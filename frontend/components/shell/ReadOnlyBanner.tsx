"use client";

import Link from "next/link";
import { AlertTriangle } from "lucide-react";
import { useAuth } from "@/lib/auth";

// UX polish only — ReadOnlyEnforcementFilter on the backend is what actually
// blocks mutating requests. This just makes sure nobody is surprised by it:
// visible to every business user (not just Owners), since staff hit this
// wall too even though only an Owner can act on it.
export default function ReadOnlyBanner() {
  const { session, business } = useAuth();

  if (business?.billingStatus !== "READ_ONLY") return null;

  const canRenew = session?.role === "OWNER";

  return (
    <div className="flex items-center gap-3 border-b border-danger/20 bg-danger-soft px-4 py-2.5 text-sm text-danger sm:px-6 lg:px-8">
      <AlertTriangle size={16} className="shrink-0" />
      <p className="flex-1">
        Your subscription has ended — renew to keep creating and editing.
        {!canRenew && " Ask your business owner to renew."}
      </p>
      {canRenew && (
        <Link href="/dashboard/billing" className="shrink-0 whitespace-nowrap font-semibold underline underline-offset-2 hover:no-underline">
          Renew now
        </Link>
      )}
    </div>
  );
}
