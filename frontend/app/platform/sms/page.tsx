"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import {
  api,
  ApiError,
  PlatformSmsAccount,
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
  const [accounts, setAccounts] = useState<PlatformSmsAccount[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const [o, a] = await Promise.all([
      api.getPlatformSmsOverview(session.token),
      api.listPlatformSmsAccounts(session.token),
    ]);
    setOverview(o);
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
        <PageHeader title="SMS" subtitle="The SMS provider, what a credit costs and sells for, and each business's sender name and credits." />

        {error && <p className="text-sm text-danger">{error}</p>}

        {fetching || !overview ? (
          <CardSkeleton count={2} />
        ) : (
          <>
            {session && <OverviewCard token={session.token} overview={overview} onChanged={load} />}
            {session && <AccountsSection token={session.token} accounts={accounts} onChanged={load} />}
          </>
        )}
      </div>
    </PlatformShell>
  );
}

// The mNotify connection: paste the API key here (it is stored encrypted and never shown again — only
// its last four characters), and see straight away whether it works.
function ProviderKeyForm({ token, overview, onChanged }: { token: string; overview: PlatformSmsOverview; onChanged: () => Promise<void> }) {
  const [apiKey, setApiKey] = useState("");
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);

  const connected = overview.providerConfigured && overview.providerBalance != null;
  const isMock = overview.provider === "mock";

  async function save(value: string) {
    setSaving(true);
    setResult(null);
    try {
      const updated = await api.updatePlatformSmsProviderKey(token, value);
      setApiKey("");
      if (value.trim() === "") {
        setResult({ ok: true, text: updated.keySource === "SERVER" ? "Removed. The server's own key is being used again." : "Removed." });
      } else if (updated.providerBalance != null) {
        setResult({ ok: true, text: `Saved — connected to ${updated.provider}. Balance: ${updated.providerBalance.toLocaleString()} credits.` });
      } else {
        setResult({
          ok: false,
          text: "Saved, but we couldn't connect with that key. Check it in your mNotify dashboard (API section) and paste it again.",
        });
      }
      await onChanged();
    } catch (err) {
      setResult({ ok: false, text: err instanceof ApiError ? err.message : "Couldn't save that. Please try again." });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="sm:col-span-3 border-t border-border pt-4">
      <div className="flex flex-wrap items-center gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">mNotify connection</p>
        <Badge tone={isMock ? "info" : connected ? "success" : "danger"}>
          {isMock ? "Test mode (nothing is really sent)" : connected ? "Connected" : "Not connected"}
        </Badge>
      </div>
      <p className="mt-1 text-xs text-ink-500">
        {overview.keySource === "SETTINGS" && `Using the key saved here (${overview.keyHint}).`}
        {overview.keySource === "SERVER" && `Using the key from the server's settings (${overview.keyHint}). Save one here to override it.`}
        {overview.keySource === "NONE" && "No API key yet — paste yours below to start sending texts."}
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (apiKey.trim()) void save(apiKey);
        }}
        className="mt-2 flex flex-wrap items-center gap-2"
      >
        <input
          type="password"
          autoComplete="off"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="Paste your mNotify API key"
          aria-label="mNotify API key"
          className={`${INPUT_CLASS} w-full sm:w-80`}
        />
        <Button type="submit" disabled={saving || apiKey.trim() === ""}>
          {saving ? "Saving..." : "Save key"}
        </Button>
        {overview.keySource === "SETTINGS" && (
          <Button type="button" variant="ghost" disabled={saving} onClick={() => save("")}>
            Remove saved key
          </Button>
        )}
      </form>
      <p className="mt-1 text-xs text-ink-500">Stored encrypted. It is never shown again after you save it, and clients can never see it.</p>
      {result && <p className={`mt-1 text-xs ${result.ok ? "text-success" : "text-danger"}`}>{result.text}</p>}
    </div>
  );
}

function OverviewCard({ token, overview, onChanged }: { token: string; overview: PlatformSmsOverview; onChanged: () => Promise<void> }) {
  const short =
    overview.providerBalance != null && overview.outstandingClientCredits > overview.providerBalance;
  const savedCost = overview.providerCostPerCredit != null ? String(overview.providerCostPerCredit) : "";
  const savedSell = overview.sellPricePerCredit != null ? String(overview.sellPricePerCredit) : "";
  const [cost, setCost] = useState(savedCost);
  const [sell, setSell] = useState(savedSell);
  const [saving, setSaving] = useState(false);
  const [costError, setCostError] = useState<string | null>(null);
  const priceChanged = cost !== savedCost || sell !== savedSell;

  // A worked example, so the numbers can be sanity-checked before a client sees them.
  const costNum = cost.trim() === "" ? null : Number(cost);
  const sellNum = sell.trim() === "" ? null : Number(sell);
  const example = 100;
  const exampleCredits = sellNum && sellNum > 0 ? Math.floor(example / sellNum) : null;
  const profitPerCredit = costNum != null && sellNum != null ? sellNum - costNum : null;
  const exampleProfit = exampleCredits != null && costNum != null ? example - exampleCredits * costNum : null;

  async function savePricing() {
    setSaving(true);
    setCostError(null);
    try {
      await api.updatePlatformSmsSettings(token, costNum, sellNum);
      await onChanged();
    } catch (err) {
      setCostError(err instanceof ApiError ? err.message : "Couldn't save that.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card className="grid gap-4 p-5 sm:grid-cols-3">
      <ProviderKeyForm token={token} overview={overview} onChanged={onChanged} />
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
        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Pricing per credit (GHS)</p>
        <div className="mt-2 flex flex-wrap items-end gap-4">
          <label className="flex flex-col gap-1 text-xs text-ink-500">
            What the provider charges us
            <input
              type="number"
              step="0.001"
              min="0"
              value={cost}
              onChange={(e) => setCost(e.target.value)}
              placeholder="e.g. 0.035"
              aria-label="Provider cost per credit"
              className={`${INPUT_CLASS} w-36`}
            />
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-500">
            What we sell it for
            <input
              type="number"
              step="0.001"
              min="0"
              value={sell}
              onChange={(e) => setSell(e.target.value)}
              placeholder="e.g. 0.05"
              aria-label="Selling price per credit"
              className={`${INPUT_CLASS} w-36`}
            />
          </label>
          <Button type="button" variant="secondary" onClick={savePricing} disabled={saving || !priceChanged}>
            {saving ? "Saving..." : "Save"}
          </Button>
        </div>
        <p className="mt-2 text-xs text-ink-500">
          {exampleCredits != null ? (
            <>
              A business paying GHS {example} gets <strong>{exampleCredits.toLocaleString()} credits</strong> (amount ÷ selling price).
              {profitPerCredit != null && (
                <>
                  {" "}You make GHS {profitPerCredit.toFixed(3)} per credit
                  {exampleProfit != null && <>, about GHS {exampleProfit.toFixed(2)} on that purchase</>}.
                </>
              )}
              {profitPerCredit != null && profitPerCredit < 0 && <span className="text-danger"> That is a loss.</span>}
            </>
          ) : (
            <>Businesses can&apos;t buy credits until you set a selling price. The provider cost is only for your profit figures; clients never see it.</>
          )}
        </p>
        {costError && <p className="mt-1 text-xs text-danger">{costError}</p>}
      </div>
    </Card>
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
