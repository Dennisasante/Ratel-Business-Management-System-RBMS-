"use client";

import { useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LayoutDashboard, Building2, History, ShieldAlert, Users, KeyRound, LogOut, Layers, CreditCard, LifeBuoy } from "lucide-react";
import { usePlatformAuth } from "@/lib/platformAuth";
import InstallAppButton from "@/components/InstallAppButton";

const NAV_ITEMS = [
  { href: "/platform", label: "Overview", icon: LayoutDashboard },
  { href: "/platform/businesses", label: "Businesses", icon: Building2 },
  { href: "/platform/plans", label: "Plans", icon: Layers },
  { href: "/platform/billing-settings", label: "Billing Settings", icon: CreditCard },
  { href: "/platform/help-requests", label: "Help Requests", icon: LifeBuoy },
  { href: "/platform/activity", label: "Activity Log", icon: History },
  { href: "/platform/audit", label: "Admin Actions", icon: ShieldAlert },
  { href: "/platform/admins", label: "Admins", icon: Users },
];

export default function PlatformShell({ children }: { children: React.ReactNode }) {
  const { session, loading, logout } = usePlatformAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !session) router.push("/platform/login");
  }, [loading, session, router]);

  // A newly created Super Admin (temporary password) can't use anything
  // else until they set their own — same pattern as staff on the business side.
  useEffect(() => {
    if (session?.mustChangePassword && pathname !== "/platform/change-password") {
      router.replace("/platform/change-password");
    }
  }, [session, pathname, router]);

  if (loading || !session) {
    return <div className="flex min-h-screen items-center justify-center bg-sidebar text-sidebar-text">Loading...</div>;
  }

  return (
    <div className="min-h-screen bg-canvas">
      <header className="bg-accent">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/branding/tallia-logo-reversed.svg" alt="Tallia" className="h-5 w-auto" />
            <span className="text-[10px] font-medium uppercase tracking-wide text-white/70">by Ratel Systems</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="hidden text-sm text-white/80 sm:inline">{session.fullName}</span>
            <InstallAppButton className="flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm text-white/80 hover:bg-white/10 hover:text-white" />
            <Link
              href="/platform/change-password"
              className="flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm text-white/80 hover:bg-white/10 hover:text-white"
            >
              <KeyRound size={15} />
              <span className="hidden sm:inline">Password</span>
            </Link>
            <button
              onClick={logout}
              className="flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm text-white/80 hover:bg-white/10 hover:text-white"
            >
              <LogOut size={15} />
              Log out
            </button>
          </div>
        </div>
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4 sm:px-6">
          {NAV_ITEMS.map((item) => {
            const active = pathname === item.href;
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-1.5 whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition ${
                  active
                    ? "border-white text-white"
                    : "border-transparent text-white/70 hover:text-white"
                }`}
              >
                <Icon size={15} />
                {item.label}
              </Link>
            );
          })}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}
