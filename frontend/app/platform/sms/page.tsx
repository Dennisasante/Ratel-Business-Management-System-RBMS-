"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import {
  api,
  ApiError,
  PlatformSmsAccount,
  PlatformSmsBundle,
  PlatformSmsOverview,
  SmsSenderStatus,
} from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";
import FormField from "@/components/FormField";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const INPUT_CLASS =
  "rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20";

export default function PlatformSmsPage() {
  const { session } = usePlatformAuth();
  const [overview, setOverview] = useState<PlatformSmsOverview | null>(null);
  const [bundles, setBundles] = useState<PlatformSmsBundle[]>([]);
  const [accounts, setAccounts] = useState<PlatformSmsAccount[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const [o, b, a] = await Promise.all([
      api.getPlatformSmsOverview(session.token),
      api.listPlatformSmsBundles(session.token),
      api.listPlatformSmsAccounts(session.token),
    ]);
    setOverview(o);
    setBundles(b);
    setAccounts(a);
  }, [session]);

  useEffect(() => {
    load()
      .catch((err) => setError(err instanceof ApiError ? err.message : "Couldn't load SMS."))
      .finally(() => setFetching(false));
  }, [load]);

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader title="SMS" subtitle="The SMS provider, the credit bundles on sale, and each business's sender name and credits." />

        {error && <p className="text-sm text-danger">{error}</p>}

        {fetching || !overview ? (
          <CardSkeleton count={2} />
        ) : (
          <>
            {session && <OverviewCard token={session.token} overview={overview} onChanged={load} />}
            {session && <BundlesSection token={session.token} bundles={bundles} onChanged={load} />}
            {session && <AccountsSection token={session.token} accounts={accounts} onChanged={load} />}
          </>
        )}
      </div>
    </PlatformShell>
  );
}

function OverviewCard({ token, overview, onChanged }: { token: string; overview: PlatformSmsOverview; onChanged: () => Promise<void> }) {
  const short =
    overview.providerBalance != null && overview.outstandingClientCredits > overview.providerBalance;
  const [cost, setCost] = useState(overview.providerCostPerCredit != null ? String(overview.providerCostPerCredit) : "");
  const [saving, setSaving] = useState(false);
  const [costError, setCostError] = useState<string | null>(null);
  const costChanged = cost !== (overview.providerCostPerCredit != null ? String(overview.providerCostPerCredit) : "");

  async function saveCost() {
    setSaving(true);
    setCostError(null);
    try {
      await api.updatePlatformSmsSettings(token, cost.trim() === "" ? null : Number(cost));
      await onChanged();
    } catch (err) {
      setCostError(err instanceof ApiError ? err.message : "Couldn't save that.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card className="grid gap-4 p-5 sm:grid-cols-3">
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Provider</p>
        <p className="flex items-center gap-2 text-sm font-medium text-ink-900">
          {overview.provider}
          <Badge tone={overview.providerConfigured ? "success" : "danger"}>
            {overview.providerConfigured ? "Connected" : "No API key"}
          </Badge>
        </p>
      </div>
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Our balance with the provider</p>
        <p className="text-2xl font-semibold text-ink-900">
          {overview.providerBalance != null ? overview.providerBalance.toLocaleString() : "—"}
        </p>
        {overview.providerBonus != null && overview.providerBonus > 0 && (
          <p className="text-xs text-ink-500">+ {overview.providerBonus.toLocaleString()} bonus</p>
        )}
      </div>
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Credits businesses hold</p>
        <p className="text-2xl font-semibold text-ink-900">{overview.outstandingClientCredits.toLocaleString()}</p>
        {short && (
          <p className="mt-1 flex items-center gap-1 text-xs text-danger">
            <AlertTriangle size={13} /> More than we hold — top up the provider before they send it all.
          </p>
        )}
      </div>
      <div className="sm:col-span-3 border-t border-border pt-4">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">What the provider charges us per credit (GHS)</p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <input
            type="number"
            step="0.0001"
            min="0"
            value={cost}
            onChange={(e) => setCost(e.target.value)}
            placeholder="e.g. 0.0800"
            aria-label="Provider cost per credit"
            className={`${INPUT_CLASS} w-36`}
          />
          <Button type="button" variant="secondary" onClick={saveCost} disabled={saving || !costChanged}>
            {saving ? "Saving..." : "Save"}
          </Button>
          <span className="text-xs text-ink-500">Used only to show your profit on each bundle below. Clients never see it.</span>
        </div>
        {costError && <p className="mt-1 text-xs text-danger">{costError}</p>}
      </div>
    </Card>
  );
}

function BundlesSection({ token, bundles, onChanged }: { token: string; bundles: PlatformSmsBundle[]; onChanged: () => Promise<void> }) {
  const [editing, setEditing] = useState<PlatformSmsBundle | null>(null);
  const [name, setName] = useState("");
  const [credits, setCredits] = useState("");
  const [price, setPrice] = useState("");
  const [active, setActive] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEdit(bundle: PlatformSmsBundle | null) {
    setEditing(bundle);
    setName(bundle?.name ?? "");
    setCredits(bundle ? String(bundle.credits) : "");
    setPrice(bundle ? String(bundle.price) : "");
    setActive(bundle?.active ?? true);
    setError(null);
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaving(true);
    const payload = {
      name,
      credits: Number(credits),
      price: Number(price),
      active,
      sortOrder: editing?.sortOrder ?? bundles.length,
    };
    try {
      if (editing) await api.updatePlatformSmsBundle(token, editing.id, payload);
      else await api.createPlatformSmsBundle(token, payload);
      startEdit(null);
      await onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-base font-semibold text-ink-900">Bundles</h2>
      <Card className="p-0">
        {bundles.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">No bundles yet — add one below so businesses can buy credits.</p>
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Name</Th>
                <Th>Credits</Th>
                <Th>Price (GHS)</Th>
                <Th>Per credit</Th>
                <Th>Profit per bundle</Th>
                <Th>Status</Th>
                <Th />
              </Tr>
            </THead>
            <TBody>
              {bundles.map((b) => (
                <Tr key={b.id}>
                  <Td>{b.name}</Td>
                  <Td>{b.credits.toLocaleString()}</Td>
                  <Td>{b.price.toFixed(2)}</Td>
                  <Td>{b.pricePerCredit.toFixed(3)}</Td>
                  <Td className={b.profitPerBundle != null && b.profitPerBundle < 0 ? "text-danger" : ""}>
                    {b.profitPerBundle != null ? `GHS ${b.profitPerBundle.toFixed(2)}` : "—"}
                  </Td>
                  <Td>
                    <Badge tone={b.active ? "success" : "neutral"}>{b.active ? "On sale" : "Hidden"}</Badge>
                  </Td>
                  <Td>
                    <button onClick={() => startEdit(b)} className="text-xs font-medium text-accent hover:underline">
                      Edit
                    </button>
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      <Card className="max-w-lg p-5">
        <form onSubmit={handleSave} className="flex flex-col gap-4">
          <h3 className="text-sm font-semibold text-ink-900">{editing ? `Edit ${editing.name}` : "Add a bundle"}</h3>
          <FormField label="Name" name="bundleName" required value={name} onChange={setName} />
          <div className="grid grid-cols-2 gap-4">
            <FormField label="Credits" name="bundleCredits" type="number" required value={credits} onChange={setCredits} />
            <FormField label="Price (GHS)" name="bundlePrice" type="number" required value={price} onChange={setPrice} />
          </div>
          <label className="flex items-center gap-2 text-sm text-ink-700">
            <input
              type="checkbox"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
              className="rounded border-border text-accent focus:ring-accent/20"
            />
            On sale
          </label>
          <p className="-mt-2 text-xs text-ink-500">
            Changing a bundle only affects future purchases — past ones keep the price they were bought at.
          </p>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex gap-2">
            <Button type="submit" disabled={saving}>
              {saving ? "Saving..." : editing ? "Save changes" : "Add bundle"}
            </Button>
            {editing && (
              <Button type="button" variant="secondary" onClick={() => startEdit(null)}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </Card>
    </section>
  );
}

function AccountsSection({ token, accounts, onChanged }: { token: string; accounts: PlatformSmsAccount[]; onChanged: () => Promise<void> }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-base font-semibold text-ink-900">Businesses</h2>
      {accounts.length === 0 ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">
            No business has SMS switched on yet. Turn it on from a business&apos;s page under Modules.
          </p>
        </Card>
      ) : (
        <Card className="p-0">
          <Table>
            <THead>
              <Tr>
                <Th>Business</Th>
                <Th>Sender name</Th>
                <Th>Credits</Th>
                <Th>Adjust credits</Th>
              </Tr>
            </THead>
            <TBody>
              {accounts.map((account) => (
                <AccountRow key={account.businessId} token={token} account={account} onChanged={onChanged} />
              ))}
            </TBody>
          </Table>
        </Card>
      )}
    </section>
  );
}

function AccountRow({ token, account, onChanged }: { token: string; account: PlatformSmsAccount; onChanged: () => Promise<void> }) {
  const [senderId, setSenderId] = useState(account.senderId ?? "");
  const [status, setStatus] = useState<SmsSenderStatus>(account.senderStatus);
  const [delta, setDelta] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<"sender" | "credits" | "verify" | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(kind: "sender" | "credits" | "verify", action: () => Promise<unknown>) {
    setError(null);
    setBusy(kind);
    try {
      const result = await action();
      // Saving or re-checking a sender name returns what the provider said about it.
      if (result && typeof result === "object" && "note" in result) {
        const returned = result as PlatformSmsAccount;
        setNote(returned.note);
        setStatus(returned.senderStatus);
        setSenderId(returned.senderId ?? "");
      }
      await onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setBusy(null);
    }
  }

  const senderDirty = senderId !== (account.senderId ?? "") || status !== account.senderStatus;

  return (
    <Tr>
      <Td className="align-top font-medium">{account.businessName}</Td>
      <Td className="align-top">
        <div className="flex flex-wrap items-center gap-2">
          <input
            value={senderId}
            onChange={(e) => setSenderId(e.target.value)}
            maxLength={11}
            placeholder="e.g. GLOWSALON"
            aria-label={`Sender name for ${account.businessName}`}
            className={`${INPUT_CLASS} w-36`}
          />
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as SmsSenderStatus)}
            aria-label={`Sender status for ${account.businessName}`}
            className={INPUT_CLASS}
          >
            <option value="NONE">Not set up</option>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
          </select>
          <Button
            type="button"
            variant="secondary"
            disabled={!senderDirty || busy !== null}
            onClick={() => run("sender", () => api.updatePlatformSmsSender(token, account.businessId, senderId, status))}
          >
            {busy === "sender" ? "Checking..." : "Save & check"}
          </Button>
          <Button
            type="button"
            variant="ghost"
            disabled={busy !== null || !account.senderId}
            onClick={() => run("verify", () => api.verifyPlatformSmsSender(token, account.businessId))}
          >
            {busy === "verify" ? "Checking..." : "Check again"}
          </Button>
        </div>
        {note && <p className="mt-1 max-w-sm text-xs text-ink-500">{note}</p>}
      </Td>
      <Td className="align-top">{account.creditBalance.toLocaleString()}</Td>
      <Td className="align-top">
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="number"
            value={delta}
            onChange={(e) => setDelta(e.target.value)}
            placeholder="+100 / -50"
            aria-label={`Credits to add or remove for ${account.businessName}`}
            className={`${INPUT_CLASS} w-28`}
          />
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={200}
            placeholder="Reason"
            aria-label={`Reason for adjusting ${account.businessName}`}
            className={`${INPUT_CLASS} w-40`}
          />
          <Button
            type="button"
            variant="secondary"
            disabled={busy !== null || !Number(delta) || reason.trim() === ""}
            onClick={() =>
              run("credits", async () => {
                await api.adjustPlatformSmsCredits(token, account.businessId, Number(delta), reason);
                setDelta("");
                setReason("");
              })
            }
          >
            {busy === "credits" ? "Applying..." : "Apply"}
          </Button>
        </div>
        {error && <p className="mt-1 text-xs text-danger">{error}</p>}
      </Td>
    </Tr>
  );
}
