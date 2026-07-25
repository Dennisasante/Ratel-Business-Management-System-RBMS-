// Same purpose as TableSkeleton, shaped for card/stat grids instead of tables.
export default function CardSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="grid animate-pulse gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="rounded-xl border border-border bg-surface p-5 shadow-card">
          <div className="h-3 w-1/3 rounded bg-border" />
          <div className="mt-3 h-6 w-2/3 rounded bg-border" />
          <div className="mt-2 h-3 w-1/2 rounded bg-border" />
        </div>
      ))}
    </div>
  );
}
