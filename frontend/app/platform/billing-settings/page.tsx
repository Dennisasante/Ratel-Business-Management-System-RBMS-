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

  const load = useCallback(async () => {
    if (!session) return;
    const data = await api.getPlatformBillingSettings(session.token);
    setSettings(data);
    setTrialDays(String(data.trialDays));
    setUsdRate(data.usdDisplayRate != null ? String(data.usdDisplayRate) : "");
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
      </div>
    </PlatformShell>
  );
}
