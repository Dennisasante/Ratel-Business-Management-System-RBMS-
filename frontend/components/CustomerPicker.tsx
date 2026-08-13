"use client";

import { useEffect, useRef, useState } from "react";
import { api, Customer, CustomerPayload } from "@/lib/api";
import Modal from "@/components/Modal";
import CustomerForm from "@/components/CustomerForm";

interface CustomerPickerProps {
  token: string;
  onSelect: (customer: Customer) => void;
  placeholder?: string;
}

// Search-by-name-or-phone against GET /api/customers?search=, debounced, plus
// a "+ Add New Customer" row that opens the same CustomerForm used elsewhere.
// Every sale/order/booking now requires a real Customer, so this replaces the
// old "Walk-in customer" <select> fallbacks across those forms. Deliberately
// self-contained (owns its own search + quick-add state) so every caller just
// needs a token and an onSelect callback — remount via a changing `key` prop
// to reset it after a successful submit.
export default function CustomerPicker({ token, onSelect, placeholder = "Search by name or phone" }: CustomerPickerProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Customer[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<Customer | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
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
        const found = await api.listCustomers(token, { search: query.trim() });
        if (!cancelled) setResults(found);
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

  function pick(customer: Customer) {
    setSelected(customer);
    setQuery("");
    setResults([]);
    setOpen(false);
    onSelect(customer);
  }

  async function handleAddCustomer(payload: CustomerPayload) {
    const customer = await api.createCustomer(token, payload);
    setShowAddModal(false);
    pick(customer);
  }

  if (selected) {
    return (
      <div className="flex items-center justify-between rounded-lg border border-border bg-surface px-3 py-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-ink-900">{selected.fullName}</p>
          {selected.phone && <p className="truncate text-xs text-ink-500">{selected.phone}</p>}
        </div>
        <button
          type="button"
          onClick={() => setSelected(null)}
          className="shrink-0 text-xs font-medium text-accent-hover hover:underline"
        >
          Change
        </button>
      </div>
    );
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
      {open && (
        <div className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-lg border border-border bg-surface shadow-panel">
          {searching && <p className="px-3 py-2 text-xs text-ink-500">Searching...</p>}
          {!searching && query.trim() && results.length === 0 && (
            <p className="px-3 py-2 text-xs text-ink-500">No matches for &quot;{query.trim()}&quot;</p>
          )}
          {results.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => pick(c)}
              className="flex w-full flex-col items-start px-3 py-2 text-left text-sm hover:bg-canvas"
            >
              <span className="font-medium text-ink-900">{c.fullName}</span>
              {c.phone && <span className="text-xs text-ink-500">{c.phone}</span>}
            </button>
          ))}
          <button
            type="button"
            onClick={() => {
              setShowAddModal(true);
              setOpen(false);
            }}
            className="block w-full border-t border-border px-3 py-2 text-left text-sm font-medium text-accent-hover hover:bg-canvas"
          >
            + Add New Customer
          </button>
        </div>
      )}

      {showAddModal && (
        <Modal title="Add customer" onClose={() => setShowAddModal(false)}>
          <CustomerForm onSubmit={handleAddCustomer} />
        </Modal>
      )}
    </div>
  );
}
