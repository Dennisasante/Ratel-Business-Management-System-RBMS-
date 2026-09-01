"use client";

import Card from "@/components/ui/Card";
import { formatGHS, formatPercent } from "@/lib/dashboardFormat";
import { SalesBreakdown, SalesBreakdownDimension } from "@/lib/api";

const DIMENSIONS: { key: SalesBreakdownDimension; label: string }[] = [
  { key: "PAYMENT_METHOD", label: "Payment method" },
  { key: "CATEGORY", label: "Category" },
  { key: "SALESPERSON", label: "Salesperson" },
];

/** Section 7 — deliberately no "by branch" option, RBMS has no branch concept. */
export default function SalesBreakdownCard({
  breakdown,
  dimension,
  onDimensionChange,
}: {
  breakdown: SalesBreakdown | null;
  dimension: SalesBreakdownDimension;
  onDimensionChange: (d: SalesBreakdownDimension) => void;
}) {
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-ink-900">Sales breakdown</h2>
        <div className="flex rounded-lg border border-border bg-canvas p-0.5 text-xs">
          {DIMENSIONS.map((d) => (
            <button
              key={d.key}
              onClick={() => onDimensionChange(d.key)}
              className={`rounded-md px-2.5 py-1.5 font-medium transition ${
                dimension === d.key ? "bg-surface text-ink-900 shadow-sm" : "text-ink-500 hover:text-ink-900"
              }`}
            >
              {d.label}
            </button>
          ))}
        </div>
      </div>

      {!breakdown ? (
        <div className="mt-4 animate-pulse space-y-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-6 rounded bg-canvas" />
          ))}
        </div>
      ) : breakdown.entries.length === 0 ? (
        <p className="mt-4 text-sm text-ink-500">No sales in this period yet.</p>
      ) : (
        <div className="mt-4 flex flex-col gap-3">
          {breakdown.entries.map((e) => (
            <div key={e.label}>
              <div className="flex items-center justify-between text-sm">
                <span className="text-ink-900">{e.label}</span>
                <span className="tabular text-ink-500">
                  {formatGHS(e.revenue)} · {formatPercent(e.percentOfTotal)}
                </span>
              </div>
              <div className="mt-1 h-2 overflow-hidden rounded-full bg-canvas">
                <div className="h-full rounded-full bg-accent" style={{ width: `${e.percentOfTotal ?? 0}%` }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
