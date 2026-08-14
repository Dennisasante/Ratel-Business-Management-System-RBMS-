"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { api, ApiError, BookingDetail } from "@/lib/api";
import PaystackCheckoutButton from "@/components/PaystackCheckoutButton";
import PoweredByRatel from "@/components/PoweredByRatel";
import {
  Sparkles,
  CalendarDays,
  Clock3,
  Banknote,
  RefreshCcw,
  XCircle,
  CheckCircle2,
  ArrowLeft,
  MessageCircle,
  MapPin,
  AlertCircle,
} from "lucide-react";

const ACCENT = "#a76545";

function formatMoney(amount: number, currency: string) {
  return `${currency} ${amount.toFixed(2)}`;
}

const STATUS_STYLE: Record<string, { label: string; bg: string; fg: string }> = {
  RECEIVED: { label: "Received", bg: "#fdf1e7", fg: "#a76545" },
  IN_PROGRESS: { label: "In progress", bg: "#eef1fb", fg: "#4a5eb5" },
  COMPLETED: { label: "Completed", bg: "#e8f3ea", fg: "#3f8a53" },
  PICKED_UP: { label: "Done", bg: "#e8f3ea", fg: "#3f8a53" },
  CANCELLED: { label: "Cancelled", bg: "#fdf1ee", fg: "#b1432e" },
};

function formatWhen(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

export default function ManageBookingPage() {
  const params = useParams<{ token: string }>();
  const token = params.token;

  const [booking, setBooking] = useState<BookingDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [rescheduling, setRescheduling] = useState(false);
  const [newWhen, setNewWhen] = useState("");
  const [rescheduleError, setRescheduleError] = useState<string | null>(null);
  const [rescheduleSaving, setRescheduleSaving] = useState(false);

  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const [payError, setPayError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const detail = await api.getBookingByToken(token);
      setBooking(detail);
      setLoadError(null);
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : "Couldn't load this booking.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleReschedule(e: React.FormEvent) {
    e.preventDefault();
    if (!newWhen) return;
    setRescheduleSaving(true);
    setRescheduleError(null);
    try {
      await api.rescheduleBooking(token, new Date(newWhen).toISOString());
      await load();
      setRescheduling(false);
      setNewWhen("");
    } catch (err) {
      setRescheduleError(err instanceof ApiError ? err.message : "Couldn't reschedule this booking.");
    } finally {
      setRescheduleSaving(false);
    }
  }

  async function handleCancel() {
    setCancelling(true);
    setCancelError(null);
    try {
      await api.cancelBooking(token);
      await load();
      setConfirmingCancel(false);
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : "Couldn't cancel this booking.");
    } finally {
      setCancelling(false);
    }
  }

  const isTerminal = booking && (booking.status === "CANCELLED" || booking.status === "PICKED_UP");
  const statusStyle = booking ? STATUS_STYLE[booking.status] ?? { label: booking.status, bg: "#f3ede4", fg: "#7a6d5f" } : null;
  // Mirrors BookingService's server-side enforceCancellationCutoff — server stays
  // authoritative regardless, this just avoids showing controls that will 400.
  const withinCutoff =
    !!booking &&
    booking.cancellationCutoffHours > 0 &&
    new Date(booking.scheduledAt).getTime() - Date.now() < booking.cancellationCutoffHours * 60 * 60 * 1000;

  return (
    <main
      className="flex min-h-screen flex-col items-center justify-center px-4 py-12"
      style={{
        background:
          "radial-gradient(1200px circle at 50% -10%, rgba(167,101,69,0.14), transparent 55%), #fdfaf6",
      }}
    >
      <div
        className="w-full max-w-md rounded-3xl p-8"
        style={{
          background: "#fff",
          border: "1px solid #f0e6d8",
          boxShadow: "0 1px 2px rgba(42,32,24,0.04), 0 24px 48px -16px rgba(42,32,24,0.18)",
        }}
      >
        {loading && <p className="text-sm text-[#9a8c7c]">Loading...</p>}

        {!loading && loadError && <p className="text-sm text-[#b1432e]">{loadError}</p>}

        {!loading && booking && (
          <div className="flex flex-col gap-6">
            <div>
              <div
                className="mb-2 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider"
                style={{ color: ACCENT }}
              >
                <Sparkles size={13} />
                Manage your appointment
              </div>
              <div className="flex items-start justify-between gap-3">
                <h1 className="text-xl font-semibold tracking-tight text-[#1c140d]">
                  {booking.businessName ?? "Your booking"}
                </h1>
                {statusStyle && (
                  <span
                    className="whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold"
                    style={{ background: statusStyle.bg, color: statusStyle.fg }}
                  >
                    {statusStyle.label}
                  </span>
                )}
              </div>
              <p className="mt-0.5 text-sm text-[#9a8c7c]">Booking #{booking.bookingNumber}</p>
            </div>

            <div className="flex flex-col gap-3 rounded-2xl bg-[#fdfaf6] p-4">
              <p className="text-base font-semibold text-[#2a2018]">{booking.serviceName ?? "Service"}</p>
              <div className="flex items-center gap-2 text-sm text-[#5c5041]">
                <CalendarDays size={16} className="text-[#a76545]/70" />
                {formatWhen(booking.scheduledAt)}
              </div>
              <div className="flex items-center gap-2 text-sm text-[#5c5041]">
                <Clock3 size={16} className="text-[#a76545]/70" />
                {formatTime(booking.scheduledAt)}
              </div>
              <div className="flex items-center gap-2 text-sm text-[#5c5041]">
                <Banknote size={16} className="text-[#a76545]/70" />
                {booking.paymentStatus === "PAID"
                  ? "Paid"
                  : booking.paymentStatus === "PAY_IN_PERSON"
                    ? "Paying in person"
                    : booking.paymentStatus === "FAILED"
                      ? "Payment failed"
                      : booking.amountDue != null
                        ? `${formatMoney(booking.amountDue, booking.currency)} due`
                        : "Payment pending"}
              </div>
              {booking.customerLocation && (
                <div className="flex items-start gap-2 text-sm text-[#5c5041]">
                  <MapPin size={16} className="mt-0.5 shrink-0 text-[#a76545]/70" />
                  {booking.customerLocation}
                </div>
              )}
            </div>

            {booking.businessWhatsappLink && (
              <a
                href={booking.businessWhatsappLink}
                target="_blank"
                rel="noopener noreferrer"
                className="flex w-full items-center justify-center gap-2 rounded-full border-[1.5px] px-4 py-3 text-sm font-semibold transition hover:opacity-90"
                style={{ borderColor: "#3f8a53", color: "#3f8a53" }}
              >
                <MessageCircle size={15} />
                Message us on WhatsApp
              </a>
            )}

            {!isTerminal && booking.amountDue != null && (
              <div className="flex flex-col gap-2">
                {payError && <p className="rounded-lg bg-[#fdf1ee] px-3 py-2 text-sm text-[#b1432e]">{payError}</p>}
                <PaystackCheckoutButton
                  planId={token}
                  months={1}
                  buttonLabel={`Pay ${formatMoney(booking.amountDue, booking.currency)} now`}
                  onStartCheckout={() => api.startBookingPayment(token)}
                  onVerify={async (reference) => {
                    const result = await api.verifyBookingPayment(reference);
                    if (result.success) await load();
                    else setPayError(result.message);
                    return result.success;
                  }}
                  onError={(msg) => setPayError(msg)}
                  className="w-full rounded-full bg-[#a76545] py-3 text-sm font-semibold text-white transition hover:opacity-90"
                />
              </div>
            )}

            {!isTerminal && withinCutoff && (
              <div className="flex items-start gap-2 rounded-2xl bg-[#fdf1e7] p-4 text-sm text-[#7a3324]">
                <AlertCircle size={16} className="mt-0.5 shrink-0" />
                <span>
                  This booking can no longer be changed — it&apos;s within {booking.cancellationCutoffHours} hour
                  {booking.cancellationCutoffHours === 1 ? "" : "s"} of the appointment. Please contact the business
                  directly if you need to make a change.
                </span>
              </div>
            )}

            {!isTerminal && !withinCutoff && (
              <div className="flex flex-col gap-3 border-t border-[#f0e6d8] pt-5">
                {!rescheduling ? (
                  <button
                    onClick={() => setRescheduling(true)}
                    className="flex w-full items-center justify-center gap-2 rounded-full border-[1.5px] px-4 py-3 text-sm font-semibold transition hover:opacity-90"
                    style={{ borderColor: ACCENT, color: ACCENT }}
                  >
                    <RefreshCcw size={15} />
                    Reschedule
                  </button>
                ) : (
                  <form onSubmit={handleReschedule} className="flex flex-col gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setRescheduling(false);
                        setRescheduleError(null);
                      }}
                      className="flex items-center gap-1 self-start text-xs font-medium text-[#9a8c7c]"
                    >
                      <ArrowLeft size={13} /> Back
                    </button>
                    <label className="text-xs font-semibold uppercase tracking-wide text-[#4a3d2f]" htmlFor="new-when">
                      New date &amp; time
                    </label>
                    <input
                      id="new-when"
                      type="datetime-local"
                      required
                      value={newWhen}
                      onChange={(e) => setNewWhen(e.target.value)}
                      min={new Date(Date.now() + 30 * 60 * 1000).toISOString().slice(0, 16)}
                      className="w-full rounded-xl border-[1.5px] border-[#ece1d2] px-3 py-2.5 text-sm text-[#2a2018] focus:border-[#a76545] focus:outline-none"
                    />
                    {rescheduleError && <p className="rounded-lg bg-[#fdf1ee] px-3 py-2 text-sm text-[#b1432e]">{rescheduleError}</p>}
                    <button
                      type="submit"
                      disabled={rescheduleSaving}
                      className="w-full rounded-full py-3 text-sm font-semibold text-white transition disabled:opacity-55"
                      style={{ background: ACCENT }}
                    >
                      {rescheduleSaving ? "Saving..." : "Save new time"}
                    </button>
                  </form>
                )}

                {!confirmingCancel ? (
                  <button
                    onClick={() => setConfirmingCancel(true)}
                    className="flex w-full items-center justify-center gap-2 rounded-full px-4 py-3 text-sm font-medium text-[#9a8c7c] transition hover:text-[#b1432e]"
                  >
                    <XCircle size={15} />
                    Cancel booking
                  </button>
                ) : (
                  <div className="flex flex-col gap-3 rounded-2xl bg-[#fdf1ee] p-4">
                    <p className="text-sm font-medium text-[#7a3324]">Are you sure you want to cancel?</p>
                    {cancelError && <p className="text-sm text-[#b1432e]">{cancelError}</p>}
                    <div className="flex gap-2">
                      <button
                        onClick={handleCancel}
                        disabled={cancelling}
                        className="flex-1 rounded-full bg-[#b1432e] py-2.5 text-sm font-semibold text-white transition disabled:opacity-55"
                      >
                        {cancelling ? "Cancelling..." : "Yes, cancel"}
                      </button>
                      <button
                        onClick={() => setConfirmingCancel(false)}
                        className="rounded-full px-4 py-2.5 text-sm font-medium text-[#7a3324]"
                      >
                        Keep it
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {isTerminal && (
              <div className="flex flex-col items-center gap-2 border-t border-[#f0e6d8] pt-5 text-center">
                <div
                  className="flex h-11 w-11 items-center justify-center rounded-full"
                  style={{
                    background: booking.status === "CANCELLED" ? "#fdf1ee" : "#e8f3ea",
                    color: booking.status === "CANCELLED" ? "#b1432e" : "#3f8a53",
                  }}
                >
                  {booking.status === "CANCELLED" ? <XCircle size={20} /> : <CheckCircle2 size={20} />}
                </div>
                <p className="text-sm text-[#7a6d5f]">
                  {booking.status === "CANCELLED" ? "This booking has been cancelled." : "This booking is complete."}
                </p>
              </div>
            )}
          </div>
        )}
      </div>
      <PoweredByRatel className="mt-6 text-xs text-[#b3a690]" iconSize={16} />
    </main>
  );
}
