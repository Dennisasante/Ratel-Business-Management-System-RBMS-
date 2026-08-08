"use client";

import Link from "next/link";
import { Sparkles } from "lucide-react";

// Shown on a gated page (bookings, ecommerce orders, custom wig requests)
// when the business's plan doesn't include that feature. UX only — the real
// enforcement is PlanFeatureService.requireFeature() on the backend.
export default function UpsellBanner({ message }: { message: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-accent/20 bg-accent-soft px-4 py-2.5 text-sm text-accent-hover">
      <Sparkles size={16} className="shrink-0" />
      <p className="flex-1">{message}</p>
      <Link
        href="/dashboard/billing"
        className="shrink-0 whitespace-nowrap font-semibold underline underline-offset-2 hover:no-underline"
      >
        Upgrade plan
      </Link>
    </div>
  );
}
