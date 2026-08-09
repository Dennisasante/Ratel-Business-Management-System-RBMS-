"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, XCircle, Trash2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BlackoutDate, BusinessIntegrations } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";
import FormField from "@/components/FormField";

const DAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export default function IntegrationsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [data, setData] = useState<BusinessIntegrations | null>(null);
  const [fetching, setFetching] = useState(true);

  // Paystack form state
  const [paystackPublicKey, setPaystackPublicKey] = useState("");
  const [paystackSecretKey, setPaystackSecretKey] = useState("");
  const [savingPaystack, setSavingPaystack] = useState(false);
  const [testingPaystack, setTestingPaystack] = useState(false);
  const [paystackResult, setPaystackResult] = useState<{ success: boolean; message: string } | null>(null);

  // WooCommerce form state
  const [wooSiteUrl, setWooSiteUrl] = useState("");
  const [wooConsumerKey, setWooConsumerKey] = useState("");
  const [wooConsumerSecret, setWooConsumerSecret] = useState("");
  const [savingWoo, setSavingWoo] = useState(false);
  const [testingWoo, setTestingWoo] = useState(false);
  const [wooResult, setWooResult] = useState<{ success: boolean; message: string } | null>(null);

  // WhatsApp + test mode
  const [whatsappNumber, setWhatsappNumber] = useState("");
  const [savingWhatsapp, setSavingWhatsapp] = useState(false);
  const [testMode, setTestMode] = useState(false);
  const [savingTestMode, setSavingTestMode] = useState(false);

  // Booking payment policy
  const [paymentPolicy, setPaymentPolicy] = useState<"NONE" | "DEPOSIT" | "FULL">("NONE");
  const [depositPercent, setDepositPercent] = useState(50);
  const [allowPayInPerson, setAllowPayInPerson] = useState(false);
  const [savingPolicy, setSavingPolicy] = useState(false);
  const [policyError, setPolicyError] = useState<string | null>(null);

  // Cancellation cutoff
  const [cancellationCutoffHours, setCancellationCutoffHours] = useState(0);
  const [savingCutoff, setSavingCutoff] = useState(false);
  const [cutoffError, setCutoffError] = useState<string | null>(null);

  // Booking hours
  const [workingDays, setWorkingDays] = useState<number[]>([1, 2, 3, 4, 5, 6]);
  const [workingHoursStart, setWorkingHoursStart] = useState("09:00");
  const [workingHoursEnd, setWorkingHoursEnd] = useState("18:00");
  const [savingHours, setSavingHours] = useState(false);
  const [hoursError, setHoursError] = useState<string | null>(null);
  const [blackoutDates, setBlackoutDates] = useState<BlackoutDate[]>([]);
  const [newBlackoutDate, setNewBlackoutDate] = useState("");
  const [newBlackoutLabel, setNewBlackoutLabel] = useState("");
  const [addingBlackout, setAddingBlackout] = useState(false);
  const [blackoutError, setBlackoutError] = useState<string | null>(null);
  const [removingBlackoutId, setRemovingBlackoutId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const [result, blackouts] = await Promise.all([
      api.getBusinessIntegrations(session.token),
      api.listBlackoutDates(session.token),
    ]);
    setData(result);
    setPaystackPublicKey(result.paystackPublicKey ?? "");
    setWooSiteUrl(result.woocommerceSiteUrl ?? "");
    setWhatsappNumber(result.whatsappNotifyNumber ?? "");
    setTestMode(result.testMode);
    setPaymentPolicy(result.bookingPaymentPolicy);
    setDepositPercent(result.bookingDepositPercent);
    setAllowPayInPerson(result.bookingAllowPayInPerson);
    setCancellationCutoffHours(result.bookingCancellationCutoffHours);
    setWorkingDays(result.workingDays);
    setWorkingHoursStart(result.workingHoursStart.slice(0, 5));
    setWorkingHoursEnd(result.workingHoursEnd.slice(0, 5));
    setBlackoutDates(blackouts);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!loading && session && session.role !== "OWNER") router.push("/dashboard/profile");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load().finally(() => setFetching(false));
  }, [session, load]);

  async function savePaystack(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingPaystack(true);
    setPaystackResult(null);
    try {
      const result = await api.updateBusinessIntegrations(session.token, {
        paystackPublicKey,
        ...(paystackSecretKey ? { paystackSecretKey } : {}),
      });
      setData(result);
      setPaystackSecretKey("");
    } catch (err) {
      setPaystackResult({ success: false, message: err instanceof ApiError ? err.message : "Couldn't save." });
    } finally {
      setSavingPaystack(false);
    }
  }

  async function testPaystack() {
    if (!session) return;
    setTestingPaystack(true);
    setPaystackResult(null);
    try {
      setPaystackResult(await api.testPaystackIntegration(session.token));
    } catch (err) {
      setPaystackResult({ success: false, message: err instanceof ApiError ? err.message : "Couldn't test connection." });
    } finally {
      setTestingPaystack(false);
    }
  }

  async function saveWoo(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingWoo(true);
    setWooResult(null);
    try {
      const result = await api.updateBusinessIntegrations(session.token, {
        woocommerceSiteUrl: wooSiteUrl,
        ...(wooConsumerKey ? { woocommerceConsumerKey: wooConsumerKey } : {}),
        ...(wooConsumerSecret ? { woocommerceConsumerSecret: wooConsumerSecret } : {}),
      });
      setData(result);
      setWooConsumerKey("");
      setWooConsumerSecret("");
    } catch (err) {
      setWooResult({ success: false, message: err instanceof ApiError ? err.message : "Couldn't save." });
    } finally {
      setSavingWoo(false);
    }
  }

  async function testWoo() {
    if (!session) return;
    setTestingWoo(true);
    setWooResult(null);
    try {
      const result = await api.testWooCommerceIntegration(session.token);
      setWooResult(result);
      if (result.success) {
        const fresh = await api.getBusinessIntegrations(session.token);
        setData(fresh);
      }
    } catch (err) {
      setWooResult({ success: false, message: err instanceof ApiError ? err.message : "Couldn't test connection." });
    } finally {
      setTestingWoo(false);
    }
  }

  async function saveWhatsapp(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingWhatsapp(true);
    try {
      const result = await api.updateBusinessIntegrations(session.token, { whatsappNotifyNumber: whatsappNumber });
      setData(result);
    } finally {
      setSavingWhatsapp(false);
    }
  }

  async function savePolicy(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingPolicy(true);
    setPolicyError(null);
    try {
      const result = await api.updateBusinessIntegrations(session.token, {
        bookingPaymentPolicy: paymentPolicy,
        ...(paymentPolicy === "DEPOSIT" ? { bookingDepositPercent: depositPercent } : {}),
        bookingAllowPayInPerson: allowPayInPerson,
      });
      setData(result);
    } catch (err) {
      setPolicyError(err instanceof ApiError ? err.message : "Couldn't save.");
    } finally {
      setSavingPolicy(false);
    }
  }

  async function saveCutoff(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingCutoff(true);
    setCutoffError(null);
    try {
      const result = await api.updateBusinessIntegrations(session.token, {
        bookingCancellationCutoffHours: cancellationCutoffHours,
      });
      setData(result);
    } catch (err) {
      setCutoffError(err instanceof ApiError ? err.message : "Couldn't save.");
    } finally {
      setSavingCutoff(false);
    }
  }

  function toggleWorkingDay(day: number) {
    setWorkingDays((prev) => (prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day].sort()));
  }

  async function saveHours(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingHours(true);
    setHoursError(null);
    try {
      const result = await api.updateBusinessIntegrations(session.token, {
        workingDays,
        workingHoursStart,
        workingHoursEnd,
      });
      setData(result);
    } catch (err) {
      setHoursError(err instanceof ApiError ? err.message : "Couldn't save.");
    } finally {
      setSavingHours(false);
    }
  }

  async function addBlackoutDate(e: React.FormEvent) {
    e.preventDefault();
    if (!session || !newBlackoutDate) return;
    setAddingBlackout(true);
    setBlackoutError(null);
    try {
      const created = await api.addBlackoutDate(session.token, { date: newBlackoutDate, label: newBlackoutLabel || undefined });
      setBlackoutDates((prev) => [...prev, created].sort((a, b) => a.date.localeCompare(b.date)));
      setNewBlackoutDate("");
      setNewBlackoutLabel("");
    } catch (err) {
      setBlackoutError(err instanceof ApiError ? err.message : "Couldn't add that date.");
    } finally {
      setAddingBlackout(false);
    }
  }

  async function removeBlackoutDate(id: string) {
    if (!session) return;
    setRemovingBlackoutId(id);
    try {
      await api.removeBlackoutDate(session.token, id);
      setBlackoutDates((prev) => prev.filter((b) => b.id !== id));
    } finally {
      setRemovingBlackoutId(null);
    }
  }

  async function toggleTestMode() {
    if (!session) return;
    setSavingTestMode(true);
    try {
      const next = !testMode;
      const result = await api.updateBusinessIntegrations(session.token, { testMode: next });
      setData(result);
      setTestMode(result.testMode);
    } finally {
      setSavingTestMode(false);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link href="/dashboard/profile" className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-900">
          <ArrowLeft size={14} /> Back to profile
        </Link>
      </div>

      <PageHeader
        title="Integrations"
        subtitle="Your own Paystack and WooCommerce credentials — separate from Ratel's own billing, this is how your customers pay you directly."
      />

      {fetching || !data ? (
        <CardSkeleton count={3} />
      ) : (
        <>
          <Card className="max-w-2xl p-5">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-ink-900">Test mode</h2>
              <button
                onClick={toggleTestMode}
                disabled={savingTestMode}
                className={`relative h-6 w-11 rounded-full transition disabled:opacity-50 ${testMode ? "bg-accent" : "bg-border"}`}
              >
                <span
                  className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition ${testMode ? "left-5" : "left-0.5"}`}
                />
              </button>
            </div>
            <p className="mt-1 text-xs text-ink-500">
              Turn this on while you&apos;re setting up a client&apos;s site — test bookings won&apos;t look like real business
              until you turn it back off.
            </p>
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">Paystack</h2>
            <p className="mt-1 text-xs text-ink-500">
              Your own Paystack account (Settings → API Keys &amp; Webhooks) — your customers pay you directly through
              these, separate from your Ratel subscription.
            </p>

            <form onSubmit={savePaystack} className="mt-4 flex flex-col gap-3">
              <FormField
                label="Public key"
                name="paystackPublicKey"
                value={paystackPublicKey}
                onChange={setPaystackPublicKey}
                placeholder="pk_live_..."
              />
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Secret key</label>
                {data.paystackSecretConfigured && (
                  <p className="text-xs text-ink-500">Currently set: {data.paystackSecretMasked}</p>
                )}
                <input
                  type="password"
                  value={paystackSecretKey}
                  onChange={(e) => setPaystackSecretKey(e.target.value)}
                  placeholder={data.paystackSecretConfigured ? "Enter a new key to change it" : "sk_live_..."}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
              </div>

              {paystackResult && (
                <p className={`flex items-center gap-1.5 text-sm ${paystackResult.success ? "text-success" : "text-danger"}`}>
                  {paystackResult.success ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
                  {paystackResult.message}
                </p>
              )}

              <div className="flex gap-2">
                <Button type="submit" disabled={savingPaystack} variant="secondary">
                  {savingPaystack ? "Saving..." : "Save"}
                </Button>
                <Button type="button" onClick={testPaystack} disabled={testingPaystack || !data.paystackSecretConfigured}>
                  {testingPaystack ? "Testing..." : "Test connection"}
                </Button>
              </div>
            </form>
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">Booking payments</h2>
            <p className="mt-1 text-xs text-ink-500">
              Whether a customer has to pay before their booking counts as confirmed. Needs a Paystack secret key
              above — until one&apos;s saved, bookings are confirmed without payment regardless of this setting.
            </p>

            <form onSubmit={savePolicy} className="mt-4 flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Policy</label>
                <select
                  value={paymentPolicy}
                  onChange={(e) => setPaymentPolicy(e.target.value as "NONE" | "DEPOSIT" | "FULL")}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                >
                  <option value="NONE">No payment required — pay in person</option>
                  <option value="DEPOSIT">Require a deposit to confirm</option>
                  <option value="FULL">Require full payment to confirm</option>
                </select>
              </div>

              {paymentPolicy === "DEPOSIT" && (
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-ink-700">Deposit percentage</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      min={1}
                      max={99}
                      value={depositPercent}
                      onChange={(e) => setDepositPercent(Number(e.target.value))}
                      className="w-24 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                    />
                    <span className="text-sm text-ink-500">% of the service price, paid now — the rest in person</span>
                  </div>
                </div>
              )}

              {paymentPolicy !== "NONE" && (
                <div className="flex items-center gap-2 rounded-lg bg-canvas px-3 py-2">
                  <input
                    id="allowPayInPerson"
                    type="checkbox"
                    checked={allowPayInPerson}
                    onChange={(e) => setAllowPayInPerson(e.target.checked)}
                    className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
                  />
                  <label htmlFor="allowPayInPerson" className="text-sm text-ink-700">
                    Let customers choose to pay in person instead
                  </label>
                </div>
              )}

              {policyError && <p className="text-sm text-danger">{policyError}</p>}

              <Button type="submit" disabled={savingPolicy} className="self-start">
                {savingPolicy ? "Saving..." : "Save"}
              </Button>
            </form>
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">Cancellation policy</h2>
            <p className="mt-1 text-xs text-ink-500">
              Owner sets a rule like &ldquo;no cancellation within 1–2 hours of the appointment&rdquo; — applies to both
              cancelling and rescheduling a booking.
            </p>

            <form onSubmit={saveCutoff} className="mt-4 flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Cancellation cutoff (hours before appointment)</label>
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    min={0}
                    value={cancellationCutoffHours}
                    onChange={(e) => setCancellationCutoffHours(Number(e.target.value))}
                    placeholder="e.g. 1 or 2"
                    className="w-24 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                  <span className="text-sm text-ink-500">0 = no restriction</span>
                </div>
              </div>

              {cutoffError && <p className="text-sm text-danger">{cutoffError}</p>}

              <Button type="submit" disabled={savingCutoff} className="self-start">
                {savingCutoff ? "Saving..." : "Save"}
              </Button>
            </form>
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">Booking hours</h2>
            <p className="mt-1 text-xs text-ink-500">
              When online bookings are allowed. A customer can&apos;t pick a day, time, or date outside of this — it&apos;s
              enforced on the server, not just hidden in the widget.
            </p>

            <form onSubmit={saveHours} className="mt-4 flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Open days</label>
                <div className="flex flex-wrap gap-1.5">
                  {DAY_LABELS.map((label, i) => {
                    const day = i + 1;
                    const active = workingDays.includes(day);
                    return (
                      <button
                        type="button"
                        key={day}
                        onClick={() => toggleWorkingDay(day)}
                        className={`rounded-lg border px-2.5 py-1.5 text-xs font-medium transition ${
                          active ? "border-accent bg-accent-soft text-accent-hover" : "border-border text-ink-500 hover:bg-canvas"
                        }`}
                      >
                        {label}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-ink-700">Opens at</label>
                  <input
                    type="time"
                    value={workingHoursStart}
                    onChange={(e) => setWorkingHoursStart(e.target.value)}
                    className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-ink-700">Closes at</label>
                  <input
                    type="time"
                    value={workingHoursEnd}
                    onChange={(e) => setWorkingHoursEnd(e.target.value)}
                    className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                </div>
              </div>

              {hoursError && <p className="text-sm text-danger">{hoursError}</p>}

              <Button type="submit" disabled={savingHours} className="self-start">
                {savingHours ? "Saving..." : "Save"}
              </Button>
            </form>

            <div className="mt-5 border-t border-border pt-4">
              <label className="text-sm font-medium text-ink-700">Closed dates</label>
              <p className="mt-1 text-xs text-ink-500">Holidays, staff retreats — any specific day you&apos;re not taking bookings.</p>

              <form onSubmit={addBlackoutDate} className="mt-3 flex items-end gap-2">
                <input
                  type="date"
                  value={newBlackoutDate}
                  onChange={(e) => setNewBlackoutDate(e.target.value)}
                  required
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
                <input
                  type="text"
                  value={newBlackoutLabel}
                  onChange={(e) => setNewBlackoutLabel(e.target.value)}
                  placeholder="Label (optional)"
                  className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
                <Button type="submit" disabled={addingBlackout || !newBlackoutDate} variant="secondary">
                  {addingBlackout ? "Adding..." : "Add"}
                </Button>
              </form>
              {blackoutError && <p className="mt-2 text-sm text-danger">{blackoutError}</p>}

              {blackoutDates.length > 0 && (
                <ul className="mt-3 flex flex-col gap-1.5">
                  {blackoutDates.map((b) => (
                    <li key={b.id} className="flex items-center justify-between rounded-lg bg-canvas px-3 py-2 text-sm">
                      <span className="text-ink-900">
                        {new Date(b.date + "T00:00:00").toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}
                        {b.label ? ` · ${b.label}` : ""}
                      </span>
                      <button
                        onClick={() => removeBlackoutDate(b.id)}
                        disabled={removingBlackoutId === b.id}
                        className="text-ink-500 hover:text-danger disabled:opacity-50"
                        aria-label="Remove"
                      >
                        <Trash2 size={14} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">WooCommerce</h2>
            <p className="mt-1 text-xs text-ink-500">
              From your site&apos;s WooCommerce → Settings → Advanced → REST API. Testing the connection also registers
              the order webhook automatically — nothing to set up in WordPress yourself.
            </p>

            <form onSubmit={saveWoo} className="mt-4 flex flex-col gap-3">
              <FormField
                label="Site URL"
                name="wooSiteUrl"
                value={wooSiteUrl}
                onChange={setWooSiteUrl}
                placeholder="https://yourshop.com"
              />
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Consumer key</label>
                {data.woocommerceConfigured && (
                  <p className="text-xs text-ink-500">Currently set: {data.woocommerceConsumerKeyMasked}</p>
                )}
                <input
                  type="password"
                  value={wooConsumerKey}
                  onChange={(e) => setWooConsumerKey(e.target.value)}
                  placeholder={data.woocommerceConfigured ? "Enter a new key to change it" : "ck_..."}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Consumer secret</label>
                <input
                  type="password"
                  value={wooConsumerSecret}
                  onChange={(e) => setWooConsumerSecret(e.target.value)}
                  placeholder={data.woocommerceConfigured ? "Enter a new secret to change it" : "cs_..."}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
              </div>

              {wooResult && (
                <p className={`flex items-center gap-1.5 text-sm ${wooResult.success ? "text-success" : "text-danger"}`}>
                  {wooResult.success ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
                  {wooResult.message}
                </p>
              )}
              {data.woocommerceWebhookRegistered && (
                <p className="text-xs text-success">Order webhook is registered — new orders will sync automatically.</p>
              )}

              <div className="flex gap-2">
                <Button type="submit" disabled={savingWoo} variant="secondary">
                  {savingWoo ? "Saving..." : "Save"}
                </Button>
                <Button type="button" onClick={testWoo} disabled={testingWoo || !data.woocommerceConfigured}>
                  {testingWoo ? "Testing..." : "Test connection"}
                </Button>
              </div>
            </form>
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">WhatsApp</h2>
            <p className="mt-1 text-xs text-ink-500">
              Where &quot;new booking&quot; notifications get a one-tap WhatsApp link sent to. E.164 format, e.g. +233550995080.
            </p>
            <form onSubmit={saveWhatsapp} className="mt-4 flex items-end gap-2">
              <div className="flex-1">
                <FormField
                  label="Notify number"
                  name="whatsappNumber"
                  value={whatsappNumber}
                  onChange={setWhatsappNumber}
                  placeholder="+233550995080"
                />
              </div>
              <Button type="submit" disabled={savingWhatsapp}>
                {savingWhatsapp ? "Saving..." : "Save"}
              </Button>
            </form>
          </Card>
        </>
      )}
    </div>
  );
}
