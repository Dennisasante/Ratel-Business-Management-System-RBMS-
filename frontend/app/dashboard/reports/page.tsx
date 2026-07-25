"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { TrendingUp, TrendingDown, Wallet, Download, Percent, Receipt, Tag } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, Expense, ExpenseCategory, ReportSummary, Sale, ServiceOrder, StaffCommission } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import StatCard from "@/components/ui/StatCard";
import CardSkeleton from "@/components/ui/CardSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

type ViewFilter = "all" | "sales" | "expenses";

const EXPENSE_CATEGORIES: ExpenseCategory[] = ["RENT", "UTILITIES", "TRANSPORT", "SUPPLIES", "SALARY", "MARKETING", "OTHER"];

function firstDayOfMonth(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function ReportsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [from, setFrom] = useState(firstDayOfMonth());
  const [to, setTo] = useState(today());
  const [summary, setSummary] = useState<ReportSummary | null>(null);
  const [commissions, setCommissions] = useState<StaffCommission[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [sales, setSales] = useState<Sale[]>([]);
  const [serviceOrders, setServiceOrders] = useState<ServiceOrder[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  const [view, setView] = useState<ViewFilter>("all");
  const [expenseCategory, setExpenseCategory] = useState<ExpenseCategory | "">("");

  const loadReport = useCallback(async () => {
    if (!session) return;
    setError(null);
    try {
      const [s, c, e, sl, so] = await Promise.all([
        api.getReportSummary(session.token, from, to),
        api.getCommissions(session.token, from, to),
        api.listExpenses(session.token),
        api.listSales(session.token),
        api.listServiceOrders(session.token),
      ]);
      setSummary(s);
      setCommissions(c);
      setExpenses(e);
      setSales(sl);
      setServiceOrders(so);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load the report.");
    }
  }, [session, from, to]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadReport().finally(() => setFetching(false));
  }, [session, loadReport]);

  // Expenses aren't scoped by date on the list endpoint, so the range + category
  // narrowing both happen here, client-side, against the one fetched batch.
  const expensesInRange = useMemo(
    () => expenses.filter((e) => e.expenseDate >= from && e.expenseDate <= to),
    [expenses, from, to]
  );
  const expensesByCategory = useMemo(() => {
    const totals = new Map<ExpenseCategory, number>();
    for (const cat of EXPENSE_CATEGORIES) totals.set(cat, 0);
    for (const e of expensesInRange) totals.set(e.category, (totals.get(e.category) ?? 0) + e.amount);
    return totals;
  }, [expensesInRange]);
  const filteredExpenses = useMemo(
    () => (expenseCategory ? expensesInRange.filter((e) => e.category === expenseCategory) : expensesInRange),
    [expensesInRange, expenseCategory]
  );
  const filteredExpenseTotal = filteredExpenses.reduce((sum, e) => sum + e.amount, 0);

  // Discounts given, at the attendant's/owner's discretion — sales scoped by sale
  // date, service orders scoped by pickup date, matching how each report already
  // scopes revenue for that side of the business.
  const salesDiscountTotal = useMemo(
    () =>
      sales
        .filter((s) => s.createdAt.slice(0, 10) >= from && s.createdAt.slice(0, 10) <= to)
        .reduce((sum, s) => sum + s.items.reduce((iSum, i) => iSum + i.discountAmount, 0), 0),
    [sales, from, to]
  );
  const serviceDiscountTotal = useMemo(
    () =>
      serviceOrders
        .filter((o) => o.pickedUpAt && o.pickedUpAt.slice(0, 10) >= from && o.pickedUpAt.slice(0, 10) <= to)
        .reduce((sum, o) => sum + o.discountAmount, 0),
    [serviceOrders, from, to]
  );
  const totalDiscounts = salesDiscountTotal + serviceDiscountTotal;

  async function handleExport() {
    if (!session) return;
    setExporting(true);
    try {
      await api.downloadReportExport(session.token, from, to);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't export the report.");
    } finally {
      setExporting(false);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const isProfit = (summary?.profit ?? 0) >= 0;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Financial Reports" subtitle="Revenue, expenses, and profit for any date range." />

      <Card className="flex flex-wrap items-end gap-3 p-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">From</label>
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">To</label>
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
        <Button onClick={() => { setFetching(true); loadReport().finally(() => setFetching(false)); }} disabled={fetching}>
          {fetching ? "Updating..." : "Update"}
        </Button>
        <Button variant="secondary" onClick={handleExport} disabled={exporting}>
          <Download size={15} />
          {exporting ? "Exporting..." : "Export to Excel"}
        </Button>
      </Card>

      <div className="flex flex-wrap items-center gap-2">
        <ViewChip label="All" active={view === "all"} onClick={() => setView("all")} />
        <ViewChip label="Sales" active={view === "sales"} onClick={() => setView("sales")} />
        <ViewChip label="Expenses" active={view === "expenses"} onClick={() => setView("expenses")} />
        {view === "expenses" && (
          <select
            value={expenseCategory}
            onChange={(e) => setExpenseCategory(e.target.value as ExpenseCategory | "")}
            className="rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          >
            <option value="">All categories</option>
            {EXPENSE_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        )}
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {fetching ? (
        <CardSkeleton count={3} />
      ) : summary ? (
        <>
          {view !== "expenses" && (
            <div className={`grid gap-4 ${view === "all" ? "sm:grid-cols-2 lg:grid-cols-4" : "sm:grid-cols-2 lg:max-w-lg"}`}>
              {(view === "all" || view === "sales") && (
                <StatCard
                  label="Revenue"
                  value={`GH₵${summary.revenue.toFixed(2)}`}
                  hint={`${summary.salesCount} sale${summary.salesCount === 1 ? "" : "s"}`}
                  icon={TrendingUp}
                  tone="success"
                />
              )}
              {view === "all" && (
                <>
                  <StatCard
                    label="Expenses"
                    value={`GH₵${summary.expenses.toFixed(2)}`}
                    hint={`${summary.expenseCount} expense${summary.expenseCount === 1 ? "" : "s"}`}
                    icon={TrendingDown}
                    tone="danger"
                  />
                  <StatCard
                    label="Profit"
                    value={`GH₵${summary.profit.toFixed(2)}`}
                    hint={`${summary.from} → ${summary.to}`}
                    icon={Wallet}
                    tone={isProfit ? "success" : "danger"}
                  />
                </>
              )}
              {(view === "all" || view === "sales") && (
                <StatCard
                  label="Discounts given"
                  value={`GH₵${totalDiscounts.toFixed(2)}`}
                  hint="At staff/owner discretion"
                  icon={Tag}
                  tone="info"
                />
              )}
            </div>
          )}

          {view === "expenses" && (
            <div className="grid gap-4 sm:grid-cols-2">
              <StatCard
                label={expenseCategory ? `${expenseCategory} expenses` : "Total expenses"}
                value={`GH₵${filteredExpenseTotal.toFixed(2)}`}
                hint={`${filteredExpenses.length} expense${filteredExpenses.length === 1 ? "" : "s"} · ${from} → ${to}`}
                icon={TrendingDown}
                tone="danger"
              />
              <Card className="p-5">
                <h2 className="text-sm font-semibold text-ink-900">By category</h2>
                <ul className="mt-3 flex flex-col gap-1.5">
                  {EXPENSE_CATEGORIES.map((cat) => (
                    <li key={cat} className="flex items-center justify-between text-xs">
                      <span className="text-ink-500">{cat}</span>
                      <span className="tabular font-medium text-ink-900">GH₵{(expensesByCategory.get(cat) ?? 0).toFixed(2)}</span>
                    </li>
                  ))}
                </ul>
              </Card>
            </div>
          )}

          {view === "expenses" ? (
            <Card>
              <div className="flex items-center gap-2 p-5 pb-0">
                <Receipt size={16} className="text-accent-hover" />
                <h2 className="text-base font-semibold text-ink-900">Expense detail</h2>
              </div>
              {filteredExpenses.length === 0 ? (
                <p className="p-5 text-sm text-ink-500">No expenses match this filter.</p>
              ) : (
                <div className="mt-3">
                  <Table>
                    <THead>
                      <Tr>
                        <Th>Date</Th>
                        <Th>Category</Th>
                        <Th>Description</Th>
                        <Th className="text-right">Amount</Th>
                      </Tr>
                    </THead>
                    <TBody>
                      {filteredExpenses.map((e) => (
                        <Tr key={e.id}>
                          <Td className="text-ink-500">{e.expenseDate}</Td>
                          <Td className="text-ink-500">{e.category}</Td>
                          <Td className="text-ink-500">{e.description ?? "—"}</Td>
                          <Td className="tabular text-right font-medium">GH₵{e.amount.toFixed(2)}</Td>
                        </Tr>
                      ))}
                    </TBody>
                  </Table>
                </div>
              )}
            </Card>
          ) : (
            <Card>
              <div className="flex items-center gap-2 p-5 pb-0">
                <Percent size={16} className="text-accent-hover" />
                <h2 className="text-base font-semibold text-ink-900">Staff commissions</h2>
              </div>
              {commissions.length === 0 ? (
                <p className="p-5 text-sm text-ink-500">No commission-earning sales in this range.</p>
              ) : (
                <div className="mt-3">
                  <Table>
                    <THead>
                      <Tr>
                        <Th>Staff</Th>
                        <Th>Rate</Th>
                        <Th>Sales</Th>
                        <Th>Total sold</Th>
                        <Th className="text-right">Commission earned</Th>
                      </Tr>
                    </THead>
                    <TBody>
                      {commissions.map((c) => (
                        <Tr key={c.userId}>
                          <Td className="font-medium">{c.userName}</Td>
                          <Td className="tabular text-ink-500">{c.commissionRate}%</Td>
                          <Td className="tabular text-ink-500">{c.salesCount}</Td>
                          <Td className="tabular text-ink-500">GH₵{c.totalSales.toFixed(2)}</Td>
                          <Td className="tabular text-right font-medium">GH₵{c.commissionEarned.toFixed(2)}</Td>
                        </Tr>
                      ))}
                    </TBody>
                  </Table>
                </div>
              )}
            </Card>
          )}
        </>
      ) : null}
    </div>
  );
}

function ViewChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
        active
          ? "border-accent bg-accent-soft text-accent-hover"
          : "border-border bg-surface text-ink-700 hover:border-border-strong"
      }`}
    >
      {label}
    </button>
  );
}
