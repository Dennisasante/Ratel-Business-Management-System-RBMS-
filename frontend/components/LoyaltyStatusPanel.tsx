"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, Copy, Gift } from "lucide-react";
import { api, ApiError, LoyaltyStatus } from "@/lib/api";
import Button from "@/components/ui/Button";

// One filled circle per visit, up to the target — a stamp card. Beyond ~12 a bar reads better.
export function LoyaltyStamps({ visits, target }: { visits: number; target: number }) {
  const filled = Math.min(visits, target);
  if (target > 12) {
    return (
      <div className="h-2 w-full overflow-hidden rounded-full bg-canvas" role="img" aria-label={`${filled} of ${target} visits`}>
        <div className="h-full rounded-full bg-accent transition-all" style={{ width: `${(filled / target) * 100}%` }} />
      </div>
    );
  }
  return (
    <div className="flex flex-wrap gap-1.5" role="img" aria-label={`${filled} of ${target} visits`}>
      {Array.from({ length: target }, (_, i) => (
        <span
          key={i}
          className={`flex h-5 w-5 items-center justify-center rounded-full border text-[10px] ${
            i < filled ? "border-accent bg-accent text-white" : "border-border bg-surface text-transparent"
          }`}
        >
          <Check size={11} strokeWidth={3} />
        </span>
      ))}
    </div>
  );
}

/**
 * A customer's loyalty standing: their stamp card, and — when a reward is due — a prompt to give it.
 * Used at the till (compact, as soon as a customer is picked / just after a sale) and on the
 * customer's own page in the app. Renders nothing for a business without an active program.
 */
export default function LoyaltyStatusPanel({
  token,
  customerId,
  name,
  compact = false,
  onChanged,
}: {
  token: string;
  customerId: string;
  name?: string | null;
  compact?: boolean;
  onChanged?: () => void;
}) {
  const [status, setStatus] = useState<LoyaltyStatus | null>(null);
  const [redeeming, setRedeeming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    try {
      setStatus(await api.getLoyaltyStatus(token, customerId));
    } catch {
      setStatus(null); // no loyalty module / no access: just don't show anything
    }
  }, [token, customerId]);

  useEffect(() => {
    setStatus(null);
    setError(null);
    load();
  }, [load]);

  async function handleRedeem() {
    setRedeeming(true);
    setError(null);
    try {
      setStatus(await api.redeemLoyaltyReward(token, customerId));
      onChanged?.();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't mark that reward as given.");
      await load();
    } finally {
      setRedeeming(false);
    }
  }

  async function copyLink() {
    if (!status?.pointsUrl) return;
    await navigator.clipboard.writeText(status.pointsUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  if (!status || !status.active) return null;

  const first = name ? name.trim().split(/\s+/)[0] : null;
  const remaining = Math.max(status.target - status.visits, 0);

  return (
    <div className={`rounded-lg border ${status.rewardDue ? "border-success bg-success-soft" : "border-border bg-surface"} ${compact ? "px-3 py-2" : "p-4"}`}>
      <div className="flex items-start gap-2">
        <Gift size={compact ? 14 : 16} className={`mt-0.5 shrink-0 ${status.rewardDue ? "text-success" : "text-ink-500"}`} />
        <div className="flex flex-1 flex-col gap-2">
          <p className={`${compact ? "text-xs" : "text-sm"} font-medium text-ink-900`}>
            {status.rewardDue
              ? `${first ?? "This customer"} has earned a reward: ${status.rewardDescription}`
              : `${first ? `${first} · ` : ""}${status.visits} of ${status.target} visits${remaining > 0 ? ` · ${remaining} more for ${status.rewardDescription}` : ""}`}
          </p>
          {!compact && <LoyaltyStamps visits={status.visits} target={status.target} />}
          {status.rewardDue && (
            <div className="flex flex-col items-start gap-1">
              <p className="text-xs text-ink-700">Apply the reward to this visit, then mark it as given.</p>
              <Button type="button" onClick={handleRedeem} disabled={redeeming} className="px-3 py-1.5 text-xs">
                {redeeming ? "Saving..." : "Mark reward as given"}
              </Button>
            </div>
          )}
          {error && <p className="text-xs text-danger">{error}</p>}
          {!compact && status.pointsUrl && (
            <div className="flex items-center gap-2">
              <code className="truncate rounded bg-canvas px-2 py-1 text-xs text-ink-700">{status.pointsUrl}</code>
              <button
                type="button"
                onClick={copyLink}
                className="flex shrink-0 items-center gap-1 text-xs font-medium text-accent-hover hover:underline"
              >
                {copied ? <Check size={12} /> : <Copy size={12} />}
                {copied ? "Copied" : "Copy link"}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
