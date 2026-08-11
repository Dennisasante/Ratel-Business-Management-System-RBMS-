"use client";

import { useState } from "react";
import { ApiError, ServiceCatalogItemPayload, ServiceType } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface QuickServiceCatalogFormProps {
  serviceTypes: ServiceType[];
  onSubmit: (payload: ServiceCatalogItemPayload) => Promise<void>;
}

// Deliberately minimal compared to the full catalog editor (no booking
// settings) — this is for the "need a new service mid-sale" case, not full
// catalog management. Service types must already exist; this form doesn't
// create one, matching how the full catalog manager also requires picking
// an existing type.
export default function QuickServiceCatalogForm({ serviceTypes, onSubmit }: QuickServiceCatalogFormProps) {
  const [serviceTypeId, setServiceTypeId] = useState(serviceTypes[0]?.id ?? "");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({ serviceTypeId, name, price: Number(price) || 0 });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (serviceTypes.length === 0) {
    return <p className="text-sm text-ink-500">Add a category in the Service Catalog first, then come back here.</p>;
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">
          Category <span className="text-danger">*</span>
        </label>
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

      <FormField label="Service name" name="name" required value={name} onChange={setName} />
      <FormField label="Price (GH₵)" name="price" type="number" required value={price} onChange={setPrice} />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : "Add service"}
      </Button>
    </form>
  );
}
