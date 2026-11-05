"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, CheckCircle2, MessageSquare, Send } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, SmsAccount, SmsBundle, SmsEstimate, SmsLedgerEntry, SmsMessage } from "@/lib/api";
import PaystackCheckoutButton from "@/components/PaystackCheckoutButton";
import SmsReceiptCard from "@/components/SmsReceiptCard";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";
import EmptyState from "@/components/ui/EmptyState";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const SENDER_LABELS: Record<string, string> = {
  NONE: "Not set up",
  PENDING: "Awaiting approval",
  APPROVED: "Approved",
};

const SENDER_TONES: Record<string, "neutral" | "info" | "success"> = {
  NONE: "neutral",
  PENDING: "info",
  APPROVED: "success",
};

const STATUS_TONES: Record<string, "neutral" | "success" | "danger"> = {
  QUEUED: "neutral",
  SENT: "success",
  FAILED: "danger",
};

const LEDGER_LABELS: Record<string, string> = {
  PURCHASE: "Bought",
  SEND: "Sent",
  REFUND: "Refunded",
  ADJUSTMENT: "Adjusted",
};

function formatWhen(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export default function SmsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();
  const isOwner = session?.role === "OWNER";

  const [account, setAccount] = useState<SmsAccount | null>(null);
  const [bundles, setBundles] = useState<SmsBundle[]>([]);
  const [messages, setMessages] = useState<SmsMessage[]>([]);
  const [ledger, setLedger] = useState<SmsLedgerEntry[]>([]);
  const [fetching, setFetching] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [phone, setPhone] = useState("");
  const [text, setText] = useState("");
  const [estimate, setEstimate] = useState<SmsEstimate | null>(null);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [sendNotice, setSendNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const [a, m, l] = await Promise.all([
      api.getSmsAccount(session.token),
      api.listSmsMessages(session.token),
      api.listSmsLedger(session.token),
    ]);
    setAccount(a);
    setMessages(m);
    setLedger(l);
    // Bundles are an Owner-only endpoint — a Manager can send but not buy.
    if (session.role === "OWNER") setBundles(await api.listSmsBundles(session.token));
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load()
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : "Couldn't load your SMS account."))
      .finally(() => setFetching(false));
  }, [session, load]);

  // Live credit cost while typing — the server's own count, so the number shown
  // is exactly what will be charged. Debounced to one request per pause.
  useEffect(() => {
    if (!session || text.trim() === "") {
      setEstimate(null);
      return;
    }
    const handle = setTimeout(() => {
      api.estimateSms(session.token, text).then(setEstimate).catch(() => setEstimate(null));
    }, 300);
    return () => clearTimeout(handle);
  }, [session, text]);

  async function handleVerify(reference: string): Promise<boolean> {
    if (!session) return false;
    setError(null);
    setNotice(null);
    try {
      const result = await api.verifySmsPurchase(session.token, reference);
      if (result.success) {
        setNotice("Payment confirmed — your credits have been added.");
      } else {
        setError(result.message || "Payment wasn't completed yet — if you just paid, wait a few seconds and try again.");
      }
      await load();
      return result.success;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't confirm this payment. If you were charged, contact support.");
      return false;
    }
  }

  async function handleSend(e: React.FormEvent) {
    e.preventDefault();
    if (!session || sending) return;
    setSendError(null);
    setSendNotice(null);
    setSending(true);
    try {
      await api.sendTestSms(session.token, phone, text);
      setSendNotice("Message sent.");
      setText("");
      await load();
    } catch (err) {
      setSendError(err instanceof ApiError ? err.message : "Couldn't send that message. Please try again.");
      await load().catch(() => {});
    } finally {
      setSending(false);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const canSend = !!account && account.providerConfigured && account.senderApproved;
  const cost = estimate?.segments ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="SMS" subtitle="Message your customers. Each text uses one credit per 160 characters." />

      {fetching ? (
        <CardSkeleton count={2} />
      ) : loadError || !account ? (
        <p className="text-sm text-danger">{loadError ?? "Couldn't load your SMS account."}</p>
      ) : (
        <>
          <Card className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Credits</p>
              <p className="text-3xl font-semibold text-ink-900">{account.creditBalance.toLocaleString()}</p>
              {account.lowBalance && (
                <p className="mt-1 flex items-center gap-1 text-xs text-danger">
                  <AlertTriangle size={13} /> Running low — buy more so your messages keep going out.
                </p>
              )}
            </div>
            <div className="flex flex-col gap-1 sm:items-end">
              <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Sender name</p>
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-ink-900">{account.senderId ?? "—"}</span>
                <Badge tone={SENDER_TONES[account.senderStatus] ?? "neutral"}>
                  {SENDER_LABELS[account.senderStatus] ?? account.senderStatus}
                </Badge>
              </div>
              {!account.senderApproved && (
                <p className="text-xs text-ink-500">Contact support to set up your sender name before you can send.</p>
              )}
            </div>
          </Card>

          {!account.providerConfigured && (
            <p className="flex items-center gap-1.5 text-sm text-danger">
              <AlertTriangle size={15} /> SMS isn&apos;t available right now. Please contact support.
            </p>
          )}
          {error && <p className="text-sm text-danger">{error}</p>}
          {notice && (
            <p className="flex items-center gap-1.5 text-sm text-success">
              <CheckCircle2 size={15} /> {notice}
            </p>
          )}

          <SmsReceiptCard token={session.token} account={account} onChanged={load} />

          {isOwner && account.providerConfigured && (
            <section className="flex flex-col gap-3">
              <h2 className="text-base font-semibold text-ink-900">Buy credits</h2>
              {bundles.length === 0 ? (
                <p className="text-sm text-ink-500">No credit bundles are on sale yet — check back soon.</p>
              ) : (
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {bundles.map((bundle) => (
                    <Card key={bundle.id} className="flex flex-col gap-3 p-5">
                      <h3 className="text-sm font-semibold text-ink-900">{bundle.name}</h3>
                      <div>
                        <p className="text-2xl font-semibold text-ink-900">GHS {bundle.price.toFixed(2)}</p>
                        <p className="text-xs text-ink-500">
                          {bundle.credits.toLocaleString()} credits · GHS {bundle.pricePerCredit.toFixed(3)} each
                        </p>
                      </div>
                      {/* The checkout button is shared with subscriptions, which call this
                          field a plan id — here it carries the bundle id (months is unused). */}
                      <PaystackCheckoutButton
                        planId={bundle.id}
                        months={1}
                        buttonLabel="Pay with Paystack"
                        className="mt-1 w-full rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white transition hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                        onStartCheckout={(bundleId) => api.startSmsPurchase(session.token, bundleId)}
                        onVerify={handleVerify}
                        onError={setError}
                      />
                    </Card>
                  ))}
                </div>
              )}
            </section>
          )}

          <section className="flex flex-col gap-3">
            <h2 className="text-base font-semibold text-ink-900">Send a message</h2>
            <Card className="max-w-xl p-5">
              <form onSubmit={handleSend} className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="sms-phone" className="text-sm font-medium text-ink-700">
                    Phone number
                  </label>
                  <input
                    id="sms-phone"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="024 123 4567"
                    inputMode="tel"
                    required
                    className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="sms-text" className="text-sm font-medium text-ink-700">
                    Message
                  </label>
                  <textarea
                    id="sms-text"
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    rows={4}
                    maxLength={1000}
                    required
                    className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                  />
                  <p className="text-xs text-ink-500">
                    {estimate
                      ? `${estimate.length} characters · ${cost} credit${cost === 1 ? "" : "s"}`
                      : "Each message uses at least 1 credit."}
                  </p>
                  {estimate?.unicode && (
                    <p className="text-xs text-ink-500">
                      This message has a symbol such as ₵, so it fits fewer characters per credit (70 instead of 160).
                    </p>
                  )}
                </div>
                {sendError && <p className="text-sm text-danger">{sendError}</p>}
                {sendNotice && (
                  <p className="flex items-center gap-1.5 text-sm text-success">
                    <CheckCircle2 size={15} /> {sendNotice}
                  </p>
                )}
                <Button type="submit" disabled={!canSend || sending || text.trim() === ""} className="self-start">
                  <Send size={15} />
                  {sending ? "Sending..." : "Send message"}
                </Button>
              </form>
            </Card>
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-base font-semibold text-ink-900">Recent messages</h2>
            <Card className="p-0">
              {messages.length === 0 ? (
                <EmptyState icon={MessageSquare} title="No messages yet" description="Messages you send will show up here." />
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <THead>
                      <Tr>
                        <Th>When</Th>
                        <Th>To</Th>
                        <Th>Message</Th>
                        <Th>Credits</Th>
                        <Th>Status</Th>
                      </Tr>
                    </THead>
                    <TBody>
                      {messages.map((m) => (
                        <Tr key={m.id}>
                          <Td>{formatWhen(m.createdAt)}</Td>
                          <Td>{m.recipient}</Td>
                          <Td className="max-w-xs truncate">{m.message}</Td>
                          <Td>{m.status === "FAILED" ? 0 : m.creditsUsed}</Td>
                          <Td>
                            <Badge tone={STATUS_TONES[m.status] ?? "neutral"}>{m.status.toLowerCase()}</Badge>
                          </Td>
                        </Tr>
                      ))}
                    </TBody>
                  </Table>
                </div>
              )}
            </Card>
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-base font-semibold text-ink-900">Credit history</h2>
            <Card className="p-0">
              {ledger.length === 0 ? (
                <p className="p-5 text-sm text-ink-500">Nothing here yet.</p>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <THead>
                      <Tr>
                        <Th>When</Th>
                        <Th>What</Th>
                        <Th>Change</Th>
                        <Th>Balance</Th>
                      </Tr>
                    </THead>
                    <TBody>
                      {ledger.map((entry, i) => (
                        <Tr key={`${entry.createdAt}-${i}`}>
                          <Td>{formatWhen(entry.createdAt)}</Td>
                          <Td>
                            {LEDGER_LABELS[entry.type] ?? entry.type}
                            {entry.description ? ` — ${entry.description}` : ""}
                          </Td>
                          <Td className={entry.delta >= 0 ? "text-success" : "text-ink-900"}>
                            {entry.delta > 0 ? `+${entry.delta}` : entry.delta}
                          </Td>
                          <Td>{entry.balanceAfter}</Td>
                        </Tr>
                      ))}
                    </TBody>
                  </Table>
                </div>
              )}
            </Card>
          </section>
        </>
      )}
    </div>
  );
}
