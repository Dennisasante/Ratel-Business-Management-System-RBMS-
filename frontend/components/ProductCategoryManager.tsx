"use client";

import { useState } from "react";
import { Plus, Pencil, Check, X, Trash2 } from "lucide-react";
import { ApiError, ProductCategory, ProductCategoryPayload } from "@/lib/api";
import FormField from "@/components/FormField";
import Button from "@/components/ui/Button";

interface ProductCategoryManagerProps {
  categories: ProductCategory[];
  onCreate: (payload: ProductCategoryPayload) => Promise<void>;
  onRename: (id: string, payload: ProductCategoryPayload) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

export default function ProductCategoryManager({ categories, onCreate, onRename, onDelete }: ProductCategoryManagerProps) {
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const [rowError, setRowError] = useState<{ id: string; message: string } | null>(null);
  const [rowBusy, setRowBusy] = useState<string | null>(null);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onCreate({ name });
      setName("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't add that category.");
    } finally {
      setSubmitting(false);
    }
  }

  function startEdit(category: ProductCategory) {
    setEditingId(category.id);
    setEditValue(category.name);
    setRowError(null);
  }

  async function saveEdit(id: string) {
    setRowBusy(id);
    setRowError(null);
    try {
      await onRename(id, { name: editValue });
      setEditingId(null);
    } catch (err) {
      setRowError({ id, message: err instanceof ApiError ? err.message : "Couldn't rename this category." });
    } finally {
      setRowBusy(null);
    }
  }

  async function handleDelete(category: ProductCategory) {
    setRowBusy(category.id);
    setRowError(null);
    try {
      await onDelete(category.id);
    } catch (err) {
      setRowError({ id: category.id, message: err instanceof ApiError ? err.message : "Couldn't remove this category." });
    } finally {
      setRowBusy(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={handleAdd} className="flex items-end gap-2">
        <div className="flex-1">
          <FormField label="New category" name="categoryName" value={name} onChange={setName} placeholder="e.g. Wigs" />
        </div>
        <Button type="submit" disabled={submitting || !name.trim()}>
          {submitting ? "Adding..." : <Plus size={16} />}
        </Button>
      </form>
      {error && <p className="text-sm text-danger">{error}</p>}

      {categories.length === 0 ? (
        <p className="text-sm text-ink-500">No categories yet — add one above.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {categories.map((c) => (
            <li key={c.id} className="rounded-lg border border-border px-3 py-2">
              <div className="flex items-center justify-between gap-2">
                {editingId === c.id ? (
                  <input
                    autoFocus
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    className="flex-1 rounded-md border border-border px-2 py-1 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                ) : (
                  <span className="text-sm font-medium text-ink-900">{c.name}</span>
                )}
                <span className="shrink-0 text-xs text-ink-500">
                  {c.productCount} product{c.productCount === 1 ? "" : "s"}
                </span>
                <div className="flex shrink-0 items-center gap-2">
                  {editingId === c.id ? (
                    <>
                      <button
                        onClick={() => saveEdit(c.id)}
                        disabled={rowBusy === c.id || !editValue.trim()}
                        className="rounded-md p-1 text-success hover:bg-success-soft disabled:cursor-not-allowed disabled:opacity-50"
                        aria-label="Save"
                      >
                        <Check size={15} />
                      </button>
                      <button
                        onClick={() => setEditingId(null)}
                        className="rounded-md p-1 text-ink-500 hover:bg-canvas"
                        aria-label="Cancel"
                      >
                        <X size={15} />
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        onClick={() => startEdit(c)}
                        className="rounded-md p-1 text-ink-500 hover:bg-canvas hover:text-ink-900"
                        aria-label="Rename"
                      >
                        <Pencil size={15} />
                      </button>
                      <button
                        onClick={() => handleDelete(c)}
                        disabled={rowBusy === c.id}
                        className="rounded-md p-1 text-danger hover:bg-danger-soft disabled:cursor-not-allowed disabled:opacity-50"
                        aria-label="Remove"
                      >
                        <Trash2 size={15} />
                      </button>
                    </>
                  )}
                </div>
              </div>
              {rowError?.id === c.id && <p className="mt-1.5 text-xs text-danger">{rowError.message}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
