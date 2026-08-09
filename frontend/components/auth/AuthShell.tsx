import Link from "next/link";
import { ReactNode } from "react";
import { Sparkles } from "lucide-react";

export default function AuthShell({
  eyebrow,
  title,
  subtitle,
  maxWidth = "sm",
  children,
}: {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  maxWidth?: "sm" | "md";
  children: ReactNode;
}) {
  return (
    <main className="relative isolate flex min-h-screen items-center justify-center overflow-hidden bg-canvas px-4 py-12">
      <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
        <div className="animate-blob-drift absolute -left-24 -top-24 h-[420px] w-[420px] rounded-full bg-accent/20 blur-3xl" />
        <div className="animate-blob-drift absolute -right-16 top-10 h-[360px] w-[360px] rounded-full bg-info/20 blur-3xl [animation-delay:-6s]" />
        <div className="animate-blob-drift absolute bottom-0 left-1/3 h-[300px] w-[300px] rounded-full bg-danger/10 blur-3xl [animation-delay:-11s]" />
      </div>

      <div className={`reveal is-visible w-full ${maxWidth === "md" ? "max-w-md" : "max-w-sm"}`}>
        <Link href="/" className="mb-6 flex items-center justify-center gap-2">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/branding/ratel-icon.png" alt="" width={26} height={26} />
          <span className="text-base font-semibold tracking-tight text-ink-900">Ratel Systems</span>
        </Link>

        <div className="rounded-2xl border border-border bg-surface p-8 shadow-panel">
          {eyebrow && (
            <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider text-accent">
              <Sparkles size={12} /> {eyebrow}
            </div>
          )}
          <h1 className="text-xl font-semibold tracking-tight text-ink-900">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-ink-500">{subtitle}</p>}

          <div className="mt-6">{children}</div>
        </div>
      </div>
    </main>
  );
}
