"use client";

import { useState, useRef, useEffect } from "react";
import Link from "next/link";
import { Menu, ChevronDown, LogOut, KeyRound, Building2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import InstallAppButton from "@/components/InstallAppButton";

export default function Topbar({ onMenuClick }: { onMenuClick: () => void }) {
  const { session, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const initials = session?.fullName
    ?.split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  return (
    <header className="flex h-14 items-center justify-between border-b border-border bg-surface px-4 sm:px-6">
      <button
        onClick={onMenuClick}
        className="rounded-md p-2 text-ink-700 hover:bg-canvas lg:hidden"
        aria-label="Open menu"
      >
        <Menu size={20} />
      </button>

      <div className="hidden lg:block" />

      <div className="flex items-center gap-3">
        <InstallAppButton className="flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-ink-700 hover:bg-canvas" />
        <div className="relative" ref={menuRef}>
        <button
          onClick={() => setMenuOpen((v) => !v)}
          className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-canvas"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-accent-soft text-xs font-semibold text-accent-hover">
            {initials}
          </span>
          <span className="hidden text-left sm:block">
            <span className="block text-sm font-medium text-ink-900">{session?.fullName}</span>
            <span className="block text-xs text-ink-500">{session?.role}</span>
          </span>
          <ChevronDown size={16} className="text-ink-500" />
        </button>

        {menuOpen && (
          <div className="absolute right-0 z-20 mt-2 w-44 overflow-hidden rounded-lg border border-border bg-surface shadow-panel">
            <Link
              href="/dashboard/profile"
              onClick={() => setMenuOpen(false)}
              className="flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm text-ink-700 hover:bg-canvas"
            >
              <Building2 size={16} />
              Business profile
            </Link>
            <Link
              href="/dashboard/change-password"
              onClick={() => setMenuOpen(false)}
              className="flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm text-ink-700 hover:bg-canvas"
            >
              <KeyRound size={16} />
              Change password
            </Link>
            <button
              onClick={logout}
              className="flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm text-ink-700 hover:bg-canvas"
            >
              <LogOut size={16} />
              Log out
            </button>
          </div>
        )}
        </div>
      </div>
    </header>
  );
}
