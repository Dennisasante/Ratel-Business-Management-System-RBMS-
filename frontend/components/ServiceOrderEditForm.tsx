"use client";

import { useState } from "react";
import { ApiError, ServiceOrder, ServiceOrderUpdatePayload, UserSummary } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import ServiceOrderPhotoGallery from "@/components/ServiceOrderPhotoGallery";

interface ServiceOrderEditFormProps {
  order: ServiceOrder;
  staff: UserSummary[];
  onSubmit: (payload: ServiceOrderUpdatePayload) => Promise<void>;
}

// Edits notes/price/discount/assignee/scheduling only — type, customer and status
// change through their own flows.
export default function ServiceOrderEditForm({ order, staff, onSubmit }: ServiceOrderEditFormProps) {
  // Editing price/discount only makes sense for a single-item order — for a
  // multi-service order the backend rejects a price change outright (no
  // support yet for editing one line among several), so hide the fields
  // instead of letting the user hit that error.
  const singleItem = order.items.length <= 1;

  const [notes, setNotes] = useState(order.notes ?? "");
  const [basePrice, setBasePrice] = useState(String(order.price + order.discountAmount));
  const [price, setPrice] = useState(String(order.price));
  const [discount, setDiscountState] = useState(String(order.discountAmount));
  const [assignedStaffId, setAssignedStaffId] = useState(order.assignedStaffId ?? "");
  const [scheduledAt, setScheduledAt] = useState(order.scheduledAt ? order.scheduledAt.slice(0, 16) : "");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function setDiscount(value: string) {
    const base = Number(basePrice) || 0;
    const d = Math.min(Number(value) || 0, base);
    setDiscountState(value);
    setPrice(String(Math.max(base - d, 0)));
  }

  function setPriceManually(value: string) {
    setPrice(value);
    setBasePrice(value);
    setDiscountState("0");
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        notes: notes || undefined,
        price: singleItem ? Number(price) || 0 : undefined,
        discountAmount: singleItem ? Number(discount) || 0 : undefined,
        assignedStaffId: assignedStaffId || undefined,
        scheduledAt: scheduledAt ? new Date(scheduledAt).toISOString() : undefined,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {singleItem ? (
        <div className="grid grid-cols-2 gap-3">
          <FormField label="Price" name="price" type="number" value={price} onChange={setPriceManually} />
          <FormField label="Discount (optional)" name="discount" type="number" value={discount} onChange={setDiscount} />
        </div>
      ) : (
        <p className="rounded-lg border border-border bg-canvas px-3 py-2 text-xs text-ink-500">
          This order has {order.items.length} services — price/discount can&rsquo;t be edited per line yet. Total: GH₵
          {order.price.toFixed(2)}.
        </p>
      )}

      <FormField
        label="Schedule for (optional)"
        name="scheduledAt"
        type="datetime-local"
        value={scheduledAt}
        onChange={setScheduledAt}
      />

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Assigned staff (optional, tracking only)</label>
        <select
          value={assignedStaffId}
          onChange={(e) => setAssignedStaffId(e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          <option value="">Unassigned</option>
          {staff.map((s) => (
            <option key={s.id} value={s.id}>
              {s.fullName}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Notes</label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? "Saving..." : "Save changes"}
      </Button>

      <ServiceOrderPhotoGallery serviceOrderId={order.id} />
    </form>
  );
}
