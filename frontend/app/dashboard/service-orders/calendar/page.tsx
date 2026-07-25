"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ChevronLeft, ChevronRight } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ServiceOrder, ServiceOrderStatus } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import CardSkeleton from "@/components/ui/CardSkeleton";

const STATUS_TONES: Record<ServiceOrderStatus, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  RECEIVED: "info",
  IN_PROGRESS: "accent",
  COMPLETED: "success",
  PICKED_UP: "violet",
  CANCELLED: "danger",
};

const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function dateKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function buildMonthGrid(monthStart: Date): Date[] {
  const firstOfMonth = new Date(monthStart.getFullYear(), monthStart.getMonth(), 1);
  const gridStart = new Date(firstOfMonth);
  gridStart.setDate(gridStart.getDate() - firstOfMonth.getDay());

  const days: Date[] = [];
  for (let i = 0; i < 42; i++) {
    const d = new Date(gridStart);
    d.setDate(gridStart.getDate() + i);
    days.push(d);
  }
  return days;
}

export default function ServiceOrdersCalendarPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [orders, setOrders] = useState<ServiceOrder[]>([]);
  const [fetching, setFetching] = useState(true);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    const data = await api.listServiceOrders(session.token);
    setOrders(data.filter((o) => o.scheduledAt));
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    load().finally(() => setFetching(false));
  }, [session, load]);

  const ordersByDay = useMemo(() => {
    const map = new Map<string, ServiceOrder[]>();
    for (const o of orders) {
      if (!o.scheduledAt) continue;
      const key = dateKey(new Date(o.scheduledAt));
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(o);
    }
    return map;
  }, [orders]);

  const days = useMemo(() => buildMonthGrid(month), [month]);
  const todayKey = dateKey(new Date());
  const selectedOrders = selectedDay ? ordersByDay.get(selectedDay) ?? [] : [];

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link href="/dashboard/service-orders" className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-900">
          <ArrowLeft size={14} /> Back to service orders
        </Link>
      </div>

      <PageHeader
        title="Service Orders Calendar"
        subtitle="Orders booked ahead for a specific date and time."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="secondary" onClick={() => setMonth((m) => new Date(m.getFullYear(), m.getMonth() - 1, 1))} aria-label="Previous month">
              <ChevronLeft size={16} />
            </Button>
            <span className="min-w-[9rem] text-center text-sm font-medium text-ink-900">
              {month.toLocaleDateString(undefined, { month: "long", year: "numeric" })}
            </span>
            <Button variant="secondary" onClick={() => setMonth((m) => new Date(m.getFullYear(), m.getMonth() + 1, 1))} aria-label="Next month">
              <ChevronRight size={16} />
            </Button>
          </div>
        }
      />

      {fetching ? (
        <CardSkeleton count={1} />
      ) : (
        <Card className="p-3">
          <div className="grid grid-cols-7 gap-px overflow-hidden rounded-lg bg-border text-xs font-medium text-ink-500">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label} className="bg-canvas px-2 py-1.5 text-center">
                {label}
              </div>
            ))}
            {days.map((day) => {
              const key = dateKey(day);
              const inMonth = day.getMonth() === month.getMonth();
              const dayOrders = ordersByDay.get(key) ?? [];
              const isToday = key === todayKey;
              return (
                <button
                  key={key}
                  onClick={() => setSelectedDay(dayOrders.length > 0 ? key : null)}
                  className={`flex min-h-[90px] flex-col items-start gap-1 bg-surface p-1.5 text-left transition hover:bg-canvas ${
                    inMonth ? "" : "opacity-40"
                  }`}
                >
                  <span className={`tabular flex h-5 w-5 items-center justify-center rounded-full text-xs ${
                    isToday ? "bg-accent text-white" : "text-ink-700"
                  }`}>
                    {day.getDate()}
                  </span>
                  <div className="flex w-full flex-col gap-0.5">
                    {dayOrders.slice(0, 3).map((o) => (
                      <span
                        key={o.id}
                        className={`truncate rounded px-1 py-0.5 text-[10px] font-medium ${
                          o.status === "CANCELLED" ? "bg-danger-soft text-danger" : "bg-accent-soft text-accent-hover"
                        }`}
                      >
                        #{o.orderNumber} {o.serviceTypeName ?? ""}
                      </span>
                    ))}
                    {dayOrders.length > 3 && (
                      <span className="text-[10px] text-ink-500">+{dayOrders.length - 3} more</span>
                    )}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>
      )}

      {selectedDay && selectedOrders.length > 0 && (
        <Card className="p-5">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-ink-900">
              {new Date(selectedDay).toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}
            </h2>
            <button onClick={() => setSelectedDay(null)} className="text-sm font-medium text-ink-500 hover:text-ink-900">
              Close
            </button>
          </div>
          <ul className="mt-4 flex flex-col gap-3">
            {selectedOrders.map((o) => (
              <li key={o.id} className="flex items-center justify-between border-b border-border pb-3 text-sm last:border-0 last:pb-0">
                <div>
                  <p className="font-medium text-ink-900">
                    #{o.orderNumber} · {o.serviceTypeName ?? "—"}
                  </p>
                  <p className="text-xs text-ink-500">
                    {o.customerName ?? "Walk-in"} ·{" "}
                    {new Date(o.scheduledAt!).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}
                  </p>
                </div>
                <Badge tone={STATUS_TONES[o.status]}>{o.status.replace("_", " ")}</Badge>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {!fetching && orders.length === 0 && (
        <p className="text-sm text-ink-500">
          No orders are booked ahead yet — set a &ldquo;Schedule for&rdquo; date when creating or editing an order to have it show up here.
        </p>
      )}
    </div>
  );
}
