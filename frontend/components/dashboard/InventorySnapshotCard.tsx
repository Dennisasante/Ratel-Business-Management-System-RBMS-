import Link from "next/link";
import Card from "@/components/ui/Card";
import { formatGHS } from "@/lib/dashboardFormat";
import { InventorySnapshot } from "@/lib/api";

/** Section 6 — current-state inventory health, never date-filtered. */
export default function InventorySnapshotCard({ snapshot }: { snapshot: InventorySnapshot | null }) {
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-ink-900">Inventory snapshot</h2>
        <Link href="/dashboard/inventory" className="text-xs font-medium text-accent-hover hover:underline">
          View inventory
        </Link>
      </div>

      {!snapshot ? (
        <div className="mt-4 h-24 animate-pulse rounded-lg bg-canvas" />
      ) : (
        <>
          <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div>
              <p className="text-xs text-ink-500">Products</p>
              <p className="tabular text-lg font-semibold text-ink-900">{snapshot.totalProducts}</p>
            </div>
            <div>
              <p className="text-xs text-ink-500">Total quantity</p>
              <p className="tabular text-lg font-semibold text-ink-900">{snapshot.totalQuantity}</p>
            </div>
            <div>
              <p className="text-xs text-ink-500">Low / out of stock</p>
              <p className="tabular text-lg font-semibold text-ink-900">
                {snapshot.lowStockCount} / {snapshot.outOfStockCount}
              </p>
            </div>
            <div>
              <p className="text-xs text-ink-500">Inventory value</p>
              <p className="tabular text-lg font-semibold text-ink-900">{formatGHS(snapshot.inventoryValue)}</p>
            </div>
          </div>

          {snapshot.lowStockItems.length > 0 && (
            <div className="mt-4 border-t border-border pt-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">Running low</p>
              <ul className="flex flex-col gap-1.5">
                {snapshot.lowStockItems.map((item) => (
                  <li key={item.productId} className="flex items-center justify-between text-sm">
                    <span className="truncate text-ink-900">{item.productName}</span>
                    <span className="tabular text-xs text-ink-500">{item.quantity} left</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </Card>
  );
}
