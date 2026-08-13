"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Users, Plus, Search } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, Customer, CustomerPayload } from "@/lib/api";
import Modal from "@/components/Modal";
import CustomerForm from "@/components/CustomerForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

export default function CustomersPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [fetching, setFetching] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [search, setSearch] = useState("");

  const loadCustomers = useCallback(async () => {
    if (!session) return;
    setCustomers(await api.listCustomers(session.token, { search: search || undefined }));
  }, [session, search]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadCustomers().finally(() => setFetching(false));
  }, [session, loadCustomers]);

  async function handleCreate(payload: CustomerPayload) {
    if (!session) return;
    await api.createCustomer(session.token, payload);
    await loadCustomers();
    setShowAdd(false);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Customers"
        subtitle={`${customers.length} customer${customers.length === 1 ? "" : "s"}`}
        actions={
          <Button onClick={() => setShowAdd(true)}>
            <Plus size={16} /> Add customer
          </Button>
        }
      />

      <div className="relative max-w-sm">
        <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name, phone, or email"
          className="w-full rounded-lg border border-border bg-surface py-2 pl-9 pr-3 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>

      <Card>
        {fetching ? (
          <TableSkeleton cols={5} />
        ) : customers.length === 0 ? (
          <EmptyState
            icon={Users}
            title={search ? "No matching customers" : "No customers yet"}
            description={
              search
                ? "Try a different name, phone number, or email."
                : "Add a customer to start tracking their purchase history."
            }
            action={
              search ? undefined : (
                <Button onClick={() => setShowAdd(true)}>
                  <Plus size={16} /> Add customer
                </Button>
              )
            }
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Name</Th>
                <Th>Contact</Th>
                <Th>Source</Th>
                <Th>Purchases</Th>
                <Th className="text-right">Total spent</Th>
              </Tr>
            </THead>
            <TBody>
              {customers.map((c) => (
                <Tr key={c.id}>
                  <Td className="font-medium">
                    <Link href={`/dashboard/customers/${c.id}`} className="text-ink-900 hover:text-accent-hover hover:underline">
                      {c.fullName}
                    </Link>
                  </Td>
                  <Td className="text-ink-500">{c.phone ?? c.email ?? "—"}</Td>
                  <Td className="text-ink-500">{c.source ?? "—"}</Td>
                  <Td className="tabular text-ink-500">{c.purchaseCount}</Td>
                  <Td className="tabular text-right font-medium">GH₵{c.totalSpent.toFixed(2)}</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {showAdd && (
        <Modal title="Add customer" onClose={() => setShowAdd(false)}>
          <CustomerForm onSubmit={handleCreate} />
        </Modal>
      )}
    </div>
  );
}
