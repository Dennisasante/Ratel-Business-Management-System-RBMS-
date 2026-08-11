"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { Package, Users2, ShoppingCart, Wallet, Trash2, Power, CalendarDays, ShoppingBag, Sparkles, Wrench, CreditCard, MessageCircle, Pencil } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, ApiError, PlatformBusinessDetail, SubscriptionPlan } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import StatCard from "@/components/ui/StatCard";
import Modal from "@/components/Modal";
import CardSkeleton from "@/components/ui/CardSkeleton";

const BILLING_STATUS_LABEL: Record<string, string> = {
  TRIALING: "Trialing",
  ACTIVE: "Active",
  READ_ONLY: "Read-only",
};

const BILLING_STATUS_TONE: Record<string, "success" | "info" | "danger"> = {
  ACTIVE: "success",
  TRIALING: "info",
  READ_ONLY: "danger",
};

const ROLE_LABEL: Record<string, string> = {
  OWNER: "Owner",
  MANAGER: "Administrator",
  SALES_PERSON: "Sales person",
  ACCOUNTANT: "Accountant",
  STAFF: "Staff",
};

function formatDate(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export default function PlatformBusinessDetailPage() {
  const { session } = usePlatformAuth();
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [business, setBusiness] = useState<PlatformBusinessDetail | null>(null);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [showDelete, setShowDelete] = useState(false);
  const [confirmName, setConfirmName] = useState("");
  const [resetResult, setResetResult] = useState<{ name: string; password: string } | null>(null);
  const [resettingUserId, setResettingUserId] = useState<string | null>(null);

  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [editingBilling, setEditingBilling] = useState(false);
  const [planId, setPlanId] = useState<string>("");
  const [trialEndsAt, setTrialEndsAt] = useState<string>("");
  const [priceOverride, setPriceOverride] = useState<string>("");
  const [savingBilling, setSavingBilling] = useState(false);
  const [billingError, setBillingError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session || !params.id) return;
    setBusiness(await api.getPlatformBusiness(session.token, params.id));
  }, [session, params.id]);

  useEffect(() => {
    load().finally(() => setFetching(false));
  }, [load]);

  useEffect(() => {
    if (!session) return;
    api.listPlatformSubscriptionPlans(session.token).then(setPlans);
  }, [session]);

  function startEditingBilling() {
    if (!business) return;
    setPlanId(business.subscriptionPlanId ?? "");
    setTrialEndsAt(business.trialEndsAt ? business.trialEndsAt.slice(0, 10) : "");
    setPriceOverride(business.priceOverride != null ? String(business.priceOverride) : "");
    setBillingError(null);
    setEditingBilling(true);
  }

  async function saveBilling() {
    if (!session || !business) return;
    setSavingBilling(true);
    setBillingError(null);
    try {
      await api.updatePlatformBusinessBilling(session.token, business.id, {
        subscriptionPlanId: planId || undefined,
        clearPlan: !planId,
        trialEndsAt: trialEndsAt ? new Date(trialEndsAt + "T00:00:00Z").toISOString() : undefined,
        priceOverride: priceOverride !== "" ? Number(priceOverride) : undefined,
        clearPriceOverride: priceOverride === "",
      });
      await load();
      setEditingBilling(false);
    } catch (err) {
      setBillingError(err instanceof ApiError ? err.message : "Couldn't save billing changes.");
    } finally {
      setSavingBilling(false);
    }
  }

  async function handleToggleStatus() {
    if (!session || !business) return;
    setError(null);
    setBusy(true);
    try {
      await api.setPlatformBusinessStatus(session.token, business.id, !business.active);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update this business.");
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete() {
    if (!session || !business) return;
    setError(null);
    setBusy(true);
    try {
      await api.deletePlatformBusiness(session.token, business.id);
      router.push("/platform/businesses");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't delete this business.");
      setBusy(false);
    }
  }

  async function handleResetPassword(userId: string, userName: string) {
    if (!session || !business) return;
    setError(null);
    setResettingUserId(userId);
    try {
      const res = await api.resetPlatformUserPassword(session.token, business.id, userId);
      setResetResult({ name: userName, password: res.temporaryPassword });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't reset that password.");
    } finally {
      setResettingUserId(null);
    }
  }

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <Link href="/platform/businesses" className="text-sm text-ink-500 hover:underline">
          ← Businesses
        </Link>

        {fetching ? (
          <CardSkeleton count={4} />
        ) : !business ? (
          <p className="text-sm text-ink-500">Business not found.</p>
        ) : (
          <>
            <PageHeader
              title={business.name}
              subtitle={`${business.industry} · ${business.location ?? "No location set"}`}
              actions={
                <>
                  <Badge tone={business.active ? "success" : "danger"}>{business.active ? "Active" : "Suspended"}</Badge>
                  <Button variant="secondary" onClick={handleToggleStatus} disabled={busy}>
                    <Power size={15} />
                    {busy ? "Working..." : business.active ? "Suspend" : "Reactivate"}
                  </Button>
                  <Button variant="danger" onClick={() => setShowDelete(true)} disabled={busy}>
                    <Trash2 size={15} />
                    Delete
                  </Button>
                </>
              }
            />

            {error && <p className="text-sm text-danger">{error}</p>}

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard label="Products" value={business.productCount} icon={Package} tone="accent" />
              <StatCard label="Customers" value={business.customerCount} icon={Users2} tone="info" />
              <StatCard label="Sales" value={business.salesCount} hint={`GH₵${business.totalRevenue.toFixed(2)} revenue`} icon={ShoppingCart} tone="success" />
              <StatCard label="Expenses" value={business.expenseCount} hint={`GH₵${business.totalExpenses.toFixed(2)} total`} icon={Wallet} tone="danger" />
            </div>

            <div>
              <h2 className="mb-3 text-sm font-semibold text-ink-500">Feature usage</h2>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <StatCard label="Bookings" value={business.bookingCount} icon={CalendarDays} tone="accent" />
                <StatCard label="E-commerce orders" value={business.ecommerceOrderCount} icon={ShoppingBag} tone="info" />
                <StatCard label="Custom wig requests" value={business.customWigRequestCount} icon={Sparkles} tone="danger" />
                <StatCard label="Service orders" value={business.serviceOrderCount} icon={Wrench} tone="success" />
              </div>
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
              <div className="flex flex-col gap-6">
              <Card className="p-5">
                <h2 className="text-base font-semibold text-ink-900">Business details</h2>
                <dl className="mt-4 space-y-3 text-sm">
                  <div className="flex justify-between border-b border-border pb-3">
                    <dt className="text-ink-500">Contact email</dt>
                    <dd className="font-medium text-ink-900">{business.contactEmail ?? "—"}</dd>
                  </div>
                  <div className="flex justify-between border-b border-border pb-3">
                    <dt className="text-ink-500">Contact phone</dt>
                    <dd className="font-medium text-ink-900">{business.contactPhone ?? "—"}</dd>
                  </div>
                  <div className="flex justify-between border-b border-border pb-3">
                    <dt className="text-ink-500">Currency</dt>
                    <dd className="font-medium text-ink-900">{business.currency}</dd>
                  </div>
                </dl>

                <div className="mt-4 flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-ink-900">Billing</h3>
                  {!editingBilling && (
                    <button
                      onClick={startEditingBilling}
                      className="flex items-center gap-1 text-xs font-medium text-accent-hover hover:underline"
                    >
                      <Pencil size={12} /> Edit
                    </button>
                  )}
                </div>

                {editingBilling ? (
                  <div className="mt-3 flex flex-col gap-3 rounded-lg bg-canvas p-3">
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-medium text-ink-700">Plan</label>
                      <select
                        value={planId}
                        onChange={(e) => setPlanId(e.target.value)}
                        className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                      >
                        <option value="">No plan</option>
                        {plans.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} — GH₵{p.price.toFixed(2)}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-medium text-ink-700">Trial ends</label>
                      <input
                        type="date"
                        value={trialEndsAt}
                        onChange={(e) => setTrialEndsAt(e.target.value)}
                        className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-medium text-ink-700">Price override (GH₵)</label>
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={priceOverride}
                        onChange={(e) => setPriceOverride(e.target.value)}
                        placeholder="Plan default"
                        className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                      />
                      <p className="text-xs text-ink-500">Leave blank to charge this business the plan&apos;s normal price.</p>
                    </div>

                    {billingError && <p className="text-sm text-danger">{billingError}</p>}

                    <div className="flex gap-2">
                      <Button onClick={saveBilling} disabled={savingBilling}>
                        {savingBilling ? "Saving..." : "Save"}
                      </Button>
                      <Button variant="secondary" onClick={() => setEditingBilling(false)} disabled={savingBilling}>
                        Cancel
                      </Button>
                    </div>
                  </div>
                ) : (
                  <dl className="mt-3 space-y-3 text-sm">
                    <div className="flex justify-between border-b border-border pb-3">
                      <dt className="text-ink-500">Plan</dt>
                      <dd className="font-medium text-ink-900">{business.planName}</dd>
                    </div>
                    <div className="flex justify-between border-b border-border pb-3">
                      <dt className="text-ink-500">Billing status</dt>
                      <dd>
                        <Badge tone={BILLING_STATUS_TONE[business.billingStatus] ?? "info"}>
                          {BILLING_STATUS_LABEL[business.billingStatus] ?? business.billingStatus}
                        </Badge>
                      </dd>
                    </div>
                    <div className="flex justify-between border-b border-border pb-3">
                      <dt className="text-ink-500">{business.billingStatus === "TRIALING" ? "Trial ends" : "Renews"}</dt>
                      <dd className="font-medium text-ink-900">
                        {formatDate(business.billingStatus === "TRIALING" ? business.trialEndsAt : business.currentPeriodEndsAt)}
                      </dd>
                    </div>
                    <div className="flex justify-between">
                      <dt className="text-ink-500">Price override</dt>
                      <dd className="font-medium text-ink-900">
                        {business.priceOverride != null ? `GH₵${business.priceOverride.toFixed(2)}` : "Plan default"}
                      </dd>
                    </div>
                  </dl>
                )}

                <div className="mt-4">
                  <p className="text-sm text-ink-500">Enabled modules</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {business.enabledModules.map((m) => (
                      <Badge key={m} tone="accent">
                        {m}
                      </Badge>
                    ))}
                  </div>
                </div>
              </Card>

              <Card className="p-5">
                <h2 className="text-base font-semibold text-ink-900">Integrations</h2>
                <ul className="mt-4 space-y-3 text-sm">
                  <li className="flex items-center justify-between border-b border-border pb-3">
                    <span className="flex items-center gap-2 text-ink-700">
                      <CreditCard size={15} className="text-ink-500" />
                      Paystack
                    </span>
                    <Badge tone={business.paystackConfigured ? "success" : "neutral"}>
                      {business.paystackConfigured ? "Configured" : "Not set up"}
                    </Badge>
                  </li>
                  <li className="flex items-center justify-between border-b border-border pb-3">
                    <span className="flex items-center gap-2 text-ink-700">
                      <ShoppingBag size={15} className="text-ink-500" />
                      WooCommerce
                    </span>
                    <Badge tone={business.woocommerceConfigured ? "success" : "neutral"}>
                      {business.woocommerceConfigured ? "Configured" : "Not set up"}
                    </Badge>
                  </li>
                  <li className="flex items-center justify-between">
                    <span className="flex items-center gap-2 text-ink-700">
                      <MessageCircle size={15} className="text-ink-500" />
                      WhatsApp notify number
                    </span>
                    <Badge tone={business.whatsappConfigured ? "success" : "neutral"}>
                      {business.whatsappConfigured ? "Configured" : "Not set up"}
                    </Badge>
                  </li>
                </ul>
              </Card>
              </div>

              <Card className="p-5">
                <div className="flex items-center justify-between">
                  <h2 className="text-base font-semibold text-ink-900">Team ({business.users.length})</h2>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {Object.entries(business.staffByRole)
                    .filter(([, count]) => count > 0)
                    .map(([role, count]) => (
                      <Badge key={role} tone="neutral">
                        {count} {ROLE_LABEL[role] ?? role}
                        {count === 1 ? "" : "s"}
                      </Badge>
                    ))}
                </div>
                <ul className="mt-4 space-y-3">
                  {business.users.map((u) => (
                    <li key={u.id} className="flex items-center justify-between gap-2 border-b border-border pb-3 text-sm last:border-0 last:pb-0">
                      <div>
                        <p className="font-medium text-ink-900">{u.fullName}</p>
                        <p className="text-ink-500">{u.email}</p>
                      </div>
                      <div className="flex shrink-0 items-center gap-3">
                        <Badge tone="neutral">{ROLE_LABEL[u.role] ?? u.role}</Badge>
                        <button
                          onClick={() => handleResetPassword(u.id, u.fullName)}
                          disabled={resettingUserId === u.id}
                          className="whitespace-nowrap text-xs font-medium text-accent-hover hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {resettingUserId === u.id ? "Resetting..." : "Reset password"}
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              </Card>
            </div>
          </>
        )}
      </div>

      {showDelete && business && (
        <Modal title={`Delete ${business.name}?`} onClose={() => setShowDelete(false)}>
          <p className="text-sm text-ink-700">
            This permanently deletes the business, its {business.users.length} user account(s), and all its
            products, sales, customers, and expenses. This can&apos;t be undone.
          </p>
          <p className="mt-3 text-sm text-ink-700">
            Type <span className="font-semibold text-ink-900">{business.name}</span> to confirm.
          </p>
          <input
            value={confirmName}
            onChange={(e) => setConfirmName(e.target.value)}
            className="mt-2 w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-danger focus:outline-none focus:ring-2 focus:ring-danger/20"
          />
          <Button
            variant="danger"
            className="mt-4 w-full"
            disabled={confirmName !== business.name || busy}
            onClick={handleDelete}
          >
            {busy ? "Deleting..." : "Permanently delete this business"}
          </Button>
        </Modal>
      )}

      {resetResult && (
        <Modal title={`New password for ${resetResult.name}`} onClose={() => setResetResult(null)}>
          <p className="text-sm text-ink-700">
            Share this temporary password with them directly. They&apos;ll be asked to set their own on next login.
          </p>
          <p className="mt-3 rounded-lg bg-canvas px-3 py-2 text-center font-mono text-lg text-ink-900">
            {resetResult.password}
          </p>
        </Modal>
      )}
    </PlatformShell>
  );
}
