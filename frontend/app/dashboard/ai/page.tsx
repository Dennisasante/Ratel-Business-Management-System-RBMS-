"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Bot, Sparkles, MessageCircle, Plus, Send, Wrench, ShieldAlert } from "lucide-react";
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
            <TabChip label="Test AI" active={tab === "test"} onClick={() => setTab("test")} />
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
// Test AI
// ---------------------------------------------------------------------------

type TestTurn = { role: "USER" | "ASSISTANT"; content: string; toolCalls?: AiToolCallSummary[] };

// Quick-start shortcuts (spec §15) — these just populate and send a normal
// chat message through the same endpoint every other message goes through;
// they never bypass the AI/tool layer.
const QUICK_STARTS: { label: string; message: string }[] = [
  { label: "Ask about the beach", message: "What activities and facilities do you have?" },
  { label: "Check availability", message: "Can I visit this Saturday?" },
  { label: "Make a booking", message: "I want to book the beach day pass." },
  { label: "Plan an event", message: "I want to organize a birthday party." },
  { label: "Talk to someone", message: "I'd like to speak with a member of staff." },
];

function TestAiTab({ token, onTurnCompleted }: { token: string; onTurnCompleted: () => void }) {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [turns, setTurns] = useState<TestTurn[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
      setError(err instanceof ApiError ? err.message : "Couldn't reach Tallia AI.");
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
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-ink-500">
          Chat with your AI exactly as a customer would — this uses your real settings and knowledge base.
        </p>
        <Button variant="secondary" onClick={startNewConversation} className="shrink-0">
          New conversation
        </Button>
      </div>

      <div className="flex flex-wrap gap-2">
        {QUICK_STARTS.map((q) => (
          <button
            key={q.label}
            type="button"
            disabled={busy}
            onClick={() => sendMessage(q.message)}
            className="rounded-full border border-border bg-surface px-3 py-1 text-xs font-medium text-ink-700 hover:border-accent hover:text-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
          >
            {q.label}
          </button>
        ))}
      </div>

      <div className="flex min-h-64 max-h-[28rem] flex-col gap-3 overflow-y-auto rounded-lg border border-border bg-canvas p-4">
        {turns.length === 0 ? (
          <p className="m-auto text-sm text-ink-400">Say hello, or try one of the shortcuts above.</p>
        ) : (
          turns.map((turn, i) => (
            <div key={i} className="flex flex-col gap-1">
              <div className={`flex ${turn.role === "USER" ? "justify-end" : "justify-start"}`}>
                <div
                  className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${
                    turn.role === "USER" ? "bg-accent text-white" : "bg-surface text-ink-900 shadow-card"
                  }`}
                >
                  <p className="whitespace-pre-wrap">{turn.content}</p>
                </div>
              </div>
              {turn.toolCalls && turn.toolCalls.length > 0 && (
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
          ))
        )}
        {busy && <TypingIndicator />}
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <form onSubmit={handleSend} className="flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type a message..."
          disabled={busy}
          className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
        <Button type="submit" disabled={busy || !input.trim()}>
          <Send size={15} className="mr-1.5" />
          {busy ? "Sending..." : "Send"}
        </Button>
      </form>
    </Card>
  );
}
