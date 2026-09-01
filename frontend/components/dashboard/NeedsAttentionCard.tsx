import Link from "next/link";
import { AlertTriangle, PackageX, TrendingDown, Sparkles, ShoppingBag, ClipboardCheck, FileWarning, ChevronRight, PartyPopper } from "lucide-react";
import Card from "@/components/ui/Card";
import { DashboardAttention } from "@/lib/api";

function AttentionRow({
  href,
  icon: Icon,
  label,
  count,
  zeroHint,
}: {
  href: string;
  icon: typeof AlertTriangle;
  label: string;
  count: number;
  zeroHint: string;
}) {
  return (
    <Link
      href={href}
      className="flex items-center justify-between gap-3 border-b border-border py-3 text-sm last:border-0 transition hover:bg-canvas -mx-1 px-1 rounded-md"
    >
      <div className="flex items-center gap-3">
        <span
          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${
            count > 0 ? "bg-danger-soft text-danger" : "bg-canvas text-ink-500"
          }`}
        >
          <Icon size={15} />
        </span>
        <div>
          <p className="font-medium text-ink-900">{label}</p>
          <p className="text-xs text-ink-500">{count > 0 ? `${count} need${count === 1 ? "s" : ""} your attention` : zeroHint}</p>
        </div>
      </div>
      <div className="flex items-center gap-1.5">
        {count > 0 && (
          <span className="rounded-full bg-danger px-2 py-0.5 text-xs font-semibold text-white">{count}</span>
        )}
        <ChevronRight size={15} className="text-ink-500" />
      </div>
    </Link>
  );
}

/** Section 3 — expanded from the original dashboard's version to cover every module the spec calls out. */
export default function NeedsAttentionCard({ attention }: { attention: DashboardAttention | null }) {
  const rows = attention
    ? [
        { key: "lowStock", href: "/dashboard/inventory", icon: AlertTriangle, label: "Low stock alerts", count: attention.lowStockProducts, zeroHint: "All good" },
        { key: "outOfStock", href: "/dashboard/inventory", icon: PackageX, label: "Out of stock", count: attention.outOfStockProducts, zeroHint: "Nothing out of stock" },
        { key: "lowMargin", href: "/dashboard/inventory", icon: TrendingDown, label: "Low margin products", count: attention.lowMarginProducts, zeroHint: "All above your margin threshold" },
        ...(attention.customWigRequestsEnabled
          ? [{ key: "wig", href: "/dashboard/custom-wig-requests", icon: Sparkles, label: "New custom wig requests", count: attention.newCustomWigRequests, zeroHint: "All caught up" }]
          : []),
        ...(attention.ecommerceEnabled
          ? [{ key: "ecom", href: "/dashboard/ecommerce-orders", icon: ShoppingBag, label: "E-commerce orders to fulfill", count: attention.ecommerceOrdersToFulfill, zeroHint: "All caught up" }]
          : []),
        { key: "approvals", href: "/dashboard/approvals", icon: ClipboardCheck, label: "Pending approvals", count: attention.pendingApprovals, zeroHint: "Nothing waiting" },
        { key: "invoices", href: "/dashboard/invoices", icon: FileWarning, label: "Overdue invoices", count: attention.overdueInvoices, zeroHint: "Nothing overdue" },
      ]
    : [];

  const totalNeedingAttention = rows.reduce((sum, r) => sum + r.count, 0);

  return (
    <Card className="p-5">
      <h2 className="text-base font-semibold text-ink-900">Needs your attention</h2>
      <p className="mt-1 text-sm text-ink-500">A running list of what&apos;s waiting on you today.</p>

      {!attention ? (
        <div className="mt-3 animate-pulse space-y-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-10 rounded-lg bg-canvas" />
          ))}
        </div>
      ) : totalNeedingAttention === 0 ? (
        <div className="mt-4 flex flex-col items-center gap-2 rounded-lg bg-success-soft py-8 text-center">
          <PartyPopper size={22} className="text-success" />
          <p className="text-sm font-medium text-ink-900">You&apos;re all caught up!</p>
          <p className="text-xs text-ink-500">Nothing needs your attention right now.</p>
        </div>
      ) : (
        <div className="mt-3 flex flex-col">
          {rows.map((r) => (
            <AttentionRow key={r.key} href={r.href} icon={r.icon} label={r.label} count={r.count} zeroHint={r.zeroHint} />
          ))}
        </div>
      )}
    </Card>
  );
}
