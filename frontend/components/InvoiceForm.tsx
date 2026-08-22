"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { ApiError, Customer, Invoice, InvoiceItemPayload, InvoicePayload, Product } from "@/lib/api";
import CustomerPicker from "@/components/CustomerPicker";
import ProductPicker from "@/components/ProductPicker";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface InvoiceFormProps {
  token: string;
  invoice?: Invoice;
  onSubmit: (payload: InvoicePayload) => Promise<void>;
}

type ItemDraft = {
  description: string;
  quantity: string;
  unitPrice: string;
  discountAmount: string;
};

function emptyItem(): ItemDraft {
  return { description: "", quantity: "1", unitPrice: "", discountAmount: "" };
}

// Customer selection is optional here (unlike Sales/Service Orders, which
// require a real Customer) — an invoice can go to a client who isn't in the
// system at all. CustomerPicker just pre-fills the plain fields below; those
// stay independently editable either way, since Invoice snapshots the
// name/email/phone/address rather than only joining through customerId.
export default function InvoiceForm({ token, invoice, onSubmit }: InvoiceFormProps) {
  const [customerId, setCustomerId] = useState<string | undefined>(invoice?.customerId ?? undefined);
  const [customerName, setCustomerName] = useState(invoice?.customerName ?? "");
  const [customerEmail, setCustomerEmail] = useState(invoice?.customerEmail ?? "");
  const [customerPhone, setCustomerPhone] = useState(invoice?.customerPhone ?? "");
  const [customerAddress, setCustomerAddress] = useState(invoice?.customerAddress ?? "");
  const [issueDate, setIssueDate] = useState(invoice?.issueDate ?? new Date().toISOString().slice(0, 10));
  const [dueDate, setDueDate] = useState(invoice?.dueDate ?? "");
  const [notes, setNotes] = useState(invoice?.notes ?? "");
  const [items, setItems] = useState<ItemDraft[]>(
    invoice
      ? invoice.items.map((i) => ({
          description: i.description,
          quantity: String(i.quantity),
          unitPrice: String(i.unitPrice),
          discountAmount: String(i.discountAmount),
        }))
      : [emptyItem()]
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function handlePickCustomer(customer: Customer) {
    setCustomerId(customer.id);
    setCustomerName(customer.fullName);
    setCustomerEmail(customer.email ?? "");
    setCustomerPhone(customer.phone ?? "");
  }

  function updateItem(index: number, patch: Partial<ItemDraft>) {
    setItems((prev) => prev.map((it, i) => (i === index ? { ...it, ...patch } : it)));
  }

  function addItem() {
    setItems((prev) => [...prev, emptyItem()]);
  }

  // Fills the single still-untouched row in place rather than appending a
  // duplicate blank line under it; every pick after that just appends.
  function addProduct(product: Product) {
    const draft: ItemDraft = {
      description: product.name,
      quantity: "1",
      unitPrice: String(product.sellingPrice),
      discountAmount: "",
    };
    setItems((prev) => {
      if (prev.length === 1 && !prev[0].description.trim() && !prev[0].unitPrice) {
        return [draft];
      }
      return [...prev, draft];
    });
  }

  function removeItem(index: number) {
    setItems((prev) => (prev.length > 1 ? prev.filter((_, i) => i !== index) : prev));
  }

  function lineSubtotal(item: ItemDraft): number {
    const qty = Number(item.quantity) || 0;
    const price = Number(item.unitPrice) || 0;
    const discount = Number(item.discountAmount) || 0;
    return Math.max(qty * price - discount, 0);
  }

  const total = items.reduce((sum, item) => sum + lineSubtotal(item), 0);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!customerName.trim()) {
      setError("A customer name is required.");
      return;
    }

    const itemPayloads: InvoiceItemPayload[] = items.map((it) => ({
      description: it.description,
      quantity: Number(it.quantity) || 0,
      unitPrice: Number(it.unitPrice) || 0,
      discountAmount: it.discountAmount ? Number(it.discountAmount) : undefined,
    }));
    if (itemPayloads.some((it) => !it.description.trim() || it.quantity < 1)) {
      setError("Every line item needs a description and a quantity of at least 1.");
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        customerId,
        customerName: customerName.trim(),
        customerEmail: customerEmail.trim() || undefined,
        customerPhone: customerPhone.trim() || undefined,
        customerAddress: customerAddress.trim() || undefined,
        issueDate,
        dueDate: dueDate || undefined,
        notes: notes.trim() || undefined,
        items: itemPayloads,
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
        <label className="text-sm font-medium text-ink-700">Find an existing customer (optional)</label>
        <CustomerPicker token={token} onSelect={handlePickCustomer} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Customer name" name="customerName" required value={customerName} onChange={setCustomerName} />
        <FormField label="Email" name="customerEmail" type="email" value={customerEmail} onChange={setCustomerEmail} />
        <FormField label="Phone" name="customerPhone" value={customerPhone} onChange={setCustomerPhone} />
        <FormField label="Address" name="customerAddress" value={customerAddress} onChange={setCustomerAddress} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Issue date" name="issueDate" type="date" required value={issueDate} onChange={setIssueDate} />
        <FormField label="Due date (optional)" name="dueDate" type="date" value={dueDate} onChange={setDueDate} />
      </div>

      <div className="flex flex-col gap-2">
        <label className="text-sm font-medium text-ink-700">Line items</label>
        <ProductPicker token={token} onSelect={addProduct} />
        {items.map((item, index) => (
          <div key={index} className="flex flex-col gap-2 rounded-lg border border-border p-3">
            <div className="flex items-start gap-2">
              <input
                value={item.description}
                onChange={(e) => updateItem(index, { description: e.target.value })}
                placeholder="Description"
                className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              {items.length > 1 && (
                <button
                  type="button"
                  onClick={() => removeItem(index)}
                  className="rounded-md p-2 text-ink-500 hover:bg-canvas hover:text-danger"
                  aria-label="Remove line"
                >
                  <Trash2 size={15} />
                </button>
              )}
            </div>
            <div className="grid grid-cols-3 gap-2">
              <input
                type="number"
                min={1}
                value={item.quantity}
                onChange={(e) => updateItem(index, { quantity: e.target.value })}
                placeholder="Qty"
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              <input
                type="number"
                step="0.01"
                min={0}
                value={item.unitPrice}
                onChange={(e) => updateItem(index, { unitPrice: e.target.value })}
                placeholder="Unit price"
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              <input
                type="number"
                step="0.01"
                min={0}
                value={item.discountAmount}
                onChange={(e) => updateItem(index, { discountAmount: e.target.value })}
                placeholder="Discount"
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
            </div>
            <p className="text-right text-xs text-ink-500">Line total: GH₵{lineSubtotal(item).toFixed(2)}</p>
          </div>
        ))}
        <button
          type="button"
          onClick={addItem}
          className="flex items-center justify-center gap-1.5 rounded-lg border border-dashed border-border py-2 text-sm font-medium text-ink-700 hover:bg-canvas"
        >
          <Plus size={14} /> Add line
        </button>
      </div>

      <div className="flex items-center justify-between rounded-lg bg-canvas px-3 py-2">
        <span className="text-sm font-medium text-ink-700">Total</span>
        <span className="tabular text-base font-semibold text-ink-900">GH₵{total.toFixed(2)}</span>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Notes (optional)</label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          placeholder="Payment terms, thank-you note, etc."
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? "Saving..." : invoice ? "Save changes" : "Create invoice"}
      </Button>
    </form>
  );
}
