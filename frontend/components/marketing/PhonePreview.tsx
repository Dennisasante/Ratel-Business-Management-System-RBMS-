import { CalendarDays, Sparkles, ShoppingBag, ChevronRight } from "lucide-react";

// Mocks the real hosted "one link" hub (/start/[slug]) inside a phone frame —
// doubles as proof the product itself is mobile-first, not just this page.
const OPTIONS = [
  { icon: CalendarDays, label: "Book a service", sub: "Pick a time, any hour" },
  { icon: Sparkles, label: "Custom order", sub: "Built around your options" },
  { icon: ShoppingBag, label: "Shop products", sub: "Browse what's in stock" },
];

export default function PhonePreview() {
  return (
    <div className="animate-float-slower mx-auto w-full max-w-[240px] sm:max-w-[260px]">
      <div className="rounded-[2rem] border-[6px] border-sidebar bg-sidebar p-1.5 shadow-panel sm:rounded-[2.25rem] sm:border-8">
        <div className="overflow-hidden rounded-[1.4rem] bg-canvas sm:rounded-[1.6rem]">
          {/* status bar */}
          <div className="flex items-center justify-between px-4 pb-1 pt-2.5 text-[9px] text-ink-300">
            <span>9:41</span>
            <span className="h-1.5 w-6 rounded-full bg-ink-300/40" />
          </div>

          <div className="px-4 pb-5 pt-2">
            <div className="flex items-center gap-2">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src="/branding/tallia-icon-mark.svg" alt="" width={20} height={20} className="rounded" />
              <div className="min-w-0">
                <p className="truncate text-[11px] font-semibold text-ink-900">Skulba Salon</p>
                <p className="text-[9px] text-ink-500">Choose what you need</p>
              </div>
            </div>

            <div className="mt-4 flex flex-col gap-2">
              {OPTIONS.map((o) => (
                <div
                  key={o.label}
                  className="flex items-center gap-2.5 rounded-xl border border-border bg-surface p-2.5 shadow-card"
                >
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-accent-hover">
                    <o.icon size={13} strokeWidth={1.75} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[10px] font-semibold text-ink-900">{o.label}</p>
                    <p className="truncate text-[8.5px] text-ink-500">{o.sub}</p>
                  </div>
                  <ChevronRight size={12} className="shrink-0 text-ink-300" />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
