"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, CheckCircle2, History, Megaphone, MessageSquare, Receipt, Send, ShoppingBag, Wallet } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, SmsAccount, SmsBundle, SmsEstimate, SmsLedgerEntry, SmsMessage, SmsReceiptMode } from "@/lib/api";
import PaystackCheckoutButton from "@/components/PaystackCheckoutButton";
import SmsReceiptCard from "@/components/SmsReceiptCard";
import SmsCampaignsPanel from "@/components/SmsCampaignsPanel";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";
import EmptyState from "@/components/ui/EmptyState";
import Tabs, { TabItem, tabButtonId, tabPanelId } from "@/components/ui/Tabs";
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

const KIND_LABELS: Record<string, string> = {
  TEST: "Message",
  RECEIPT: "Receipt",
  LOYALTY: "Points",
  CAMPAIGN: "Broadcast",
  TRANSACTIONAL: "Notice",
};

const LEDGER_LABELS: Record<string, string> = {
  PURCHASE: "Bought",
  SEND: "Sent",
  REFUND: "Refunded",
  ADJUSTMENT: "Adjusted",
};

const RECEIPT_MODE_LABELS: Record<SmsReceiptMode, string> = {
  PAPER: "Paper only",
  SMS: "SMS only",
  BOTH: "Paper and SMS",
};

type TabId = "send" | "campaigns" | "receipts" | "buy" | "messages" | "credits";

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

  const [tab, setTab] = useState<TabId>("send");

  const [phone, setPhone] = useState("");
  const [text, setText] = useState("");
  const [estimate, setEstimate] = useState<SmsEstimate | null>(null);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [sendNotice, setSendNotice] = useState<string | null>(null);

  // Buying credits is an Owner action, so Managers simply don't get that tab.
  const tabs: TabItem[] = useMemo(
    () => [
      { id: "send", label: "Send a message", icon: Send },
      { id: "campaigns", label: "Campaigns", icon: Megaphone },
      { id: "receipts", label: "Receipts", icon: Receipt },
      ...(isOwner ? [{ id: "buy", label: "Buy credits", icon: ShoppingBag }] : []),
      { id: "messages", label: "Messages", icon: MessageSquare },
      { id: "credits", label: "Credit history", icon: History },
    ],
    [isOwner]
  );

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

  // The tab lives in the address (?tab=receipts) so it survives a refresh and can be linked to.
  useEffect(() => {
    const wanted = new URLSearchParams(window.location.search).get("tab");
    if (wanted && tabs.some((t) => t.id === wanted)) setTab(wanted as TabId);
  }, [tabs]);

  function selectTab(id: string) {
    setTab(id as TabId);
    window.history.replaceState(null, "", `?tab=${id}`);
  }

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
      <PageHeader title="SMS" subtitle="Message your customers and send them receipts. Each text uses one credit per 160 characters." />

      {fetching ? (
        <CardSkeleton count={2} />
      ) : loadError || !account ? (
        <p className="text-sm text-danger">{loadError ?? "Couldn't load your SMS account."}</p>
      ) : (
        <>
          {/* Always visible, whichever tab is open: what you have, who it's sent as, how receipts go. */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4">
            <Card className="flex items-start gap-3 p-3 sm:p-4">
              <Wallet size={18} className="mt-1 hidden shrink-0 text-ink-400 sm:block" />
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Credits</p>
                <p className="text-2xl font-semibold text-ink-900">{account.creditBalance.toLocaleString()}</p>
                {account.lowBalance && (
                  <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-danger">
                    <span className="flex items-center gap-1 whitespace-nowrap">
                      <AlertTriangle size={12} /> Running low
                    </span>
                    {isOwner && (
                      <button type="button" onClick={() => selectTab("buy")} className="whitespace-nowrap font-medium underline">
                        Buy more
                      </button>
                    )}
                  </p>
                )}
              </div>
            </Card>
            <Card className="flex items-start gap-3 p-3 sm:p-4">
              <MessageSquare size={18} className="mt-1 hidden shrink-0 text-ink-400 sm:block" />
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Sender name</p>
                <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-ink-900">
                  {account.senderId ?? "—"}
                  <Badge tone={SENDER_TONES[account.senderStatus] ?? "neutral"}>{SENDER_LABELS[account.senderStatus] ?? account.senderStatus}</Badge>
                </p>
                {!account.senderApproved && <p className="mt-0.5 text-xs text-ink-500">Contact support to get this set up before you can send.</p>}
              </div>
            </Card>
            <Card className="col-span-2 flex items-start gap-3 p-3 sm:col-span-1 sm:p-4">
              <Receipt size={18} className="mt-1 hidden shrink-0 text-ink-400 sm:block" />
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Customer receipts</p>
                <p className="text-sm font-medium text-ink-900">{RECEIPT_MODE_LABELS[account.receiptMode] ?? account.receiptMode}</p>
                <button type="button" onClick={() => selectTab("receipts")} className="mt-0.5 text-xs font-medium text-accent-hover hover:underline">
                  Change
                </button>
              </div>
            </Card>
          </div>

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

          <div className="flex flex-col gap-5">
            <Tabs tabs={tabs} active={tab} onChange={selectTab} ariaLabel="SMS sections" />

            {tab === "send" && (
              <div role="tabpanel" id={tabPanelId("send")} aria-labelledby={tabButtonId("send")}>
                <Card className="max-w-xl p-5">
                  <h2 className="text-base font-semibold text-ink-900">Send a message</h2>
                  <p className="mt-0.5 text-xs text-ink-500">To one customer, from your sender name.</p>
                  <form onSubmit={handleSend} className="mt-4 flex flex-col gap-4">
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
              </div>
            )}

            {tab === "campaigns" && (
              <div role="tabpanel" id={tabPanelId("campaigns")} aria-labelledby={tabButtonId("campaigns")}>
                <SmsCampaignsPanel token={session.token} account={account} onChanged={load} />
              </div>
            )}

            {tab === "receipts" && (
              <div role="tabpanel" id={tabPanelId("receipts")} aria-labelledby={tabButtonId("receipts")}>
                <SmsReceiptCard token={session.token} account={account} onChanged={load} />
              </div>
            )}

            {tab === "buy" && isOwner && (
              <div role="tabpanel" id={tabPanelId("buy")} aria-labelledby={tabButtonId("buy")} className="flex flex-col gap-3">
                <div>
                  <h2 className="text-base font-semibold text-ink-900">Buy credits</h2>
                  <p className="mt-0.5 text-xs text-ink-500">Pay with Paystack. Credits are added as soon as the payment is confirmed.</p>
                </div>
                {!account.providerConfigured ? (
                  <p className="text-sm text-ink-500">Credits can&apos;t be bought until SMS is switched on for your account.</p>
                ) : bundles.length === 0 ? (
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
              </div>
            )}

            {tab === "messages" && (
              <div role="tabpanel" id={tabPanelId("messages")} aria-labelledby={tabButtonId("messages")} className="flex flex-col gap-3">
                <div>
                  <h2 className="text-base font-semibold text-ink-900">Messages</h2>
                  <p className="mt-0.5 text-xs text-ink-500">Your most recent texts and whether they went out. A failed text costs you nothing.</p>
                </div>
                <Card className="p-0">
                  {messages.length === 0 ? (
                    <EmptyState icon={MessageSquare} title="No messages yet" description="Messages you send, and receipts texted to customers, show up here." />
                  ) : (
                    <Table>
                      <THead>
                        <Tr>
                          <Th>When</Th>
                          <Th>Type</Th>
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
                            <Td>
                              <Badge tone={m.kind === "RECEIPT" ? "accent" : "neutral"}>{KIND_LABELS[m.kind] ?? m.kind}</Badge>
                            </Td>
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
                  )}
                </Card>
              </div>
            )}

            {tab === "credits" && (
              <div role="tabpanel" id={tabPanelId("credits")} aria-labelledby={tabButtonId("credits")} className="flex flex-col gap-3">
                <div>
                  <h2 className="text-base font-semibold text-ink-900">Credit history</h2>
                  <p className="mt-0.5 text-xs text-ink-500">Every credit added or used, with your balance after each one.</p>
                </div>
                <Card className="p-0">
                  {ledger.length === 0 ? (
                    <p className="p-5 text-sm text-ink-500">Nothing here yet.</p>
                  ) : (
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
                            <Td className={entry.delta >= 0 ? "text-success" : "text-ink-900"}>{entry.delta > 0 ? `+${entry.delta}` : entry.delta}</Td>
                            <Td>{entry.balanceAfter}</Td>
                          </Tr>
                        ))}
                      </TBody>
                    </Table>
                  )}
                </Card>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
