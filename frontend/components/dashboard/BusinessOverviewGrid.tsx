import { Wallet, TrendingUp, Percent, Receipt, PiggyBank, Users, Wrench } from "lucide-react";
import StatCard from "@/components/ui/StatCard";
import TrendBadge from "./TrendBadge";
import { formatGHS, formatPercent } from "@/lib/dashboardFormat";
import { DashboardSummary } from "@/lib/api";

/** Section 1 of the restructured Dashboard — see the spec's "Business Overview" list. */
export default function BusinessOverviewGrid({ summary }: { summary: DashboardSummary }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      <StatCard
        label="Revenue"
        value={formatGHS(summary.revenue)}
        icon={Wallet}
        tone="accent"
        trend={<TrendBadge current={summary.revenue} previous={summary.previousRevenue} />}
      />
      <StatCard
        label="Gross profit"
        value={formatGHS(summary.grossProfit)}
        icon={TrendingUp}
        tone="success"
        trend={<TrendBadge current={summary.grossProfit} previous={summary.previousGrossProfit} />}
      />
      <StatCard
        label="Gross margin"
        value={formatPercent(summary.grossMarginPercent)}
        icon={Percent}
        tone="info"
        trend={<TrendBadge current={summary.grossMarginPercent} previous={summary.previousGrossMarginPercent} />}
      />
      <StatCard
        label="Expenses"
        value={formatGHS(summary.expenses)}
        icon={Receipt}
        tone="danger"
        trend={<TrendBadge current={summary.expenses} previous={summary.previousExpenses} invert />}
      />
      <StatCard
        label="Net profit"
        value={formatGHS(summary.netProfit)}
        icon={PiggyBank}
        tone={summary.netProfit !== null && summary.netProfit < 0 ? "danger" : "success"}
        trend={<TrendBadge current={summary.netProfit} previous={summary.previousNetProfit} />}
      />
      <StatCard label="Team members" value={summary.teamMembers} icon={Users} tone="accent" />
      <StatCard label="Active service orders" value={summary.activeServiceOrders} hint="Received or in progress" icon={Wrench} tone="info" />
    </div>
  );
}
