"use client";

import { useState } from "react";
import { Plus, Pencil, Check, X, Trash2 } from "lucide-react";
import { ApiError, ProductCategory, ProductCategoryPayload, sortCategoriesHierarchically } from "@/lib/api";
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
  // "" = top-level category. Only top-level categories (parentId null) are valid choices
  // here — a subcategory can't itself have subcategories (single-level nesting only).
  const [parentId, setParentId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const [rowError, setRowError] = useState<{ id: string; message: string } | null>(null);
  const [rowBusy, setRowBusy] = useState<string | null>(null);

  const topLevelCategories = categories.filter((c) => !c.parentId);
  const ordered = sortCategoriesHierarchically(categories);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onCreate({ name, parentId: parentId || undefined });
      setName("");
      setParentId("");
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

  async function saveEdit(category: ProductCategory) {
    setRowBusy(category.id);
    setRowError(null);
    try {
      // Renaming never changes which parent (if any) the category already has.
      await onRename(category.id, { name: editValue, parentId: category.parentId ?? undefined });
      setEditingId(null);
    } catch (err) {
      setRowError({ id: category.id, message: err instanceof ApiError ? err.message : "Couldn't rename this category." });
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
      <form onSubmit={handleAdd} className="flex flex-col gap-2">
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <FormField label="New category" name="categoryName" value={name} onChange={setName} placeholder="e.g. Beverages" />
          </div>
          <Button type="submit" disabled={submitting || !name.trim()}>
            {submitting ? "Adding..." : <Plus size={16} />}
          </Button>
        </div>
        {topLevelCategories.length > 0 && (
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-ink-700">Subcategory of (optional)</label>
            <select
              value={parentId}
              onChange={(e) => setParentId(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              <option value="">None — top-level category</option>
              {topLevelCategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </form>
      {error && <p className="text-sm text-danger">{error}</p>}

      {categories.length === 0 ? (
        <p className="text-sm text-ink-500">No categories yet — add one above.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {ordered.map((c) => (
            <li
              key={c.id}
              className={`rounded-lg border border-border px-3 py-2 ${c.parentId ? "ml-5 border-dashed" : ""}`}
            >
              <div className="flex items-center justify-between gap-2">
                {editingId === c.id ? (
                  <input
                    autoFocus
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    className="flex-1 rounded-md border border-border px-2 py-1 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                ) : (
                  <span className="text-sm font-medium text-ink-900">
                    {c.parentId && <span className="mr-1 text-ink-400">—</span>}
                    {c.name}
                  </span>
                )}
                <span className="shrink-0 text-xs text-ink-500">
                  {c.productCount} product{c.productCount === 1 ? "" : "s"}
                  {!c.parentId && c.subcategoryCount > 0
                    ? `, ${c.subcategoryCount} subcategor${c.subcategoryCount === 1 ? "y" : "ies"}`
                    : ""}
                </span>
                <div className="flex shrink-0 items-center gap-2">
                  {editingId === c.id ? (
                    <>
                      <button
                        onClick={() => saveEdit(c)}
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
