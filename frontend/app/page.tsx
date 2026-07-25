import Link from "next/link";
import { LayoutDashboard, Package, ShoppingCart, Receipt } from "lucide-react";

const FEATURES = [
  { icon: Package, label: "Inventory", desc: "Track stock, low-stock alerts, full movement history" },
  { icon: ShoppingCart, label: "Sales / POS", desc: "Cart-based checkout that updates stock automatically" },
  { icon: Receipt, label: "Expenses", desc: "Log costs by category, see them roll into your reports" },
  { icon: LayoutDashboard, label: "Reports", desc: "Revenue, expenses, and profit for any date range" },
];

export default function Home() {
  return (
    <main className="min-h-screen bg-canvas">
      <div className="mx-auto flex min-h-screen max-w-5xl flex-col justify-center px-6 py-16">
        <div className="mx-auto w-full max-w-2xl text-center">
          <span className="inline-flex items-center rounded-full border border-border bg-surface px-3 py-1 text-xs font-medium text-ink-500">
            Built for salons, retail, and everything between
          </span>
          <h1 className="mt-5 text-3xl font-semibold tracking-tight text-ink-900 sm:text-4xl">
            One system to run your whole business
          </h1>
          <p className="mt-3 text-base text-ink-500">
            Inventory, sales, customers, and expenses — in one place, built for
            how your business actually runs day to day.
          </p>
          <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              href="/register"
              className="inline-flex items-center justify-center rounded-lg bg-accent px-6 py-3 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover"
            >
              Register your business
            </Link>
            <Link
              href="/login"
              className="inline-flex items-center justify-center rounded-lg border border-border bg-surface px-6 py-3 text-sm font-medium text-ink-900 transition hover:bg-canvas"
            >
              Log in
            </Link>
          </div>
        </div>

        <div className="mx-auto mt-14 grid w-full max-w-3xl grid-cols-2 gap-4 sm:grid-cols-4">
          {FEATURES.map((f) => (
            <div key={f.label} className="rounded-xl border border-border bg-surface p-4 text-left shadow-card">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent-soft text-accent-hover">
                <f.icon size={18} strokeWidth={1.75} />
              </div>
              <p className="mt-3 text-sm font-medium text-ink-900">{f.label}</p>
              <p className="mt-1 text-xs text-ink-500">{f.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}
