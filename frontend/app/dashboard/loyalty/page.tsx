"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { CheckCircle2, Gift } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, LoyaltyCustomerRow, LoyaltyProgram } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";
import EmptyState from "@/components/ui/EmptyState";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const INPUT_CLASS =
  "rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20";

export default function LoyaltyPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [program, setProgram] = useState<LoyaltyProgram | null>(null);
  const [rows, setRows] = useState<LoyaltyCustomerRow[]>([]);
  const [fetching, setFetching] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [enabled, setEnabled] = useState(true);
  const [visits, setVisits] = useState("10");
  const [reward, setReward] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [redeemingId, setRedeemingId] = useState<string | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const [p, r] = await Promise.all([api.getLoyaltyProgram(session.token), api.listLoyaltyCustomers(session.token)]);
    setProgram(p);
    setEnabled(p.enabled);
    setVisits(String(p.visitsForReward));
    setReward(p.rewardDescription);
    setRows(r);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load()
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : "Couldn't load the loyalty program."))
      .finally(() => setFetching(false));
  }, [session, load]);

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      await api.updateLoyaltyProgram(session.token, { enabled, visitsForReward: Number(visits), rewardDescription: reward });
      await load();
      setSaved(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : "Couldn't save that. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  async function handleRedeem(customerId: string) {
    if (!session) return;
    setRedeemingId(customerId);
    setRowError(null);
    try {
      await api.redeemLoyaltyReward(session.token, customerId);
      await load();
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : "Couldn't mark that reward as given.");
      await load().catch(() => {});
    } finally {
      setRedeemingId(null);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const dueCount = rows.filter((r) => r.rewardDue).length;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Loyalty" subtitle="Reward your regulars. Every visit earns a point; enough points earn a reward." />

      {fetching ? (
        <CardSkeleton count={2} />
      ) : loadError || !program ? (
        <p className="text-sm text-danger">{loadError ?? "Couldn't load the loyalty program."}</p>
      ) : (
        <>
          <Card className="max-w-xl p-5">
            <h2 className="text-base font-semibold text-ink-900">The program</h2>
            <form onSubmit={handleSave} className="mt-4 flex flex-col gap-4">
              <label className="flex items-center gap-2 text-sm text-ink-700">
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                  className="rounded border-border text-accent focus:ring-accent/20"
                />
                Points program is on
              </label>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="loyalty-visits" className="text-sm font-medium text-ink-700">
                    Visits for a reward
                  </label>
                  <input
                    id="loyalty-visits"
                    type="number"
                    min={1}
                    max={100}
                    value={visits}
                    onChange={(e) => setVisits(e.target.value)}
                    className={INPUT_CLASS}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="loyalty-reward" className="text-sm font-medium text-ink-700">
                    The reward
                  </label>
                  <input
                    id="loyalty-reward"
                    value={reward}
                    maxLength={120}
                    onChange={(e) => setReward(e.target.value)}
                    placeholder="e.g. a free wash"
                    className={INPUT_CLASS}
                  />
                </div>
              </div>
              <ul className="list-disc pl-5 text-xs text-ink-500">
                <li>A customer earns at most one visit a day, when a sale including a counting service is paid or a service order is completed.</li>
                <li>
                  Choose which services count under <Link href="/dashboard/service-orders/catalog" className="text-accent-hover hover:underline">Service catalog</Link>.
                  Product-only sales never count.
                </li>
                <li>Changing the target only affects visits from now on — rewards already given stay given.</li>
              </ul>
              {saveError && <p className="text-sm text-danger">{saveError}</p>}
              <div className="flex items-center gap-3">
                <Button type="submit" disabled={saving}>
                  {saving ? "Saving..." : "Save"}
                </Button>
                {saved && (
                  <span className="flex items-center gap-1 text-xs text-success">
                    <CheckCircle2 size={13} /> Saved
                  </span>
                )}
              </div>
            </form>
          </Card>

          <section className="flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <h2 className="text-base font-semibold text-ink-900">Customers</h2>
              {dueCount > 0 && <Badge tone="success">{dueCount} due a reward</Badge>}
            </div>
            {rowError && <p className="text-sm text-danger">{rowError}</p>}
            <Card className="p-0">
              {rows.length === 0 ? (
                <EmptyState
                  icon={Gift}
                  title="No visits counted yet"
                  description="Customers appear here after their first counted visit."
                />
              ) : (
                <Table>
                  <THead>
                    <Tr>
                      <Th>Customer</Th>
                      <Th>Progress</Th>
                      <Th>Visits so far</Th>
                      <Th />
                    </Tr>
                  </THead>
                  <TBody>
                    {rows.map((r) => (
                      <Tr key={r.customerId}>
                        <Td>
                          <Link href={`/dashboard/customers/${r.customerId}`} className="font-medium text-ink-900 hover:underline">
                            {r.customerName}
                          </Link>
                          {r.phone && <p className="text-xs text-ink-500">{r.phone}</p>}
                        </Td>
                        <Td>
                          {r.rewardDue ? (
                            <Badge tone="success">Reward due{r.rewardsDue > 1 ? ` ×${r.rewardsDue}` : ""}</Badge>
                          ) : (
                            <span className="tabular">
                              {r.visits} / {r.target}
                            </span>
                          )}
                        </Td>
                        <Td className="tabular text-ink-500">{r.lifetimeVisits}</Td>
                        <Td className="text-right">
                          {r.rewardDue && (
                            <Button
                              type="button"
                              variant="secondary"
                              disabled={redeemingId === r.customerId}
                              onClick={() => handleRedeem(r.customerId)}
                              className="px-3 py-1.5 text-xs"
                            >
                              {redeemingId === r.customerId ? "Saving..." : "Mark reward given"}
                            </Button>
                          )}
                        </Td>
                      </Tr>
                    ))}
                  </TBody>
                </Table>
              )}
            </Card>
          </section>
        </>
      )}
    </div>
  );
}
