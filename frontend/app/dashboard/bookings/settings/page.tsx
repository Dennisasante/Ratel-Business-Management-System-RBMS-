"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Trash2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BlackoutDate, WorkingHoursEntry } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";

const DAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

type DayRow = { dayOfWeek: number; open: boolean; startTime: string; endTime: string };

function defaultDayRows(hours: WorkingHoursEntry[]): DayRow[] {
  return DAY_LABELS.map((_, i) => {
    const day = i + 1;
    const existing = hours.find((h) => h.dayOfWeek === day);
    return {
      dayOfWeek: day,
      open: !!existing,
      startTime: existing ? existing.startTime.slice(0, 5) : "09:00",
      endTime: existing ? existing.endTime.slice(0, 5) : "18:00",
    };
  });
}

export default function BookingSettingsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [fetching, setFetching] = useState(true);

  // Payment policy
  const [paymentPolicy, setPaymentPolicy] = useState<"NONE" | "DEPOSIT" | "FULL">("NONE");
  const [depositPercent, setDepositPercent] = useState(50);
  const [allowPayInPerson, setAllowPayInPerson] = useState(false);
  const [savingPolicy, setSavingPolicy] = useState(false);
  const [policyError, setPolicyError] = useState<string | null>(null);

  // Cancellation cutoff
  const [cancellationCutoffHours, setCancellationCutoffHours] = useState(0);
  const [savingCutoff, setSavingCutoff] = useState(false);
  const [cutoffError, setCutoffError] = useState<string | null>(null);

  // Per-day working hours
  const [dayRows, setDayRows] = useState<DayRow[]>(defaultDayRows([]));
  const [savingHours, setSavingHours] = useState(false);
  const [hoursError, setHoursError] = useState<string | null>(null);

  // Blackout dates
  const [blackoutDates, setBlackoutDates] = useState<BlackoutDate[]>([]);
  const [newBlackoutDate, setNewBlackoutDate] = useState("");
  const [newBlackoutLabel, setNewBlackoutLabel] = useState("");
  const [addingBlackout, setAddingBlackout] = useState(false);
  const [blackoutError, setBlackoutError] = useState<string | null>(null);
  const [removingBlackoutId, setRemovingBlackoutId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const [settings, blackouts] = await Promise.all([
      api.getBookingSettings(session.token),
      api.listBlackoutDates(session.token),
    ]);
    setPaymentPolicy(settings.paymentPolicy);
    setDepositPercent(settings.depositPercent);
    setAllowPayInPerson(settings.allowPayInPerson);
    setCancellationCutoffHours(settings.cancellationCutoffHours);
    setDayRows(defaultDayRows(settings.workingHours));
    setBlackoutDates(blackouts);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!loading && session && session.role !== "OWNER") router.push("/dashboard/bookings");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load().finally(() => setFetching(false));
  }, [session, load]);

  async function savePolicy(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingPolicy(true);
    setPolicyError(null);
    try {
      await api.updateBookingSettings(session.token, {
        paymentPolicy,
        ...(paymentPolicy === "DEPOSIT" ? { depositPercent } : {}),
        allowPayInPerson,
      });
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
      await api.updateBookingSettings(session.token, { cancellationCutoffHours });
    } catch (err) {
      setCutoffError(err instanceof ApiError ? err.message : "Couldn't save.");
    } finally {
      setSavingCutoff(false);
    }
  }

  function toggleDayOpen(day: number) {
    setDayRows((prev) => prev.map((r) => (r.dayOfWeek === day ? { ...r, open: !r.open } : r)));
  }

  function updateDayTime(day: number, field: "startTime" | "endTime", value: string) {
    setDayRows((prev) => prev.map((r) => (r.dayOfWeek === day ? { ...r, [field]: value } : r)));
  }

  async function saveHours(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingHours(true);
    setHoursError(null);
    try {
      const workingHours: WorkingHoursEntry[] = dayRows
        .filter((r) => r.open)
        .map((r) => ({ dayOfWeek: r.dayOfWeek, startTime: r.startTime, endTime: r.endTime }));
      await api.updateBookingSettings(session.token, { workingHours });
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

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link href="/dashboard/bookings" className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-900">
          <ArrowLeft size={14} /> Back to bookings
        </Link>
      </div>

      <PageHeader
        title="Booking settings"
        subtitle="Payment policy, hours, and closed dates for your online booking page."
      />

      {fetching ? (
        <CardSkeleton count={3} />
      ) : (
        <>
          <Card className="max-w-2xl p-5">
            <h2 className="text-base font-semibold text-ink-900">Booking payments</h2>
            <p className="mt-1 text-xs text-ink-500">
              Set whether a customer has to pay to confirm a booking — and separately, whether paying at the shop is
              also allowed. The two can both be on at once: a customer chooses whichever they prefer at checkout.
              Requiring payment needs a Paystack secret key under Integrations first — until one&apos;s saved,
              bookings are confirmed without payment regardless of this setting.
            </p>

            <form onSubmit={savePolicy} className="mt-4 flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-ink-700">Require payment to confirm</label>
                <select
                  value={paymentPolicy}
                  onChange={(e) => setPaymentPolicy(e.target.value as "NONE" | "DEPOSIT" | "FULL")}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                >
                  <option value="NONE">Not required</option>
                  <option value="DEPOSIT">A deposit, online</option>
                  <option value="FULL">Full payment, online</option>
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

              <div className="flex items-center gap-2 rounded-lg bg-canvas px-3 py-2">
                <input
                  id="allowPayInPerson"
                  type="checkbox"
                  checked={allowPayInPerson}
                  onChange={(e) => setAllowPayInPerson(e.target.checked)}
                  className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
                />
                <label htmlFor="allowPayInPerson" className="text-sm text-ink-700">
                  Also let customers choose to pay at the shop instead
                </label>
              </div>

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
              When online bookings are allowed, per day — Sunday can keep different hours than the rest of the week,
              or stay closed entirely. Enforced on the server, not just hidden in the widget.
            </p>

            <form onSubmit={saveHours} className="mt-4 flex flex-col gap-2">
              {dayRows.map((row) => (
                <div key={row.dayOfWeek} className="flex items-center gap-3 rounded-lg bg-canvas px-3 py-2">
                  <button
                    type="button"
                    onClick={() => toggleDayOpen(row.dayOfWeek)}
                    className={`w-14 shrink-0 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition ${
                      row.open ? "border-accent bg-accent-soft text-accent-hover" : "border-border text-ink-500 hover:bg-surface"
                    }`}
                  >
                    {DAY_LABELS[row.dayOfWeek - 1]}
                  </button>
                  {row.open ? (
                    <div className="flex items-center gap-2">
                      <input
                        type="time"
                        value={row.startTime}
                        onChange={(e) => updateDayTime(row.dayOfWeek, "startTime", e.target.value)}
                        className="rounded-lg border border-border bg-surface px-2.5 py-1.5 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                      />
                      <span className="text-xs text-ink-500">to</span>
                      <input
                        type="time"
                        value={row.endTime}
                        onChange={(e) => updateDayTime(row.dayOfWeek, "endTime", e.target.value)}
                        className="rounded-lg border border-border bg-surface px-2.5 py-1.5 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                      />
                    </div>
                  ) : (
                    <span className="text-sm text-ink-400">Closed</span>
                  )}
                </div>
              ))}

              {hoursError && <p className="text-sm text-danger">{hoursError}</p>}

              <Button type="submit" disabled={savingHours} className="mt-2 self-start">
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
        </>
      )}
    </div>
  );
}
