import Link from "next/link";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import { formatPercent } from "@/lib/dashboardFormat";
import { ProductAttentionItem, ProductsNeedingAttention } from "@/lib/api";

function MiniList({ items, render }: { items: ProductAttentionItem[]; render: (item: ProductAttentionItem) => React.ReactNode }) {
  if (items.length === 0) {
    return <p className="text-xs text-ink-500">None right now.</p>;
  }
  return (
    <ul className="flex flex-col gap-2">
      {items.slice(0, 5).map((item) => (
        <li key={item.productId} className="flex items-center justify-between gap-2 text-sm">
          <span className="truncate text-ink-900">{item.productName}</span>
          {render(item)}
        </li>
      ))}
    </ul>
  );
}

/** Section 5 — current-state product health, never date-filtered. */
export default function ProductsNeedingAttentionCard({ data }: { data: ProductsNeedingAttention | null }) {
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-ink-900">Products needing attention</h2>
        <Link href="/dashboard/inventory" className="text-xs font-medium text-accent-hover hover:underline">
          View all
        </Link>
      </div>

      {!data ? (
        <div className="mt-4 animate-pulse space-y-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-16 rounded-lg bg-canvas" />
          ))}
        </div>
      ) : (
        <div className="mt-4 grid gap-4 sm:grid-cols-3">
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">Low stock</p>
            <MiniList items={data.lowStock} render={(i) => <Badge tone="danger">{i.quantity} left</Badge>} />
          </div>
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">Out of stock</p>
            <MiniList items={data.outOfStock} render={() => <Badge tone="danger">0 left</Badge>} />
          </div>
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">Low margin</p>
            <MiniList items={data.lowMargin} render={(i) => <Badge tone="danger">{formatPercent(i.profitMarginPercent)}</Badge>} />
          </div>
        </div>
      )}
    </Card>
  );
}
