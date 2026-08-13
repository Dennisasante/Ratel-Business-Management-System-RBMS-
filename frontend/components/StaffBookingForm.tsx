"use client";

import { useState } from "react";
import { ApiError, CreateStaffBookingPayload, ServiceCatalogItem, ServicePackage, StaffMember } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import CustomerPicker from "@/components/CustomerPicker";

interface StaffBookingFormProps {
  token: string;
  catalog: ServiceCatalogItem[];
  packages: ServicePackage[];
  staff: StaffMember[];
  onSubmit: (payload: CreateStaffBookingPayload) => Promise<void>;
}

// "catalog:<id>" or "package:<id>" — a single select covering both, same
// idea as the public booking widget's combined service picker.
function parseServiceKey(key: string): { serviceCatalogId?: string; packageId?: string } {
  const [kind, id] = key.split(":");
  return kind === "package" ? { packageId: id } : { serviceCatalogId: id };
}

export default function StaffBookingForm({ token, catalog, packages, staff, onSubmit }: StaffBookingFormProps) {
  const firstKey = catalog[0] ? `catalog:${catalog[0].id}` : packages[0] ? `package:${packages[0].id}` : "";
  const [serviceKey, setServiceKey] = useState(firstKey);
  const [customerId, setCustomerId] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [customerLocation, setCustomerLocation] = useState("");
  const [assignedStaffId, setAssignedStaffId] = useState("");
  const [paymentStatus, setPaymentStatus] = useState<"UNPAID" | "PAID" | "PAY_IN_PERSON">("UNPAID");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const selectedCatalogItem = serviceKey.startsWith("catalog:")
    ? catalog.find((c) => c.id === serviceKey.slice("catalog:".length))
    : undefined;
  const requiresLocation = selectedCatalogItem?.requiresLocation ?? false;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!customerId) {
      setError("Pick a customer first.");
      return;
    }
    if (!serviceKey) {
      setError("Add a service first.");
      return;
    }
    if (!scheduledAt) {
      setError("Choose a date and time.");
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit({
        ...parseServiceKey(serviceKey),
        customerId,
        scheduledAt: new Date(scheduledAt).toISOString(),
        notes: notes || undefined,
        customerLocation: requiresLocation ? customerLocation || undefined : undefined,
        assignedStaffId: assignedStaffId || undefined,
        paymentStatus,
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
        <label className="text-sm font-medium text-ink-700">
          Service <span className="text-danger">*</span>
        </label>
        <select
          value={serviceKey}
          onChange={(e) => setServiceKey(e.target.value)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          {catalog.map((c) => (
            <option key={`catalog:${c.id}`} value={`catalog:${c.id}`}>
              {c.name} — GH₵{c.price.toFixed(2)}
            </option>
          ))}
          {packages.map((p) => (
            <option key={`package:${p.id}`} value={`package:${p.id}`}>
              {p.name} (package) — GH₵{p.price.toFixed(2)}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">
          Customer <span className="text-danger">*</span>
        </label>
        <CustomerPicker token={token} onSelect={(c) => setCustomerId(c.id)} />
      </div>

      <FormField
        label="Date & time"
        name="scheduledAt"
        type="datetime-local"
        required
        value={scheduledAt}
        onChange={setScheduledAt}
      />

      {requiresLocation && (
        <FormField label="Location" name="customerLocation" required value={customerLocation} onChange={setCustomerLocation} />
      )}

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Assigned staff (optional)</label>
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
        <label className="text-sm font-medium text-ink-700">Payment</label>
        <select
          value={paymentStatus}
          onChange={(e) => setPaymentStatus(e.target.value as typeof paymentStatus)}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          <option value="UNPAID">Unpaid</option>
          <option value="PAID">Paid</option>
          <option value="PAY_IN_PERSON">Pay in person</option>
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Notes</label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={2}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Creating..." : "Create booking"}
      </Button>
    </form>
  );
}
