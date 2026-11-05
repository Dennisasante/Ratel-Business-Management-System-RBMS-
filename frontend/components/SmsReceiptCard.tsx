"use client";

import { useEffect, useState } from "react";
import { Check, Receipt } from "lucide-react";
import { api, ApiError, SmsAccount, SmsReceiptMode, SmsReceiptPreview } from "@/lib/api";
import Card from "@/components/ui/Card";

const OPTIONS: { mode: SmsReceiptMode; title: string; description: string }[] = [
  { mode: "PAPER", title: "Paper only", description: "No text message. Receipts print as they do today." },
  { mode: "SMS", title: "SMS only", description: "The customer is texted a short receipt the moment a sale is paid. No paper pops up." },
  { mode: "BOTH", title: "Both", description: "Text the customer and print a paper receipt too." },
];

export default function SmsReceiptCard({
  token,
  account,
  onChanged,
}: {
  token: string;
  account: SmsAccount;
  onChanged: () => Promise<void>;
}) {
  const [preview, setPreview] = useState<SmsReceiptPreview | null>(null);
  const [saving, setSaving] = useState<SmsReceiptMode | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.getSmsReceiptPreview(token).then(setPreview).catch(() => setPreview(null));
  }, [token]);

  async function choose(mode: SmsReceiptMode) {
    if (mode === account.receiptMode || saving) return;
    setSaving(mode);
    setError(null);
    try {
      await api.setSmsReceiptMode(token, mode);
      await onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save that. Please try again.");
    } finally {
      setSaving(null);
    }
  }

  const sendsSms = account.receiptMode !== "PAPER";

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-base font-semibold text-ink-900">Customer receipts</h2>
      <Card className="max-w-2xl p-5">
        <div className="flex items-center gap-2">
          <Receipt size={16} className="text-ink-500" />
          <p className="text-sm text-ink-700">How should customers get their receipt after a sale?</p>
        </div>

        <div role="radiogroup" aria-label="Receipt type" className="mt-3 grid gap-2 sm:grid-cols-3">
          {OPTIONS.map((option) => {
            const selected = account.receiptMode === option.mode;
            return (
              <button
                key={option.mode}
                type="button"
                role="radio"
                aria-checked={selected}
                disabled={saving !== null}
                onClick={() => choose(option.mode)}
                className={`flex flex-col gap-1 rounded-xl border p-3 text-left transition disabled:opacity-60 ${
                  selected ? "border-accent ring-1 ring-accent" : "border-border hover:bg-canvas"
                }`}
              >
                <span className="flex items-center gap-1.5 text-sm font-semibold text-ink-900">
                  {option.title}
                  {selected && <Check size={14} className="text-accent" />}
                </span>
                <span className="text-xs text-ink-500">{option.description}</span>
              </button>
            );
          })}
        </div>
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}

        {sendsSms && (
          <div className="mt-4 flex flex-col gap-2">
            <p className="text-xs font-medium uppercase tracking-wide text-ink-500">What the customer receives</p>
            <p className="rounded-lg bg-canvas px-3 py-2 text-sm text-ink-900">{preview ? preview.message : "..."}</p>
            {preview && (
              <p className="text-xs text-ink-500">
                {preview.length} characters · {preview.segments} credit{preview.segments === 1 ? "" : "s"} per receipt (sample details shown).
              </p>
            )}
            <ul className="list-disc pl-5 text-xs text-ink-500">
              <li>It sends on its own as soon as a sale is fully paid, and uses your SMS credits.</li>
              <li>If a customer has no phone number, or you run out of credits, the sale still goes through and staff see a note.</li>
              <li>A discount, when there is one, shows as &ldquo;saved&rdquo;. Very long business names drop the greeting first, then the booking link.</li>
            </ul>
            {!account.senderApproved && (
              <p className="text-xs text-danger">Texts won&apos;t go out until your sender name is approved — contact support.</p>
            )}
          </div>
        )}
      </Card>
    </section>
  );
}
