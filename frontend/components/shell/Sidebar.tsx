"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Package,
  ShoppingCart,
  ShoppingBag,
  Sparkles,
  Users,
  Receipt,
  BarChart3,
  History,
  UserCog,
  Truck,
  ClipboardList,
  Wrench,
  CreditCard,
  Wallet,
  CalendarCheck2,
  ArrowUpRight,
  LifeBuoy,
  X,
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import PoweredByRatel from "@/components/PoweredByRatel";
import { StaffRole } from "@/lib/api";

// MANAGER manages the business day-to-day (clients, the system) without
// touching billing — "Administrator" is what that actually means to an owner
// hiring for the role, even though the underlying permission name stays MANAGER.
const ROLE_LABELS: Partial<Record<StaffRole, string>> = {
  MANAGER: "Administrator",
};

type NavItem = {
  href: string;
  label: string;
  icon: typeof LayoutDashboard;
  ownerOnly?: boolean;
  external?: boolean;
};

// Grouped so related items sit together instead of one long flat list —
// "Bookings" (the internal list/settings) and the public "Booking page"
// link live in the same group since they're two views of the same thing,
// not unrelated features.
const NAV_GROUPS: { label: string | null; items: NavItem[] }[] = [
  {
    label: null,
    items: [{ href: "/dashboard", label: "Dashboard", icon: LayoutDashboard }],
  },
  {
    label: "Bookings",
    items: [{ href: "/dashboard/bookings", label: "Bookings", icon: CalendarCheck2 }],
  },
  {
    label: "Operations",
    items: [
      { href: "/dashboard/inventory", label: "Inventory", icon: Package },
      { href: "/dashboard/sales", label: "Sales / POS", icon: ShoppingCart },
      { href: "/dashboard/service-orders", label: "Service Orders", icon: Wrench },
      { href: "/dashboard/ecommerce-orders", label: "E-commerce Orders", icon: ShoppingBag },
      { href: "/dashboard/custom-wig-requests", label: "Custom Wig Requests", icon: Sparkles },
      { href: "/dashboard/suppliers", label: "Suppliers", icon: Truck },
      { href: "/dashboard/purchase-orders", label: "Purchase Orders", icon: ClipboardList },
    ],
  },
  {
    label: "Business",
    items: [
      { href: "/dashboard/customers", label: "Customers", icon: Users },
      { href: "/dashboard/payments", label: "Payments", icon: Wallet },
      { href: "/dashboard/expenses", label: "Expenses", icon: Receipt },
      { href: "/dashboard/reports", label: "Reports", icon: BarChart3 },
      { href: "/dashboard/team", label: "Team", icon: UserCog },
      { href: "/dashboard/activity", label: "Activity Log", icon: History },
      { href: "/dashboard/billing", label: "Billing", icon: CreditCard, ownerOnly: true },
      { href: "/dashboard/help", label: "Help & Support", icon: LifeBuoy },
    ],
  },
];

export default function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const { session, business } = useAuth();

  return (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-text">
      <div className="flex items-center justify-between px-5 py-5">
        {/* The business's own identity leads here — logo and name front and
            center, since this is their system day to day. Ratel's mark moves
            to a quiet footer credit below instead of competing for top billing. */}
        <div className="flex min-w-0 items-center gap-3">
          {business?.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={business.logoUrl}
              alt=""
              className="h-10 w-10 shrink-0 rounded-lg object-cover ring-1 ring-white/10"
            />
          ) : (
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-sidebar-hover text-base font-semibold text-sidebar-text-active ring-1 ring-white/10">
              {(business?.name ?? session?.businessName ?? "R")[0]}
            </span>
          )}
          <div className="min-w-0">
            <p className="truncate text-base font-semibold tracking-tight text-sidebar-text-active">
              {business?.name ?? session?.businessName ?? "Ratel"}
            </p>
            <p className="truncate text-xs text-sidebar-text">
              {(session?.role && ROLE_LABELS[session.role as StaffRole]) ?? session?.role ?? "Business Management"}
            </p>
          </div>
        </div>
        <button
          onClick={onNavigate}
          className="rounded-md p-1 text-sidebar-text hover:bg-sidebar-hover lg:hidden"
          aria-label="Close menu"
        >
          <X size={18} />
        </button>
      </div>

      <nav className="flex-1 space-y-4 px-3 py-2 overflow-y-auto">
        {NAV_GROUPS.map((group, groupIndex) => {
          const items = group.items.filter((item) => !item.ownerOnly || session?.role === "OWNER");
          if (items.length === 0) return null;

          return (
            <div key={group.label ?? `group-${groupIndex}`}>
              {group.label && (
                <p className="mb-1 px-3 text-[11px] font-semibold uppercase tracking-wider text-sidebar-text/60">
                  {group.label}
                </p>
              )}
              <div className="space-y-0.5">
                {items.map((item) => {
                  const active = pathname === item.href;
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      onClick={onNavigate}
                      className={`relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
                        active
                          ? "bg-sidebar-hover text-sidebar-text-active"
                          : "text-sidebar-text hover:bg-sidebar-hover hover:text-sidebar-text-active"
                      }`}
                    >
                      {active && (
                        <span className="absolute inset-y-1 left-0 w-0.5 rounded-full bg-accent" aria-hidden />
                      )}
                      <Icon size={18} strokeWidth={1.75} />
                      {item.label}
                    </Link>
                  );
                })}
                {group.label === "Bookings" && business?.slug && (
                  <a
                    href={`/book/${business.slug}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-sidebar-text transition hover:bg-sidebar-hover hover:text-sidebar-text-active"
                  >
                    <ArrowUpRight size={18} strokeWidth={1.75} />
                    Booking page
                  </a>
                )}
              </div>
            </div>
          );
        })}
      </nav>

      <div className="border-t border-sidebar-border px-5 py-4">
        <PoweredByRatel className="text-xs text-sidebar-text" />
      </div>
    </div>
  );
}
