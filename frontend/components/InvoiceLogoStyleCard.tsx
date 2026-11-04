"use client";

import { useState } from "react";
import { AlertTriangle, Building2, Check, FileText, Eye } from "lucide-react";
import { api, ApiError, BusinessSummary, InvoiceLogoStyle } from "@/lib/api";
import { isWebp, webpToPng } from "@/lib/imageConvert";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Modal from "@/components/Modal";

const OPTIONS: { style: InvoiceLogoStyle; title: string; description: string }[] = [
  {
    style: "HEADER",
    title: "Logo at the top",
    description: "Your logo sits beside your business name at the top of the invoice. Clean, and works with any logo.",
  },
  {
    style: "WATERMARK",
    title: "Faded logo behind everything",
    description: "One large, very light copy of your logo fills the page behind the invoice, like letterhead. Best with a transparent-background logo.",
  },
];

// A tiny stand-in for the invoice page — enough to tell the two styles apart
// at a glance, drawn with the business's real logo.
function Thumbnail({ style, logoUrl }: { style: InvoiceLogoStyle; logoUrl: string | null }) {
  const logo = logoUrl ? (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={logoUrl} alt="" className="h-full w-full object-contain" />
  ) : (
    <Building2 className="h-full w-full text-ink-300" strokeWidth={1.25} />
  );

  return (
    <div className="relative mx-auto aspect-[210/297] w-28 overflow-hidden rounded border border-border bg-white p-2 shadow-card">
      {style === "WATERMARK" && (
        <div className="absolute inset-0 flex items-center justify-center p-3 opacity-20" aria-hidden>
          <div className="h-3/5 w-4/5">{logo}</div>
        </div>
      )}
      <div className="relative flex flex-col gap-1.5">
        <div className="flex items-start justify-between">
          {style === "HEADER" ? (
            <div className="flex items-center gap-1">
              <div className="h-5 w-5 shrink-0">{logo}</div>
              <div className="h-1 w-6 rounded bg-ink-300" />
            </div>
          ) : (
            <div className="h-1 w-8 rounded bg-ink-300" />
          )}
          <div className="h-3 w-9 rounded-sm bg-canvas" />
        </div>
        <div className="h-px bg-ink-900/70" />
        <div className="h-3 rounded-sm bg-canvas/80" />
        <div className="mt-1 h-2 rounded-sm bg-ink-900/80" />
        <div className="h-2 rounded-sm border border-border" />
        <div className="h-2 rounded-sm border border-border" />
        <div className="ml-auto h-2 w-1/2 rounded-sm bg-accent-soft" />
      </div>
    </div>
  );
}

export default function InvoiceLogoStyleCard({
  token,
  business,
  onSaved,
}: {
  token: string;
  business: BusinessSummary;
  onSaved: () => Promise<void>;
}) {
  const saved = business.invoiceLogoStyle ?? "HEADER";
  const [choice, setChoice] = useState<InvoiceLogoStyle>(saved);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [justSaved, setJustSaved] = useState(false);

  const [previewing, setPreviewing] = useState<InvoiceLogoStyle | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);

  const [fixing, setFixing] = useState(false);

  // A WebP logo displays fine in the browser but can't be printed onto an
  // invoice PDF — so it would silently go missing. Offer a one-click fix.
  const logoIsWebp = !!business.logoUrl && /\.webp(\?|$)/i.test(business.logoUrl);

  async function openPreview(style: InvoiceLogoStyle) {
    setPreviewing(style);
    setPreviewError(null);
    setPreviewLoading(true);
    setPreviewUrl((old) => {
      if (old) URL.revokeObjectURL(old);
      return null;
    });
    try {
      setPreviewUrl(await api.getInvoiceStylePreviewBlobUrl(token, style));
    } catch (err) {
      setPreviewError(err instanceof ApiError ? err.message : "Couldn't load the preview.");
    } finally {
      setPreviewLoading(false);
    }
  }

  function closePreview() {
    setPreviewing(null);
    setPreviewUrl((old) => {
      if (old) URL.revokeObjectURL(old);
      return null;
    });
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    setJustSaved(false);
    try {
      await api.updateInvoiceLogoStyle(token, choice);
      await onSaved();
      setJustSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save that. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  async function handleFixLogo() {
    if (!business.logoUrl) return;
    setFixing(true);
    setError(null);
    try {
      const blob = await (await fetch(business.logoUrl)).blob();
      const png = await webpToPng(new File([blob], "logo.webp", { type: "image/webp" }));
      await api.uploadBusinessLogo(token, png);
      await onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't convert your logo. Try uploading a PNG or JPEG instead.");
    } finally {
      setFixing(false);
    }
  }

  return (
    <Card className="max-w-2xl p-5">
      <div className="flex items-center gap-2">
        <FileText size={16} className="text-ink-500" />
        <h2 className="text-base font-semibold text-ink-900">Invoice logo</h2>
      </div>
      <p className="mt-1 text-xs text-ink-500">Choose how your logo appears on the invoices you send. Preview each one with your own logo first.</p>

      {!business.logoUrl && (
        <p className="mt-3 rounded-lg bg-canvas px-3 py-2 text-xs text-ink-700">
          You haven&apos;t uploaded a logo yet — add one under Business details above and it will show on your invoices.
        </p>
      )}

      {logoIsWebp && (
        <div className="mt-3 flex flex-col gap-2 rounded-lg border border-danger/30 bg-danger-soft px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="flex items-start gap-1.5 text-xs text-danger">
            <AlertTriangle size={14} className="mt-0.5 shrink-0" />
            Your logo is a WebP image, which can&apos;t be printed on invoices — so it&apos;s missing from them right now.
          </p>
          <Button type="button" variant="secondary" onClick={handleFixLogo} disabled={fixing} className="shrink-0">
            {fixing ? "Fixing..." : "Fix my logo"}
          </Button>
        </div>
      )}

      <div role="radiogroup" aria-label="Invoice logo style" className="mt-4 grid gap-3 sm:grid-cols-2">
        {OPTIONS.map((option) => {
          const selected = choice === option.style;
          return (
            <div
              key={option.style}
              className={`flex flex-col gap-3 rounded-xl border p-4 transition ${
                selected ? "border-accent ring-1 ring-accent" : "border-border"
              }`}
            >
              <button
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => {
                  setChoice(option.style);
                  setJustSaved(false);
                }}
                className="flex flex-col gap-3 text-left"
              >
                <Thumbnail style={option.style} logoUrl={business.logoUrl} />
                <div>
                  <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm font-semibold text-ink-900">
                    {option.title}
                    {selected && <Check size={14} className="text-accent" />}
                    {saved === option.style && (
                      <span className="whitespace-nowrap rounded bg-accent-soft px-1.5 py-0.5 text-[10px] font-medium text-accent-hover">
                        In use
                      </span>
                    )}
                  </p>
                  <p className="mt-1 text-xs text-ink-500">{option.description}</p>
                </div>
              </button>
              <button
                type="button"
                onClick={() => openPreview(option.style)}
                className="inline-flex items-center gap-1.5 self-start text-xs font-medium text-accent-hover hover:underline"
              >
                <Eye size={13} /> Preview invoice
              </button>
            </div>
          );
        })}
      </div>

      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
      <div className="mt-4 flex items-center gap-3">
        <Button type="button" onClick={handleSave} disabled={saving || choice === saved}>
          {saving ? "Saving..." : "Save choice"}
        </Button>
        {justSaved && <span className="text-xs text-success">Saved — new invoices will use this.</span>}
      </div>

      {previewing && (
        <Modal
          title={previewing === "HEADER" ? "Preview: logo at the top" : "Preview: faded logo behind everything"}
          onClose={closePreview}
          maxWidthClassName="max-w-3xl"
        >
          <div className="mt-3 flex flex-col gap-3">
            <div className="overflow-hidden rounded-lg border border-border bg-canvas">
              {previewLoading ? (
                <div className="flex h-[65vh] items-center justify-center text-sm text-ink-500">Loading preview...</div>
              ) : previewUrl ? (
                <iframe src={previewUrl} title="Invoice style preview" className="h-[65vh] w-full" />
              ) : (
                <div className="flex h-[65vh] items-center justify-center text-sm text-danger">
                  {previewError ?? "Couldn't load the preview."}
                </div>
              )}
            </div>
            {previewUrl && (
              <a href={previewUrl} target="_blank" rel="noopener noreferrer" className="self-end text-xs font-medium text-accent-hover hover:underline">
                Open preview in a new tab
              </a>
            )}
            <div className="flex gap-2">
              <Button
                type="button"
                onClick={() => {
                  setChoice(previewing);
                  setJustSaved(false);
                  closePreview();
                }}
              >
                Use this style
              </Button>
              <Button type="button" variant="ghost" onClick={closePreview}>
                Close
              </Button>
            </div>
            <p className="text-xs text-ink-500">This is a sample invoice with made-up details — nothing is saved until you press &ldquo;Save choice&rdquo;.</p>
          </div>
        </Modal>
      )}
    </Card>
  );
}
