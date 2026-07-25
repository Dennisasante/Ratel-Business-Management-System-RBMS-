"use client";

import { useState } from "react";
import { ApiError, ExpenseCategory, ExpensePayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface ExpenseFormProps {
  onSubmit: (payload: ExpensePayload) => Promise<void>;
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

export default function ExpenseForm({ onSubmit }: ExpenseFormProps) {
  const today = new Date().toISOString().slice(0, 10);
  const [form, setForm] = useState({
    category: "OTHER" as ExpenseCategory,
    description: "",
    amount: "",
    expenseDate: today,
  });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        category: form.category,
        description: form.description || undefined,
        amount: Number(form.amount) || 0,
        expenseDate: form.expenseDate,
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
        <label htmlFor="category" className="text-sm font-medium text-ink-700">
          Category <span className="text-danger">*</span>
        </label>
        <select
          id="category"
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

      <FormField
        label="Amount (GH₵)"
        name="amount"
        type="number"
        required
        value={form.amount}
        onChange={(v) => set("amount", v)}
      />

      <FormField label="Date" name="expenseDate" type="date" required value={form.expenseDate} onChange={(v) => set("expenseDate", v)} />

      <FormField
        label="Description"
        name="description"
        value={form.description}
        onChange={(v) => set("description", v)}
        placeholder="e.g. Electricity bill"
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : "Log expense"}
      </Button>
    </form>
  );
}
