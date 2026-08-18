// Hero product visual — a static, hand-built mockup of the real dashboard shell
// (same sidebar/canvas/accent tokens the logged-in app uses), not a screenshot.
// Keeps the marketing bundle image-free and never goes stale when the real
// dashboard's copy changes.
const NAV_ITEMS = ["Dashboard", "Sales / POS", "Bookings", "Service Orders", "Payments", "Customers"];

function StatMock({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return (
    <div
      className={`rounded-lg border p-2 sm:p-3 ${
        accent ? "border-accent/20 bg-accent-soft" : "border-border bg-surface"
      }`}
    >
      <p className={`truncate text-[9px] font-medium sm:text-[10.5px] ${accent ? "text-accent-hover" : "text-ink-500"}`}>
        {label}
      </p>
      <p className={`mt-1 text-xs font-semibold tabular sm:text-base ${accent ? "text-accent" : "text-ink-900"}`}>
        {value}
      </p>
    </div>
  );
}

function MiniChart() {
  // Fixed illustrative points — a gently rising trend line, not real data.
  const points = "0,52 35,46 70,48 105,34 140,38 175,20 210,26 245,10 280,14";
  return (
    <svg viewBox="0 0 280 60" className="mt-2 h-12 w-full sm:h-14" preserveAspectRatio="none">
      <defs>
        <linearGradient id="revenueFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#004aad" stopOpacity="0.22" />
          <stop offset="100%" stopColor="#004aad" stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={`0,60 ${points} 280,60`} fill="url(#revenueFill)" />
      <polyline points={points} fill="none" stroke="#004aad" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export default function DashboardPreview() {
  return (
    <div className="animate-float-slow mx-auto max-w-4xl rounded-2xl border border-border bg-surface p-2 shadow-panel sm:p-3">
      {/* browser chrome */}
      <div className="flex items-center gap-1.5 px-2 pb-2 pt-1.5">
        <span className="h-2.5 w-2.5 rounded-full bg-danger/60" />
        <span className="h-2.5 w-2.5 rounded-full bg-[#E8A83C]/60" />
        <span className="h-2.5 w-2.5 rounded-full bg-success/60" />
        <div className="ml-2 flex-1 truncate rounded-md bg-canvas px-3 py-1 text-center text-[10px] text-ink-300 sm:text-xs">
          yourbusiness.ratelsystems.tech/dashboard
        </div>
      </div>

      {/* app shell mock */}
      <div className="flex overflow-hidden rounded-xl border border-border">
        {/* sidebar — hidden on the very smallest screens so the content stays legible */}
        <div className="hidden w-32 shrink-0 flex-col gap-1 bg-sidebar p-2.5 [@media(min-width:420px)]:flex sm:w-40 sm:gap-1.5 sm:p-3">
          <div className="mb-2 flex items-center gap-1.5 px-0.5">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/branding/tallia-icon-mark.svg" alt="" width={14} height={14} />
            <span className="truncate text-[9px] font-semibold text-white sm:text-[10.5px]">Skulba Salon</span>
          </div>
          {NAV_ITEMS.map((item, i) => (
            <div
              key={item}
              className={`truncate rounded-md px-2 py-1 text-[8.5px] sm:py-1.5 sm:text-[10px] ${
                i === 0 ? "bg-accent font-medium text-white" : "text-sidebar-text"
              }`}
            >
              {item}
            </div>
          ))}
        </div>

        {/* main content */}
        <div className="min-w-0 flex-1 bg-canvas p-3 sm:p-4">
          <p className="text-[10px] text-ink-500 sm:text-xs">Good morning, Test!</p>
          <p className="text-xs font-semibold text-ink-900 sm:text-sm">Here&apos;s how today is going.</p>

          <div className="mt-2.5 grid grid-cols-3 gap-1.5 sm:mt-3 sm:gap-2">
            <StatMock label="Team members" value="4" />
            <StatMock label="Today's revenue" value="GH₵1,150" accent />
            <StatMock label="Active orders" value="6" />
          </div>

          <div className="mt-2.5 rounded-lg border border-border bg-surface p-2.5 sm:mt-3 sm:p-3">
            <div className="flex items-center justify-between">
              <p className="text-[9px] font-medium text-ink-700 sm:text-[10.5px]">Revenue this week</p>
              <span className="rounded-full bg-success-soft px-1.5 py-0.5 text-[8px] font-medium text-success sm:text-[9px]">
                +18%
              </span>
            </div>
            <MiniChart />
          </div>
        </div>
      </div>
    </div>
  );
}
