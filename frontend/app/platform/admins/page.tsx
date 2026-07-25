"use client";

import { useEffect, useState, useCallback } from "react";
import { Users, Plus } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, CreatePlatformAdminPayload, PlatformAdminSummary } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import Modal from "@/components/Modal";
import PlatformAdminForm from "@/components/PlatformAdminForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

export default function PlatformAdminsPage() {
  const { session } = usePlatformAuth();
  const [admins, setAdmins] = useState<PlatformAdminSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [showAdd, setShowAdd] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    setAdmins(await api.listPlatformAdmins(session.token));
  }, [session]);

  useEffect(() => {
    load().finally(() => setFetching(false));
  }, [load]);

  async function handleCreate(payload: CreatePlatformAdminPayload) {
    if (!session) return;
    await api.createPlatformAdmin(session.token, payload);
    await load();
    setShowAdd(false);
  }

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Admins"
          subtitle={`${admins.length} account${admins.length === 1 ? "" : "s"} with platform access`}
          actions={
            <Button onClick={() => setShowAdd(true)}>
              <Plus size={16} /> Add Super Admin
            </Button>
          }
        />

        <Card>
          {fetching ? (
            <TableSkeleton cols={3} />
          ) : (
            <Table>
              <THead>
                <Tr>
                  <Th>Name</Th>
                  <Th>Email</Th>
                  <Th>Added</Th>
                </Tr>
              </THead>
              <TBody>
                {admins.map((a) => (
                  <Tr key={a.id}>
                    <Td className="font-medium">{a.fullName}</Td>
                    <Td className="text-ink-500">{a.email}</Td>
                    <Td className="text-ink-500">{new Date(a.createdAt).toLocaleDateString()}</Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
          )}
        </Card>

        <p className="flex items-center gap-1.5 text-xs text-ink-500">
          <Users size={13} />
          Every account listed here has full platform access — the same as yours.
        </p>
      </div>

      {showAdd && (
        <Modal title="Add Super Admin" onClose={() => setShowAdd(false)}>
          <PlatformAdminForm onSubmit={handleCreate} />
        </Modal>
      )}
    </PlatformShell>
  );
}
