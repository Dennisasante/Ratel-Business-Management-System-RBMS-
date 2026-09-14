"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Bot, Sparkles, MessageCircle, Plus, Send, Wrench, ShieldAlert, CheckCircle2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  AiOverview,
  AiSettings,
  AiSettingsPayload,
  AiKnowledgeEntry,
  AiKnowledgeEntryPayload,
  AiConversationSummary,
  AiConversationDetail,
  AiMessage,
  AiActionEntry,
  AiToolCallSummary,
  AiChannelStatus,
} from "@/lib/api";
import Modal from "@/components/Modal";
import UpsellBanner from "@/components/UpsellBanner";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

type Tab = "overview" | "settings" | "knowledge" | "conversations" | "channels" | "test";

const CONVERSATION_STATUS_TONE: Record<string, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  ACTIVE: "info",
  ESCALATED: "danger",
  CLOSED: "neutral",
};

export default function AiDashboardPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [tab, setTab] = useState<Tab>("overview");
  const [upsellMessage, setUpsellMessage] = useState<string | null>(null);
  const [fetching, setFetching] = useState(true);
  const [overview, setOverview] = useState<AiOverview | null>(null);

  const canConfigure = session?.role === "OWNER" || session?.role === "MANAGER";

  const loadOverview = useCallback(async () => {
    if (!session) return;
    try {
      const data = await api.getAiOverview(session.token);
      setOverview(data);
      setUpsellMessage(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setUpsellMessage(err.message);
      } else {
        throw err;
      }
    }
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadOverview().finally(() => setFetching(false));
  }, [session, loadOverview]);

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Tallia AI"
        subtitle="Your AI concierge — configure it, teach it what to say, and test it before customers ever see it."
      />

      {upsellMessage && <UpsellBanner message={upsellMessage} />}

      {!upsellMessage && (
        <>
          <div className="flex flex-wrap gap-2">
            <TabChip label="Overview" active={tab === "overview"} onClick={() => setTab("overview")} />
            <TabChip label="Settings" active={tab === "settings"} onClick={() => setTab("settings")} />
            <TabChip label="Knowledge Base" active={tab === "knowledge"} onClick={() => setTab("knowledge")} />
            <TabChip label="Conversations" active={tab === "conversations"} onClick={() => setTab("conversations")} />
            <TabChip label="Channels" active={tab === "channels"} onClick={() => setTab("channels")} />
            <TabChip label="AI Concierge" active={tab === "test"} onClick={() => setTab("test")} />
          </div>

          {fetching ? (
            <TableSkeleton cols={4} />
          ) : (
            <>
              {tab === "overview" && <OverviewTab overview={overview} />}
              {tab === "settings" && <SettingsTab token={session.token} canConfigure={canConfigure} onSaved={loadOverview} />}
              {tab === "knowledge" && <KnowledgeTab token={session.token} canConfigure={canConfigure} onChanged={loadOverview} />}
              {tab === "conversations" && <ConversationsTab token={session.token} />}
              {tab === "channels" && <ChannelsTab token={session.token} />}
              {tab === "test" && <TestAiTab token={session.token} onTurnCompleted={loadOverview} />}
            </>
          )}
        </>
      )}
    </div>
  );
}

function TabChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
        active
          ? "border-accent bg-accent-soft text-accent-hover"
          : "border-border bg-surface text-ink-700 hover:border-border-strong"
      }`}
    >
      {label}
    </button>
  );
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-xl border border-border bg-surface p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular text-ink-900">{value}</p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------

function OverviewTab({ overview }: { overview: AiOverview | null }) {
  if (!overview) return <TableSkeleton cols={4} />;
  return (
    <Card className="flex flex-col gap-5 p-5">
      <div className="flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-accent-soft text-accent-hover">
          <Bot size={20} />
        </div>
        <div>
          <p className="text-sm font-semibold text-ink-900">{overview.agentName}</p>
          <Badge tone={overview.active ? "success" : "neutral"}>{overview.active ? "Active" : "Paused"}</Badge>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <StatCard label="Conversations" value={overview.conversationCount} />
        <StatCard label="Active" value={overview.activeConversationCount} />
        <StatCard label="Escalations" value={overview.escalatedCount} />
        <StatCard label="AI actions" value={overview.actionCount} />
        <StatCard label="Bookings by AI" value={overview.bookingsCreatedByAi} />
        <StatCard label="Knowledge entries" value={overview.knowledgeEntryCount} />
      </div>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Channels — read-only status only. WhatsApp connection itself is Super-
// Admin-configured (Platform > Businesses > this business), not from here —
// no fake "Connect" button for a channel this dashboard can't actually wire
// up on its own.
// ---------------------------------------------------------------------------

function ChannelsTab({ token }: { token: string }) {
  const [channels, setChannels] = useState<AiChannelStatus[] | null>(null);

  useEffect(() => {
    api.listAiChannels(token).then(setChannels);
  }, [token]);

  if (!channels) return <TableSkeleton cols={2} />;

  return (
    <Card className="flex flex-col gap-3 p-5">
      <div>
        <p className="text-sm font-semibold text-ink-900">Channels</p>
        <p className="text-xs text-ink-500">
          Where customers can currently reach Tallia AI. Web Demo (the Test AI tab) is always available once AI is
          on. WhatsApp is connected by Ratel on request — contact support once you have a WhatsApp Business number
          ready.
        </p>
      </div>
      <div className="flex flex-col divide-y divide-border rounded-lg border border-border">
        {channels.map((c) => (
          <div key={c.channel} className="flex items-center justify-between gap-3 px-4 py-3">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-canvas text-ink-500">
                <MessageCircle size={16} />
              </div>
              <div>
                <p className="text-sm font-medium text-ink-900">{c.label}</p>
                <p className="text-xs text-ink-500">{c.statusMessage}</p>
                {c.connected && (c.displayName || c.phoneNumberId) && (
                  <p className="text-xs text-ink-400">
                    {c.displayName ?? "—"}
                    {c.phoneNumberId ? ` · ${c.phoneNumberId}` : ""}
                  </p>
                )}
              </div>
            </div>
            <Badge tone={c.connected ? "success" : "neutral"}>{c.connected ? "Connected" : "Not connected"}</Badge>
          </div>
        ))}
      </div>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

function SettingsTab({ token, canConfigure, onSaved }: { token: string; canConfigure: boolean; onSaved: () => void }) {
  const [settings, setSettings] = useState<AiSettings | null>(null);
  const [form, setForm] = useState<AiSettingsPayload | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api.getAiSettings(token).then((s) => {
      setSettings(s);
      setForm({
        active: s.active,
        agentName: s.agentName,
        greeting: s.greeting ?? "",
        tone: s.tone ?? "",
        systemInstructions: s.systemInstructions ?? "",
        humanHandoffEnabled: s.humanHandoffEnabled,
        humanHandoffMessage: s.humanHandoffMessage ?? "",
      });
    });
  }, [token]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form) return;
    setError(null);
    setSaved(false);
    setBusy(true);
    try {
      const updated = await api.updateAiSettings(token, form);
      setSettings(updated);
      setSaved(true);
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save settings.");
    } finally {
      setBusy(false);
    }
  }

  if (!settings || !form) return <TableSkeleton cols={2} />;

  const inputClass =
    "rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20 disabled:opacity-60 disabled:cursor-not-allowed";

  return (
    <Card className="p-5">
      {!canConfigure && (
        <p className="mb-4 rounded-lg bg-canvas px-3 py-2 text-xs text-ink-500">
          Only the Owner or an Administrator can change these settings.
        </p>
      )}
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <label className="flex items-center gap-2 text-sm font-medium text-ink-700">
          <input
            type="checkbox"
            checked={form.active}
            disabled={!canConfigure}
            onChange={(e) => setForm({ ...form, active: e.target.checked })}
          />
          AI is active
        </label>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Agent name</label>
            <input
              required
              disabled={!canConfigure}
              value={form.agentName}
              onChange={(e) => setForm({ ...form, agentName: e.target.value })}
              className={inputClass}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Tone / personality</label>
            <input
              disabled={!canConfigure}
              placeholder="e.g. warm and upbeat"
              value={form.tone}
              onChange={(e) => setForm({ ...form, tone: e.target.value })}
              className={inputClass}
            />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Greeting</label>
          <textarea
            disabled={!canConfigure}
            value={form.greeting}
            onChange={(e) => setForm({ ...form, greeting: e.target.value })}
            className={`min-h-16 ${inputClass}`}
          />
        </div>

        <label className="flex items-center gap-2 text-sm font-medium text-ink-700">
          <input
            type="checkbox"
            checked={form.humanHandoffEnabled}
            disabled={!canConfigure}
            onChange={(e) => setForm({ ...form, humanHandoffEnabled: e.target.checked })}
          />
          Allow handing off to a team member
        </label>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Human handoff message</label>
          <input
            disabled={!canConfigure}
            placeholder="Let me connect you with a team member."
            value={form.humanHandoffMessage}
            onChange={(e) => setForm({ ...form, humanHandoffMessage: e.target.value })}
            className={inputClass}
          />
        </div>

        <div className="flex flex-col gap-1.5 rounded-lg border border-border p-3">
          <label className="flex items-center gap-1.5 text-sm font-medium text-ink-700">
            <ShieldAlert size={14} className="text-danger" />
            System instructions
          </label>
          <p className="text-xs text-ink-500">
            These directly shape how the AI behaves and what it will and won&apos;t do — kept in addition to (never
            in place of) Tallia&apos;s own built-in safety rules. Only change this if you know what you want the AI to do
            differently.
          </p>
          <textarea
            disabled={!canConfigure}
            value={form.systemInstructions}
            onChange={(e) => setForm({ ...form, systemInstructions: e.target.value })}
            className={`min-h-28 ${inputClass}`}
          />
        </div>

        {error && <p className="text-sm text-danger">{error}</p>}
        {saved && !error && <p className="text-sm text-success">Settings saved.</p>}

        {canConfigure && (
          <Button type="submit" disabled={busy} className="w-fit">
            {busy ? "Saving..." : "Save settings"}
          </Button>
        )}
      </form>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Knowledge Base
// ---------------------------------------------------------------------------

function KnowledgeTab({ token, canConfigure, onChanged }: { token: string; canConfigure: boolean; onChanged: () => void }) {
  const [entries, setEntries] = useState<AiKnowledgeEntry[]>([]);
  const [fetching, setFetching] = useState(true);
  const [editing, setEditing] = useState<AiKnowledgeEntry | "new" | null>(null);

  const load = useCallback(async () => {
    const data = await api.listAiKnowledgeEntries(token);
    setEntries(data);
  }, [token]);

  useEffect(() => {
    setFetching(true);
    load().finally(() => setFetching(false));
  }, [load]);

  async function handleSubmit(payload: AiKnowledgeEntryPayload) {
    if (editing === "new") {
      await api.createAiKnowledgeEntry(token, payload);
    } else if (editing) {
      await api.updateAiKnowledgeEntry(token, editing.id, payload);
    }
    setEditing(null);
    await load();
    onChanged();
  }

  async function handleDeactivate(entry: AiKnowledgeEntry) {
    await api.deactivateAiKnowledgeEntry(token, entry.id);
    await load();
    onChanged();
  }

  return (
    <div className="flex flex-col gap-4">
      {canConfigure && (
        <div className="flex justify-end">
          <Button onClick={() => setEditing("new")}>
            <Plus size={15} className="mr-1.5" /> Add entry
          </Button>
        </div>
      )}
      <Card>
        {fetching ? (
          <TableSkeleton cols={3} />
        ) : entries.length === 0 ? (
          <EmptyState
            icon={Sparkles}
            title="No knowledge entries yet"
            description="Add FAQs, policies, and business details the AI is allowed to answer from."
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Title</Th>
                <Th>Category</Th>
                <Th>Status</Th>
                <Th className="text-right">Actions</Th>
              </Tr>
            </THead>
            <TBody>
              {entries.map((entry) => (
                <Tr key={entry.id}>
                  <Td className="font-medium">{entry.title}</Td>
                  <Td className="text-ink-500">{entry.category}</Td>
                  <Td>
                    <Badge tone={entry.active ? "success" : "neutral"}>{entry.active ? "Active" : "Inactive"}</Badge>
                  </Td>
                  <Td className="text-right">
                    {canConfigure && (
                      <div className="flex justify-end gap-3">
                        <button onClick={() => setEditing(entry)} className="text-sm font-medium text-accent-hover hover:underline">
                          Edit
                        </button>
                        {entry.active && (
                          <button
                            onClick={() => handleDeactivate(entry)}
                            className="text-sm font-medium text-ink-500 hover:underline"
                          >
                            Deactivate
                          </button>
                        )}
                      </div>
                    )}
                  </Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {editing && (
        <KnowledgeEntryModal
          entry={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSubmit={handleSubmit}
        />
      )}
    </div>
  );
}

function KnowledgeEntryModal({
  entry,
  onClose,
  onSubmit,
}: {
  entry: AiKnowledgeEntry | null;
  onClose: () => void;
  onSubmit: (payload: AiKnowledgeEntryPayload) => Promise<void>;
}) {
  const [title, setTitle] = useState(entry?.title ?? "");
  const [category, setCategory] = useState(entry?.category ?? "FAQ");
  const [content, setContent] = useState(entry?.content ?? "");
  const [active, setActive] = useState(entry?.active ?? true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!title.trim() || !content.trim()) {
      setError("Title and content are required.");
      return;
    }
    setBusy(true);
    try {
      await onSubmit({ title: title.trim(), category, content: content.trim(), active });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save this entry.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={entry ? "Edit knowledge entry" : "Add knowledge entry"} onClose={onClose}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Title *</label>
          <input
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Category</label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          >
            {["FAQ", "BUSINESS_INFO", "SERVICE", "POLICY", "RESTAURANT", "HOTEL", "EVENTS", "BEACH", "OTHER"].map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Content *</label>
          <textarea
            required
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder='e.g. "Beach opening hours are 8:00 AM to 10:00 PM."'
            className="min-h-28 rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
        <label className="flex items-center gap-2 text-sm font-medium text-ink-700">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Active (available to the AI)
        </label>
        {error && <p className="text-sm text-danger">{error}</p>}
        <Button type="submit" disabled={busy}>
          {busy ? "Saving..." : "Save entry"}
        </Button>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Conversations
// ---------------------------------------------------------------------------

function ConversationsTab({ token }: { token: string }) {
  const [conversations, setConversations] = useState<AiConversationSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [detail, setDetail] = useState<AiConversationDetail | null>(null);

  useEffect(() => {
    setFetching(true);
    api
      .listAiConversations(token)
      .then(setConversations)
      .finally(() => setFetching(false));
  }, [token]);

  async function openDetail(id: string) {
    const full = await api.getAiConversation(token, id);
    setDetail(full);
  }

  return (
    <>
      <Card>
        {fetching ? (
          <TableSkeleton cols={5} />
        ) : conversations.length === 0 ? (
          <EmptyState
            icon={MessageCircle}
            title="No conversations yet"
            description="Conversations from the Test AI panel (and later, real channels) will show up here."
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Customer</Th>
                <Th>Channel</Th>
                <Th>Status</Th>
                <Th>Started</Th>
                <Th>Last message</Th>
              </Tr>
            </THead>
            <TBody>
              {conversations.map((c) => (
                <Tr key={c.id}>
                  <Td>
                    <button onClick={() => openDetail(c.id)} className="font-medium text-accent-hover hover:underline">
                      {c.customerName ?? "Unidentified"}
                    </button>
                  </Td>
                  <Td className="text-ink-500">{c.channel}</Td>
                  <Td>
                    <Badge tone={CONVERSATION_STATUS_TONE[c.status] ?? "neutral"}>{c.status}</Badge>
                  </Td>
                  <Td className="tabular text-ink-500">{new Date(c.startedAt).toLocaleString()}</Td>
                  <Td className="tabular text-ink-500">{new Date(c.lastMessageAt).toLocaleString()}</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {detail && (
        <Modal title={`Conversation — ${detail.customerName ?? "Unidentified"}`} onClose={() => setDetail(null)}>
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <Badge tone={CONVERSATION_STATUS_TONE[detail.status] ?? "neutral"}>{detail.status}</Badge>
              <span className="text-xs text-ink-500">via {detail.channel}</span>
            </div>
            <ConversationTimeline detail={detail} />
          </div>
        </Modal>
      )}
    </>
  );
}

// Interleaves message bubbles with tool-call markers in chronological
// order — this is what makes it obvious to a client watching the demo that
// the AI is really calling into Tallia (checkAvailability, createCustomer,
// createBooking, ...), not just generating text. Internal/developer detail
// only: tool name + outcome, never raw arguments/results or secrets.
function ConversationTimeline({ detail }: { detail: AiConversationDetail }) {
  type TimelineEntry = { kind: "message"; at: string; message: AiMessage } | { kind: "action"; at: string; action: AiActionEntry };
  const entries: TimelineEntry[] = [
    ...detail.messages.map((m) => ({ kind: "message" as const, at: m.createdAt, message: m })),
    ...detail.actions.map((a) => ({ kind: "action" as const, at: a.createdAt, action: a })),
  ].sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());

  return (
    <div className="flex max-h-96 flex-col gap-2 overflow-y-auto">
      {entries.map((entry, i) =>
        entry.kind === "message" ? (
          <MessageBubble key={`m-${i}`} message={entry.message} />
        ) : (
          <ActionMarker key={`a-${i}`} action={entry.action} />
        )
      )}
    </div>
  );
}

function MessageBubble({ message }: { message: AiMessage }) {
  const isUser = message.role === "USER";
  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${
          isUser ? "bg-accent text-white" : "bg-canvas text-ink-900"
        }`}
      >
        <p className="mb-0.5 text-[10px] uppercase tracking-wide opacity-70">{message.role}</p>
        <p className="whitespace-pre-wrap">{message.content}</p>
      </div>
    </div>
  );
}

function TypingIndicator() {
  return (
    <div className="flex justify-start">
      <div className="flex items-center gap-1 rounded-xl bg-surface px-3 py-2.5 shadow-card">
        {[0, 150, 300].map((delay) => (
          <span
            key={delay}
            className="h-1.5 w-1.5 animate-bounce rounded-full bg-ink-400 motion-reduce:animate-none"
            style={{ animationDelay: `${delay}ms` }}
          />
        ))}
      </div>
    </div>
  );
}

function ActionMarker({ action }: { action: AiActionEntry }) {
  const tone =
    action.status === "SUCCEEDED"
      ? "border-success/30 bg-success/10 text-success"
      : action.status === "BLOCKED"
      ? "border-danger/30 bg-danger/10 text-danger"
      : action.status === "FAILED"
      ? "border-danger/30 bg-danger/10 text-danger"
      : "border-border bg-surface text-ink-500";
  return (
    <div className="flex justify-center">
      <span className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-[11px] font-medium ${tone}`}>
        <Wrench size={11} />
        Tool: {action.toolName} — {action.status.toLowerCase()}
      </span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// AI Concierge (formerly "Test AI") — Cafe Bar Noir client-demo UI upgrade.
//
// Presentation only: every message rendered here is still exactly the real
// text AiChatService/MockAiProvider generated from real tool calls (see the
// parse* helpers below) — nothing here invents package names, prices,
// substitution options, or policy text. "Choose package" / "I understand and
// agree — Confirm" are convenience buttons that simply SEND the equivalent
// natural-language reply through the exact same api.sendAiChatMessage() every
// typed message goes through — never a shortcut around the AI/tool/policy
// layer, matching the same "quick-start populates and sends a real message"
// discipline QUICK_STARTS already used. Substitutions are deliberately NOT
// given a second, independent dropdown-editing path here — that would be a
// parallel mechanism alongside the real one (natural language in the chat,
// resolved by the real MockAiProvider/AiToolService/PackagePricingService
// chain); the summary card below only ever DISPLAYS the current real
// selection, exactly as returned.
// ---------------------------------------------------------------------------

type TestTurn = { role: "USER" | "ASSISTANT"; content: string; toolCalls?: AiToolCallSummary[] };

// Quick-start shortcuts — these just populate and send a normal chat message
// through the same endpoint every other message goes through; they never
// bypass the AI/tool layer. "Ask about the beach" (a leftover from the
// earlier resort demo) has been removed — every label below is Cafe Bar
// Noir-appropriate.
const QUICK_STARTS: { label: string; message: string }[] = [
  { label: "View the Menu", message: "What's on the menu?" },
  { label: "Explore Dinner Packages", message: "What dinner packages do you have?" },
  { label: "Make a Booking", message: "I'd like to make a reservation." },
  { label: "Plan an Event", message: "I'm planning an event." },
  { label: "Talk to Someone", message: "I'd like to speak with a member of staff." },
];

// ---- Parsing real assistant text into presentation shapes ----------------
// Every shape below is detected from the EXACT text MockAiProvider emits
// (see backend/.../MockAiProvider.java — buildOrderAndPolicySummary,
// handlePackageBooking's package-listing branch, finalBookingText). If a
// message doesn't match any of these, it just renders as a normal formatted
// chat bubble — most replies (menu/policy Q&A, substitution rejections,
// clarifying questions) are exactly that, deliberately never forced into a
// card.

type ParsedPackageListing = { intro: string; packages: { name: string; pricePerGuest: string }[]; outro: string };
type ParsedOrderSummary = {
  packageName: string;
  guests: string;
  when: string;
  lines: { label: string; value: string }[];
  perGuest: string;
  total: string;
  deposit: string;
  balance: string;
  policyText: string | null;
};
type ParsedConfirmation = { guests: string; bookingNumber: string };

function parsePackageListing(text: string): ParsedPackageListing | null {
  const lines = text.split("\n");
  if (!/^We have \d+ dinner packages available:$/.test(lines[0] ?? "")) return null;
  const packages: { name: string; pricePerGuest: string }[] = [];
  let outro = "";
  for (let i = 1; i < lines.length; i++) {
    const m = lines[i].match(/^- (.+) — GH₵([\d,.]+) per guest$/);
    if (m) packages.push({ name: m[1], pricePerGuest: m[2] });
    else if (lines[i].trim()) outro = lines[i].trim();
  }
  if (packages.length === 0) return null;
  return { intro: lines[0], packages, outro };
}

function parseOrderSummary(text: string): ParsedOrderSummary | null {
  if (!text.startsWith("Here's your ") || !text.includes("Shall I go ahead and confirm this reservation?")) return null;
  const lines = text.split("\n");
  const headerMatch = lines[0]?.match(/^Here's your (.+) for (\d+) guests? on (.+):$/);
  if (!headerMatch) return null;
  const [, packageName, guests, when] = headerMatch;

  const lineItems: { label: string; value: string }[] = [];
  let idx = 1;
  while (idx < lines.length && lines[idx].startsWith("- ")) {
    const m = lines[idx].match(/^- (.+?): (.+)$/);
    if (m) lineItems.push({ label: m[1], value: m[2] });
    idx++;
  }

  const priceMatch = (lines[idx] ?? "").match(/Per guest: GH₵([\d,.]+) — Total: GH₵([\d,.]+)/);
  idx++;
  const depositMatch = (lines[idx] ?? "").match(
    /A 70% deposit of GH₵([\d,.]+) secures the booking, with the remaining GH₵([\d,.]+) due/
  );
  idx++;
  if (!priceMatch || !depositMatch) return null;

  const rest = lines.slice(idx).join("\n");
  const policyMatch = rest.match(/^A couple of important policies: ([\s\S]*)\nShall I go ahead/);

  return {
    packageName,
    guests,
    when,
    lines: lineItems,
    perGuest: priceMatch[1],
    total: priceMatch[2],
    deposit: depositMatch[1],
    balance: depositMatch[2],
    policyText: policyMatch ? policyMatch[1] : null,
  };
}

function parseConfirmation(text: string): ParsedConfirmation | null {
  const m = text.match(/reservation for (\d+) guests is confirmed! Booking #(\S+?)\.?$/);
  if (!m) return null;
  return { guests: m[1], bookingNumber: m[2] };
}

// Bullet-aware plain-text renderer — the fallback for any assistant message
// that isn't one of the specific card shapes above (menu answers, policy
// answers, substitution rejections, clarifying questions).
function FormattedMessage({ text }: { text: string }) {
  const lines = text.split("\n");
  const blocks: React.ReactNode[] = [];
  let i = 0;
  let key = 0;
  while (i < lines.length) {
    if (lines[i].startsWith("- ")) {
      const items: string[] = [];
      while (i < lines.length && lines[i].startsWith("- ")) {
        items.push(lines[i].slice(2));
        i++;
      }
      blocks.push(
        <ul key={key++} className="list-disc space-y-0.5 pl-5">
          {items.map((it, j) => (
            <li key={j}>{it}</li>
          ))}
        </ul>
      );
    } else if (lines[i].trim() === "") {
      i++;
    } else {
      blocks.push(<p key={key++}>{lines[i]}</p>);
      i++;
    }
  }
  return <div className="flex flex-col gap-1.5 text-[13.5px] leading-relaxed text-ink-900">{blocks}</div>;
}

function PackageListCard({
  data,
  onChoose,
  disabled,
}: {
  data: ParsedPackageListing;
  onChoose: (name: string) => void;
  disabled: boolean;
}) {
  return (
    <div className="flex flex-col gap-3">
      <p className="text-[13.5px] text-ink-700">{data.intro}</p>
      <div className="grid gap-3 sm:grid-cols-2">
        {data.packages.map((p) => (
          <div key={p.name} className="flex flex-col gap-2 rounded-xl border border-border bg-canvas p-4">
            <div className="flex items-start justify-between gap-2">
              <p className="text-sm font-semibold text-ink-900">{p.name}</p>
              <span className="shrink-0 rounded-full bg-surface px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-ink-500">
                Demo
              </span>
            </div>
            <p className="text-lg font-semibold text-ink-900">
              GH₵{p.pricePerGuest} <span className="text-xs font-normal text-ink-500">/ guest</span>
            </p>
            <Button variant="secondary" disabled={disabled} onClick={() => onChoose(p.name)} className="mt-1 w-full justify-center">
              Choose package
            </Button>
          </div>
        ))}
      </div>
      {data.outro && <p className="text-[13.5px] text-ink-500">{data.outro}</p>}
    </div>
  );
}

function OrderSummaryCard({
  data,
  onConfirm,
  disabled,
}: {
  data: ParsedOrderSummary;
  onConfirm: () => void;
  disabled: boolean;
}) {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <p className="text-[10px] font-medium uppercase tracking-wide text-ink-500">Your dinner</p>
        <p className="text-base font-semibold text-ink-900">{data.packageName}</p>
        <p className="text-xs text-ink-500">
          {data.guests} guests · {data.when}
        </p>
      </div>

      <ul className="flex flex-col gap-1 text-[13.5px]">
        {data.lines.map((l) => (
          <li key={l.label} className="flex justify-between gap-3">
            <span className="text-ink-500">{l.label}</span>
            <span className="font-medium text-ink-900">{l.value}</span>
          </li>
        ))}
      </ul>

      <div className="flex flex-col gap-1 border-t border-border pt-3 text-[13.5px]">
        <div className="flex justify-between">
          <span className="text-ink-500">
            GH₵{data.perGuest} × {data.guests}
          </span>
          <span className="font-semibold text-ink-900">GH₵{data.total}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-ink-500">70% deposit</span>
          <span className="font-medium text-ink-900">GH₵{data.deposit}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-ink-500">Balance due before the event</span>
          <span className="font-medium text-ink-900">GH₵{data.balance}</span>
        </div>
      </div>

      {data.policyText && (
        <div className="flex flex-col gap-1.5 rounded-lg bg-canvas p-3">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-ink-500">Before we confirm your reservation</p>
          <p className="text-xs leading-relaxed text-ink-700">{data.policyText}</p>
        </div>
      )}

      <Button disabled={disabled} onClick={onConfirm} className="w-full justify-center">
        I understand and agree — Confirm
      </Button>
    </div>
  );
}

function ConfirmationCard({ data }: { data: ParsedConfirmation }) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <CheckCircle2 size={18} className="text-success" />
        <p className="text-sm font-semibold text-ink-900">Reservation request confirmed</p>
      </div>
      <p className="text-[13.5px] text-ink-700">
        Booking #{data.bookingNumber} · {data.guests} guests
      </p>
      <span className="w-fit rounded-full bg-canvas px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-ink-500">
        Demo — payment not processed
      </span>
    </div>
  );
}

function TestAiTab({ token, onTurnCompleted }: { token: string; onTurnCompleted: () => void }) {
  const { business } = useAuth();
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [turns, setTurns] = useState<TestTurn[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Off by default — the client-facing view never shows raw tool names; this
  // is purely a developer/debug toggle (per the "hide tool activity, keep it
  // behind a developer mechanism" requirement), never shown or hinted at
  // during a normal demo.
  const [devMode, setDevMode] = useState(false);

  const businessName = business?.name ?? "Your business";

  async function sendMessage(message: string) {
    if (!message.trim() || busy) return;
    setError(null);
    setTurns((prev) => [...prev, { role: "USER", content: message }]);
    setBusy(true);
    try {
      const response = await api.sendAiChatMessage(token, conversationId, message);
      setConversationId(response.conversationId);
      setTurns((prev) => [...prev, { role: "ASSISTANT", content: response.assistantMessage, toolCalls: response.toolCalls }]);
      onTurnCompleted();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : `Sorry, I couldn't complete that request. Would you like me to connect you with someone from ${businessName}?`
      );
    } finally {
      setBusy(false);
    }
  }

  async function handleSend(e: React.FormEvent) {
    e.preventDefault();
    const message = input.trim();
    if (!message) return;
    setInput("");
    await sendMessage(message);
  }

  function startNewConversation() {
    // Only resets this panel's own local view — the previous conversation
    // is untouched in the database and stays visible under Conversations.
    setConversationId(null);
    setTurns([]);
    setError(null);
  }

  return (
    <Card className="flex flex-col gap-4 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-ink-900 text-[#D9B36C]">
            <Bot size={18} />
          </div>
          <div>
            <p className="text-sm font-semibold text-ink-900">{businessName} · AI Concierge</p>
            <p className="flex items-center gap-1.5 text-xs text-ink-500">
              <span className="h-1.5 w-1.5 rounded-full bg-success" />
              Online · Demo
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setDevMode((v) => !v)}
            title="Developer-only: show which tools the AI called for each reply"
            className="flex items-center gap-1 text-[11px] font-medium text-ink-300 hover:text-ink-500"
          >
            <Wrench size={11} />
            {devMode ? "Hide activity" : "Dev view"}
          </button>
          <Button variant="secondary" onClick={startNewConversation} className="shrink-0">
            New conversation
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {QUICK_STARTS.map((q) => (
          <button
            key={q.label}
            type="button"
            disabled={busy}
            onClick={() => sendMessage(q.message)}
            className="rounded-full border border-border bg-surface px-3.5 py-1.5 text-xs font-medium text-ink-700 shadow-card transition hover:border-accent hover:text-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
          >
            {q.label}
          </button>
        ))}
      </div>

      <div className="flex min-h-64 max-h-[32rem] flex-col gap-3 overflow-y-auto rounded-xl border border-border bg-canvas p-4 sm:p-5">
        {turns.length === 0 ? (
          <div className="m-auto flex max-w-sm flex-col items-center gap-2 py-6 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-ink-900 text-[#D9B36C]">
              <Sparkles size={20} />
            </div>
            <p className="text-sm font-semibold text-ink-900">{businessName}</p>
            <p className="text-xs text-ink-500">Your AI Concierge</p>
            <p className="mt-1 text-sm text-ink-500">
              Ask about our menu, explore dinner packages, make a reservation, or speak with our team.
            </p>
          </div>
        ) : (
          turns.map((turn, i) => {
            const isUser = turn.role === "USER";
            const packageListing = !isUser ? parsePackageListing(turn.content) : null;
            const orderSummary = !isUser && !packageListing ? parseOrderSummary(turn.content) : null;
            const confirmation = !isUser && !packageListing && !orderSummary ? parseConfirmation(turn.content) : null;
            const isCard = !!(packageListing || orderSummary || confirmation);

            return (
              <div key={i} className="flex flex-col gap-1.5">
                <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
                  <div
                    className={
                      isUser
                        ? "max-w-[80%] rounded-2xl rounded-br-sm bg-accent px-4 py-2.5 text-sm text-white shadow-card"
                        : `max-w-[88%] rounded-2xl rounded-bl-sm bg-surface text-ink-900 shadow-card ${
                            isCard ? "p-4" : "px-4 py-2.5"
                          }`
                    }
                  >
                    {isUser ? (
                      <p className="whitespace-pre-wrap leading-relaxed">{turn.content}</p>
                    ) : packageListing ? (
                      <PackageListCard
                        data={packageListing}
                        disabled={busy}
                        onChoose={(name) => sendMessage(`I'll take the ${name}.`)}
                      />
                    ) : orderSummary ? (
                      <OrderSummaryCard
                        data={orderSummary}
                        disabled={busy}
                        onConfirm={() => sendMessage("Yes, I understand and agree — please confirm.")}
                      />
                    ) : confirmation ? (
                      <ConfirmationCard data={confirmation} />
                    ) : (
                      <FormattedMessage text={turn.content} />
                    )}
                  </div>
                </div>
                {devMode && turn.toolCalls && turn.toolCalls.length > 0 && (
                  <div className="ml-1 flex flex-wrap gap-1.5">
                    <span className="text-[10px] font-medium uppercase tracking-wide text-ink-400">Internal — tool activity:</span>
                    {turn.toolCalls.map((tc, j) => (
                      <span
                        key={j}
                        title={tc.summary}
                        className={`flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium ${
                          tc.status === "SUCCEEDED"
                            ? "border-success/30 bg-success/10 text-success"
                            : tc.status === "BLOCKED"
                            ? "border-danger/30 bg-danger/10 text-danger"
                            : "border-border bg-surface text-ink-500"
                        }`}
                      >
                        <Wrench size={10} />
                        {tc.toolName}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            );
          })
        )}
        {busy && <TypingIndicator />}
      </div>

      {error && <div className="rounded-lg border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger">{error}</div>}

      <form onSubmit={handleSend} className="flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={`Ask ${businessName} anything...`}
          disabled={busy}
          className="flex-1 rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
        <Button type="submit" disabled={busy || !input.trim()}>
          <Send size={15} className="mr-1.5" />
          {busy ? "Sending..." : "Send"}
        </Button>
      </form>
    </Card>
  );
}
