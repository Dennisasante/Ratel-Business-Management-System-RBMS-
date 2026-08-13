"use client";

import { useEffect, useState, useCallback } from "react";
import { usePlatformAuth } from "@/lib/platformAuth";
import { api, ApiError, PlatformBillingSettings } from "@/lib/api";
import PlatformShell from "@/components/platform/PlatformShell";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import CardSkeleton from "@/components/ui/CardSkeleton";
import Button from "@/components/ui/Button";
import FormField from "@/components/FormField";

export default function PlatformBillingSettingsPage() {
  const { session } = usePlatformAuth();
  const [settings, setSettings] = useState<PlatformBillingSettings | null>(null);
  const [fetching, setFetching] = useState(true);
  const [trialDays, setTrialDays] = useState("14");
  const [usdRate, setUsdRate] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const [paystackPublicKey, setPaystackPublicKey] = useState("");
  const [paystackSecretKey, setPaystackSecretKey] = useState("");
  const [savingPaystack, setSavingPaystack] = useState(false);
  const [paystackError, setPaystackError] = useState<string | null>(null);
  const [paystackSaved, setPaystackSaved] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    const data = await api.getPlatformBillingSettings(session.token);
    setSettings(data);
    setTrialDays(String(data.trialDays));
    setUsdRate(data.usdDisplayRate != null ? String(data.usdDisplayRate) : "");
    setPaystackPublicKey(data.paystackPublicKey ?? "");
  }, [session]);

  useEffect(() => {
    load().finally(() => setFetching(false));
  }, [load]);

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setError(null);
    setSaved(false);
    setSaving(true);
    try {
      const updated = await api.updatePlatformBillingSettings(session.token, {
        trialDays: Number(trialDays) || 0,
        usdDisplayRate: usdRate.trim() === "" ? null : Number(usdRate),
      });
      setSettings(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  async function handleSavePaystack(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setPaystackError(null);
    setPaystackSaved(false);
    setSavingPaystack(true);
    try {
      const updated = await api.updatePlatformBillingSettings(session.token, {
        trialDays: Number(trialDays) || 0,
        usdDisplayRate: usdRate.trim() === "" ? null : Number(usdRate),
        paystackPublicKey,
        ...(paystackSecretKey.trim() ? { paystackSecretKey } : {}),
      });
      setSettings(updated);
      setPaystackSecretKey("");
      setPaystackSaved(true);
    } catch (err) {
      setPaystackError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSavingPaystack(false);
    }
  }

  return (
    <PlatformShell>
      <div className="flex flex-col gap-6">
        <PageHeader title="Billing Settings" subtitle="Trial length and the manual GHS/USD display rate" />

        {fetching || !settings ? (
          <CardSkeleton count={1} />
        ) : (
          <Card className="max-w-lg p-5">
            <form onSubmit={handleSave} className="flex flex-col gap-4">
              <FormField
                label="Trial length (days)"
                name="trialDays"
                type="number"
                required
                value={trialDays}
                onChange={setTrialDays}
              />
              <p className="-mt-3 text-xs text-ink-500">Applied to every new business at signup.</p>

              <FormField
                label="USD display rate (GHS per $1, optional)"
                name="usdRate"
                type="number"
                value={usdRate}
                onChange={setUsdRate}
                placeholder="e.g. 15.20"
              />
              <p className="-mt-3 text-xs text-ink-500">
                Update this whenever the real exchange rate moves. Leave blank to hide the GHS/USD toggle on
                businesses&apos; Billing pages entirely — checkout always charges GHS either way.
              </p>

              {error && <p className="text-sm text-danger">{error}</p>}
              {saved && !error && <p className="text-sm text-success">Saved.</p>}

              <Button type="submit" disabled={saving} className="mt-1 w-full sm:w-auto">
                {saving ? "Saving..." : "Save changes"}
              </Button>
            </form>
          </Card>
        )}

        {fetching || !settings ? (
          <CardSkeleton count={1} />
        ) : (
          <Card className="max-w-lg p-5">
            <h2 className="text-base font-semibold text-ink-900">Paystack (platform billing)</h2>
            <p className="mt-1 text-sm text-ink-500">
              Tallia&apos;s own Paystack account — what businesses pay <em>you</em> through when they subscribe.
              Separate from each business&apos;s own Paystack keys, which they set themselves under their own
              Bookings/Integrations settings.
            </p>

            <form onSubmit={handleSavePaystack} className="mt-4 flex flex-col gap-4">
              <FormField
                label="Public key"
                name="paystackPublicKey"
                value={paystackPublicKey}
                onChange={setPaystackPublicKey}
                placeholder="pk_live_..."
              />
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Secret key</label>
                {settings.paystackSecretConfigured && (
                  <p className="text-xs text-ink-500">A secret key is already configured.</p>
                )}
                <input
                  type="password"
                  value={paystackSecretKey}
                  onChange={(e) => setPaystackSecretKey(e.target.value)}
                  placeholder={settings.paystackSecretConfigured ? "Enter a new key to change it" : "sk_live_..."}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
              </div>

              {paystackError && <p className="text-sm text-danger">{paystackError}</p>}
              {paystackSaved && !paystackError && <p className="text-sm text-success">Saved.</p>}

              <Button type="submit" disabled={savingPaystack} className="mt-1 w-full sm:w-auto">
                {savingPaystack ? "Saving..." : "Save Paystack keys"}
              </Button>
            </form>
          </Card>
        )}
      </div>
    </PlatformShell>
  );
}
