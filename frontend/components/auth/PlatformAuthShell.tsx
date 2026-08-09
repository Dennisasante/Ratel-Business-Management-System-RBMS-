import { ReactNode } from "react";
import { ShieldCheck } from "lucide-react";

export default function PlatformAuthShell({
  eyebrow = "Restricted access",
  title,
  subtitle,
  children,
}: {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <main className="relative isolate flex min-h-screen items-center justify-center overflow-hidden bg-sidebar px-4 py-12">
      <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
        <div className="animate-blob-drift absolute -left-20 -top-24 h-[380px] w-[380px] rounded-full bg-accent/25 blur-3xl" />
        <div className="animate-blob-drift absolute -right-16 top-16 h-[320px] w-[320px] rounded-full bg-info/20 blur-3xl [animation-delay:-6s]" />
        <div className="animate-blob-drift absolute bottom-[-80px] left-1/3 h-[280px] w-[280px] rounded-full bg-accent/10 blur-3xl [animation-delay:-11s]" />
      </div>

      <div className="reveal is-visible w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2 text-sidebar-text-active">
          <ShieldCheck size={20} />
          <span className="text-sm font-semibold tracking-wide">RATEL PLATFORM</span>
        </div>

        <div className="rounded-2xl border border-sidebar-border bg-[#20242E] p-8 shadow-panel">
          {eyebrow && (
            <div
              className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider"
              style={{ color: "#7fa5e8" }}
            >
              <ShieldCheck size={12} /> {eyebrow}
            </div>
          )}
          <h1 className="text-xl font-semibold tracking-tight text-sidebar-text-active">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-sidebar-text">{subtitle}</p>}

          <div className="mt-6">{children}</div>
        </div>
      </div>
    </main>
  );
}
