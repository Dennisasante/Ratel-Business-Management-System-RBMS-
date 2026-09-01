import Card from "@/components/ui/Card";
import { formatGHS, formatPercent } from "@/lib/dashboardFormat";
import { DashboardSummary } from "@/lib/api";

function Row({ label, value, emphasis = false, indent = false }: { label: string; value: string; emphasis?: boolean; indent?: boolean }) {
  return (
    <div className={`flex items-center justify-between py-2 ${emphasis ? "" : "border-b border-border last:border-0"}`}>
      <span className={`text-sm ${indent ? "pl-4 text-ink-500" : "text-ink-900"} ${emphasis ? "font-semibold" : ""}`}>{label}</span>
      <span className={`tabular text-sm ${emphasis ? "text-base font-semibold text-ink-900" : "text-ink-900"}`}>{value}</span>
    </div>
  );
}

/** Section 9 — the full Revenue -> COGS -> Gross Profit -> Margin -> Expenses -> Net Profit waterfall, in one place. */
export default function ProfitabilitySummaryCard({ summary }: { summary: DashboardSummary | null }) {
  return (
    <Card className="p-5">
      <h2 className="text-base font-semibold text-ink-900">Profitability</h2>
      <p className="mt-1 text-xs text-ink-500">How revenue turns into what you actually keep.</p>

      {!summary ? (
        <div className="mt-4 h-40 animate-pulse rounded-lg bg-canvas" />
      ) : (
        <div className="mt-3">
          <Row label="Revenue" value={formatGHS(summary.revenue)} />
          <Row label="Cost of goods sold" value={`− ${formatGHS(summary.cogs)}`} indent />
          <Row label="Gross profit" value={formatGHS(summary.grossProfit)} emphasis />
          <Row label="Gross margin" value={formatPercent(summary.grossMarginPercent)} indent />
          <Row label="Expenses" value={`− ${formatGHS(summary.expenses)}`} indent />
          <Row label="Net profit" value={formatGHS(summary.netProfit)} emphasis />
        </div>
      )}
    </Card>
  );
}
