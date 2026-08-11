"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Sparkles,
  CalendarDays,
  User,
  Mail,
  Phone,
  MapPin,
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Lock,
  MessageCircle,
  PackageCheck,
} from "lucide-react";
import { api, ApiError, BookableService, BookingCreated, BookingWidgetConfig } from "@/lib/api";
import PaystackCheckoutButton from "@/components/PaystackCheckoutButton";
import PoweredByRatel from "@/components/PoweredByRatel";

const ACCENT = "#a76545";

function formatMoney(amount: number, currency: string) {
  return `${currency} ${amount.toFixed(2)}`;
}

function formatTimeLabel(hhmmss: string) {
  const [hStr, m] = hhmmss.split(":");
  const h = parseInt(hStr, 10);
  const ampm = h >= 12 ? "PM" : "AM";
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${m} ${ampm}`;
}

const DAY_LABELS = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function workingHoursHint(config: BookingWidgetConfig) {
  const sorted = [...config.workingHours].sort((a, b) => a.dayOfWeek - b.dayOfWeek);
  const groups: { days: number[]; startTime: string; endTime: string }[] = [];
  for (const h of sorted) {
    const last = groups[groups.length - 1];
    if (last && last.startTime === h.startTime && last.endTime === h.endTime && last.days[last.days.length - 1] === h.dayOfWeek - 1) {
      last.days.push(h.dayOfWeek);
    } else {
      groups.push({ days: [h.dayOfWeek], startTime: h.startTime, endTime: h.endTime });
    }
  }
  const parts = groups.map((g) => {
    const label = g.days.length > 1 ? `${DAY_LABELS[g.days[0]]}–${DAY_LABELS[g.days[g.days.length - 1]]}` : DAY_LABELS[g.days[0]];
    return `${label} ${formatTimeLabel(g.startTime)} – ${formatTimeLabel(g.endTime)}`;
  });
  return `Open ${parts.join(", ")}`;
}

function isWithinWorkingHours(config: BookingWidgetConfig, whenLocal: string) {
  if (!whenLocal) return true;
  const d = new Date(whenLocal);
  const isoWeekday = d.getDay() === 0 ? 7 : d.getDay();
  const today = config.workingHours.find((h) => h.dayOfWeek === isoWeekday);
  if (!today) return false;
  const minutes = d.getHours() * 60 + d.getMinutes();
  const [startH, startM] = today.startTime.split(":").map(Number);
  const [endH, endM] = today.endTime.split(":").map(Number);
  return minutes >= startH * 60 + startM && minutes < endH * 60 + endM;
}

function policyNote(config: BookingWidgetConfig, service: BookableService | undefined) {
  if (!service || service.paymentPolicy === "NONE") return null;
  if (service.paymentPolicy === "FULL") {
    return `Full payment (${formatMoney(service.price, config.currency)}) is required to confirm this booking.`;
  }
  const deposit = (Number(service.price) * config.depositPercent) / 100;
  return `A ${config.depositPercent}% deposit (${formatMoney(deposit, config.currency)}) is required to confirm this booking.`;
}

function serviceKey(s: BookableService) {
  return s.isPackage ? `pkg:${s.packageId}` : `svc:${s.serviceCatalogId}`;
}

function formatDateTimeLabel(whenLocal: string) {
  if (!whenLocal) return null;
  const d = new Date(whenLocal);
  if (isNaN(d.getTime())) return null;
  return d.toLocaleString(undefined, { weekday: "short", month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

function BookingSummaryBody({
  config,
  service,
  scheduledAt,
  location,
}: {
  config: BookingWidgetConfig;
  service: BookableService | undefined;
  scheduledAt: string;
  location: string;
}) {
  const whenLabel = formatDateTimeLabel(scheduledAt);
  const note = policyNote(config, service);

  if (!service) {
    return <p className="text-xs text-[#9a8c7c]">Nothing selected yet.</p>;
  }

  return (
    <>
      <dl className="flex flex-col gap-2">
        <div className="flex items-baseline justify-between gap-3 text-sm">
          <dt className="shrink-0 text-[#7a6d5f]">Service</dt>
          <dd className="text-right font-medium text-[#2a2018]">{service.serviceName}</dd>
        </div>
        {whenLabel && (
          <div className="flex items-baseline justify-between gap-3 text-sm">
            <dt className="shrink-0 text-[#7a6d5f]">When</dt>
            <dd className="text-right font-medium text-[#2a2018]">{whenLabel}</dd>
          </div>
        )}
        {service.requiresLocation && location.trim() && (
          <div className="flex items-baseline justify-between gap-3 text-sm">
            <dt className="shrink-0 text-[#7a6d5f]">Location</dt>
            <dd className="text-right font-medium text-[#2a2018]">{location.trim()}</dd>
          </div>
        )}
      </dl>
      {service.includedItems.length > 0 && (
        <ul className="mt-3 space-y-0.5 border-t border-[#f0e6d8] pt-3">
          {service.includedItems.map((line) => (
            <li key={line} className="text-xs text-[#7a6d5f]">
              • {line}
            </li>
          ))}
        </ul>
      )}
      <div className="mt-4 border-t border-[#f0e6d8] pt-4">
        <div className="flex items-center justify-between">
          <span className="text-sm font-semibold text-[#4a3d2f]">Total</span>
          <span className="text-lg font-bold" style={{ color: ACCENT }}>
            {formatMoney(service.price, config.currency)}
          </span>
        </div>
        {note && <p className="mt-2 text-[11px] leading-relaxed text-[#9a8c7c]">{note}</p>}
      </div>
    </>
  );
}

export default function HostedBookingPage() {
  const params = useParams<{ slug: string }>();
  const slug = params.slug;

  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [config, setConfig] = useState<BookingWidgetConfig | null>(null);
  const [services, setServices] = useState<BookableService[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedKey, setSelectedKey] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [step1Error, setStep1Error] = useState<string | null>(null);

  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [customerWhatsapp, setCustomerWhatsapp] = useState("");
  const [customerLocation, setCustomerLocation] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [created, setCreated] = useState<BookingCreated | null>(null);
  const [payError, setPayError] = useState<string | null>(null);
  const [choosingPayInPerson, setChoosingPayInPerson] = useState(false);
  const [paidInPerson, setPaidInPerson] = useState(false);

  const load = useCallback(async () => {
    try {
      const cfg = await api.getBookingWidgetConfigBySlug(slug);
      setConfig(cfg);
      if (cfg.enabled) {
        const svc = await api.listPublicBookableServices(cfg.businessId);
        setServices(svc);
      }
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : "Couldn't load this booking page.");
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => {
    load();
  }, [load]);

  const selectedService = services.find((s) => serviceKey(s) === selectedKey);
  const showSummary = !!config && config.enabled && services.length > 0 && !created;

  function handleContinue() {
    setStep1Error(null);
    if (!selectedService) {
      setStep1Error("Pick a service to continue.");
      return;
    }
    if (!scheduledAt) {
      setStep1Error("Choose a date and time.");
      return;
    }
    if (config && !isWithinWorkingHours(config, scheduledAt)) {
      setStep1Error(`That falls outside business hours (${workingHoursHint(config)}). Please choose another time.`);
      return;
    }
    setStep(2);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!config || !selectedService) return;
    if (selectedService.requiresLocation && !customerLocation.trim()) {
      setSubmitError("Please share your location for this service.");
      return;
    }
    setSubmitError(null);
    setSubmitting(true);
    try {
      const result = await api.createPublicBooking(config.businessId, {
        ...(selectedService.isPackage
          ? { packageId: selectedService.packageId ?? undefined }
          : { serviceCatalogId: selectedService.serviceCatalogId ?? undefined }),
        scheduledAt: new Date(scheduledAt).toISOString(),
        customerName,
        customerEmail,
        customerWhatsapp,
        notes: notes || undefined,
        customerLocation: selectedService.requiresLocation ? customerLocation.trim() : undefined,
      });
      setCreated(result);
      setStep(3);
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : "Couldn't create that booking.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handlePayInPerson() {
    if (!created) return;
    setPayError(null);
    setChoosingPayInPerson(true);
    try {
      await api.payInPersonBooking(created.manageToken);
      setPaidInPerson(true);
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't save that choice — please try paying now instead.");
    } finally {
      setChoosingPayInPerson(false);
    }
  }

  return (
    <main
      className={`min-h-screen px-4 py-12 ${showSummary ? "pb-28 lg:pb-12" : "flex flex-col items-center justify-center"}`}
      style={{ background: "radial-gradient(1200px circle at 50% -10%, rgba(167,101,69,0.14), transparent 55%), #fdfaf6" }}
    >
      <div
        className={
          showSummary
            ? "mx-auto flex w-full max-w-3xl flex-col gap-6 lg:grid lg:grid-cols-[1fr_320px] lg:items-start"
            : "mx-auto flex w-full max-w-md flex-col items-center"
        }
      >
      <div
        className="w-full rounded-3xl p-8"
        style={{ background: "#fff", border: "1px solid #f0e6d8", boxShadow: "0 1px 2px rgba(42,32,24,0.04), 0 24px 48px -16px rgba(42,32,24,0.18)" }}
      >
        {loading && <p className="text-sm text-[#9a8c7c]">Loading...</p>}
        {!loading && loadError && <p className="text-sm text-[#b1432e]">{loadError}</p>}

        {!loading && !loadError && config && !config.enabled && (
          <p className="text-sm text-[#9a8c7c]">Online booking isn&apos;t available for this business right now.</p>
        )}

        {!loading && !loadError && config && config.enabled && services.length === 0 && (
          <p className="text-sm text-[#9a8c7c]">No services are available for online booking yet.</p>
        )}

        {!loading && !loadError && config && config.enabled && services.length > 0 && (
          <>
            {/* Step progress */}
            <div className="mb-5 flex gap-1.5">
              {[1, 2, 3].map((s) => (
                <div
                  key={s}
                  className="h-1 flex-1 rounded-full"
                  style={{ background: s <= step ? ACCENT : "#ecdfcd" }}
                />
              ))}
            </div>

            {step === 1 && (
              <div>
                <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
                  <Sparkles size={13} /> Book your appointment
                </div>
                <h1 className="text-xl font-semibold tracking-tight text-[#1c140d]">{config.businessName}</h1>

                <label className="mt-5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]">
                  <CalendarDays size={14} className="opacity-60" /> Choose a service
                </label>
                <div className="mt-2 flex flex-col gap-2">
                  {services.map((s) => {
                    const key = serviceKey(s);
                    const selected = key === selectedKey;
                    return (
                      <button
                        key={key}
                        type="button"
                        onClick={() => setSelectedKey(key)}
                        className="flex items-start justify-between gap-3 rounded-2xl border-[1.5px] px-4 py-3 text-left transition"
                        style={{
                          borderColor: selected ? ACCENT : "#ece1d2",
                          background: selected ? "rgba(167,101,69,0.08)" : "#fff",
                        }}
                      >
                        <div>
                          <div className="flex items-center gap-1.5">
                            <p className="text-sm font-semibold text-[#2a2018]">{s.serviceName}</p>
                            {s.isPackage && (
                              <span
                                className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                                style={{ background: "rgba(167,101,69,0.12)", color: ACCENT }}
                              >
                                <PackageCheck size={10} /> Package
                              </span>
                            )}
                          </div>
                          {s.serviceTypeName && <p className="text-xs text-[#9a8c7c]">{s.serviceTypeName}</p>}
                          {s.description && <p className="mt-1 text-xs text-[#7a6d5f]">{s.description}</p>}
                          {s.includedItems.length > 0 && (
                            <ul className="mt-1 space-y-0.5">
                              {s.includedItems.map((line) => (
                                <li key={line} className="text-xs text-[#7a6d5f]">
                                  • {line}
                                </li>
                              ))}
                            </ul>
                          )}
                        </div>
                        <p className="shrink-0 text-sm font-semibold" style={{ color: ACCENT }}>
                          {formatMoney(s.price, config.currency)}
                        </p>
                      </button>
                    );
                  })}
                </div>

                {policyNote(config, selectedService) && (
                  <p className="mt-3 rounded-xl px-3 py-2.5 text-xs" style={{ color: ACCENT, background: "rgba(167,101,69,0.08)" }}>
                    {policyNote(config, selectedService)}
                  </p>
                )}

                <label className="mt-5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="when">
                  <CalendarDays size={14} className="opacity-60" /> Date &amp; time
                </label>
                <input
                  id="when"
                  type="datetime-local"
                  value={scheduledAt}
                  onChange={(e) => setScheduledAt(e.target.value)}
                  min={new Date(Date.now() + 30 * 60 * 1000).toISOString().slice(0, 16)}
                  className="mt-2 w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                />
                <p className="mt-1.5 text-xs text-[#9a8c7c]">{workingHoursHint(config)}</p>

                {step1Error && <p className="mt-3 rounded-lg bg-[#fdf1ee] px-3 py-2 text-sm text-[#b1432e]">{step1Error}</p>}

                <button
                  onClick={handleContinue}
                  className="mt-5 w-full rounded-full py-3 text-sm font-semibold text-white transition hover:opacity-90"
                  style={{ background: ACCENT }}
                >
                  Continue
                </button>
              </div>
            )}

            {step === 2 && (
              <form onSubmit={handleSubmit}>
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="mb-3 flex items-center gap-1 text-xs font-medium text-[#9a8c7c]"
                >
                  <ArrowLeft size={13} /> Back
                </button>
                <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
                  <Sparkles size={13} /> Almost there
                </div>
                <h1 className="text-xl font-semibold tracking-tight text-[#1c140d]">Your details</h1>

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

                {selectedService?.requiresLocation && (
                  <>
                    <label className="mt-4 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="location">
                      <MapPin size={14} className="opacity-60" /> Your location
                    </label>
                    <textarea
                      id="location"
                      required
                      value={customerLocation}
                      onChange={(e) => setCustomerLocation(e.target.value)}
                      rows={2}
                      placeholder="Where should we come to?"
                      className="mt-2 w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                    />
                  </>
                )}

                <label className="mt-4 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="notes">
                  Notes (optional)
                </label>
                <textarea
                  id="notes"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  className="mt-2 w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                  rows={3}
                />

                {submitError && <p className="mt-3 rounded-lg bg-[#fdf1ee] px-3 py-2 text-sm text-[#b1432e]">{submitError}</p>}

                <button
                  type="submit"
                  disabled={submitting}
                  className="mt-5 w-full rounded-full py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-55"
                  style={{ background: ACCENT }}
                >
                  {submitting ? "Confirming..." : "Confirm booking"}
                </button>
              </form>
            )}

            {step === 3 && created && (
              <div className="text-center">
                {created.paymentRequired && !paidInPerson ? (
                  <>
                    <div className="mx-auto mb-4 flex h-13 w-13 items-center justify-center rounded-full" style={{ background: "rgba(167,101,69,0.12)", color: ACCENT, width: 52, height: 52 }}>
                      <Clock3 size={26} />
                    </div>
                    <h1 className="text-xl font-semibold text-[#1c140d]">Booking #{created.bookingNumber} received</h1>
                    <p className="mt-1 text-sm text-[#7a6d5f]">
                      Pay {created.amountDue != null && formatMoney(created.amountDue, config.currency)} now to confirm your appointment.
                    </p>
                    {created.amountDue != null && (
                      <>
                        {payError && <p className="mt-3 rounded-lg bg-[#fdf1ee] px-3 py-2 text-sm text-[#b1432e]">{payError}</p>}
                        <PaystackCheckoutButton
                          planId={created.manageToken}
                          buttonLabel={`${formatMoney(created.amountDue, config.currency)} · Pay to confirm`}
                          onStartCheckout={() => api.startBookingPayment(created.manageToken)}
                          onVerify={async (reference) => {
                            const result = await api.verifyBookingPayment(reference);
                            if (!result.success) setPayError(result.message);
                            return result.success;
                          }}
                          onError={(msg) => setPayError(msg)}
                          className="mt-4 w-full rounded-full bg-[#a76545] py-3 text-sm font-semibold text-white transition hover:opacity-90"
                        />
                        <p className="mt-3 flex items-center justify-center gap-1.5 text-[11px] text-[#b3a690]">
                          <Lock size={12} /> Secured by Paystack
                        </p>
                        {config.allowPayInPerson && (
                          <button
                            type="button"
                            onClick={handlePayInPerson}
                            disabled={choosingPayInPerson}
                            className="mt-3 w-full rounded-full border-[1.5px] py-3 text-sm font-semibold transition hover:opacity-90 disabled:opacity-55"
                            style={{ borderColor: ACCENT, color: ACCENT }}
                          >
                            {choosingPayInPerson ? "Saving..." : "I'll pay in person instead"}
                          </button>
                        )}
                      </>
                    )}
                  </>
                ) : (
                  <>
                    <div className="mx-auto mb-4 flex items-center justify-center rounded-full bg-[#e8f3ea] text-[#3f8a53]" style={{ width: 52, height: 52 }}>
                      <CheckCircle2 size={26} />
                    </div>
                    <h1 className="text-xl font-semibold text-[#1c140d]">Booking #{created.bookingNumber} confirmed</h1>
                    <p className="mt-1 text-sm text-[#7a6d5f]">
                      {paidInPerson ? "Great — pay when you arrive. We'll see you then!" : created.message}
                    </p>
                  </>
                )}

                {config.businessWhatsappLink && (
                  <a
                    href={config.businessWhatsappLink}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-4 flex w-full items-center justify-center gap-2 rounded-full border-[1.5px] px-4 py-3 text-sm font-semibold transition hover:opacity-90"
                    style={{ borderColor: "#3f8a53", color: "#3f8a53" }}
                  >
                    <MessageCircle size={15} />
                    Message us on WhatsApp
                  </a>
                )}

                <p className="mt-5 text-sm text-[#9a8c7c]">
                  <Link href={`/booking/manage/${created.manageToken}`} className="font-semibold hover:underline" style={{ color: ACCENT }}>
                    Manage this booking →
                  </Link>
                </p>
              </div>
            )}
          </>
        )}
      </div>

      {showSummary && config && (
        <aside className="hidden lg:block">
          <div
            className="sticky top-8 rounded-3xl p-6"
            style={{ background: "#fff", border: "1px solid #f0e6d8", boxShadow: "0 1px 2px rgba(42,32,24,0.04), 0 24px 48px -16px rgba(42,32,24,0.18)" }}
          >
            <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
              <Sparkles size={13} /> Your booking
            </div>
            <h2 className="text-base font-semibold text-[#1c140d]">{config.businessName}</h2>
            <div className="mt-4">
              <BookingSummaryBody config={config} service={selectedService} scheduledAt={scheduledAt} location={customerLocation} />
            </div>
          </div>
        </aside>
      )}
      </div>

      {showSummary && config && (
        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-[#f0e6d8] bg-white/95 px-4 py-3 backdrop-blur lg:hidden">
          <div className="mx-auto flex max-w-md items-center justify-between">
            <div>
              <p className="text-[11px] font-medium text-[#9a8c7c]">{selectedService ? selectedService.serviceName : "Total"}</p>
              <p className="text-base font-bold" style={{ color: ACCENT }}>
                {formatMoney(selectedService?.price ?? 0, config.currency)}
              </p>
            </div>
            <p className="text-[11px] text-[#9a8c7c]">Step {step} of 3</p>
          </div>
        </div>
      )}

      <PoweredByRatel className="mt-6 text-xs text-[#b3a690]" iconSize={16} />
    </main>
  );
}
