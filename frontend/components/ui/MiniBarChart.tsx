import { DayCount } from "@/lib/api";

export default function MiniBarChart({ data, color = "#004aad" }: { data: DayCount[]; color?: string }) {
  const max = Math.max(1, ...data.map((d) => d.count));
  const width = 560;
  const height = 80;
  const barWidth = width / data.length;

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="h-20 w-full" preserveAspectRatio="none">
      {data.map((d, i) => {
        const barHeight = (d.count / max) * (height - 4);
        return (
          <rect
            key={d.date}
            x={i * barWidth + 1}
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
