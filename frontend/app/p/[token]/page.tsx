"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { CalendarCheck2, Gift } from "lucide-react";
import { api, PublicLoyalty } from "@/lib/api";
import { LoyaltyStamps } from "@/components/LoyaltyStatusPanel";
import PoweredByRatel from "@/components/PoweredByRatel";

// A customer's own points page — reached from the link in their SMS receipt. No login: the long
// random token in the address is the credential, and the page shows only their first name,
// their progress and a way to book.
export default function PointsPage() {
  const params = useParams<{ token: string }>();
  const [data, setData] = useState<PublicLoyalty | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    api.getPublicLoyalty(params.token).then(setData).catch(() => setFailed(true));
  }, [params.token]);

  if (failed) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-canvas px-4">
        <p className="max-w-xs text-center text-sm text-ink-500">This link isn&apos;t valid any more. Check the latest text message from the business.</p>
      </main>
    );
  }
  if (!data) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-canvas">
        <p className="text-sm text-ink-500">Loading...</p>
      </main>
    );
  }

  const remaining = Math.max(data.target - data.visits, 0);

  return (
    <main className="flex min-h-screen flex-col items-center bg-canvas px-4 py-10">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-surface p-6 text-center shadow-card">
        {data.logoUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={data.logoUrl} alt="" className="mx-auto mb-3 h-16 w-16 rounded-xl object-contain" />
        )}
        <h1 className="text-lg font-semibold text-ink-900">{data.businessName}</h1>
        <p className="mt-1 text-sm text-ink-500">{data.customerFirstName ? `Hi ${data.customerFirstName}, here are your points` : "Your points"}</p>

        <div className="mt-6 flex flex-col items-center gap-3">
          <p className="text-4xl font-semibold text-ink-900">
            {Math.min(data.visits, data.target)}
            <span className="text-xl font-normal text-ink-400"> / {data.target}</span>
          </p>
          <div className="flex justify-center">
            <LoyaltyStamps visits={data.visits} target={data.target} />
          </div>
          {data.rewardDue ? (
            <div className="mt-2 flex items-start gap-2 rounded-lg bg-success-soft px-3 py-2 text-left text-sm text-success">
              <Gift size={16} className="mt-0.5 shrink-0" />
              <span>
                You&apos;ve earned <strong>{data.rewardDescription}</strong>! Show this page next time you visit and we&apos;ll apply it.
              </span>
            </div>
          ) : (
            <p className="text-sm text-ink-500">
              {remaining} more visit{remaining === 1 ? "" : "s"} to earn <strong className="text-ink-700">{data.rewardDescription}</strong>.
            </p>
          )}
        </div>

        {data.bookingPath && (
          <a
            href={data.bookingPath}
            className="mt-6 flex items-center justify-center gap-2 rounded-lg bg-accent px-4 py-3 text-sm font-medium text-white transition hover:bg-accent-hover"
          >
            <CalendarCheck2 size={16} /> Book your next appointment
          </a>
        )}
      </div>
      <div className="mt-6">
        <PoweredByRatel />
      </div>
    </main>
  );
}
