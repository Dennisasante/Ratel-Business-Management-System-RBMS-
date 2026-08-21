"use client";

import { useState } from "react";
import { ApiError, Expense, ExpenseCategory, ExpenseEditPayload, ExpensePaymentMethod } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface ExpenseEditFormProps {
  expense: Expense;
  onSubmit: (payload: ExpenseEditPayload) => Promise<void>;
}

const CATEGORIES: { value: ExpenseCategory; label: string }[] = [
  { value: "RENT", label: "Rent" },
  { value: "UTILITIES", label: "Utilities" },
  { value: "TRANSPORT", label: "Transport" },
  { value: "SUPPLIES", label: "Supplies" },
  { value: "SALARY", label: "Salary" },
  { value: "MARKETING", label: "Marketing" },
  { value: "OTHER", label: "Other" },
];

const PAYMENT_METHODS: { value: ExpensePaymentMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "MOBILE_MONEY", label: "Mobile Money" },
];

// Separate from ExpenseForm (not a shared create/edit mode toggle) — the
// mandatory reason field and pre-filled values don't need to tangle with
// the simpler create form.
export default function ExpenseEditForm({ expense, onSubmit }: ExpenseEditFormProps) {
  const [form, setForm] = useState({
    category: expense.category,
    description: expense.description ?? "",
    paymentMethod: expense.paymentMethod,
    amount: String(expense.amount),
    expenseDate: expense.expenseDate,
  });
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!reason.trim()) {
      setError("A reason is required for this edit.");
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit({
        expense: {
          category: form.category as ExpenseCategory,
          description: form.description || undefined,
          paymentMethod: form.paymentMethod as ExpensePaymentMethod,
          amount: Number(form.amount) || 0,
          expenseDate: form.expenseDate,
        },
        reason: reason.trim(),
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="edit-category" className="text-sm font-medium text-ink-700">
          Category <span className="text-danger">*</span>
        </label>
        <select
          id="edit-category"
          value={form.category}
          onChange={(e) => set("category", e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="edit-paymentMethod" className="text-sm font-medium text-ink-700">
          Paid via <span className="text-danger">*</span>
        </label>
        <select
          id="edit-paymentMethod"
          value={form.paymentMethod}
          onChange={(e) => set("paymentMethod", e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          {PAYMENT_METHODS.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
      </div>

      <FormField
        label="Amount (GH₵)"
        name="edit-amount"
        type="number"
        required
        value={form.amount}
        onChange={(v) => set("amount", v)}
      />

      <FormField label="Date" name="edit-expenseDate" type="date" required value={form.expenseDate} onChange={(v) => set("expenseDate", v)} />

      <FormField
        label="Description"
        name="edit-description"
        value={form.description}
        onChange={(v) => set("description", v)}
        placeholder="e.g. Electricity bill"
      />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="edit-reason" className="text-sm font-medium text-ink-700">
          Reason for this edit <span className="text-danger">*</span>
        </label>
        <textarea
          id="edit-reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder="Why is this changing?"
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : "Save changes"}
      </Button>
    </form>
  );
}
