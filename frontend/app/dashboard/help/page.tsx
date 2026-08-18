"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  BookOpen,
  LifeBuoy,
  ChevronDown,
  LayoutDashboard,
  ShoppingCart,
  Wrench,
  CalendarCheck2,
  Package,
  Users,
  Wallet,
  UserCog,
  CreditCard,
  BarChart3,
  Send,
  RotateCcw,
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, HelpRequest, HelpRequestCategory } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";

const CATEGORY_LABELS: Record<HelpRequestCategory, string> = {
  GENERAL: "General question",
  BUG: "Something's broken",
  BILLING: "Billing",
  FEATURE_REQUEST: "Feature request",
};

const GUIDE_SECTIONS: { icon: typeof LayoutDashboard; title: string; items: string[] }[] = [
  {
    icon: LayoutDashboard,
    title: "Getting started",
    items: [
      "The sidebar groups everything by what it's for — Bookings, day-to-day Operations, and the Business/admin tools further down.",
      "What you see depends on your role. Staff only see what they need for their own scheduled work; Manager (\"Administrator\") sees everything except billing; Owner sees all of it.",
      "The Dashboard gives a quick snapshot plus Quick Actions and a Recent Activity feed so you can jump straight into what you were doing.",
    ],
  },
  {
    icon: ShoppingCart,
    title: "Sales / POS",
    items: [
      "Add products and services to the same cart — the Products and Services tabs sit side by side, so a sale can mix both.",
      "Cash and bank transfer sales complete instantly. Card and mobile money sales start as Unpaid and open a payment panel right there — no separate page.",
      "For mobile money, enter the customer's number and network; they'll get a prompt (or an SMS code to type back in) on their own phone.",
      "Every sale has a printable receipt (58mm or 80mm) reachable from the sale row.",
    ],
  },
  {
    icon: Wrench,
    title: "Service Orders",
    items: [
      "A new order starts at Received. Use \"Move to stage\" on any row to see every stage — the ones not directly reachable from where the order is now are shown but grayed out, so you always see the whole pipeline.",
      "One order can hold several services — add as many line items as the job needs.",
      "\"Collect payment\" opens the same payment panel as Sales, right from the list — no need to open the receipt first.",
      "Photos can be attached to an order (before/after, damage, reference images) from the order's detail view.",
    ],
  },
  {
    icon: CalendarCheck2,
    title: "Bookings",
    items: [
      "Your business gets a hosted booking page customers can use themselves — find the link under Bookings in the sidebar.",
      "Staff can also create a booking manually for a walk-in or phone-in customer.",
      "Working hours, blackout dates, and whether customers pay a deposit, pay in full, or pay in person are all set from Bookings → Settings.",
    ],
  },
  {
    icon: Package,
    title: "Inventory & Purchase Orders",
    items: [
      "Products are organized into categories you control. Deleting a category or product that's still in use is blocked — deactivate it instead, which keeps historical sales intact.",
      "Stock adjustments (add/remove/correct) are logged individually, so you can always see why a count changed.",
      "Purchase Orders track what you owe suppliers — mark one paid once you've settled it, which logs an outgoing entry on the Payments ledger.",
    ],
  },
  {
    icon: Users,
    title: "Customers",
    items: [
      "Search by name, phone, or email from the Customers page.",
      "Open a customer to see their full purchase history in one place.",
    ],
  },
  {
    icon: Wallet,
    title: "Payments",
    items: [
      "The Payments page is a ledger of every payment event across Sales, Service Orders, Bookings, and Purchase Orders — both money in and money out.",
      "Card and mobile-money charges go through Paystack once it's connected under Integrations.",
      "\"Pay with Paystack\" opens a popup completed by whoever clicks it — for a prompt that reaches the customer's own phone instead, use the mobile-money charge option.",
    ],
  },
  {
    icon: UserCog,
    title: "Team & roles",
    items: [
      "Owner: full access, including Billing. Administrator (Manager): everything except Billing. Sales Person / Accountant: scoped to their area. Staff: only their own scheduled bookings/orders.",
      "Add staff from the Team page — they'll get a temporary password and must set their own on first login.",
      "Commission rates (percentage per sale) are set per staff member and captured on each sale at the time it's made.",
    ],
  },
  {
    icon: CreditCard,
    title: "Billing & subscription",
    items: [
      "New businesses start on a free trial. After it ends, there's a short grace period before the account goes read-only.",
      "Pay by card and you can optionally save it for automatic renewal, so the subscription never lapses unexpectedly.",
    ],
  },
  {
    icon: BarChart3,
    title: "Reports",
    items: [
      "The Financial Report covers sales, expenses, and discounts given, with a filterable date range and an export.",
      "Service Order reports break revenue down both by category and by individual service.",
    ],
  },
];

export default function HelpPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [tab, setTab] = useState<"guide" | "support">("guide");
  const [openSection, setOpenSection] = useState<string | null>(GUIDE_SECTIONS[0].title);
  const [requests, setRequests] = useState<HelpRequest[]>([]);
  const [fetching, setFetching] = useState(true);

  const loadRequests = useCallback(async () => {
    if (!session) return;
    const data = await api.listHelpRequests(session.token);
    setRequests(data);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadRequests().finally(() => setFetching(false));
  }, [session, loadRequests]);

  function restartTour() {
    window.sessionStorage.removeItem("rbms_onboarding_dismissed");
    window.location.href = "/dashboard?tour=1";
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Help & Support"
        subtitle="How Tallia works, and a direct line to us if something's wrong."
        actions={
          <Button variant="secondary" onClick={restartTour}>
            <RotateCcw size={15} className="mr-1.5" /> Replay walkthrough
          </Button>
        }
      />

      <div className="flex gap-1 border-b border-border">
        <TabButton active={tab === "guide"} onClick={() => setTab("guide")} icon={BookOpen} label="User guide" />
        <TabButton active={tab === "support"} onClick={() => setTab("support")} icon={LifeBuoy} label="Contact support" />
      </div>

      {tab === "guide" && (
        <Card className="divide-y divide-border">
          {GUIDE_SECTIONS.map((section) => {
            const Icon = section.icon;
            const open = openSection === section.title;
            return (
              <div key={section.title}>
                <button
                  onClick={() => setOpenSection(open ? null : section.title)}
                  className="flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left"
                >
                  <span className="flex items-center gap-2.5 text-sm font-medium text-ink-900">
                    <Icon size={17} strokeWidth={1.75} className="text-accent-hover" />
                    {section.title}
                  </span>
                  <ChevronDown size={16} className={`text-ink-500 transition-transform ${open ? "rotate-180" : ""}`} />
                </button>
                {open && (
                  <ul className="flex flex-col gap-2 px-4 pb-4 pl-10 text-sm text-ink-700">
                    {section.items.map((item, i) => (
                      <li key={i} className="list-disc marker:text-ink-300">
                        {item}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })}
        </Card>
      )}

      {tab === "support" && (
        <div className="flex flex-col gap-6">
          <SupportForm token={session.token} onSent={loadRequests} />

          <div>
            <p className="mb-3 text-sm font-medium text-ink-900">Your past requests</p>
            {fetching ? (
              <p className="text-sm text-ink-500">Loading...</p>
            ) : requests.length === 0 ? (
              <Card>
                <EmptyState
                  icon={LifeBuoy}
                  title="Nothing sent yet"
                  description="Questions or issues you send us will show up here, along with our reply."
                />
              </Card>
            ) : (
              <div className="flex flex-col gap-3">
                {requests.map((r) => (
                  <Card key={r.id} className="p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="text-sm font-medium text-ink-900">{r.subject}</p>
                        <p className="mt-0.5 text-xs text-ink-500">
                          {CATEGORY_LABELS[r.category]} &middot; {new Date(r.createdAt).toLocaleString()}
                        </p>
                      </div>
                      <Badge tone={r.status === "OPEN" ? "info" : "success"}>
                        {r.status === "OPEN" ? "Awaiting reply" : "Resolved"}
                      </Badge>
                    </div>
                    <p className="mt-2 whitespace-pre-wrap text-sm text-ink-700">{r.message}</p>
                    {r.adminResponse && (
                      <div className="mt-3 rounded-lg bg-canvas p-3">
                        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Tallia support replied</p>
                        <p className="mt-1 whitespace-pre-wrap text-sm text-ink-700">{r.adminResponse}</p>
                      </div>
                    )}
                  </Card>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: typeof BookOpen;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm font-medium transition ${
        active ? "border-accent text-ink-900" : "border-transparent text-ink-500 hover:text-ink-900"
      }`}
    >
      <Icon size={15} />
      {label}
    </button>
  );
}

function SupportForm({ token, onSent }: { token: string; onSent: () => void }) {
  const [category, setCategory] = useState<HelpRequestCategory>("GENERAL");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.createHelpRequest(token, { category, subject, message });
      setSubject("");
      setMessage("");
      setSent(true);
      onSent();
      setTimeout(() => setSent(false), 3000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't send that. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <p className="text-sm font-medium text-ink-900">Send us a message</p>
        <div className="grid gap-3 sm:grid-cols-[200px_1fr]">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Category</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as HelpRequestCategory)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            >
              {(Object.keys(CATEGORY_LABELS) as HelpRequestCategory[]).map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABELS[c]}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-700">Subject</label>
            <input
              required
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              placeholder="Short summary"
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-ink-700">Message</label>
          <textarea
            required
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="What's going on?"
            className="min-h-28 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
          />
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
        {sent && <p className="text-sm text-success">Sent — we&apos;ll reply here.</p>}
        <Button type="submit" disabled={busy} className="self-start">
          <Send size={15} className="mr-1.5" />
          {busy ? "Sending..." : "Send"}
        </Button>
      </form>
    </Card>
  );
}
