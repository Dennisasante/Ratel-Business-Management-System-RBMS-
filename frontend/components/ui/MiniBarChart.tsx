import { DayCount } from "@/lib/api";

// Same latent flaw as the dashboard's SalesProfitChart (see its own comment for the live
// bug that fix addressed): barWidth here is `width / data.length`, so a short `data` array
// would make each bar disproportionately wide — at the extreme, one point would produce a
// single bar spanning nearly the entire viewBox. Not currently reachable (the only caller,
// the platform stats page, always passes exactly 30 fixed days), but this is a shared
// `components/ui/` primitive, so a future caller with a shorter/variable-length array would
// hit the exact same bug. Reserving a minimum slot count keeps that from ever happening,
// with zero visual effect on today's fixed 30-point usage.
const MIN_CHART_SLOTS = 7;

export default function MiniBarChart({ data, color = "#004aad" }: { data: DayCount[]; color?: string }) {
  const max = Math.max(1, ...data.map((d) => d.count));
  const width = 560;
  const height = 80;
  const slotCount = Math.max(data.length, MIN_CHART_SLOTS);
  const barWidth = width / slotCount;
  const slotsBefore = Math.floor((slotCount - data.length) / 2);

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="h-20 w-full" preserveAspectRatio="none">
      {data.map((d, i) => {
        const barHeight = (d.count / max) * (height - 4);
        return (
          <rect
            key={d.date}
            x={(i + slotsBefore) * barWidth + 1}
            y={height - barHeight}
            width={Math.max(1, barWidth - 2)}
            height={barHeight}
            fill={color}
            opacity={d.count === 0 ? 0.15 : 0.85}
            rx={1}
          >
            <title>
              {d.date}: {d.count}
            </title>
          </rect>
        );
      })}
    </svg>
  );
}
