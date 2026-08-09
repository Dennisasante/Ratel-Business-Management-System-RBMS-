"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Sparkles, CalendarDays, MessageCircle, ChevronRight } from "lucide-react";
import { api, ApiError, StartHubConfig } from "@/lib/api";
import PoweredByRatel from "@/components/PoweredByRatel";

const ACCENT = "#a76545";

export default function StartHubPage() {
  const params = useParams<{ slug: string }>();
  const slug = params.slug;

  const [config, setConfig] = useState<StartHubConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setConfig(await api.getStartHubConfigBySlug(slug));
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : "Couldn't load this page.");
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => {
    load();
  }, [load]);

  const hasAnyOption = config && (config.bookingEnabled || config.customOrderEnabled);

  return (
    <main
      className="flex min-h-screen flex-col items-center justify-center px-4 py-12"
      style={{ background: "radial-gradient(1200px circle at 50% -10%, rgba(167,101,69,0.14), transparent 55%), #fdfaf6" }}
    >
      <div
        className="w-full max-w-md rounded-3xl p-8"
        style={{ background: "#fff", border: "1px solid #f0e6d8", boxShadow: "0 1px 2px rgba(42,32,24,0.04), 0 24px 48px -16px rgba(42,32,24,0.18)" }}
      >
        {loading && <p className="text-sm text-[#9a8c7c]">Loading...</p>}
        {!loading && loadError && <p className="text-sm text-[#b1432e]">{loadError}</p>}

        {!loading && !loadError && config && (
          <>
            <div className="mb-1 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: ACCENT }}>
              <Sparkles size={13} /> Welcome
            </div>
            <h1 className="text-xl font-semibold tracking-tight text-[#1c140d]">{config.businessName}</h1>
            <p className="mt-1 text-sm text-[#9a8c7c]">What would you like to do?</p>

            {!hasAnyOption && (
              <p className="mt-6 text-sm text-[#9a8c7c]">
                Nothing&apos;s available online right now — please reach out directly.
              </p>
            )}

            <div className="mt-6 flex flex-col gap-3">
              {config.bookingEnabled && (
                <Link
                  href={`/book/${slug}`}
                  className="group flex items-center justify-between rounded-2xl border-[1.5px] border-[#ece1d2] px-4 py-4 text-left transition hover:border-[#a76545]/40"
                  style={{ background: "#fff" }}
                >
                  <span className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-full" style={{ background: "rgba(167,101,69,0.1)", color: ACCENT }}>
                      <CalendarDays size={18} />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-[#2a2018]">Book an appointment</span>
                      <span className="block text-xs text-[#9a8c7c]">Pick a service and a time that works for you.</span>
                    </span>
                  </span>
                  <ChevronRight size={16} className="shrink-0 text-[#c9bba8] transition group-hover:translate-x-0.5" />
                </Link>
              )}

              {config.customOrderEnabled && (
                <Link
                  href={`/order/${slug}`}
                  className="group flex items-center justify-between rounded-2xl border-[1.5px] border-[#ece1d2] px-4 py-4 text-left transition hover:border-[#a76545]/40"
                  style={{ background: "#fff" }}
                >
                  <span className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-full" style={{ background: "rgba(167,101,69,0.1)", color: ACCENT }}>
                      <Sparkles size={18} />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-[#2a2018]">Place a custom order</span>
                      <span className="block text-xs text-[#9a8c7c]">Build your own, with live pricing as you go.</span>
                    </span>
                  </span>
                  <ChevronRight size={16} className="shrink-0 text-[#c9bba8] transition group-hover:translate-x-0.5" />
                </Link>
              )}
            </div>

            {config.businessWhatsappLink && (
              <a
                href={config.businessWhatsappLink}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-6 flex w-full items-center justify-center gap-2 rounded-full border-[1.5px] px-4 py-3 text-sm font-semibold transition hover:opacity-90"
                style={{ borderColor: "#3f8a53", color: "#3f8a53" }}
              >
                <MessageCircle size={15} />
                Message us on WhatsApp
              </a>
            )}
          </>
        )}
      </div>
      <PoweredByRatel className="mt-6 text-xs text-[#b3a690]" iconSize={16} />
    </main>
  );
}
