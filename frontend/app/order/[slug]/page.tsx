"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Sparkles,
  User,
  Mail,
  Phone,
  Camera,
  ArrowLeft,
  CheckCircle2,
  MessageCircle,
  Check,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import {
  api,
  ApiError,
  PublicCustomWigConfig,
  CustomWigRequestCreated,
  CustomItemAttribute,
  CustomItemAttributeOption,
} from "@/lib/api";
import PoweredByRatel from "@/components/PoweredByRatel";

const ACCENT = "#a76545";

function formatMoney(amount: number, currency: string) {
  return `${currency} ${amount.toFixed(2)}`;
}

// attributeId -> selected optionIds. A SINGLE attribute holds 0-1 entries,
// a MULTIPLE attribute holds 0+ — same shape either way, so the rest of the
// page never needs to branch on selection type except for card behavior.
type Selections = Record<string, string[]>;

interface StepDef {
  id: string;
  kind: "attributes" | "reference" | "review";
  title: string;
  attributeIds: string[];
}

// Consecutive attributes sharing a stepGroup render as one step (e.g. Length
// + Texture under "Choose your hair"); ungrouped attributes each get their
// own step, titled by the attribute's own name. Purely data-driven off
// whatever the owner configured — no hard-coded attribute list.
function buildAttributeSteps(attributes: CustomItemAttribute[]): StepDef[] {
  const steps: StepDef[] = [];
  for (const attr of attributes) {
    const prev = steps[steps.length - 1];
    if (attr.stepGroup && prev && prev.title === attr.stepGroup) {
      prev.attributeIds.push(attr.id);
    } else {
      steps.push({ id: attr.id, kind: "attributes", title: attr.stepGroup || attr.name, attributeIds: [attr.id] });
    }
  }
  return steps;
}

function OptionCard({
  label,
  priceModifier,
  currency,
  selected,
  manualQuote,
  onClick,
}: {
  label: string;
  priceModifier: number;
  currency: string;
  selected: boolean;
  manualQuote: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="relative flex flex-col items-start gap-1 rounded-2xl border-[1.5px] px-3.5 py-3 text-left transition"
      style={{ borderColor: selected ? ACCENT : "#ece1d2", background: selected ? "rgba(167,101,69,0.08)" : "#fff" }}
    >
      {selected && (
        <span
          className="absolute right-2 top-2 flex items-center justify-center rounded-full text-white"
          style={{ background: ACCENT, width: 18, height: 18 }}
        >
          <Check size={11} strokeWidth={3} />
        </span>
      )}
      <span className="pr-5 text-sm font-medium text-[#2a2018]">{label}</span>
      <span className="text-xs font-medium" style={{ color: manualQuote ? ACCENT : "#9a8c7c" }}>
        {manualQuote ? "Custom quote" : priceModifier > 0 ? `+${formatMoney(priceModifier, currency)}` : "Included"}
      </span>
    </button>
  );
}

function SelectedSummaryList({ config, selections }: { config: PublicCustomWigConfig; selections: Selections }) {
  const rows = config.attributes
    .map((attr) => {
      const labels = (selections[attr.id] ?? [])
        .map((id) => attr.options.find((o) => o.id === id)?.label)
        .filter((l): l is string => Boolean(l));
      return labels.length ? { name: attr.name, value: labels.join(", ") } : null;
    })
    .filter((r): r is { name: string; value: string } => Boolean(r));

  if (!rows.length) return <p className="text-xs text-[#9a8c7c]">Nothing selected yet.</p>;

  return (
    <dl className="flex flex-col gap-2">
      {rows.map((r) => (
        <div key={r.name} className="flex items-baseline justify-between gap-3 text-sm">
          <dt className="shrink-0 text-[#7a6d5f]">{r.name}</dt>
          <dd className="text-right font-medium text-[#2a2018]">{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}

export default function CustomOrderPage() {
  const params = useParams<{ slug: string }>();
  const slug = params.slug;

  const [config, setConfig] = useState<PublicCustomWigConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [stepIndex, setStepIndex] = useState(0);
  const [selections, setSelections] = useState<Selections>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [customerWhatsapp, setCustomerWhatsapp] = useState("");
  const [notes, setNotes] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [created, setCreated] = useState<CustomWigRequestCreated | null>(null);

  const load = useCallback(async () => {
    try {
      const cfg = await api.getCustomWigConfigBySlug(slug);
      setConfig(cfg);
      const defaults: Selections = {};
      cfg.attributes.forEach((attr) => {
        defaults[attr.id] = attr.selectionType === "MULTIPLE" || !attr.options.length ? [] : [attr.options[0].id];
      });
      setSelections(defaults);
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : "Couldn't load this order page.");
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => {
    load();
  }, [load]);

  const attributeSteps = config ? buildAttributeSteps(config.attributes) : [];
  const steps: StepDef[] = [
    ...attributeSteps,
    { id: "reference", kind: "reference", title: "Make it yours", attributeIds: [] },
    { id: "review", kind: "review", title: "Review your wig", attributeIds: [] },
  ];
  const currentStep = steps[stepIndex];

  const attrById = new Map((config?.attributes ?? []).map((a) => [a.id, a]));
  const selectedFlat: { option: CustomItemAttributeOption }[] = [];
  (config?.attributes ?? []).forEach((attr) => {
    (selections[attr.id] ?? []).forEach((optId) => {
      const option = attr.options.find((o) => o.id === optId);
      if (option) selectedFlat.push({ option });
    });
  });
  const total = selectedFlat.reduce((sum, s) => sum + Number(s.option.priceModifier), 0);
  const hasManualQuote = selectedFlat.some((s) => s.option.requiresManualQuote);

  const showBuilder = !loading && !loadError && !!config && config.enabled && config.attributes.length > 0 && !created;

  function selectSingle(attrId: string, optId: string) {
    setSelections((prev) => ({ ...prev, [attrId]: [optId] }));
  }

  function toggleMultiple(attrId: string, optId: string) {
    setSelections((prev) => {
      const current = prev[attrId] ?? [];
      const next = current.includes(optId) ? current.filter((id) => id !== optId) : [...current, optId];
      return { ...prev, [attrId]: next };
    });
  }

  function handlePhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setPhoto(file);
    const reader = new FileReader();
    reader.onload = (ev) => setPhotoPreview(ev.target?.result as string);
    reader.readAsDataURL(file);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!config) return;
    setSubmitError(null);
    setSubmitting(true);
    try {
      const result = await api.submitCustomWigRequest(
        config.businessId,
        {
          customerName,
          customerEmail,
          customerWhatsapp,
          selections: Object.entries(selections).flatMap(([attributeId, optionIds]) =>
            optionIds.map((optionId) => ({ attributeId, optionId }))
          ),
          notes: notes || undefined,
        },
        photo
      );
      setCreated(result);
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : "Couldn't submit that request.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main
      className={`min-h-screen px-4 py-8 lg:py-12 ${showBuilder ? "pb-28 lg:pb-12" : ""}`}
      style={{ background: "radial-gradient(1200px circle at 50% -10%, rgba(167,101,69,0.14), transparent 55%), #fdfaf6" }}
    >
      <div
        className={
          showBuilder
            ? "mx-auto flex w-full max-w-5xl flex-col gap-6 lg:grid lg:grid-cols-[1fr_340px] lg:items-start"
            : "mx-auto flex w-full max-w-md flex-col items-center"
        }
      >
        <div
          className="w-full rounded-3xl p-6 sm:p-8"
          style={{ background: "#fff", border: "1px solid #f0e6d8", boxShadow: "0 1px 2px rgba(42,32,24,0.04), 0 24px 48px -16px rgba(42,32,24,0.18)" }}
        >
          {loading && <p className="text-sm text-[#9a8c7c]">Loading...</p>}
          {!loading && loadError && <p className="text-sm text-[#b1432e]">{loadError}</p>}

          {!loading && !loadError && config && (!config.enabled || config.attributes.length === 0) && (
            <p className="text-sm text-[#9a8c7c]">Custom requests aren&apos;t available for this business right now.</p>
          )}

          {showBuilder && config && (
            <>
              <div className="mb-1 flex items-center justify-between text-[11px] font-medium text-[#9a8c7c]">
                <span>
                  Step {stepIndex + 1} of {steps.length}
                </span>
              </div>
              <div className="mb-5 flex gap-1.5">
                {steps.map((s, i) => (
                  <div key={s.id} className="h-1 flex-1 rounded-full" style={{ background: i <= stepIndex ? ACCENT : "#ecdfcd" }} />
                ))}
              </div>

              {currentStep.kind === "attributes" && (
                <div>
                  <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
                    <Sparkles size={13} /> {stepIndex === 0 ? "Design your own" : "Keep building"}
                  </div>
                  <h1 className="text-xl font-semibold tracking-tight text-[#1c140d]">{currentStep.title}</h1>

                  <div className="mt-5 flex flex-col gap-6">
                    {currentStep.attributeIds.map((attrId) => {
                      const attr = attrById.get(attrId);
                      if (!attr) return null;
                      const isMultiple = attr.selectionType === "MULTIPLE";
                      const selectedIds = selections[attr.id] ?? [];
                      const isOpen = !isMultiple || (expanded[attr.id] ?? selectedIds.length > 0);
                      return (
                        <div key={attr.id}>
                          {currentStep.attributeIds.length > 1 && (
                            <h3 className="text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]">{attr.name}</h3>
                          )}
                          {isMultiple && (
                            <button
                              type="button"
                              onClick={() => setExpanded((prev) => ({ ...prev, [attr.id]: !isOpen }))}
                              className="mt-1.5 flex items-center gap-1 text-xs font-medium"
                              style={{ color: ACCENT }}
                            >
                              {isOpen ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
                              {selectedIds.length > 0 ? `${selectedIds.length} selected` : "Want to customize further?"}
                            </button>
                          )}
                          {isOpen && (
                            <div className="mt-2 grid grid-cols-2 gap-2.5 sm:grid-cols-3">
                              {attr.options.map((opt) => (
                                <OptionCard
                                  key={opt.id}
                                  label={opt.label}
                                  priceModifier={Number(opt.priceModifier)}
                                  currency={config.currency}
                                  selected={selectedIds.includes(opt.id)}
                                  manualQuote={opt.requiresManualQuote}
                                  onClick={() => (isMultiple ? toggleMultiple(attr.id, opt.id) : selectSingle(attr.id, opt.id))}
                                />
                              ))}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>

                  <div className="mt-5 flex gap-2.5">
                    {stepIndex > 0 && (
                      <button
                        type="button"
                        onClick={() => setStepIndex((i) => i - 1)}
                        className="flex items-center gap-1 rounded-full border-[1.5px] px-4 py-3 text-sm font-semibold"
                        style={{ borderColor: "#ece1d2", color: "#4a3d2f" }}
                      >
                        <ArrowLeft size={14} /> Back
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => setStepIndex((i) => i + 1)}
                      className="flex-1 rounded-full py-3 text-sm font-semibold text-white transition hover:opacity-90"
                      style={{ background: ACCENT }}
                    >
                      Continue
                    </button>
                  </div>
                </div>
              )}

              {currentStep.kind === "reference" && (
                <div>
                  <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
                    <Sparkles size={13} /> Almost there
                  </div>
                  <h1 className="text-xl font-semibold tracking-tight text-[#1c140d]">Make it yours</h1>
                  <p className="mt-1 text-sm text-[#7a6d5f]">Optional — share a reference photo or any special instructions.</p>

                  <label className="mt-5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]">
                    <Camera size={14} className="opacity-60" /> Inspiration photo (optional)
                  </label>
                  <div className="mt-2 flex items-center gap-3">
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/png,image/jpeg,image/webp"
                      className="hidden"
                      onChange={handlePhotoChange}
                    />
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      className="inline-flex items-center gap-1.5 rounded-full border-[1.5px] px-3.5 py-2 text-xs font-semibold"
                      style={{ borderColor: ACCENT, color: ACCENT }}
                    >
                      <Camera size={14} /> Choose photo
                    </button>
                    {photoPreview && (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={photoPreview} alt="" className="h-12 w-12 rounded-lg border-[1.5px] border-[#ece1d2] object-cover" />
                    )}
                  </div>

                  <label className="mt-4 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="notes">
                    Special instructions (optional)
                  </label>
                  <textarea
                    id="notes"
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    rows={3}
                    className="mt-2 w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                  />

                  <div className="mt-5 flex gap-2.5">
                    <button
                      type="button"
                      onClick={() => setStepIndex((i) => i - 1)}
                      className="flex items-center gap-1 rounded-full border-[1.5px] px-4 py-3 text-sm font-semibold"
                      style={{ borderColor: "#ece1d2", color: "#4a3d2f" }}
                    >
                      <ArrowLeft size={14} /> Back
                    </button>
                    <button
                      type="button"
                      onClick={() => setStepIndex((i) => i + 1)}
                      className="flex-1 rounded-full py-3 text-sm font-semibold text-white transition hover:opacity-90"
                      style={{ background: ACCENT }}
                    >
                      Continue
                    </button>
                  </div>
                </div>
              )}

              {currentStep.kind === "review" && (
                <form onSubmit={handleSubmit}>
                  <button
                    type="button"
                    onClick={() => setStepIndex((i) => i - 1)}
                    className="mb-3 flex items-center gap-1 text-xs font-medium text-[#9a8c7c]"
                  >
                    <ArrowLeft size={13} /> Back
                  </button>
                  <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
                    <Sparkles size={13} /> Final step
                  </div>
                  <h1 className="text-xl font-semibold tracking-tight text-[#1c140d]">Your wig</h1>

                  <div className="mt-4 rounded-2xl border-[1.5px] border-[#ece1d2] p-4">
                    <SelectedSummaryList config={config} selections={selections} />
                    {photoPreview && (
                      <div className="mt-3 flex items-center gap-2 border-t border-[#f0e6d8] pt-3">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img src={photoPreview} alt="" className="h-10 w-10 rounded-lg border-[1.5px] border-[#ece1d2] object-cover" />
                        <span className="text-xs text-[#7a6d5f]">Reference photo attached</span>
                      </div>
                    )}
                    {notes && (
                      <p className="mt-3 border-t border-[#f0e6d8] pt-3 text-xs text-[#7a6d5f]">
                        <span className="font-medium text-[#4a3d2f]">Notes: </span>
                        {notes}
                      </p>
                    )}
                  </div>

                  <div className="mt-4 flex items-center justify-between rounded-2xl px-4 py-3.5" style={{ background: "rgba(167,101,69,0.08)" }}>
                    <span className="text-sm font-semibold text-[#4a3d2f]">{hasManualQuote ? "Estimated total" : "Estimated price"}</span>
                    <span className="text-lg font-bold" style={{ color: ACCENT }}>
                      {formatMoney(total, config.currency)}
                    </span>
                  </div>
                  {hasManualQuote && (
                    <p className="mt-1.5 text-[11px] text-[#9a8c7c]">
                      One or more choices need a manual quote — we&apos;ll confirm the final price with you.
                    </p>
                  )}

                  <label className="mt-5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="name">
                    <User size={14} className="opacity-60" /> Your name
                  </label>
                  <input
                    id="name"
                    required
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                    className="mt-2 w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                  />

                  <label className="mt-4 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="email">
                    <Mail size={14} className="opacity-60" /> Email
                  </label>
                  <input
                    id="email"
                    type="email"
                    required
                    value={customerEmail}
                    onChange={(e) => setCustomerEmail(e.target.value)}
                    className="mt-2 w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                  />

                  <label className="mt-4 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="whatsapp">
                    <Phone size={14} className="opacity-60" /> WhatsApp number
                  </label>
                  <input
                    id="whatsapp"
                    type="tel"
                    required
                    placeholder="+233 55 000 0000"
                    value={customerWhatsapp}
                    onChange={(e) => setCustomerWhatsapp(e.target.value)}
                    className="mt-2 w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                  />

                  {submitError && <p className="mt-3 rounded-lg bg-[#fdf1ee] px-3 py-2 text-sm text-[#b1432e]">{submitError}</p>}

                  <button
                    type="submit"
                    disabled={submitting}
                    className="mt-5 w-full rounded-full py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-55"
                    style={{ background: ACCENT }}
                  >
                    {submitting ? "Submitting..." : "Build My Wig"}
                  </button>
                </form>
              )}
            </>
          )}

          {created && config && (
            <div className="text-center">
              <div className="mx-auto mb-4 flex items-center justify-center rounded-full bg-[#e8f3ea] text-[#3f8a53]" style={{ width: 52, height: 52 }}>
                <CheckCircle2 size={26} />
              </div>
              <h1 className="text-xl font-semibold text-[#1c140d]">Request #{created.requestNumber} received</h1>
              <p className="mt-1 text-sm text-[#7a6d5f]">
                {hasManualQuote
                  ? `Estimated at ${formatMoney(created.estimatedPrice, config.currency)} — we'll follow up with a final quote soon.`
                  : `Estimated at ${formatMoney(created.estimatedPrice, config.currency)}. We'll follow up soon.`}
              </p>

              {config.businessWhatsappLink && (
                <a
                  href={config.businessWhatsappLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-5 flex w-full items-center justify-center gap-2 rounded-full border-[1.5px] px-4 py-3 text-sm font-semibold transition hover:opacity-90"
                  style={{ borderColor: "#3f8a53", color: "#3f8a53" }}
                >
                  <MessageCircle size={15} />
                  Message us on WhatsApp
                </a>
              )}

              <p className="mt-5 text-sm text-[#9a8c7c]">
                <Link href={`/start/${slug}`} className="font-semibold hover:underline" style={{ color: ACCENT }}>
                  Back to start →
                </Link>
              </p>
            </div>
          )}
        </div>

        {showBuilder && config && (
          <aside className="hidden lg:block">
            <div
              className="sticky top-8 rounded-3xl p-6"
              style={{ background: "#fff", border: "1px solid #f0e6d8", boxShadow: "0 1px 2px rgba(42,32,24,0.04), 0 24px 48px -16px rgba(42,32,24,0.18)" }}
            >
              <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
                <Sparkles size={13} /> Your wig
              </div>
              <h2 className="text-base font-semibold text-[#1c140d]">{config.businessName}</h2>
              <div className="mt-4">
                <SelectedSummaryList config={config} selections={selections} />
              </div>
              <div className="mt-5 border-t border-[#f0e6d8] pt-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-[#4a3d2f]">{hasManualQuote ? "Estimated total" : "Estimated price"}</span>
                  <span className="text-lg font-bold" style={{ color: ACCENT }}>
                    {formatMoney(total, config.currency)}
                  </span>
                </div>
                {hasManualQuote && <p className="mt-1 text-[11px] text-[#9a8c7c]">Final price confirmed by us</p>}
              </div>
            </div>
          </aside>
        )}
      </div>

      {showBuilder && config && (
        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-[#f0e6d8] bg-white/95 px-4 py-3 backdrop-blur lg:hidden">
          <div className="mx-auto flex max-w-md items-center justify-between">
            <div>
              <p className="text-[11px] font-medium text-[#9a8c7c]">{hasManualQuote ? "Estimated total*" : "Estimated total"}</p>
              <p className="text-base font-bold" style={{ color: ACCENT }}>
                {formatMoney(total, config.currency)}
              </p>
            </div>
            <p className="text-[11px] text-[#9a8c7c]">
              Step {stepIndex + 1} of {steps.length}
            </p>
          </div>
        </div>
      )}

      <PoweredByRatel className="mt-6 text-xs text-[#b3a690]" iconSize={16} />
    </main>
  );
}
