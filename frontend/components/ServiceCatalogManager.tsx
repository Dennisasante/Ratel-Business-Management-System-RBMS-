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
  onUpdateBookingSettings: (
    id: string,
    settings: { bookableOnline: boolean; durationMinutes: number; maxConcurrentBookings: number }
  ) => Promise<void>;
}

export default function ServiceCatalogManager({
  items,
  serviceTypes,
  onCreate,
  onToggleActive,
  onUpdateBookingSettings,
}: ServiceCatalogManagerProps) {
  const [serviceTypeId, setServiceTypeId] = useState(serviceTypes[0]?.id ?? "");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("0");
  const [bookableOnline, setBookableOnline] = useState(false);
  const [durationMinutes, setDurationMinutes] = useState("30");
  const [maxConcurrentBookings, setMaxConcurrentBookings] = useState("1");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);

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
      });
      setName("");
      setPrice("0");
      setBookableOnline(false);
      setDurationMinutes("30");
      setMaxConcurrentBookings("1");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't add that catalog item.");
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
    return <p className="text-sm text-ink-500">Add a service type first (under &ldquo;Types&rdquo;) before building a price catalog.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={handleAdd} className="flex flex-col gap-3 rounded-lg border border-border p-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Type</label>
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
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
        <Button type="submit" disabled={submitting || !name.trim()} className="w-full">
          {submitting ? "Adding..." : (
            <>
              <Plus size={16} /> Add catalog item
            </>
          )}
        </Button>
      </form>

      {items.length === 0 ? (
        <p className="text-sm text-ink-500">No catalog items yet — add one above to speed up order entry.</p>
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
                    onClick={() => setEditingId(editingId === item.id ? null : item.id)}
                    className="text-sm font-medium text-accent-hover hover:underline"
                  >
                    {editingId === item.id ? "Close" : "Booking settings"}
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

              {editingId === item.id && (
                <BookingSettingsEditor
                  item={item}
                  onSave={async (settings) => {
                    await onUpdateBookingSettings(item.id, settings);
                    setEditingId(null);
                  }}
                  onCancel={() => setEditingId(null)}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function BookingSettingsEditor({
  item,
  onSave,
  onCancel,
}: {
  item: ServiceCatalogItem;
  onSave: (settings: { bookableOnline: boolean; durationMinutes: number; maxConcurrentBookings: number }) => Promise<void>;
  onCancel: () => void;
}) {
  const [bookableOnline, setBookableOnline] = useState(item.bookableOnline);
  const [durationMinutes, setDurationMinutes] = useState(String(item.durationMinutes));
  const [maxConcurrentBookings, setMaxConcurrentBookings] = useState(String(item.maxConcurrentBookings));
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
