"use client";

import { useState } from "react";
import { Plus, Calendar } from "lucide-react";
import { ApiError, ServiceCatalogItem, ServiceCatalogItemPayload, ServiceType } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";

interface ServiceCatalogManagerProps {
  items: ServiceCatalogItem[];
  serviceTypes: ServiceType[];
  onCreate: (payload: ServiceCatalogItemPayload) => Promise<void>;
  onToggleActive: (id: string, active: boolean) => Promise<void>;
  onEdit: (id: string, fields: { serviceTypeId: string; name: string; price: number }) => Promise<void>;
  onUpdateBookingSettings: (
    id: string,
    settings: {
      bookableOnline: boolean;
      durationMinutes: number;
      maxConcurrentBookings: number;
      requiresLocation: boolean;
      paymentPolicyOverride: "NONE" | "DEPOSIT" | "FULL" | "";
    }
  ) => Promise<void>;
}

const PAYMENT_POLICY_OPTIONS: { value: "" | "NONE" | "DEPOSIT" | "FULL"; label: string }[] = [
  { value: "", label: "Use business default" },
  { value: "NONE", label: "No payment required" },
  { value: "DEPOSIT", label: "Deposit required" },
  { value: "FULL", label: "Full payment required" },
];

export default function ServiceCatalogManager({
  items,
  serviceTypes,
  onCreate,
  onToggleActive,
  onEdit,
  onUpdateBookingSettings,
}: ServiceCatalogManagerProps) {
  const [serviceTypeId, setServiceTypeId] = useState(serviceTypes[0]?.id ?? "");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("0");
  const [bookableOnline, setBookableOnline] = useState(false);
  const [durationMinutes, setDurationMinutes] = useState("30");
  const [maxConcurrentBookings, setMaxConcurrentBookings] = useState("1");
  const [requiresLocation, setRequiresLocation] = useState(false);
  const [paymentPolicyOverride, setPaymentPolicyOverride] = useState<"" | "NONE" | "DEPOSIT" | "FULL">("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [openPanel, setOpenPanel] = useState<{ id: string; panel: "edit" | "booking" } | null>(null);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onCreate({
        serviceTypeId,
        name,
        price: Number(price) || 0,
        bookableOnline,
        durationMinutes: Number(durationMinutes) || 30,
        maxConcurrentBookings: Number(maxConcurrentBookings) || 1,
        requiresLocation,
        paymentPolicyOverride,
      });
      setName("");
      setPrice("0");
      setBookableOnline(false);
      setDurationMinutes("30");
      setMaxConcurrentBookings("1");
      setRequiresLocation(false);
      setPaymentPolicyOverride("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't add that service.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggle(item: ServiceCatalogItem) {
    setTogglingId(item.id);
    try {
      await onToggleActive(item.id, !item.active);
    } finally {
      setTogglingId(null);
    }
  }

  if (serviceTypes.length === 0) {
    return <p className="text-sm text-ink-500">Add a category first (under &ldquo;Categories&rdquo;) before adding services.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={handleAdd} className="flex flex-col gap-3 rounded-lg border border-border p-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Category</label>
            <select
              value={serviceTypeId}
              onChange={(e) => setServiceTypeId(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              {serviceTypes.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          <FormField label="Price" name="catalogPrice" type="number" value={price} onChange={setPrice} />
        </div>
        <FormField label="Name" name="catalogName" required value={name} onChange={setName} placeholder="e.g. Standard install" />

        <div className="flex items-center gap-2 rounded-lg bg-canvas px-3 py-2">
          <input
            id="catalogBookableOnline"
            type="checkbox"
            checked={bookableOnline}
            onChange={(e) => setBookableOnline(e.target.checked)}
            className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
          />
          <label htmlFor="catalogBookableOnline" className="text-sm text-ink-700">
            Bookable through the online widget
          </label>
        </div>

        {bookableOnline && (
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-3">
              <FormField
                label="Duration (minutes)"
                name="catalogDuration"
                type="number"
                value={durationMinutes}
                onChange={setDurationMinutes}
              />
              <FormField
                label="Max bookings at once"
                name="catalogCapacity"
                type="number"
                value={maxConcurrentBookings}
                onChange={setMaxConcurrentBookings}
              />
            </div>
            <div className="flex items-center gap-2 rounded-lg bg-canvas px-3 py-2">
              <input
                id="catalogRequiresLocation"
                type="checkbox"
                checked={requiresLocation}
                onChange={(e) => setRequiresLocation(e.target.checked)}
                className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
              />
              <label htmlFor="catalogRequiresLocation" className="text-sm text-ink-700">
                Requires customer location (home service)
              </label>
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">Payment requirement</label>
              <select
                value={paymentPolicyOverride}
                onChange={(e) => setPaymentPolicyOverride(e.target.value as "" | "NONE" | "DEPOSIT" | "FULL")}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                {PAYMENT_POLICY_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
        <Button type="submit" disabled={submitting || !name.trim()} className="w-full">
          {submitting ? "Adding..." : (
            <>
              <Plus size={16} /> Add service
            </>
          )}
        </Button>
      </form>

      {items.length === 0 ? (
        <p className="text-sm text-ink-500">No services yet — add one above to speed up order entry.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {items.map((item) => (
            <li key={item.id} className="rounded-lg border border-border px-3 py-2">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-ink-900">{item.name}</p>
                  <p className="text-xs text-ink-500">
                    {item.serviceTypeName ?? "—"} · GH₵{item.price.toFixed(2)}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {!item.active && <Badge tone="neutral">Archived</Badge>}
                  {item.bookableOnline && (
                    <Badge tone="accent">
                      <Calendar size={11} /> Bookable
                    </Badge>
                  )}
                  <button
                    onClick={() =>
                      setOpenPanel(openPanel?.id === item.id && openPanel.panel === "edit" ? null : { id: item.id, panel: "edit" })
                    }
                    className="text-sm font-medium text-accent-hover hover:underline"
                  >
                    {openPanel?.id === item.id && openPanel.panel === "edit" ? "Close" : "Edit"}
                  </button>
                  <button
                    onClick={() =>
                      setOpenPanel(openPanel?.id === item.id && openPanel.panel === "booking" ? null : { id: item.id, panel: "booking" })
                    }
                    className="text-sm font-medium text-accent-hover hover:underline"
                  >
                    {openPanel?.id === item.id && openPanel.panel === "booking" ? "Close" : "Booking settings"}
                  </button>
                  <button
                    onClick={() => handleToggle(item)}
                    disabled={togglingId === item.id}
                    className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {togglingId === item.id ? "Saving..." : item.active ? "Archive" : "Reactivate"}
                  </button>
                </div>
              </div>

              {openPanel?.id === item.id && openPanel.panel === "edit" && (
                <EditItemForm
                  item={item}
                  serviceTypes={serviceTypes}
                  onSave={async (fields) => {
                    await onEdit(item.id, fields);
                    setOpenPanel(null);
                  }}
                  onCancel={() => setOpenPanel(null)}
                />
              )}

              {openPanel?.id === item.id && openPanel.panel === "booking" && (
                <BookingSettingsEditor
                  item={item}
                  onSave={async (settings) => {
                    await onUpdateBookingSettings(item.id, settings);
                    setOpenPanel(null);
                  }}
                  onCancel={() => setOpenPanel(null)}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function EditItemForm({
  item,
  serviceTypes,
  onSave,
  onCancel,
}: {
  item: ServiceCatalogItem;
  serviceTypes: ServiceType[];
  onSave: (fields: { serviceTypeId: string; name: string; price: number }) => Promise<void>;
  onCancel: () => void;
}) {
  const [serviceTypeId, setServiceTypeId] = useState(item.serviceTypeId);
  const [name, setName] = useState(item.name);
  const [price, setPrice] = useState(String(item.price));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    if (!name.trim()) {
      setError("Name is required.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSave({ serviceTypeId, name: name.trim(), price: Number(price) || 0 });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mt-3 flex flex-col gap-3 border-t border-border pt-3">
      <div className="grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Category</label>
          <select
            value={serviceTypeId}
            onChange={(e) => setServiceTypeId(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          >
            {serviceTypes.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </div>
        <FormField label="Price" name="editPrice" type="number" value={price} onChange={setPrice} />
      </div>
      <FormField label="Name" name="editName" required value={name} onChange={setName} />
      {error && <p className="text-sm text-danger">{error}</p>}
      <div className="flex gap-2">
        <Button type="button" onClick={handleSave} disabled={saving} className="flex-1">
          {saving ? "Saving..." : "Save"}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  );
}

function BookingSettingsEditor({
  item,
  onSave,
  onCancel,
}: {
  item: ServiceCatalogItem;
  onSave: (settings: {
    bookableOnline: boolean;
    durationMinutes: number;
    maxConcurrentBookings: number;
    requiresLocation: boolean;
    paymentPolicyOverride: "NONE" | "DEPOSIT" | "FULL" | "";
  }) => Promise<void>;
  onCancel: () => void;
}) {
  const [bookableOnline, setBookableOnline] = useState(item.bookableOnline);
  const [durationMinutes, setDurationMinutes] = useState(String(item.durationMinutes));
  const [maxConcurrentBookings, setMaxConcurrentBookings] = useState(String(item.maxConcurrentBookings));
  const [requiresLocation, setRequiresLocation] = useState(item.requiresLocation);
  const [paymentPolicyOverride, setPaymentPolicyOverride] = useState<"" | "NONE" | "DEPOSIT" | "FULL">(
    item.paymentPolicyOverride ?? ""
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      await onSave({
        bookableOnline,
        durationMinutes: Number(durationMinutes) || 30,
        maxConcurrentBookings: Number(maxConcurrentBookings) || 1,
        requiresLocation,
        paymentPolicyOverride,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mt-3 flex flex-col gap-3 border-t border-border pt-3">
      <div className="flex items-center gap-2">
        <input
          id={`bookableOnline-${item.id}`}
          type="checkbox"
          checked={bookableOnline}
          onChange={(e) => setBookableOnline(e.target.checked)}
          className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
        />
        <label htmlFor={`bookableOnline-${item.id}`} className="text-sm text-ink-700">
          Bookable through the online widget
        </label>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <FormField label="Duration (minutes)" name="editDuration" type="number" value={durationMinutes} onChange={setDurationMinutes} />
        <FormField label="Max bookings at once" name="editCapacity" type="number" value={maxConcurrentBookings} onChange={setMaxConcurrentBookings} />
      </div>
      <div className="flex items-center gap-2">
        <input
          id={`requiresLocation-${item.id}`}
          type="checkbox"
          checked={requiresLocation}
          onChange={(e) => setRequiresLocation(e.target.checked)}
          className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
        />
        <label htmlFor={`requiresLocation-${item.id}`} className="text-sm text-ink-700">
          Requires customer location (home service)
        </label>
      </div>
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Payment requirement</label>
        <select
          value={paymentPolicyOverride}
          onChange={(e) => setPaymentPolicyOverride(e.target.value as "" | "NONE" | "DEPOSIT" | "FULL")}
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          {PAYMENT_POLICY_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      <div className="flex gap-2">
        <Button type="button" onClick={handleSave} disabled={saving} className="flex-1">
          {saving ? "Saving..." : "Save"}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
