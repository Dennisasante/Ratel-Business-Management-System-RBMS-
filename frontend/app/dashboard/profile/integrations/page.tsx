"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, XCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessIntegrations } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";
import FormField from "@/components/FormField";

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
  const [notifyOnSale, setNotifyOnSale] = useState(true);
  const [savingNotifyOnSale, setSavingNotifyOnSale] = useState(false);

  // Receipt printer (e.g. Xprinter XP-80T)
  const [printerEnabled, setPrinterEnabled] = useState(false);
  const [savingPrinterEnabled, setSavingPrinterEnabled] = useState(false);
  const [printerPaperWidth, setPrinterPaperWidth] = useState<"58" | "80">("80");
  const [savingPrinterPaperWidth, setSavingPrinterPaperWidth] = useState(false);

  // Dashboard's "Low Margin Products" threshold
  const [minMarginInput, setMinMarginInput] = useState("15");
  const [savingMinMargin, setSavingMinMargin] = useState(false);
  const [minMarginResult, setMinMarginResult] = useState<{ success: boolean; message: string } | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const result = await api.getBusinessIntegrations(session.token);
    setData(result);
    setPaystackPublicKey(result.paystackPublicKey ?? "");
    setWooSiteUrl(result.woocommerceSiteUrl ?? "");
    setWhatsappNumber(result.whatsappNotifyNumber ?? "");
    setTestMode(result.testMode);
    setNotifyOnSale(result.notifyOnSale);
    setPrinterEnabled(result.receiptPrinterEnabled);
    setPrinterPaperWidth(result.receiptPrinterPaperWidth);
    setMinMarginInput(String(result.minProfitMarginPercent));
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

  async function toggleNotifyOnSale() {
    if (!session) return;
    setSavingNotifyOnSale(true);
    try {
      const next = !notifyOnSale;
      const result = await api.updateBusinessIntegrations(session.token, { notifyOnSale: next });
      setData(result);
      setNotifyOnSale(result.notifyOnSale);
    } finally {
      setSavingNotifyOnSale(false);
    }
  }

  async function togglePrinterEnabled() {
    if (!session) return;
    setSavingPrinterEnabled(true);
    try {
      const next = !printerEnabled;
      const result = await api.updateBusinessIntegrations(session.token, { receiptPrinterEnabled: next });
      setData(result);
      setPrinterEnabled(result.receiptPrinterEnabled);
    } finally {
      setSavingPrinterEnabled(false);
    }
  }

  async function changePrinterPaperWidth(width: "58" | "80") {
    if (!session || width === printerPaperWidth) return;
    setSavingPrinterPaperWidth(true);
    try {
      const result = await api.updateBusinessIntegrations(session.token, { receiptPrinterPaperWidth: width });
      setData(result);
      setPrinterPaperWidth(result.receiptPrinterPaperWidth);
    } finally {
      setSavingPrinterPaperWidth(false);
    }
  }

  async function saveMinMargin(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    const parsed = Number(minMarginInput);
    if (Number.isNaN(parsed) || parsed < 0 || parsed > 100) {
      setMinMarginResult({ success: false, message: "Enter a number between 0 and 100." });
      return;
    }
    setSavingMinMargin(true);
    setMinMarginResult(null);
    try {
      const result = await api.updateBusinessIntegrations(session.token, { minProfitMarginPercent: parsed });
      setData(result);
      setMinMarginInput(String(result.minProfitMarginPercent));
      setMinMarginResult({ success: true, message: "Saved." });
    } catch (err) {
      setMinMarginResult({ success: false, message: err instanceof ApiError ? err.message : "Couldn't save." });
    } finally {
      setSavingMinMargin(false);
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
        subtitle="Your own Paystack and WooCommerce credentials — separate from Tallia's own billing, this is how your customers pay you directly."
      />

      {fetching || !data ? (
        <CardSkeleton count={3} />
      ) : (
        <>
          <Card className="max-w-2xl border-accent/30 bg-accent-soft p-4">
            <p className="text-sm text-ink-700">
              Looking for booking payment policy, hours, or closed dates? Those moved to{" "}
              <Link href="/dashboard/bookings/settings" className="font-medium text-accent-hover underline">
                Bookings → Booking settings
              </Link>
              .
            </p>
          </Card>

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
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-ink-900">Notify on sale</h2>
              <button
                onClick={toggleNotifyOnSale}
                disabled={savingNotifyOnSale}
                className={`relative h-6 w-11 rounded-full transition disabled:opacity-50 ${notifyOnSale ? "bg-accent" : "bg-border"}`}
              >
                <span
                  className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition ${notifyOnSale ? "left-5" : "left-0.5"}`}
                />
              </button>
            </div>
            <p className="mt-1 text-xs text-ink-500">
              When on, Owners and Managers get a notification (in the bell, top right) every time anyone on the team
              records a sale. Turn it off if that gets too noisy.
            </p>
          </Card>

          <Card className="max-w-2xl p-5">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-ink-900">Receipt printer</h2>
              <button
                onClick={togglePrinterEnabled}
                disabled={savingPrinterEnabled}
                className={`relative h-6 w-11 rounded-full transition disabled:opacity-50 ${printerEnabled ? "bg-accent" : "bg-border"}`}
              >
                <span
                  className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition ${printerEnabled ? "left-5" : "left-0.5"}`}
                />
              </button>
            </div>
            <p className="mt-1 text-xs text-ink-500">
              For a thermal receipt printer (e.g. an Xprinter XP-80T, USB or a paired Bluetooth model) set up as a
              regular printer on this computer. When on, completing a sale opens the print dialog automatically at
              the paper width below — no printer, no need to turn this on.
            </p>
            {printerEnabled && (
              <div className="mt-3 flex items-center gap-2">
                <span className="text-sm font-medium text-ink-700">Paper width</span>
                <div className="flex rounded-lg border border-border bg-surface p-0.5 text-sm">
                  {(["58", "80"] as const).map((w) => (
                    <button
                      key={w}
                      onClick={() => changePrinterPaperWidth(w)}
                      disabled={savingPrinterPaperWidth}
                      className={`rounded-md px-3 py-1.5 font-medium transition disabled:opacity-50 ${
                        printerPaperWidth === w ? "bg-accent-soft text-accent-hover" : "text-ink-500 hover:text-ink-900"
                      }`}
                    >
                      {w}mm
                    </button>
                  ))}
                </div>
              </div>
            )}
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">Minimum profit margin</h2>
            <p className="mt-1 text-xs text-ink-500">
              A product below this gross margin shows under &quot;Low Margin Products&quot; on your Dashboard — a heads-up
              that it might be underpriced or costing more than it should.
            </p>
            <form onSubmit={saveMinMargin} className="mt-3 flex items-end gap-2">
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Minimum margin</label>
                <div className="flex items-center gap-1.5">
                  <input
                    type="number"
                    min={0}
                    max={100}
                    step="0.01"
                    value={minMarginInput}
                    onChange={(e) => setMinMarginInput(e.target.value)}
                    className="w-24 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                  <span className="text-sm text-ink-500">%</span>
                </div>
              </div>
              <Button type="submit" disabled={savingMinMargin}>
                {savingMinMargin ? "Saving..." : "Save"}
              </Button>
            </form>
            {minMarginResult && (
              <p className={`mt-2 flex items-center gap-1.5 text-sm ${minMarginResult.success ? "text-success" : "text-danger"}`}>
                {minMarginResult.success ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
                {minMarginResult.message}
              </p>
            )}
          </Card>

          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">Paystack</h2>
            <p className="mt-1 text-xs text-ink-500">
              Your own Paystack account (Settings → API Keys &amp; Webhooks) — your customers pay you directly through
              these, separate from your Tallia subscription.
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
