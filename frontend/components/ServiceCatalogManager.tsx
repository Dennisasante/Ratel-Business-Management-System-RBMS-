"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { ApiError, ServiceCatalogItem, ServiceCatalogItemPayload, ServiceType } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";

interface ServiceCatalogManagerProps {
  items: ServiceCatalogItem[];
  serviceTypes: ServiceType[];
  onCreate: (payload: ServiceCatalogItemPayload) => Promise<void>;
  onToggleActive: (id: string, active: boolean) => Promise<void>;
}

export default function ServiceCatalogManager({ items, serviceTypes, onCreate, onToggleActive }: ServiceCatalogManagerProps) {
  const [serviceTypeId, setServiceTypeId] = useState(serviceTypes[0]?.id ?? "");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("0");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onCreate({ serviceTypeId, name, price: Number(price) || 0 });
      setName("");
      setPrice("0");
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
            <li key={item.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
              <div>
                <p className="text-sm font-medium text-ink-900">{item.name}</p>
                <p className="text-xs text-ink-500">
                  {item.serviceTypeName ?? "—"} · GH₵{item.price.toFixed(2)}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {!item.active && <Badge tone="neutral">Archived</Badge>}
                <button
                  onClick={() => handleToggle(item)}
                  disabled={togglingId === item.id}
                  className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {togglingId === item.id ? "Saving..." : item.active ? "Archive" : "Reactivate"}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
