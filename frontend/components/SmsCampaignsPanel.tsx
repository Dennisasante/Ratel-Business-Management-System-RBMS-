"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, ArrowLeft, Megaphone, Search, Send } from "lucide-react";
import {
  api,
  ApiError,
  SmsAccount,
  SmsAudienceRow,
  SmsCampaignDetail,
  SmsCampaignPreview,
  SmsCampaignSummary,
  SmsEstimate,
} from "@/lib/api";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import Modal from "@/components/Modal";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const FOOTER = "\nReply STOP to opt out.";

const STATUS_LABELS: Record<string, string> = { SENDING: "Sending", DONE: "Finished", CANCELLED: "Stopped" };
const STATUS_TONES: Record<string, "info" | "success" | "neutral"> = { SENDING: "info", DONE: "success", CANCELLED: "neutral" };
const RECIPIENT_TONES: Record<string, "neutral" | "info" | "success" | "danger"> = {
  PENDING: "neutral",
  SENDING: "info",
  SENT: "success",
  FAILED: "danger",
  CANCELLED: "neutral",
};
const RECIPIENT_LABELS: Record<string, string> = {
  PENDING: "waiting",
  SENDING: "sending",
  SENT: "sent",
  FAILED: "not sent",
  CANCELLED: "stopped",
};

function formatWhen(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function title(c: SmsCampaignSummary) {
  if (c.name) return c.name;
  return c.message.length > 44 ? `${c.message.slice(0, 44)}…` : c.message;
}

function ProgressBar({ done, total }: { done: number; total: number }) {
  const pct = total === 0 ? 0 : Math.min(100, Math.round((done / total) * 100));
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-canvas" role="img" aria-label={`${done} of ${total} sent`}>
      <div className="h-full rounded-full bg-accent transition-all" style={{ width: `${pct}%` }} />
    </div>
  );
}

// ---------------------------------------------------------------------------------------------

export default function SmsCampaignsPanel({
  token,
  account,
  onChanged,
}: {
  token: string;
  account: SmsAccount;
  onChanged: () => Promise<void>;
}) {
  const [campaigns, setCampaigns] = useState<SmsCampaignSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [composing, setComposing] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);
  const wasSending = useRef(false);

  const load = useCallback(async () => {
    try {
      const list = await api.listSmsCampaigns(token);
      setCampaigns(list);
      const sending = list.some((c) => c.status === "SENDING");
      // When the last running campaign finishes, any credits handed back for unsent texts change the balance.
      if (wasSending.current && !sending) void onChanged();
      wasSending.current = sending;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load your campaigns.");
    }
  }, [token, onChanged]);

  useEffect(() => {
    void load();
  }, [load]);

  // Keep the progress fresh while anything is still going out.
  const anySending = campaigns?.some((c) => c.status === "SENDING") ?? false;
  useEffect(() => {
    if (!anySending) return;
    const handle = setInterval(() => void load(), 3000);
    return () => clearInterval(handle);
  }, [anySending, load]);

  const ready = account.providerConfigured && account.senderApproved;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-ink-900">Campaigns</h2>
          <p className="mt-0.5 max-w-xl text-xs text-ink-500">
            Text a promotion — a sale, a discount, news — to all your customers or just a few. You see the cost and who gets it
            before anything is sent.
          </p>
        </div>
        <Button type="button" onClick={() => setComposing(true)} disabled={!ready}>
          <Megaphone size={15} /> New campaign
        </Button>
      </div>

      {!ready && (
        <p className="flex items-center gap-1.5 text-sm text-danger">
          <AlertTriangle size={15} />
          {!account.providerConfigured ? "SMS isn't available right now. Please contact support." : "Your sender name isn't approved yet — contact support to set it up."}
        </p>
      )}
      {error && <p className="text-sm text-danger">{error}</p>}

      <Card className="p-0">
        {campaigns === null ? (
          <p className="p-5 text-sm text-ink-500">Loading...</p>
        ) : campaigns.length === 0 ? (
          <EmptyState icon={Megaphone} title="No campaigns yet" description="Your first promotion to customers will show up here, with a delivery report." />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Campaign</Th>
                <Th>Started</Th>
                <Th>Status</Th>
                <Th>Delivered</Th>
                <Th>Credits used</Th>
              </Tr>
            </THead>
            <TBody>
              {campaigns.map((c) => (
                <Tr key={c.id} className="cursor-pointer" onClick={() => setOpenId(c.id)}>
                  <Td className="max-w-xs truncate font-medium">{title(c)}</Td>
                  <Td>{formatWhen(c.createdAt)}</Td>
                  <Td>
                    <Badge tone={STATUS_TONES[c.status] ?? "neutral"}>{STATUS_LABELS[c.status] ?? c.status}</Badge>
                  </Td>
                  <Td className="min-w-[9rem]">
                    <p className="tabular text-xs text-ink-700">
                      {c.sent} of {c.recipients} sent{c.failed > 0 ? ` · ${c.failed} not sent` : ""}
                    </p>
                    <ProgressBar done={c.sent} total={c.recipients} />
                  </Td>
                  <Td className="tabular">{c.creditsUsed}</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {composing && (
        <CampaignComposer
          token={token}
          account={account}
          onClose={() => setComposing(false)}
          onSent={async (created) => {
            setComposing(false);
            await load();
            await onChanged();
            setOpenId(created.id);
          }}
        />
      )}

      {openId && (
        <CampaignReport
          token={token}
          campaignId={openId}
          onClose={() => setOpenId(null)}
          onChanged={async () => {
            await load();
            await onChanged();
          }}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Compose -> review -> send

function CampaignComposer({
  token,
  account,
  onClose,
  onSent,
}: {
  token: string;
  account: SmsAccount;
  onClose: () => void;
  onSent: (created: SmsCampaignSummary) => Promise<void>;
}) {
  // One key per composer: pressing Send twice (or a retry after a dropped connection) can only ever make one campaign.
  const requestKey = useMemo(() => crypto.randomUUID(), []);

  const [step, setStep] = useState<"compose" | "review">("compose");
  const [name, setName] = useState("");
  const [message, setMessage] = useState("");
  const [audience, setAudience] = useState<"ALL" | "SELECTED">("ALL");
  const [footer, setFooter] = useState(true);
  const [rows, setRows] = useState<SmsAudienceRow[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const [estimate, setEstimate] = useState<SmsEstimate | null>(null);
  const [preview, setPreview] = useState<SmsCampaignPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const textRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    api.listSmsAudience(token).then(setRows).catch(() => setError("Couldn't load your customers."));
  }, [token]);

  const reachable = useMemo(() => rows.filter((r) => r.reachable), [rows]);
  const shown = useMemo(() => {
    const q = search.trim().toLowerCase();
    return q ? rows.filter((r) => r.name.toLowerCase().includes(q) || (r.phone ?? "").includes(q)) : rows;
  }, [rows, search]);

  // Cost of ONE text, using a typical first name — the server's own count.
  useEffect(() => {
    if (message.trim() === "") {
      setEstimate(null);
      return;
    }
    const sample = message.replaceAll("{name}", "Kofi") + (footer ? FOOTER : "");
    const handle = setTimeout(() => api.estimateSms(token, sample).then(setEstimate).catch(() => setEstimate(null)), 300);
    return () => clearTimeout(handle);
  }, [token, message, footer]);

  function insertName() {
    const el = textRef.current;
    if (!el) {
      setMessage((m) => `${m}{name}`);
      return;
    }
    const start = el.selectionStart ?? message.length;
    const end = el.selectionEnd ?? message.length;
    setMessage(`${message.slice(0, start)}{name}${message.slice(end)}`);
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + 6, start + 6);
    });
  }

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const payload = {
    name: name.trim() || undefined,
    message,
    audience,
    customerIds: audience === "SELECTED" ? Array.from(selected) : undefined,
    includeOptOutFooter: footer,
    requestKey,
  };

  async function review() {
    setBusy(true);
    setError(null);
    try {
      setPreview(await api.previewSmsCampaign(token, payload));
      setStep("review");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't prepare that. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function send() {
    setBusy(true);
    setError(null);
    try {
      const created = await api.createSmsCampaign(token, payload);
      await onSent(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't start the campaign. Nothing was sent or charged.");
      setBusy(false);
    }
  }

  const canReview = message.trim() !== "" && (audience === "ALL" || selected.size > 0);

  return (
    <Modal title={step === "compose" ? "New campaign" : "Review and send"} onClose={onClose} maxWidthClassName="max-w-2xl">
      {step === "compose" ? (
        <div className="mt-4 flex flex-col gap-5">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="campaign-name" className="text-sm font-medium text-ink-700">
              Campaign name <span className="font-normal text-ink-400">(optional, just for you)</span>
            </label>
            <input
              id="campaign-name"
              value={name}
              maxLength={80}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Black Friday sale"
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="campaign-message" className="text-sm font-medium text-ink-700">
                Message
              </label>
              <button type="button" onClick={insertName} className="text-xs font-medium text-accent-hover hover:underline">
                Insert customer&apos;s name
              </button>
            </div>
            <textarea
              id="campaign-message"
              ref={textRef}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              rows={4}
              maxLength={600}
              placeholder="Hi {name}, enjoy 20% off all wigs this weekend at Glow Studio!"
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
            <p className="text-xs text-ink-500">
              {estimate
                ? `${estimate.length} characters · about ${estimate.segments} credit${estimate.segments === 1 ? "" : "s"} per customer`
                : "Each text uses at least 1 credit per customer."}
              {estimate?.unicode ? " · A symbol such as ₵ makes the text shorter (70 characters per credit instead of 160)." : ""}
            </p>
            <label className="mt-1 flex items-start gap-2 text-sm text-ink-700">
              <input
                type="checkbox"
                checked={footer}
                onChange={(e) => setFooter(e.target.checked)}
                className="mt-0.5 rounded border-border text-accent focus:ring-accent/20"
              />
              <span>
                End with &ldquo;Reply STOP to opt out.&rdquo;
                <span className="block text-xs text-ink-500">Recommended for promotional texts.</span>
              </span>
            </label>
          </div>

          <div className="flex flex-col gap-2">
            <p className="text-sm font-medium text-ink-700">Who gets it</p>
            <div role="radiogroup" aria-label="Audience" className="grid gap-2 sm:grid-cols-2">
              {(
                [
                  { value: "ALL", title: "All my customers", detail: `${reachable.length} can be texted` },
                  { value: "SELECTED", title: "Choose customers", detail: audience === "SELECTED" ? `${selected.size} chosen` : "Pick a few from the list" },
                ] as const
              ).map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={audience === option.value}
                  onClick={() => setAudience(option.value)}
                  className={`rounded-xl border p-3 text-left transition ${
                    audience === option.value ? "border-accent ring-1 ring-accent" : "border-border hover:bg-canvas"
                  }`}
                >
                  <span className="block text-sm font-semibold text-ink-900">{option.title}</span>
                  <span className="block text-xs text-ink-500">{option.detail}</span>
                </button>
              ))}
            </div>
            <p className="text-xs text-ink-500">Customers without a phone number, or who asked not to get promotions, are skipped automatically.</p>

            {audience === "SELECTED" && (
              <div className="rounded-xl border border-border">
                <div className="flex flex-wrap items-center gap-2 border-b border-border p-2">
                  <div className="relative min-w-[10rem] flex-1">
                    <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-400" />
                    <input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="Search name or phone"
                      aria-label="Search customers"
                      className="w-full rounded-lg border border-border bg-surface py-1.5 pl-8 pr-2 text-sm text-ink-900 focus:border-accent focus:outline-none"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelected((prev) => new Set([...prev, ...shown.filter((r) => r.reachable).map((r) => r.id)]))}
                    className="text-xs font-medium text-accent-hover hover:underline"
                  >
                    Select all shown
                  </button>
                  <button type="button" onClick={() => setSelected(new Set())} className="text-xs font-medium text-ink-500 hover:underline">
                    Clear
                  </button>
                </div>
                <ul className="max-h-56 divide-y divide-border overflow-y-auto">
                  {shown.length === 0 && <li className="p-3 text-sm text-ink-500">No customers match.</li>}
                  {shown.map((r) => (
                    <li key={r.id}>
                      <label className={`flex items-center gap-3 px-3 py-2 text-sm ${r.reachable ? "cursor-pointer hover:bg-canvas" : "opacity-50"}`}>
                        <input
                          type="checkbox"
                          disabled={!r.reachable}
                          checked={selected.has(r.id)}
                          onChange={() => toggle(r.id)}
                          className="rounded border-border text-accent focus:ring-accent/20"
                        />
                        <span className="flex-1 truncate text-ink-900">{r.name}</span>
                        <span className="text-xs text-ink-500">
                          {r.optedOut ? "Opted out" : r.phone ? r.phone : "No phone number"}
                        </span>
                      </label>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex gap-2">
            <Button type="button" onClick={review} disabled={!canReview || busy}>
              {busy ? "Checking..." : "Review"}
            </Button>
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        preview && (
          <div className="mt-4 flex flex-col gap-4">
            <div>
              <p className="text-3xl font-semibold text-ink-900">
                {preview.recipients}
                <span className="text-lg font-normal text-ink-500"> customer{preview.recipients === 1 ? "" : "s"} will get this text</span>
              </p>
              {(preview.skippedNoPhone > 0 || preview.skippedOptedOut > 0 || preview.skippedDuplicate > 0) && (
                <p className="mt-1 text-xs text-ink-500">
                  Skipped:{" "}
                  {[
                    preview.skippedNoPhone > 0 ? `${preview.skippedNoPhone} with no phone number` : null,
                    preview.skippedOptedOut > 0 ? `${preview.skippedOptedOut} who asked not to get promotions` : null,
                    preview.skippedDuplicate > 0 ? `${preview.skippedDuplicate} sharing a number with someone already included` : null,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              )}
            </div>

            <div className={`rounded-lg px-3 py-2 text-sm ${preview.enoughCredits ? "bg-canvas text-ink-700" : "bg-danger-soft text-danger"}`}>
              <p className="font-medium">
                Costs {preview.creditsNeeded} credit{preview.creditsNeeded === 1 ? "" : "s"}
                {preview.minCreditsPerText !== preview.maxCreditsPerText ? ` (${preview.minCreditsPerText}–${preview.maxCreditsPerText} per text, depending on the name)` : ""}
              </p>
              <p className="text-xs">
                {preview.enoughCredits
                  ? `You have ${preview.creditsAvailable}, leaving ${preview.creditsAvailable - preview.creditsNeeded}.`
                  : `You have ${preview.creditsAvailable} — you need ${preview.creditsNeeded - preview.creditsAvailable} more. Buy credits in the Buy credits tab first.`}
              </p>
            </div>

            {preview.sampleMessage && (
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-ink-500">What a customer receives</p>
                <p className="mt-1 whitespace-pre-line rounded-lg bg-canvas px-3 py-2 text-sm text-ink-900">{preview.sampleMessage}</p>
              </div>
            )}

            <ul className="list-disc pl-5 text-xs text-ink-500">
              <li>Texts go out over the next few minutes. You can stop the campaign at any time and the unsent ones are refunded.</li>
              <li>A text the network can&apos;t deliver is refunded automatically.</li>
              <li>
                If someone replies STOP, open their customer page, press Edit and tick &ldquo;Doesn&apos;t want promotional texts&rdquo; — future campaigns will skip
                them.
              </li>
            </ul>

            {error && <p className="text-sm text-danger">{error}</p>}
            <div className="flex gap-2">
              <Button type="button" variant="secondary" onClick={() => setStep("compose")} disabled={busy}>
                <ArrowLeft size={14} /> Back
              </Button>
              <Button type="button" onClick={send} disabled={busy || !preview.enoughCredits || preview.recipients === 0}>
                <Send size={14} />
                {busy ? "Starting..." : `Send to ${preview.recipients} customer${preview.recipients === 1 ? "" : "s"}`}
              </Button>
            </div>
          </div>
        )
      )}
    </Modal>
  );
}

// ---------------------------------------------------------------------------------------------
// The delivery report for one campaign

function CampaignReport({
  token,
  campaignId,
  onClose,
  onChanged,
}: {
  token: string;
  campaignId: string;
  onClose: () => void;
  onChanged: () => Promise<void>;
}) {
  const [detail, setDetail] = useState<SmsCampaignDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmingStop, setConfirmingStop] = useState(false);
  const [stopping, setStopping] = useState(false);

  const load = useCallback(async () => {
    try {
      setDetail(await api.getSmsCampaign(token, campaignId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load that campaign.");
    }
  }, [token, campaignId]);

  useEffect(() => {
    void load();
  }, [load]);

  const sending = detail?.campaign.status === "SENDING";
  useEffect(() => {
    if (!sending) return;
    const handle = setInterval(() => void load(), 3000);
    return () => clearInterval(handle);
  }, [sending, load]);

  async function stop() {
    setStopping(true);
    setError(null);
    try {
      await api.cancelSmsCampaign(token, campaignId);
      setConfirmingStop(false);
      await load();
      await onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't stop that campaign.");
    } finally {
      setStopping(false);
    }
  }

  const c = detail?.campaign;

  return (
    <Modal title={c ? title(c) : "Campaign"} onClose={onClose} maxWidthClassName="max-w-3xl">
      {!c ? (
        <p className="mt-4 text-sm text-ink-500">{error ?? "Loading..."}</p>
      ) : (
        <div className="mt-4 flex flex-col gap-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={STATUS_TONES[c.status] ?? "neutral"}>{STATUS_LABELS[c.status] ?? c.status}</Badge>
            <span className="text-xs text-ink-500">Started {formatWhen(c.createdAt)}</span>
          </div>

          <p className="whitespace-pre-line rounded-lg bg-canvas px-3 py-2 text-sm text-ink-900">{c.message}</p>

          <div>
            <ProgressBar done={c.sent} total={c.recipients} />
            <p className="mt-1.5 text-sm text-ink-700">
              <strong>{c.sent}</strong> of {c.recipients} sent
              {c.waiting > 0 && <> · {c.waiting} still going out</>}
              {c.failed > 0 && <> · {c.failed} couldn&apos;t be sent</>}
              {c.cancelled > 0 && <> · {c.cancelled} stopped</>}
            </p>
            <p className="text-xs text-ink-500">
              Credits: {c.creditsReserved} reserved{c.creditsRefunded > 0 ? `, ${c.creditsRefunded} returned` : ""} · <strong>{c.creditsUsed} used</strong>
            </p>
          </div>

          {sending && (
            <div>
              {!confirmingStop ? (
                <Button type="button" variant="secondary" onClick={() => setConfirmingStop(true)}>
                  Stop this campaign
                </Button>
              ) : (
                <div className="flex flex-col gap-2 rounded-lg border border-danger/30 bg-danger-soft px-3 py-2">
                  <p className="text-sm text-danger">
                    Stop now? Texts already sent can&apos;t be recalled. The {c.waiting} still waiting won&apos;t go out and their credits come back.
                  </p>
                  <div className="flex gap-2">
                    <Button type="button" variant="danger" onClick={stop} disabled={stopping}>
                      {stopping ? "Stopping..." : "Yes, stop it"}
                    </Button>
                    <Button type="button" variant="ghost" onClick={() => setConfirmingStop(false)}>
                      Keep sending
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
          {error && <p className="text-sm text-danger">{error}</p>}

          <div className="max-h-72 overflow-y-auto rounded-lg border border-border">
            <Table>
              <THead>
                <Tr>
                  <Th>Customer</Th>
                  <Th>Phone</Th>
                  <Th>Result</Th>
                </Tr>
              </THead>
              <TBody>
                {detail.recipients.map((r, i) => (
                  <Tr key={`${r.phone}-${i}`}>
                    <Td>{r.customerName ?? "—"}</Td>
                    <Td>{r.phone}</Td>
                    <Td>
                      <Badge tone={RECIPIENT_TONES[r.status] ?? "neutral"}>{RECIPIENT_LABELS[r.status] ?? r.status}</Badge>
                      {r.status === "FAILED" && <span className="ml-2 text-xs text-ink-500">credit returned</span>}
                    </Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
          </div>
          {c.recipients > detail.recipients.length && (
            <p className="text-xs text-ink-500">Showing the first {detail.recipients.length} customers. The totals above count everyone.</p>
          )}
        </div>
      )}
    </Modal>
  );
}
