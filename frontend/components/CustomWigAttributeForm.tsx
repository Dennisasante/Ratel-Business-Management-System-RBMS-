"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { ApiError, AttributeSelectionType, CustomItemAttribute, CustomItemAttributePayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface CustomWigAttributeFormProps {
  initial?: CustomItemAttribute;
  submitLabel: string;
  onSubmit: (payload: CustomItemAttributePayload) => Promise<void>;
}

interface OptionRow {
  label: string;
  priceModifier: string;
  requiresManualQuote: boolean;
}

export default function CustomWigAttributeForm({ initial, submitLabel, onSubmit }: CustomWigAttributeFormProps) {
  const [name, setName] = useState(initial?.name ?? "");
  const [selectionType, setSelectionType] = useState<AttributeSelectionType>(initial?.selectionType ?? "SINGLE");
  const [stepGroup, setStepGroup] = useState(initial?.stepGroup ?? "");
  const [options, setOptions] = useState<OptionRow[]>(
    initial?.options.length
      ? initial.options.map((o) => ({ label: o.label, priceModifier: String(o.priceModifier), requiresManualQuote: o.requiresManualQuote }))
      : [{ label: "", priceModifier: "0", requiresManualQuote: false }]
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function updateOption(index: number, field: "label" | "priceModifier", value: string) {
    setOptions((rows) => rows.map((r, i) => (i === index ? { ...r, [field]: value } : r)));
  }

  function toggleManualQuote(index: number) {
    setOptions((rows) => rows.map((r, i) => (i === index ? { ...r, requiresManualQuote: !r.requiresManualQuote } : r)));
  }

  function addOption() {
    setOptions((rows) => [...rows, { label: "", priceModifier: "0", requiresManualQuote: false }]);
  }

  function removeOption(index: number) {
    setOptions((rows) => rows.filter((_, i) => i !== index));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const cleanOptions = options
      .map((o) => ({ label: o.label.trim(), priceModifier: Number(o.priceModifier) || 0, requiresManualQuote: o.requiresManualQuote }))
      .filter((o) => o.label);

    if (!name.trim()) {
      setError("Attribute name is required.");
      return;
    }
    if (cleanOptions.length === 0) {
      setError("Add at least one option.");
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        name: name.trim(),
        selectionType,
        stepGroup: stepGroup.trim() || null,
        options: cleanOptions,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField label="Attribute name" name="attributeName" required value={name} onChange={setName} placeholder="e.g. Length" />

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-ink-700">Selection type</label>
        <div className="flex gap-2">
          {(["SINGLE", "MULTIPLE"] as const).map((type) => (
            <button
              key={type}
              type="button"
              onClick={() => setSelectionType(type)}
              className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium transition ${
                selectionType === type
                  ? "border-accent bg-accent-soft text-accent-hover"
                  : "border-border bg-surface text-ink-700 hover:bg-canvas"
              }`}
            >
              {type === "SINGLE" ? "Single choice" : "Multiple choice"}
            </button>
          ))}
        </div>
        <p className="text-xs text-ink-500">
          {selectionType === "SINGLE"
            ? "Customers pick exactly one option."
            : "Customers can pick more than one option (e.g. finishing add-ons)."}
        </p>
      </div>

      <FormField
        label="Group with (optional)"
        name="stepGroup"
        value={stepGroup}
        onChange={setStepGroup}
        placeholder="e.g. Choose your hair"
      />
      <p className="-mt-3 text-xs text-ink-500">Attributes sharing the same group appear together on one step of the customer builder.</p>

      <div className="flex flex-col gap-2">
        <label className="text-sm font-medium text-ink-700">Options</label>
        {options.map((row, i) => (
          <div key={i} className="flex flex-col gap-1.5 rounded-lg border border-border p-2.5">
            <div className="flex items-center gap-2">
              <input
                value={row.label}
                onChange={(e) => updateOption(i, "label", e.target.value)}
                placeholder="e.g. 20 inches"
                className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              <input
                type="number"
                value={row.priceModifier}
                onChange={(e) => updateOption(i, "priceModifier", e.target.value)}
                placeholder="+0"
                className="w-24 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              <button
                type="button"
                onClick={() => removeOption(i)}
                disabled={options.length === 1}
                className="rounded-md p-2 text-danger hover:bg-danger-soft disabled:cursor-not-allowed disabled:opacity-40"
                aria-label="Remove option"
              >
                <Trash2 size={15} />
              </button>
            </div>
            <label className="flex items-center gap-1.5 text-xs text-ink-600">
              <input type="checkbox" checked={row.requiresManualQuote} onChange={() => toggleManualQuote(i)} className="accent-accent" />
              Requires manual quote (e.g. custom colour) — price shown to customers as an estimate
            </label>
          </div>
        ))}
        <button
          type="button"
          onClick={addOption}
          className="flex items-center gap-1.5 self-start text-xs font-medium text-accent-hover hover:underline"
        >
          <Plus size={13} /> Add option
        </button>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" disabled={submitting} className="mt-1 w-full">
        {submitting ? "Saving..." : submitLabel}
      </Button>
    </form>
  );
}
