import Link from "next/link";
import Card from "@/components/ui/Card";
import { formatGHS } from "@/lib/dashboardFormat";
import { Expense } from "@/lib/api";

function titleCase(s: string): string {
  return s.charAt(0) + s.slice(1).toLowerCase();
}

/** Section 8 — reuses the existing Expenses module's own records, no second expense-tracking system. */
export default function ExpensesSummaryCard({ expenses }: { expenses: Expense[] | null }) {
  const total = expenses?.reduce((sum, e) => sum + e.amount, 0) ?? 0;

  const byCategory = new Map<string, number>();
  for (const e of expenses ?? []) {
    byCategory.set(e.category, (byCategory.get(e.category) ?? 0) + e.amount);
  }
  const topCategories = [...byCategory.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5);

  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-ink-900">Expenses</h2>
        <Link href="/dashboard/expenses" className="text-xs font-medium text-accent-hover hover:underline">
          View all
        </Link>
      </div>

      {!expenses ? (
        <div className="mt-4 h-20 animate-pulse rounded-lg bg-canvas" />
      ) : (
        <>
          <p className="mt-2 text-2xl font-semibold text-ink-900">{formatGHS(total)}</p>
          <p className="text-xs text-ink-500">Total for this period</p>

          {topCategories.length > 0 && (
            <div className="mt-4 flex flex-col gap-2 border-t border-border pt-3">
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-ink-500">Top categories</p>
              {topCategories.map(([category, amount]) => (
                <div key={category} className="flex items-center justify-between text-sm">
                  <span className="text-ink-900">{titleCase(category)}</span>
                  <span className="tabular text-ink-500">{formatGHS(amount)}</span>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </Card>
  );
}
