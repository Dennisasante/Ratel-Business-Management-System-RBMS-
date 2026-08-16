"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { Bell } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, Notification } from "@/lib/api";

const POLL_MS = 45_000;

function timeAgo(iso: string) {
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

// Owner + Manager only — matches who already handles admin-level concerns
// elsewhere (billing, team). No websocket/SSE infra exists anywhere in this
// codebase, so this polls, same pragmatic choice as the daily digest job.
export default function NotificationBell() {
  const { session } = useAuth();
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const ref = useRef<HTMLDivElement>(null);

  const canSee = session?.role === "OWNER" || session?.role === "MANAGER";

  const refreshCount = useCallback(async () => {
    if (!session) return;
    const { count } = await api.getUnreadNotificationCount(session.token);
    setUnreadCount(count);
  }, [session]);

  useEffect(() => {
    if (!canSee) return;
    refreshCount();
    const interval = setInterval(refreshCount, POLL_MS);
    return () => clearInterval(interval);
  }, [canSee, refreshCount]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  async function handleOpen() {
    const next = !open;
    setOpen(next);
    if (next && session) {
      setNotifications(await api.listNotifications(session.token));
    }
  }

  async function handleMarkRead(id: string) {
    if (!session) return;
    setNotifications((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
    setUnreadCount((c) => Math.max(0, c - 1));
    await api.markNotificationRead(session.token, id);
  }

  async function handleMarkAllRead() {
    if (!session) return;
    setNotifications((list) => list.map((n) => ({ ...n, read: true })));
    setUnreadCount(0);
    await api.markAllNotificationsRead(session.token);
  }

  if (!canSee) return null;

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={handleOpen}
        className="relative rounded-lg p-2 text-ink-700 hover:bg-canvas"
        aria-label="Notifications"
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 overflow-hidden rounded-lg border border-border bg-surface shadow-panel">
          <div className="flex items-center justify-between border-b border-border px-3 py-2.5">
            <span className="text-sm font-semibold text-ink-900">Notifications</span>
            {unreadCount > 0 && (
              <button onClick={handleMarkAllRead} className="text-xs font-medium text-accent-hover hover:underline">
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {notifications.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-ink-500">No notifications yet.</p>
            ) : (
              notifications.map((n) => (
                <button
                  key={n.id}
                  onClick={() => !n.read && handleMarkRead(n.id)}
                  className={`block w-full border-b border-border px-3 py-2.5 text-left last:border-0 hover:bg-canvas ${
                    n.read ? "" : "bg-accent-soft"
                  }`}
                >
                  <p className="text-sm font-medium text-ink-900">{n.title}</p>
                  {n.body && <p className="mt-0.5 text-xs text-ink-500">{n.body}</p>}
                  <p className="mt-1 text-[11px] text-ink-300">{timeAgo(n.createdAt)}</p>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
