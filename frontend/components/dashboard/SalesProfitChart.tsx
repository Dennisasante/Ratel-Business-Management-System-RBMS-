"use client";

import { useMemo, useState } from "react";
import Card from "@/components/ui/Card";
import { formatGHS } from "@/lib/dashboardFormat";
import { DashboardChart } from "@/lib/api";

type Metric = "revenue" | "grossProfit" | "orders";

const METRICS: { key: Metric; label: string; barClass: string; format: (v: number) => string }[] = [
  { key: "revenue", label: "Revenue", barClass: "fill-accent", format: formatGHS },
  { key: "grossProfit", label: "Gross profit", barClass: "fill-success", format: formatGHS },
  { key: "orders", label: "Orders", barClass: "fill-info", format: (v) => String(v) },
];

const CHART_HEIGHT = 200;

// Real bug found live: the SVG viewBox is sized at 40 units per point, then
// stretched to fill the card's full width via preserveAspectRatio="none".
// That ratio holds up fine with many points (bars stay reasonably thin with
// visible gaps) but breaks down completely for very few points — "Today"
// (or any narrow custom range) returns exactly ONE chart point, so a single
// bar occupying 60% of a 1-slot viewBox gets stretched into one giant solid
// block that no longer reads as a chart at all. Reserving a minimum number
// of slots regardless of the real point count keeps the bar-to-empty-space
// ratio sane even when there's only one (or a few) real data points.
const MIN_CHART_SLOTS = 7;

/** Section 2 — a single switchable bar chart, deliberately the only chart on the page (spec: avoid excessive charts). */
export default function SalesProfitChart({ chart }: { chart: DashboardChart | null }) {
  const [metric, setMetric] = useState<Metric>("revenue");
  const active = METRICS.find((m) => m.key === metric)!;

  const points = chart?.points ?? [];
  const values = points.map((p) => p[metric]);
  const max = Math.max(1, ...values);

  const hasAnyData = values.some((v) => v > 0);

  // Show every point's label when there are few enough to fit; otherwise
  // thin them out so labels never overlap on a 90-day view.
  const labelStride = useMemo(() => {
    if (points.length <= 10) return 1;
    if (points.length <= 20) return 2;
    return Math.ceil(points.length / 10);
  }, [points.length]);

  // See MIN_CHART_SLOTS above — reserve at least that many slots, split evenly
  // as empty padding before/after the real bars, so a 1-point ("Today") chart
  // gets one reasonably-sized, centered bar instead of a stretched block.
  const slotCount = Math.max(points.length, MIN_CHART_SLOTS);
  const emptySlots = slotCount - points.length;
  const slotsBefore = Math.floor(emptySlots / 2);
  const slotsAfter = emptySlots - slotsBefore;

  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-ink-900">Sales &amp; profit</h2>
          <p className="mt-0.5 text-xs text-ink-500">
            {chart ? `By ${chart.granularity.toLowerCase()}` : "Loading..."}
          </p>
        </div>
        <div className="flex rounded-lg border border-border bg-canvas p-0.5 text-sm">
          {METRICS.map((m) => (
            <button
              key={m.key}
              onClick={() => setMetric(m.key)}
              className={`rounded-md px-3 py-1.5 font-medium transition ${
                metric === m.key ? "bg-surface text-ink-900 shadow-sm" : "text-ink-500 hover:text-ink-900"
              }`}
            >
              {m.label}
            </button>
          ))}
        </div>
      </div>

      {!chart ? (
        <div className="mt-4 h-[200px] animate-pulse rounded-lg bg-canvas" />
      ) : !hasAnyData ? (
        <div className="mt-4 flex h-[200px] items-center justify-center rounded-lg bg-canvas text-sm text-ink-500">
          No {active.label.toLowerCase()} in this period yet.
        </div>
      ) : (
        <div className="mt-4">
          <svg viewBox={`0 0 ${slotCount * 40} ${CHART_HEIGHT}`} className="h-[200px] w-full" preserveAspectRatio="none">
            {points.map((p, i) => {
              const value = p[metric];
              const barHeight = Math.max(2, (value / max) * (CHART_HEIGHT - 24));
              const barWidth = 24;
              const x = (i + slotsBefore) * 40 + (40 - barWidth) / 2;
              const y = CHART_HEIGHT - 24 - barHeight;
              return (
                <g key={p.bucketStart}>
                  <title>{`${p.label}: ${active.format(value)}`}</title>
                  <rect x={x} y={y} width={barWidth} height={barHeight} rx={3} className={active.barClass} opacity={0.9} />
                </g>
              );
            })}
          </svg>
          <div className="mt-1 flex text-[10px] text-ink-500" style={{ fontVariantNumeric: "tabular-nums" }}>
            {slotsBefore > 0 && <div className="flex-1" style={{ flexGrow: slotsBefore }} />}
            {points.map((p, i) => (
              <div key={p.bucketStart} className="flex-1 text-center">
                {i % labelStride === 0 ? p.label : ""}
              </div>
            ))}
            {slotsAfter > 0 && <div className="flex-1" style={{ flexGrow: slotsAfter }} />}
          </div>
        </div>
      )}
    </Card>
  );
}
