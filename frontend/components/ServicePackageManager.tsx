"use client";

import { useState } from "react";
import { Plus, Trash2, Package as PackageIcon } from "lucide-react";
import { ApiError, ServiceCatalogItem, ServicePackage, ServicePackagePayload, ServiceType } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";

interface ServicePackageManagerProps {
  packages: ServicePackage[];
  serviceTypes: ServiceType[];
  catalogItems: ServiceCatalogItem[];
  onCreate: (payload: ServicePackagePayload) => Promise<void>;
  onUpdate: (id: string, payload: ServicePackagePayload) => Promise<void>;
  onToggleActive: (id: string, active: boolean) => Promise<void>;
}

interface ItemRow {
  serviceCatalogId: string;
  quantity: string;
}

const PAYMENT_POLICY_OPTIONS: { value: "" | "NONE" | "DEPOSIT" | "FULL"; label: string }[] = [
  { value: "", label: "Use business default" },
  { value: "NONE", label: "No payment required" },
  { value: "DEPOSIT", label: "Deposit required" },
  { value: "FULL", label: "Full payment required" },
];

export default function ServicePackageManager({
  packages,
  serviceTypes,
  catalogItems,
  onCreate,
  onUpdate,
  onToggleActive,
}: ServicePackageManagerProps) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  if (serviceTypes.length === 0) {
    return <p className="text-sm text-ink-500">Add a service type first (under &ldquo;Types&rdquo;) before building a package.</p>;
  }
  if (catalogItems.length === 0) {
    return <p className="text-sm text-ink-500">Add some catalog items first (under &ldquo;Catalog&rdquo;) — a package bundles existing services together.</p>;
  }

  async function handleToggle(pkg: ServicePackage) {
    setTogglingId(pkg.id);
    try {
      await onToggleActive(pkg.id, !pkg.active);
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <PackageForm
        serviceTypes={serviceTypes}
        catalogItems={catalogItems}
        submitLabel="Add package"
        onSubmit={onCreate}
      />

      {packages.length === 0 ? (
        <p className="text-sm text-ink-500">No packages yet — bundle a few catalog items above at a special price.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {packages.map((pkg) => (
            <li key={pkg.id} className="rounded-lg border border-border px-3 py-2">
              <div className="flex items-center justify-between">
                <div>
                  <p className="flex items-center gap-1.5 text-sm font-medium text-ink-900">
                    <PackageIcon size={13} className="text-accent-hover" /> {pkg.name}
                  </p>
                  <p className="text-xs text-ink-500">
                    {pkg.serviceTypeName ?? "—"} · GH₵{pkg.price.toFixed(2)} · {pkg.items.length} item{pkg.items.length === 1 ? "" : "s"}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {!pkg.active && <Badge tone="neutral">Archived</Badge>}
                  {pkg.bookableOnline && <Badge tone="accent">Bookable</Badge>}
                  <button
                    onClick={() => setEditingId(editingId === pkg.id ? null : pkg.id)}
                    className="text-sm font-medium text-accent-hover hover:underline"
                  >
                    {editingId === pkg.id ? "Close" : "Edit"}
                  </button>
                  <button
                    onClick={() => handleToggle(pkg)}
                    disabled={togglingId === pkg.id}
                    className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {togglingId === pkg.id ? "Saving..." : pkg.active ? "Archive" : "Reactivate"}
                  </button>
                </div>
              </div>

              {editingId === pkg.id && (
                <div className="mt-3 border-t border-border pt-3">
                  <PackageForm
                    initial={pkg}
                    serviceTypes={serviceTypes}
                    catalogItems={catalogItems}
                    submitLabel="Save changes"
                    onSubmit={async (payload) => {
                      await onUpdate(pkg.id, payload);
                      setEditingId(null);
                    }}
                  />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function PackageForm({
  initial,
  serviceTypes,
  catalogItems,
  submitLabel,
  onSubmit,
}: {
  initial?: ServicePackage;
  serviceTypes: ServiceType[];
  catalogItems: ServiceCatalogItem[];
  submitLabel: string;
  onSubmit: (payload: ServicePackagePayload) => Promise<void>;
}) {
  const [serviceTypeId, setServiceTypeId] = useState(initial?.serviceTypeId ?? serviceTypes[0]?.id ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [price, setPrice] = useState(initial ? String(initial.price) : "0");
  const [bookableOnline, setBookableOnline] = useState(initial?.bookableOnline ?? false);
  const [durationMinutes, setDurationMinutes] = useState(initial ? String(initial.durationMinutes) : "60");
  const [maxConcurrentBookings, setMaxConcurrentBookings] = useState(initial ? String(initial.maxConcurrentBookings) : "1");
  const [paymentPolicyOverride, setPaymentPolicyOverride] = useState<"" | "NONE" | "DEPOSIT" | "FULL">(
    initial?.paymentPolicyOverride ?? ""
  );
  const [items, setItems] = useState<ItemRow[]>(
    initial?.items.length
      ? initial.items.map((i) => ({ serviceCatalogId: i.serviceCatalogId, quantity: String(i.quantity) }))
      : [{ serviceCatalogId: catalogItems[0]?.id ?? "", quantity: "1" }]
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function updateItem(index: number, field: "serviceCatalogId" | "quantity", value: string) {
    setItems((rows) => rows.map((r, i) => (i === index ? { ...r, [field]: value } : r)));
  }

  function addItem() {
    setItems((rows) => [...rows, { serviceCatalogId: catalogItems[0]?.id ?? "", quantity: "1" }]);
  }

  function removeItem(index: number) {
    setItems((rows) => rows.filter((_, i) => i !== index));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const cleanItems = items
      .filter((r) => r.serviceCatalogId)
      .map((r) => ({ serviceCatalogId: r.serviceCatalogId, quantity: Number(r.quantity) || 1 }));

    if (!name.trim()) {
      setError("Package name is required.");
      return;
    }
    if (cleanItems.length === 0) {
      setError("Add at least one item to the package.");
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        serviceTypeId,
        name: name.trim(),
        description: description.trim() || undefined,
        price: Number(price) || 0,
        bookableOnline,
        durationMinutes: Number(durationMinutes) || 60,
        maxConcurrentBookings: Number(maxConcurrentBookings) || 1,
        items: cleanItems,
        paymentPolicyOverride,
      });
      if (!initial) {
        setName("");
        setDescription("");
        setPrice("0");
        setBookableOnline(false);
        setDurationMinutes("60");
        setMaxConcurrentBookings("1");
        setItems([{ serviceCatalogId: catalogItems[0]?.id ?? "", quantity: "1" }]);
        setPaymentPolicyOverride("");
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save that package.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-lg border border-border p-3">
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
        <FormField label="Price" name="packagePrice" type="number" value={price} onChange={setPrice} />
      </div>
      <FormField label="Name" name="packageName" required value={name} onChange={setName} placeholder="e.g. Bridal Package - Basic" />

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Description (optional)</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          placeholder="What's included, in the customer's own words"
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>

      <div className="flex flex-col gap-2">
        <label className="text-sm font-medium text-ink-700">Included services</label>
        {items.map((row, i) => (
          <div key={i} className="flex items-center gap-2">
            <select
              value={row.serviceCatalogId}
              onChange={(e) => updateItem(i, "serviceCatalogId", e.target.value)}
              className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              {catalogItems.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <input
              type="number"
              min={1}
              value={row.quantity}
              onChange={(e) => updateItem(i, "quantity", e.target.value)}
              className="w-20 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
            <button
              type="button"
              onClick={() => removeItem(i)}
              disabled={items.length === 1}
              className="rounded-md p-2 text-danger hover:bg-danger-soft disabled:cursor-not-allowed disabled:opacity-40"
              aria-label="Remove item"
            >
              <Trash2 size={15} />
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={addItem}
          className="flex items-center gap-1.5 self-start text-xs font-medium text-accent-hover hover:underline"
        >
          <Plus size={13} /> Add item
        </button>
      </div>

      <div className="flex items-center gap-2 rounded-lg bg-canvas px-3 py-2">
        <input
          id={`packageBookable-${initial?.id ?? "new"}`}
          type="checkbox"
          checked={bookableOnline}
          onChange={(e) => setBookableOnline(e.target.checked)}
          className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
        />
        <label htmlFor={`packageBookable-${initial?.id ?? "new"}`} className="text-sm text-ink-700">
          Bookable through the online widget
        </label>
      </div>

      {bookableOnline && (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-2 gap-3">
            <FormField label="Duration (minutes)" name="packageDuration" type="number" value={durationMinutes} onChange={setDurationMinutes} />
            <FormField label="Max bookings at once" name="packageCapacity" type="number" value={maxConcurrentBookings} onChange={setMaxConcurrentBookings} />
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
        {submitting ? "Saving..." : (
          <>
            <Plus size={16} /> {submitLabel}
          </>
        )}
      </Button>
    </form>
  );
}
