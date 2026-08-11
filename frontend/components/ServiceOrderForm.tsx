"use client";

import { useState } from "react";
import { X } from "lucide-react";
import {
  ApiError,
  Customer,
  CustomerPayload,
  ServiceCatalogItem,
  ServiceOrderPayload,
  ServiceType,
  UserSummary,
} from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import Modal from "@/components/Modal";
import CustomerForm from "@/components/CustomerForm";

interface ServiceOrderFormProps {
  serviceTypes: ServiceType[];
  customers: Customer[];
  catalog: ServiceCatalogItem[];
  staff: UserSummary[];
  submitLabel: string;
  onSubmit: (payload: ServiceOrderPayload) => Promise<void>;
  onCreateCustomer: (payload: CustomerPayload) => Promise<Customer>;
}

interface CartLine {
  key: string;
  serviceTypeId: string;
  serviceTypeName: string;
  serviceCatalogId: string;
  serviceName: string;
  price: number;
  discount: number;
}

export default function ServiceOrderForm({
  serviceTypes,
  customers,
  catalog,
  staff,
  submitLabel,
  onSubmit,
  onCreateCustomer,
}: ServiceOrderFormProps) {
  const [pickerTypeId, setPickerTypeId] = useState(serviceTypes[0]?.id ?? "");
  const [pickerCatalogId, setPickerCatalogId] = useState("");
  const [pickerPrice, setPickerPrice] = useState("0");

  const [lines, setLines] = useState<CartLine[]>([]);

  const [customerId, setCustomerId] = useState("");
  const [notes, setNotes] = useState("");
  const [assignedStaffId, setAssignedStaffId] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");

  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showAddCustomer, setShowAddCustomer] = useState(false);

  const pickerCatalogOptions = catalog.filter((c) => c.serviceTypeId === pickerTypeId);

  function setPickerType(id: string) {
    setPickerTypeId(id);
    setPickerCatalogId("");
    setPickerPrice("0");
  }

  function pickCatalogItem(id: string) {
    setPickerCatalogId(id);
    const item = pickerCatalogOptions.find((c) => c.id === id);
    setPickerPrice(item ? String(item.price) : "0");
  }

  function addLine() {
    const type = serviceTypes.find((t) => t.id === pickerTypeId);
    if (!type) return;
    const catalogItem = pickerCatalogOptions.find((c) => c.id === pickerCatalogId);
    setLines((prev) => [
      ...prev,
      {
        key: `${Date.now()}-${prev.length}`,
        serviceTypeId: type.id,
        serviceTypeName: type.name,
        serviceCatalogId: pickerCatalogId,
        serviceName: catalogItem ? catalogItem.name : type.name,
        price: Number(pickerPrice) || 0,
        discount: 0,
      },
    ]);
    setPickerCatalogId("");
    setPickerPrice("0");
  }

  function removeLine(key: string) {
    setLines((prev) => prev.filter((l) => l.key !== key));
  }

  function setLineDiscount(key: string, value: string) {
    setLines((prev) =>
      prev.map((l) => (l.key === key ? { ...l, discount: Math.min(Number(value) || 0, l.price) } : l))
    );
  }

  async function handleAddCustomer(payload: CustomerPayload) {
    const customer = await onCreateCustomer(payload);
    setCustomerId(customer.id);
    setShowAddCustomer(false);
  }

  const total = lines.reduce((sum, l) => sum + Math.max(l.price - l.discount, 0), 0);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (lines.length === 0) {
      setError("Add at least one service.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        customerId: customerId || undefined,
        notes: notes || undefined,
        assignedStaffId: assignedStaffId || undefined,
        scheduledAt: scheduledAt ? new Date(scheduledAt).toISOString() : undefined,
        items: lines.map((l) => ({
          serviceTypeId: l.serviceTypeId,
          serviceCatalogId: l.serviceCatalogId || undefined,
          price: l.price,
          discountAmount: l.discount,
        })),
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium text-ink-700">Customer (optional)</label>
            <button
              type="button"
              onClick={() => setShowAddCustomer(true)}
              className="text-xs font-medium text-accent-hover hover:underline"
            >
              + New customer
            </button>
          </div>
          <select
            value={customerId}
            onChange={(e) => setCustomerId(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          >
            <option value="">No customer on file</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.fullName}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
          <label className="text-sm font-medium text-ink-700">Add a service</label>
          <div className="grid grid-cols-2 gap-2">
            <select
              value={pickerTypeId}
              onChange={(e) => setPickerType(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              {serviceTypes.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
            <select
              value={pickerCatalogId}
              onChange={(e) => pickCatalogItem(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              <option value="">Custom price</option>
              {pickerCatalogOptions.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} — GH₵{c.price.toFixed(2)}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <FormField label="Price" name="pickerPrice" type="number" value={pickerPrice} onChange={setPickerPrice} />
            </div>
            <Button type="button" variant="secondary" onClick={addLine} disabled={!pickerTypeId}>
              Add service
            </Button>
          </div>
        </div>

        {lines.length > 0 && (
          <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
            {lines.map((l) => (
              <div key={l.key} className="flex items-center justify-between gap-2 border-b border-border pb-2 text-sm last:border-0 last:pb-0">
                <div className="min-w-0">
                  <p className="truncate font-medium text-ink-900">{l.serviceName}</p>
                  <p className="text-xs text-ink-500">{l.serviceTypeName}</p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <input
                    type="number"
                    value={l.discount || ""}
                    onChange={(e) => setLineDiscount(l.key, e.target.value)}
                    placeholder="Discount"
                    className="w-20 rounded-lg border border-border bg-surface px-2 py-1.5 text-xs text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                  <span className="tabular w-16 text-right font-medium text-ink-900">
                    GH₵{Math.max(l.price - l.discount, 0).toFixed(2)}
                  </span>
                  <button type="button" onClick={() => removeLine(l.key)} className="text-ink-400 hover:text-danger">
                    <X size={14} />
                  </button>
                </div>
              </div>
            ))}
            <div className="flex justify-between pt-1 text-sm font-semibold text-ink-900">
              <span>Total</span>
              <span className="tabular">GH₵{total.toFixed(2)}</span>
            </div>
          </div>
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
            placeholder="What's wrong / what's needed"
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>

        {error && <p className="text-sm text-danger">{error}</p>}

        <Button type="submit" disabled={submitting} className="mt-1 w-full">
          {submitting ? "Saving..." : submitLabel}
        </Button>
      </form>

      {showAddCustomer && (
        <Modal title="Add customer" onClose={() => setShowAddCustomer(false)}>
          <CustomerForm onSubmit={handleAddCustomer} />
        </Modal>
      )}
    </>
  );
}
