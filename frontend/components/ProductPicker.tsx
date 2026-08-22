"use client";

import { useEffect, useRef, useState } from "react";
import { api, Product } from "@/lib/api";

interface ProductPickerProps {
  token: string;
  onSelect: (product: Product) => void;
  placeholder?: string;
}

// Search-as-you-type against GET /api/products?search=, same debounced
// dropdown shape as CustomerPicker — but unlike a customer, picking a
// product doesn't "select" a persistent value here; it appends a new
// invoice line and the box clears itself, ready to add another. Invoices
// stay deliberately independent of real inventory (no stock is touched,
// no productId is stored on the line) — this is purely a convenience so
// staff don't have to retype a name/price they already have on file.
export default function ProductPicker({ token, onSelect, placeholder = "Search inventory to add a line..." }: ProductPickerProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Product[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open || !query.trim()) {
      setResults([]);
      setSearching(false);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const timeout = setTimeout(async () => {
      try {
        const found = await api.listProducts(token, { search: query.trim() });
        if (!cancelled) setResults(found.slice(0, 8));
      } catch {
        if (!cancelled) setResults([]);
      } finally {
        if (!cancelled) setSearching(false);
      }
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timeout);
    };
  }, [query, open, token]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  function pick(product: Product) {
    onSelect(product);
    setQuery("");
    setResults([]);
    setOpen(false);
  }

  return (
    <div ref={containerRef} className="relative">
      <input
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        placeholder={placeholder}
        className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
      />
      {open && query.trim() && (
        <div className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-lg border border-border bg-surface shadow-panel">
          {searching && <p className="px-3 py-2 text-xs text-ink-500">Searching...</p>}
          {!searching && results.length === 0 && (
            <p className="px-3 py-2 text-xs text-ink-500">No products match &quot;{query.trim()}&quot;</p>
          )}
          {results.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => pick(p)}
              className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-canvas"
            >
              <span className="min-w-0 truncate font-medium text-ink-900">{p.name}</span>
              <span className="shrink-0 tabular text-xs text-ink-500">GH₵{p.sellingPrice.toFixed(2)}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
