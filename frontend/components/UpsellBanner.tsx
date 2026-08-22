"use client";

import Link from "next/link";
import { Sparkles } from "lucide-react";

// Shown on a gated page (bookings, ecommerce orders, custom wig requests)
// when a 403 comes back. Two different backend gates can produce that 403
// on the same page: PlanFeatureService ("Upgrade your plan to use this.")
// and ModuleAccessService ("This isn't available for your business." — a
// Super Admin decision, independent of billing). Only the plan-gate message
// gets the "Upgrade plan" link — showing it for a Super-Admin-disabled
// module would wrongly imply paying more could turn it back on.
export default function UpsellBanner({ message }: { message: string }) {
  const isPlanGate = message.toLowerCase().includes("upgrade your plan");
  return (
    <div className="flex items-center gap-3 rounded-lg border border-accent/20 bg-accent-soft px-4 py-2.5 text-sm text-accent-hover">
      <Sparkles size={16} className="shrink-0" />
      <p className="flex-1">{message}</p>
      {isPlanGate && (
        <Link
          href="/dashboard/billing"
          className="shrink-0 whitespace-nowrap font-semibold underline underline-offset-2 hover:no-underline"
        >
          Upgrade plan
        </Link>
      )}
    </div>
  );
}
