"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Receipt, Plus } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, Expense, ExpenseEditPayload, ExpensePayload, isPendingApproval } from "@/lib/api";
import Modal from "@/components/Modal";
import ExpenseForm from "@/components/ExpenseForm";
import ExpenseEditForm from "@/components/ExpenseEditForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";
import DateRangeFilter from "@/components/ui/DateRangeFilter";
import { DateRangeValue, defaultDateRangeValue } from "@/lib/dateRangePresets";

export default function ExpensesPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [dateRange, setDateRange] = useState<DateRangeValue>(defaultDateRangeValue());
  const [fetching, setFetching] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [editingExpense, setEditingExpense] = useState<Expense | null>(null);
  const [actionInfo, setActionInfo] = useState<string | null>(null);

  const loadExpenses = useCallback(async () => {
    if (!session) return;
    setExpenses(await api.listExpenses(session.token, { from: dateRange.from ?? undefined, to: dateRange.to ?? undefined }));
  }, [session, dateRange]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadExpenses().finally(() => setFetching(false));
  }, [session, loadExpenses]);

  async function handleCreate(payload: ExpensePayload) {
    if (!session) return;
    await api.createExpense(session.token, payload);
    await loadExpenses();
    setShowAdd(false);
  }

  async function handleEditSubmit(payload: ExpenseEditPayload) {
    if (!session || !editingExpense) return;
    const result = await api.updateExpense(session.token, editingExpense.id, payload);
    setActionInfo(isPendingApproval(result) ? result.message : null);
    await loadExpenses();
    setEditingExpense(null);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const total = expenses.reduce((sum, e) => sum + e.amount, 0);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Expenses"
        subtitle={!fetching ? `${expenses.length} recorded · GH₵${total.toFixed(2)} total` : undefined}
        actions={
          <Button onClick={() => setShowAdd(true)}>
            <Plus size={16} /> Log expense
          </Button>
        }
      />

      <Card>
        <div className="p-5 pb-0">
          <DateRangeFilter value={dateRange} onChange={setDateRange} />
        </div>
        {actionInfo && <p className="px-5 pt-4 text-sm text-info">{actionInfo}</p>}
        {fetching ? (
          <TableSkeleton cols={7} />
        ) : expenses.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title="No expenses in this range"
            description="Try a wider date range, or log your first expense to start tracking costs."
            action={
              <Button onClick={() => setShowAdd(true)}>
                <Plus size={16} /> Log expense
              </Button>
            }
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Date</Th>
                <Th>Category</Th>
                <Th>Description</Th>
                <Th>Paid via</Th>
                <Th>Recorded by</Th>
                <Th className="text-right">Amount</Th>
                <Th></Th>
              </Tr>
            </THead>
            <TBody>
              {expenses.map((e) => (
                <Tr key={e.id}>
                  <Td className="text-ink-500">{e.expenseDate}</Td>
                  <Td>
                    <Badge tone="neutral">{e.category}</Badge>
                  </Td>
                  <Td className="text-ink-500">{e.description ?? "—"}</Td>
                  <Td className="text-ink-500">{e.paymentMethod === "MOBILE_MONEY" ? "Mobile Money" : "Cash"}</Td>
                  <Td className="text-ink-500">{e.recordedByName}</Td>
                  <Td className="tabular text-right font-medium">GH₵{e.amount.toFixed(2)}</Td>
                  <Td className="text-right">
                    <button
                      onClick={() => {
                        setActionInfo(null);
                        setEditingExpense(e);
                      }}
                      className="text-xs font-medium text-accent-hover hover:underline"
                    >
                      Edit
                    </button>
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {editingExpense && (
        <Modal title="Edit expense" onClose={() => setEditingExpense(null)}>
          <ExpenseEditForm expense={editingExpense} onSubmit={handleEditSubmit} />
        </Modal>
      )}

      {showAdd && (
        <Modal title="Log expense" onClose={() => setShowAdd(false)}>
          <ExpenseForm onSubmit={handleCreate} />
        </Modal>
      )}
    </div>
  );
}
