"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Truck, Plus, Search } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, Supplier, SupplierPayload } from "@/lib/api";
import Modal from "@/components/Modal";
import SupplierForm from "@/components/SupplierForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

export default function SuppliersPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [fetching, setFetching] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [search, setSearch] = useState("");

  const loadSuppliers = useCallback(async () => {
    if (!session) return;
    setSuppliers(await api.listSuppliers(session.token));
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadSuppliers().finally(() => setFetching(false));
  }, [session, loadSuppliers]);

  async function handleCreate(payload: SupplierPayload) {
    if (!session) return;
    await api.createSupplier(session.token, payload);
    await loadSuppliers();
    setShowAdd(false);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const visibleSuppliers = suppliers.filter((s) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return s.name.toLowerCase().includes(q) || (s.phone ?? "").includes(q) || (s.email ?? "").toLowerCase().includes(q);
  });

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Suppliers"
        subtitle={`${suppliers.length} supplier${suppliers.length === 1 ? "" : "s"}`}
        actions={
          <Button onClick={() => setShowAdd(true)}>
            <Plus size={16} /> Add supplier
          </Button>
        }
      />

      {suppliers.length > 0 && (
        <div className="relative max-w-sm">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name, phone, or email"
            className="w-full rounded-lg border border-border bg-surface py-2 pl-9 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
      )}

      <Card>
        {fetching ? (
          <TableSkeleton cols={3} />
        ) : suppliers.length === 0 ? (
          <EmptyState
            icon={Truck}
            title="No suppliers yet"
            description="Add a supplier to start creating purchase orders against them."
            action={
              <Button onClick={() => setShowAdd(true)}>
                <Plus size={16} /> Add supplier
              </Button>
            }
          />
        ) : visibleSuppliers.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">No suppliers match this search.</p>
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Name</Th>
                <Th>Contact</Th>
                <Th>Notes</Th>
              </Tr>
            </THead>
            <TBody>
              {visibleSuppliers.map((s) => (
                <Tr key={s.id}>
                  <Td className="font-medium">{s.name}</Td>
                  <Td className="text-ink-500">{s.phone ?? s.email ?? "—"}</Td>
                  <Td className="text-ink-500">{s.notes ?? "—"}</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {showAdd && (
        <Modal title="Add supplier" onClose={() => setShowAdd(false)}>
          <SupplierForm onSubmit={handleCreate} />
        </Modal>
      )}
    </div>
  );
}
