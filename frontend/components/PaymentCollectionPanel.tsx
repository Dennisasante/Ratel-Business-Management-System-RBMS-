"use client";

import { useState } from "react";
import { CheckCircle2, RotateCcw, Smartphone } from "lucide-react";
import { ApiError, MobileMoneyChargeResponse, MobileMoneyProvider, PaymentMethod, RecordPaymentPayload } from "@/lib/api";
import Badge from "@/components/ui/Badge";

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  UNPAID: "Unpaid",
  PARTIALLY_PAID: "Partially paid",
  PAID: "Paid",
  FAILED: "Payment failed",
  REFUNDED: "Refunded",
};

const PAYMENT_STATUS_TONES: Record<string, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  UNPAID: "neutral",
  PARTIALLY_PAID: "info",
  PAID: "success",
  FAILED: "danger",
  REFUNDED: "violet",
};

const RECORD_PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "MOBILE_MONEY_DIRECT", label: "Direct Mobile Money" },
  { value: "MOBILE_MONEY", label: "Online Payment" },
];

const MOMO_PROVIDERS: { value: MobileMoneyProvider; label: string }[] = [
  { value: "mtn", label: "MTN Mobile Money" },
  { value: "atl", label: "AirtelTigo Money" },
  { value: "vod", label: "Telecel Cash (Vodafone)" },
];

interface PaidLike {
  paymentStatus: string;
}

// Everything needed to collect a card/mobile-money payment for a single
// order/sale, with no dependency on the printable receipt — usable inline
// in a list row's modal, right after checkout, or on the receipt page too.
// The caller curries in its own session token; this component only knows
// about the id and the four payment actions.
export default function PaymentCollectionPanel<T extends PaidLike>({
  id,
  amount,
  balanceDue,
  paymentStatus,
  paystackConfigured,
  onVerifyPayment,
  onMarkPaid,
  onChargeMobileMoney,
  onSubmitOtp,
  onRecordPayment,
  onRefund,
  onChanged,
}: {
  id: string;
  amount: number;
  balanceDue: number;
  paymentStatus: string;
  paystackConfigured: boolean;
  onVerifyPayment: (reference: string) => Promise<T>;
  onMarkPaid: (id: string) => Promise<T>;
  onChargeMobileMoney: (id: string, phone: string, provider: MobileMoneyProvider) => Promise<MobileMoneyChargeResponse>;
  onSubmitOtp: (reference: string, otp: string) => Promise<T>;
  onRecordPayment: (id: string, payload: RecordPaymentPayload) => Promise<T>;
  onRefund: (id: string, note?: string) => Promise<T>;
  onChanged: (updated: T) => void;
}) {
  const [payError, setPayError] = useState<string | null>(null);
  const [markingPaid, setMarkingPaid] = useState(false);

  const [showMomoForm, setShowMomoForm] = useState(false);
  const [momoPhone, setMomoPhone] = useState("");
  const [momoProvider, setMomoProvider] = useState<MobileMoneyProvider>("mtn");
  const [chargingMomo, setChargingMomo] = useState(false);
  const [momoPending, setMomoPending] = useState<{ reference: string; message: string; status: string } | null>(null);
  const [verifyingMomo, setVerifyingMomo] = useState(false);
  const [otpCode, setOtpCode] = useState("");
  const [submittingOtp, setSubmittingOtp] = useState(false);

  const [showRecordForm, setShowRecordForm] = useState(false);
  const [recordAmount, setRecordAmount] = useState(() => balanceDue.toFixed(2));
  const [recordMethod, setRecordMethod] = useState<PaymentMethod>("CASH");
  const [recordingPayment, setRecordingPayment] = useState(false);

  const [refunding, setRefunding] = useState(false);

  async function handleMarkPaid() {
    setMarkingPaid(true);
    setPayError(null);
    try {
      const updated = await onMarkPaid(id);
      onChanged(updated);
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't mark this as paid.");
    } finally {
      setMarkingPaid(false);
    }
  }

  async function handleRecordPayment(e: React.FormEvent) {
    e.preventDefault();
    const parsed = Number(recordAmount);
    if (!parsed || parsed <= 0) return;
    setRecordingPayment(true);
    setPayError(null);
    try {
      const updated = await onRecordPayment(id, { amount: parsed, method: recordMethod });
      onChanged(updated);
      setShowRecordForm(false);
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't record that payment.");
    } finally {
      setRecordingPayment(false);
    }
  }

  async function handleRefund() {
    if (!confirm("Refund this payment? This can't be undone.")) return;
    setRefunding(true);
    setPayError(null);
    try {
      const updated = await onRefund(id);
      onChanged(updated);
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't process this refund.");
    } finally {
      setRefunding(false);
    }
  }

  async function handleChargeMobileMoney(e: React.FormEvent) {
    e.preventDefault();
    if (!momoPhone.trim()) return;
    setChargingMomo(true);
    setPayError(null);
    try {
      const result = await onChargeMobileMoney(id, momoPhone.trim(), momoProvider);
      if (result.status.toLowerCase() === "success") {
        setShowMomoForm(false);
        setMomoPending(null);
        // A same-shaped refetch isn't available here — caller's onChanged
        // expects a full entity, so ask it to reload via a verify call.
        const updated = await onVerifyPayment(result.reference);
        onChanged(updated);
      } else if (result.status.toLowerCase() === "failed" || !result.reference) {
        // Paystack rejected the charge outright (most often a first-time payer
        // whose mobile money account needs identification/verification before
        // any charge is even attempted) — there's no reference to verify
        // against later, so this isn't "still in progress." Show Paystack's
        // own reason instead of a dead-end "waiting for approval" state.
        setPayError(
          result.message || "Paystack couldn't start this charge. Try again, or ask the customer to check their mobile money app."
        );
      } else {
        setMomoPending({
          reference: result.reference,
          status: result.status,
          message:
            result.displayText ||
            result.message ||
            (result.status.toLowerCase() === "send_otp"
              ? "A code was texted to the customer's phone."
              : "Ask the customer to check their phone and approve the payment."),
        });
      }
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't start the mobile money charge.");
    } finally {
      setChargingMomo(false);
    }
  }

  async function handleVerifyMobileMoney() {
    if (!momoPending) return;
    setVerifyingMomo(true);
    setPayError(null);
    try {
      const updated = await onVerifyPayment(momoPending.reference);
      onChanged(updated);
      if (updated.paymentStatus === "PAID") {
        setMomoPending(null);
        setShowMomoForm(false);
      } else {
        setPayError("Not confirmed yet — ask the customer to approve it on their phone, then try again.");
      }
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't verify this payment.");
    } finally {
      setVerifyingMomo(false);
    }
  }

  async function handleSubmitOtp(e: React.FormEvent) {
    e.preventDefault();
    if (!momoPending || !otpCode.trim()) return;
    setSubmittingOtp(true);
    setPayError(null);
    try {
      const updated = await onSubmitOtp(momoPending.reference, otpCode.trim());
      onChanged(updated);
      if (updated.paymentStatus === "PAID") {
        setMomoPending(null);
        setShowMomoForm(false);
        setOtpCode("");
      } else {
        setPayError("That code didn't work — check with the customer and try again, or use Verify below once they've actually approved it.");
      }
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "Couldn't submit that code.");
    } finally {
      setSubmittingOtp(false);
    }
  }

  const collecting = paymentStatus === "UNPAID" || paymentStatus === "PARTIALLY_PAID" || paymentStatus === "FAILED";
  const refundable = paymentStatus === "PAID" || paymentStatus === "PARTIALLY_PAID";

  return (
    <div className="flex w-full flex-col gap-2">
      <div className="flex items-center justify-between rounded-lg border border-border bg-surface px-3 py-2">
        <span className="text-sm font-medium text-ink-700">Payment</span>
        <Badge tone={PAYMENT_STATUS_TONES[paymentStatus] ?? "neutral"}>
          {PAYMENT_STATUS_LABELS[paymentStatus] ?? paymentStatus}
        </Badge>
      </div>

      {paymentStatus === "PARTIALLY_PAID" && (
        <p className="text-xs text-ink-500">
          GH₵{(amount - balanceDue).toFixed(2)} collected · GH₵{balanceDue.toFixed(2)} outstanding
        </p>
      )}

      {payError && <p className="text-xs text-danger">{payError}</p>}

      {collecting && (
        <div className="flex flex-col gap-2">
          <button
            onClick={handleMarkPaid}
            disabled={markingPaid}
            className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-ink-700 transition hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
          >
            <CheckCircle2 size={15} />
            {markingPaid ? "Marking..." : "Mark as paid"}
          </button>

          <button
            type="button"
            onClick={() => setShowRecordForm((s) => !s)}
            className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-ink-700 transition hover:bg-canvas"
          >
            {showRecordForm ? "Cancel" : "Record a partial payment"}
          </button>

          {showRecordForm && (
            <form onSubmit={handleRecordPayment} className="flex flex-col gap-2 rounded-lg border border-border p-3">
              <input
                type="number"
                step="0.01"
                min="0.01"
                max={balanceDue}
                value={recordAmount}
                onChange={(e) => setRecordAmount(e.target.value)}
                placeholder="Amount received"
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              <select
                value={recordMethod}
                onChange={(e) => setRecordMethod(e.target.value as PaymentMethod)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                {RECORD_PAYMENT_METHODS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
              <button
                type="submit"
                disabled={recordingPayment || !Number(recordAmount)}
                className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
              >
                {recordingPayment ? "Recording..." : "Record payment"}
              </button>
            </form>
          )}

          {paystackConfigured && !momoPending && (
            <button
              type="button"
              onClick={() => setShowMomoForm((s) => !s)}
              className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-ink-700 transition hover:bg-canvas"
            >
              <Smartphone size={15} />
              {showMomoForm ? "Cancel mobile money charge" : "Charge mobile money"}
            </button>
          )}

          {showMomoForm && !momoPending && (
            <form onSubmit={handleChargeMobileMoney} className="flex flex-col gap-2 rounded-lg border border-border p-3">
              <select
                value={momoProvider}
                onChange={(e) => setMomoProvider(e.target.value as MobileMoneyProvider)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              >
                {MOMO_PROVIDERS.map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </select>
              <input
                value={momoPhone}
                onChange={(e) => setMomoPhone(e.target.value)}
                placeholder="Customer's mobile money number"
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              <button
                type="submit"
                disabled={chargingMomo || !momoPhone.trim()}
                className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
              >
                {chargingMomo ? "Sending prompt..." : `Send GH₵${amount.toFixed(2)} prompt`}
              </button>
            </form>
          )}

          {momoPending && momoPending.status.toLowerCase() === "send_otp" && (
            <form onSubmit={handleSubmitOtp} className="flex flex-col gap-2 rounded-lg border border-border p-3">
              <p className="text-xs text-ink-500">{momoPending.message}</p>
              <input
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value)}
                placeholder="Code from the customer's SMS"
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
              <button
                type="submit"
                disabled={submittingOtp || !otpCode.trim()}
                className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
              >
                {submittingOtp ? "Submitting..." : "Submit code"}
              </button>
              <button
                type="button"
                onClick={handleVerifyMobileMoney}
                disabled={verifyingMomo}
                className="w-full rounded-lg border border-border px-4 py-2 text-sm font-medium text-ink-700 transition hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
              >
                {verifyingMomo ? "Checking..." : "Already approved — verify instead"}
              </button>
            </form>
          )}

          {momoPending && momoPending.status.toLowerCase() !== "send_otp" && (
            <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
              <p className="text-xs text-ink-500">{momoPending.message}</p>
              <button
                onClick={handleVerifyMobileMoney}
                disabled={verifyingMomo}
                className="w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
              >
                {verifyingMomo ? "Checking..." : "They've approved it — verify now"}
              </button>
            </div>
          )}
        </div>
      )}

      {refundable && (
        <button
          type="button"
          onClick={handleRefund}
          disabled={refunding}
          className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-danger transition hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
        >
          <RotateCcw size={15} />
          {refunding ? "Refunding..." : "Refund"}
        </button>
      )}
    </div>
  );
}
