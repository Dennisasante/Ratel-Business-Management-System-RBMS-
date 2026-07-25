"use client";

import { useState } from "react";
import { ApiError, MovementType, Product, StockAdjustmentPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface StockAdjustFormProps {
  product: Product;
  onSubmit: (payload: StockAdjustmentPayload) => Promise<void>;
}

const TYPES: { value: MovementType; label: string; hint: string }[] = [
  { value: "ADD", label: "Add stock", hint: "e.g. new delivery arrived" },
  { value: "REMOVE", label: "Remove stock", hint: "e.g. sold, damaged, given away" },
  { value: "ADJUST", label: "Set exact count", hint: "e.g. after a physical stock take" },
];

export default function StockAdjustForm({ product, onSubmit }: StockAdjustFormProps) {
  const [movementType, setMovementType] = useState<MovementType>("ADD");
  const [quantity, setQuantity] = useState("1");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({ movementType, quantity: Number(quantity), note: note || undefined });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <p className="text-sm text-ink-500">
        Current stock: <span className="tabular font-semibold text-ink-900">{product.quantity}</span>
      </p>

      <div className="flex flex-col gap-2">
        {TYPES.map((t) => (
          <label
            key={t.value}
            className={`flex cursor-pointer items-start gap-2 rounded-lg border p-2.5 text-sm transition ${
              movementType === t.value ? "border-accent bg-accent-soft" : "border-border hover:bg-canvas"
            }`}
          >
            <input
              type="radio"
              name="movementType"
              className="mt-1 accent-accent"
              checked={movementType === t.value}
              onChange={() => setMovementType(t.value)}
            />
            <span>
              <span className="block font-medium text-ink-900">{t.label}</span>
              <span className="block text-xs text-ink-500">{t.hint}</span>
            </span>
          </label>
        ))}
      </div>

      <FormField
        label={movementType === "ADJUST" ? "New total quantity" : "Quantity"}
        name="quantity"
        type="number"
        required
        value={quantity}
        onChange={setQuantity}
      />

      <FormField label="Note (optional)" name="note" value={note} onChange={setNote} />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : "Save"}
      </Button>
    </form>
  );
}
