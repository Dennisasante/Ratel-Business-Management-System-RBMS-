"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Receipt, Plus } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, Expense, ExpensePayload } from "@/lib/api";
import Modal from "@/components/Modal";
import ExpenseForm from "@/components/ExpenseForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

export default function ExpensesPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [fetching, setFetching] = useState(true);
  const [showAdd, setShowAdd] = useState(false);

  const loadExpenses = useCallback(async () => {
    if (!session) return;
    setExpenses(await api.listExpenses(session.token));
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadExpenses().finally(() => setFetching(false));
  }, [session, loadExpenses]);

  async function handleCreate(payload: ExpensePayload) {
    if (!session) return;
    await api.createExpense(session.token, payload);
    await loadExpenses();
    setShowAdd(false);
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
        {fetching ? (
          <TableSkeleton cols={6} />
        ) : expenses.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title="No expenses logged yet"
            description="Log your first expense to start tracking costs."
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
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {showAdd && (
        <Modal title="Log expense" onClose={() => setShowAdd(false)}>
          <ExpenseForm onSubmit={handleCreate} />
        </Modal>
      )}
    </div>
  );
}
