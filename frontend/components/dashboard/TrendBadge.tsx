import { ArrowDown, ArrowUp, Minus } from "lucide-react";

/**
 * Period-over-period comparison pill — "current vs previous, both from the
 * dashboard's own /summary endpoint. Renders nothing when `previous` is
 * null (the "All" filter has no equivalent prior period) so it never shows
 * a misleading 0%/∞% — see the spec's "comparison shown only when data
 * supports it" requirement.
 */
export default function TrendBadge({
  current,
  previous,
  // Some metrics (e.g. Expenses) are "good" when they go down — flips the color.
  invert = false,
}: {
  current: number | null;
  previous: number | null;
  invert?: boolean;
}) {
  if (previous === null || current === null) return null;
  if (previous === 0) {
    if (current === 0) return null; // 0 -> 0, nothing meaningful to show
    // Can't express a percent change off a zero base — show the raw direction instead.
    const up = current > 0;
    const good = invert ? !up : up;
    return (
      <span className={`inline-flex items-center gap-0.5 text-xs font-medium ${good ? "text-success" : "text-danger"}`}>
        {up ? <ArrowUp size={11} /> : <ArrowDown size={11} />}
        new
      </span>
    );
  }

  const change = ((current - previous) / Math.abs(previous)) * 100;
  if (Math.abs(change) < 0.05) {
    return (
      <span className="inline-flex items-center gap-0.5 text-xs font-medium text-ink-500">
        <Minus size={11} />
        flat
      </span>
    );
  }
  const up = change > 0;
  const good = invert ? !up : up;
  return (
    <span className={`inline-flex items-center gap-0.5 text-xs font-medium ${good ? "text-success" : "text-danger"}`}>
      {up ? <ArrowUp size={11} /> : <ArrowDown size={11} />}
      {Math.abs(change).toFixed(1)}% vs previous period
    </span>
  );
}
