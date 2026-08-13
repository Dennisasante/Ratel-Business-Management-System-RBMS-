"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Users, Plus, Check } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, CreateStaffPayload, StaffMember, StaffMemberPayload, StaffRole, UserSummary } from "@/lib/api";
import Modal from "@/components/Modal";
import StaffForm from "@/components/StaffForm";
import StaffMemberForm from "@/components/StaffMemberForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const ROLES: StaffRole[] = ["OWNER", "MANAGER", "SALES_PERSON", "ACCOUNTANT"];

const ROLE_LABELS: Partial<Record<StaffRole, string>> = {
  MANAGER: "Administrator",
};

export default function TeamPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [rowError, setRowError] = useState<string | null>(null);
  const [rateDrafts, setRateDrafts] = useState<Record<string, string>>({});
  const [pendingAction, setPendingAction] = useState<string | null>(null); // `${userId}:${action}`

  const [staffMembers, setStaffMembers] = useState<StaffMember[]>([]);
  const [fetchingStaff, setFetchingStaff] = useState(true);
  const [staffModal, setStaffModal] = useState<{ type: "add" } | { type: "edit"; member: StaffMember } | null>(null);
  const [staffRowError, setStaffRowError] = useState<string | null>(null);
  const [staffPendingAction, setStaffPendingAction] = useState<string | null>(null);

  const loadUsers = useCallback(async () => {
    if (!session) return;
    setUsers(await api.listUsers(session.token));
  }, [session]);

  const loadStaffMembers = useCallback(async () => {
    if (!session) return;
    setStaffMembers(await api.listStaffMembers(session.token));
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    loadUsers().finally(() => setFetching(false));
    loadStaffMembers().finally(() => setFetchingStaff(false));
  }, [session, loadUsers, loadStaffMembers]);

  async function handleCreate(payload: CreateStaffPayload) {
    if (!session) return;
    await api.createStaff(session.token, payload);
    await loadUsers();
    setShowAdd(false);
  }

  async function handleSaveStaffMember(payload: StaffMemberPayload) {
    if (!session) return;
    if (staffModal?.type === "edit") {
      await api.updateStaffMember(session.token, staffModal.member.id, payload);
    } else {
      await api.createStaffMember(session.token, payload);
    }
    await loadStaffMembers();
    setStaffModal(null);
  }

  async function handleToggleStaffMemberStatus(member: StaffMember) {
    if (!session) return;
    setStaffRowError(null);
    setStaffPendingAction(member.id);
    try {
      await api.setStaffMemberActive(session.token, member.id, !member.active);
      await loadStaffMembers();
    } catch (err) {
      setStaffRowError(err instanceof ApiError ? err.message : "Couldn't update that staff member.");
    } finally {
      setStaffPendingAction(null);
    }
  }

  async function handleToggleStatus(user: UserSummary) {
    if (!session) return;
    setRowError(null);
    setPendingAction(`${user.id}:status`);
    try {
      await api.updateUserStatus(session.token, user.id, !user.active);
      await loadUsers();
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : "Couldn't update that account.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleRoleChange(user: UserSummary, role: StaffRole) {
    if (!session) return;
    setRowError(null);
    setPendingAction(`${user.id}:role`);
    try {
      await api.updateUserRole(session.token, user.id, role);
      await loadUsers();
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : "Couldn't change that role.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleSaveRate(user: UserSummary) {
    if (!session) return;
    const draft = rateDrafts[user.id];
    if (draft === undefined) return;
    const rate = Number(draft);
    if (Number.isNaN(rate) || rate < 0 || rate > 100) {
      setRowError("Commission rate must be between 0 and 100.");
      return;
    }
    setRowError(null);
    setPendingAction(`${user.id}:rate`);
    try {
      await api.updateCommissionRate(session.token, user.id, rate);
      setRateDrafts((d) => {
        const next = { ...d };
        delete next[user.id];
        return next;
      });
      await loadUsers();
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : "Couldn't update that rate.");
    } finally {
      setPendingAction(null);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const canManage = session.role === "OWNER" || session.role === "MANAGER";
  const isOwner = session.role === "OWNER";

  // Mirrors the backend's UserManagementService.assertCanManage — a Manager
  // can act on Sales/Accountant/Staff accounts but never on an Owner or
  // another Manager. Backend already rejects it with a 403 either way; this
  // just stops the UI from offering a control that will fail.
  function canManageTarget(targetRole: StaffRole) {
    return isOwner || (session!.role === "MANAGER" && targetRole !== "OWNER" && targetRole !== "MANAGER");
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Team"
        subtitle={`${users.length} account${users.length === 1 ? "" : "s"}`}
        actions={
          canManage ? (
            <Button onClick={() => setShowAdd(true)}>
              <Plus size={16} /> Add account
            </Button>
          ) : undefined
        }
      />

      {rowError && <p className="text-sm text-danger">{rowError}</p>}

      <Card>
        {fetching ? (
          <TableSkeleton cols={5} />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Name</Th>
                <Th>Email</Th>
                <Th>Role</Th>
                <Th>Status</Th>
                {isOwner && <Th>Commission %</Th>}
                {canManage && <Th className="text-right">Actions</Th>}
              </Tr>
            </THead>
            <TBody>
              {users.map((u) => (
                <Tr key={u.id}>
                  <Td className="font-medium">{u.fullName}</Td>
                  <Td className="text-ink-500">{u.email}</Td>
                  <Td>
                    {isOwner ? (
                      <select
                        value={u.role}
                        onChange={(e) => handleRoleChange(u, e.target.value as StaffRole)}
                        disabled={pendingAction === `${u.id}:role`}
                        className="rounded-md border border-border bg-surface px-2 py-1 text-xs text-ink-900 focus:border-accent focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {ROLES.map((r) => (
                          <option key={r} value={r}>
                            {ROLE_LABELS[r] ?? r}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <Badge tone="neutral">{ROLE_LABELS[u.role as StaffRole] ?? u.role}</Badge>
                    )}
                  </Td>
                  <Td>
                    <Badge tone={u.active ? "success" : "danger"}>{u.active ? "Active" : "Inactive"}</Badge>
                  </Td>
                  {isOwner && (
                    <Td>
                      <div className="flex items-center gap-1.5">
                        <input
                          type="number"
                          min={0}
                          max={100}
                          step="0.5"
                          value={rateDrafts[u.id] ?? String(u.commissionRate)}
                          onChange={(e) => setRateDrafts((d) => ({ ...d, [u.id]: e.target.value }))}
                          className="tabular w-16 rounded-md border border-border px-2 py-1 text-xs focus:border-accent focus:outline-none"
                        />
                        {rateDrafts[u.id] !== undefined && rateDrafts[u.id] !== String(u.commissionRate) && (
                          <button
                            onClick={() => handleSaveRate(u)}
                            disabled={pendingAction === `${u.id}:rate`}
                            className="rounded-md bg-accent-soft p-1 text-accent-hover hover:bg-accent hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
                            aria-label="Save rate"
                          >
                            <Check size={12} />
                          </button>
                        )}
                      </div>
                    </Td>
                  )}
                  {canManage && (
                    <Td className="text-right">
                      {canManageTarget(u.role as StaffRole) ? (
                        <button
                          onClick={() => handleToggleStatus(u)}
                          disabled={pendingAction === `${u.id}:status`}
                          className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {pendingAction === `${u.id}:status` ? "Saving..." : u.active ? "Deactivate" : "Reactivate"}
                        </button>
                      ) : (
                        <span className="text-xs text-ink-400">—</span>
                      )}
                    </Td>
                  )}
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {!canManage && (
        <p className="flex items-center gap-1.5 text-xs text-ink-500">
          <Users size={13} />
          Only Owners and Managers can add or manage staff accounts.
        </p>
      )}

      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-ink-900">Staff</h2>
          <p className="text-xs text-ink-500">
            {staffMembers.length} member{staffMembers.length === 1 ? "" : "s"} — names to assign work to, no login required.
          </p>
        </div>
        {canManage && (
          <Button onClick={() => setStaffModal({ type: "add" })}>
            <Plus size={16} /> Add staff
          </Button>
        )}
      </div>

      {staffRowError && <p className="text-sm text-danger">{staffRowError}</p>}

      <Card>
        {fetchingStaff ? (
          <TableSkeleton cols={4} />
        ) : staffMembers.length === 0 ? (
          <p className="p-4 text-sm text-ink-500">No staff members yet.</p>
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Name</Th>
                <Th>Phone</Th>
                <Th>Status</Th>
                {canManage && <Th className="text-right">Actions</Th>}
              </Tr>
            </THead>
            <TBody>
              {staffMembers.map((m) => (
                <Tr key={m.id}>
                  <Td className="font-medium">{m.fullName}</Td>
                  <Td className="text-ink-500">{m.phone ?? "—"}</Td>
                  <Td>
                    <Badge tone={m.active ? "success" : "danger"}>{m.active ? "Active" : "Inactive"}</Badge>
                  </Td>
                  {canManage && (
                    <Td className="text-right">
                      <div className="flex justify-end gap-3">
                        <button
                          onClick={() => setStaffModal({ type: "edit", member: m })}
                          className="text-sm font-medium text-ink-700 hover:underline"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => handleToggleStaffMemberStatus(m)}
                          disabled={staffPendingAction === m.id}
                          className="text-sm font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {staffPendingAction === m.id ? "Saving..." : m.active ? "Deactivate" : "Reactivate"}
                        </button>
                      </div>
                    </Td>
                  )}
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {showAdd && (
        <Modal title="Add account" onClose={() => setShowAdd(false)}>
          <StaffForm onSubmit={handleCreate} viewerRole={session.role as StaffRole} />
        </Modal>
      )}

      {staffModal?.type === "add" && (
        <Modal title="Add staff member" onClose={() => setStaffModal(null)}>
          <StaffMemberForm onSubmit={handleSaveStaffMember} />
        </Modal>
      )}

      {staffModal?.type === "edit" && (
        <Modal title="Edit staff member" onClose={() => setStaffModal(null)}>
          <StaffMemberForm staffMember={staffModal.member} onSubmit={handleSaveStaffMember} />
        </Modal>
      )}
    </div>
  );
}
