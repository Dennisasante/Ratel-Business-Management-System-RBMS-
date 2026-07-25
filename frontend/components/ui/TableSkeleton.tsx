// Content-shaped loading state shown immediately on mount, before the first fetch
// resolves — so the layout doesn't jump once real rows arrive, and the page never
// reads as "blank" during the network round-trip.
export default function TableSkeleton({ rows = 5, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div className="animate-pulse">
      <div className="flex gap-4 border-b border-border bg-canvas px-4 py-3">
        {Array.from({ length: cols }).map((_, i) => (
          <div key={i} className="h-3 flex-1 rounded bg-border" />
        ))}
      </div>
      <div className="divide-y divide-border">
        {Array.from({ length: rows }).map((_, r) => (
          <div key={r} className="flex items-center gap-4 px-4 py-4">
            {Array.from({ length: cols }).map((_, c) => (
              <div
                key={c}
                className="h-3.5 flex-1 rounded bg-border"
                style={{ opacity: 1 - (c % 3) * 0.15 }}
              />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
