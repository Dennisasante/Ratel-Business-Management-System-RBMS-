"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  Package,
  ShoppingCart,
  Wrench,
  CalendarDays,
  ShoppingBag,
  Sparkles,
  Users,
  UserCog,
  BarChart3,
  MessageCircle,
  Monitor,
  Smartphone,
  ShieldCheck,
  ArrowRight,
  Check,
  Globe,
} from "lucide-react";
import Reveal from "@/components/marketing/Reveal";
import DashboardPreview from "@/components/marketing/DashboardPreview";
import PhonePreview from "@/components/marketing/PhonePreview";

const PAIN_POINTS = [
  { problem: "Stock counts that are really just guesses", solution: "Live inventory with automatic low-stock alerts" },
  { problem: "Bookings scattered across notebooks and WhatsApp chats", solution: "One booking page — online, 24/7, no double-bookings" },
  { problem: "No idea what each staff member actually did today", solution: "Staff accounts with roles, commissions, and their own view" },
  { problem: "Chasing customers for payment after the fact", solution: "Deposit or full payment collected right at booking" },
  { problem: "Sales, expenses, and profit you can only guess at", solution: "Real reports — revenue, expenses, profit, any date range" },
];

const FEATURES = [
  { icon: Package, title: "Inventory", desc: "Stock levels, low-stock alerts, and a full movement history for every item." },
  { icon: ShoppingCart, title: "Sales / POS", desc: "Cart-based checkout that updates stock the moment you sell." },
  { icon: Wrench, title: "Service Orders", desc: "Walk-ins and appointments, assigned to staff, with before/after photos." },
  { icon: CalendarDays, title: "Online Booking", desc: "A hosted booking page and embeddable widget so clients book themselves, any hour." },
  { icon: ShoppingBag, title: "E-commerce Sync", desc: "Connect WooCommerce and every order flows straight into one inbox." },
  { icon: Sparkles, title: "Custom Orders", desc: "A live-pricing configurator for made-to-order items, built around your options." },
  { icon: Users, title: "Customers & Suppliers", desc: "Full purchase history on one side, purchase orders on the other." },
  { icon: UserCog, title: "Team & Roles", desc: "Staff accounts, commissions, and control over who sees what." },
  { icon: BarChart3, title: "Reports", desc: "Revenue, expenses, and profit — for today, this month, or any range." },
  { icon: MessageCircle, title: "WhatsApp Built In", desc: "One-tap links to message customers and staff — no extra app to juggle." },
  { icon: Monitor, title: "Runs Everywhere", desc: "A desktop app for the shop computer, or install it on any phone or tablet." },
  { icon: ShieldCheck, title: "Yours Alone", desc: "Your data is fully isolated from every other business on Tallia." },
];

const MARQUEE_ITEMS = [
  "Inventory", "Sales & POS", "Bookings", "Service Orders", "E-commerce Sync",
  "Custom Orders", "Team & Roles", "Reports", "WhatsApp", "Desktop App", "Mobile Install",
];

const LINK_STEPS = [
  "Share your one link anywhere — WhatsApp bio, Instagram, receipts",
  "Customers pick a service, request a custom order, or shop — and pay a deposit or in full",
  "It lands on your dashboard instantly. No calls, no missed DMs",
];

export default function Home() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    function onScroll() {
      setScrolled(window.scrollY > 8);
    }
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <main className="min-h-screen overflow-x-hidden bg-canvas">
      {/* Nav */}
      <header
        className={`sticky top-0 z-30 border-b transition-colors ${
          scrolled ? "border-border bg-surface/90 backdrop-blur" : "border-transparent bg-transparent"
        }`}
      >
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-2">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/branding/tallia-icon-mark.svg" alt="" width={28} height={28} />
            <span className="text-base font-semibold tracking-tight text-ink-900">Tallia</span>
          </div>
          <nav className="flex items-center gap-2 sm:gap-3">
            <Link
              href="/login"
              className="rounded-lg px-3 py-2 text-sm font-medium text-ink-700 transition hover:text-ink-900 sm:px-4"
            >
              Log in
            </Link>
            <Link
              href="/register"
              className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover sm:px-4"
            >
              Start free
            </Link>
          </nav>
        </div>
      </header>

      {/* Hero */}
      <section className="relative isolate px-6 pb-16 pt-14 sm:pb-24 sm:pt-20">
        <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
          <div
            className="absolute inset-0 opacity-[0.35]"
            style={{
              backgroundImage: "radial-gradient(#9AA0AC 1px, transparent 1px)",
              backgroundSize: "22px 22px",
              maskImage: "radial-gradient(ellipse 60% 50% at 50% 0%, black 40%, transparent 100%)",
              WebkitMaskImage: "radial-gradient(ellipse 60% 50% at 50% 0%, black 40%, transparent 100%)",
            }}
          />
          <div className="animate-blob-drift absolute -left-24 -top-24 h-[420px] w-[420px] rounded-full bg-accent/20 blur-3xl" />
          <div className="animate-blob-drift absolute -right-16 top-10 h-[360px] w-[360px] rounded-full bg-info/20 blur-3xl [animation-delay:-6s]" />
          <div className="animate-blob-drift absolute bottom-0 left-1/3 h-[300px] w-[300px] rounded-full bg-danger/10 blur-3xl [animation-delay:-11s]" />
        </div>

        <div className="mx-auto max-w-3xl text-center">
          <div className="reveal is-visible inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1 text-xs font-medium text-ink-500 shadow-card">
            <Sparkles size={12} className="text-accent" />
            For salons, retail, and service businesses across Ghana
          </div>

          <h1
            className="reveal is-visible mt-5 text-4xl font-semibold tracking-tight text-ink-900 sm:mt-6 sm:text-5xl md:text-6xl"
            style={{ animationDelay: "80ms" }}
          >
            Run your business.
            <br />
            <span className="text-accent">Not a pile of notebooks.</span>
          </h1>

          <p className="reveal is-visible mx-auto mt-4 max-w-xl text-base text-ink-500 sm:mt-5 sm:text-lg" style={{ animationDelay: "160ms" }}>
            Inventory, sales, bookings, staff, and money — all in one system built
            for how salons, retailers, and service businesses actually run day to
            day. No spreadsheets. No sticky notes. No guessing.
          </p>

          <div className="reveal is-visible mt-7 flex flex-col items-center justify-center gap-3 sm:mt-8 sm:flex-row" style={{ animationDelay: "240ms" }}>
            <Link
              href="/register"
              className="group inline-flex w-full items-center justify-center gap-2 rounded-lg bg-accent px-6 py-3.5 text-sm font-semibold text-white shadow-card transition hover:bg-accent-hover sm:w-auto"
            >
              Start free — no card needed
              <ArrowRight size={16} className="transition-transform group-hover:translate-x-0.5" />
            </Link>
            <Link
              href="/login"
              className="inline-flex w-full items-center justify-center rounded-lg border border-border bg-surface px-6 py-3.5 text-sm font-medium text-ink-900 transition hover:bg-canvas sm:w-auto"
            >
              Log in
            </Link>
          </div>

          <p className="reveal is-visible mt-4 text-xs text-ink-500" style={{ animationDelay: "300ms" }}>
            Free trial • Works on phone, tablet, and desktop • Cancel anytime
          </p>
        </div>

        {/* Product preview */}
        <Reveal delay={340} className="mt-12 px-2 sm:mt-16">
          <DashboardPreview />
        </Reveal>

        {/* Marquee of everything the system does */}
        <div className="reveal is-visible relative mt-10 overflow-hidden sm:mt-14" style={{ animationDelay: "440ms" }}>
          <div className="pointer-events-none absolute inset-y-0 left-0 z-10 w-16 bg-gradient-to-r from-canvas to-transparent" />
          <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-16 bg-gradient-to-l from-canvas to-transparent" />
          <div className="animate-marquee flex w-max gap-3">
            {[...MARQUEE_ITEMS, ...MARQUEE_ITEMS].map((item, i) => (
              <span
                key={i}
                className="whitespace-nowrap rounded-full border border-border bg-surface px-4 py-2 text-xs font-medium text-ink-700 shadow-card"
              >
                {item}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* One link feature spotlight */}
      <section className="px-6 py-16 sm:py-24">
        <div className="mx-auto grid max-w-5xl items-center gap-10 sm:grid-cols-2 sm:gap-14">
          <Reveal>
            <h2 className="text-2xl font-semibold tracking-tight text-ink-900 sm:text-3xl">
              One link. Every way customers reach you.
            </h2>
            <p className="mt-3 text-ink-500">
              No app to download, no phone tag. Share one link and let customers
              book, order, or shop on their own — any hour, from their phone.
            </p>
            <ul className="mt-6 space-y-4">
              {LINK_STEPS.map((step, i) => (
                <li key={step} className="flex items-start gap-3">
                  <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent text-[11px] font-semibold text-white">
                    {i + 1}
                  </span>
                  <p className="text-sm text-ink-700">{step}</p>
                </li>
              ))}
            </ul>
          </Reveal>
          <Reveal delay={120}>
            <PhonePreview />
          </Reveal>
        </div>
      </section>

      {/* Pain points */}
      <section className="px-6 py-16 sm:py-24">
        <div className="mx-auto max-w-4xl">
          <Reveal className="text-center">
            <h2 className="text-2xl font-semibold tracking-tight text-ink-900 sm:text-3xl">Sound familiar?</h2>
            <p className="mt-2 text-ink-500">Every one of these is a solved problem — not a to-do you still have to manage.</p>
          </Reveal>

          <div className="mt-10 flex flex-col gap-3">
            {PAIN_POINTS.map((p, i) => (
              <Reveal key={p.problem} delay={i * 70}>
                <div className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4 shadow-card sm:flex-row sm:items-center sm:gap-6 sm:p-5">
                  <div className="flex flex-1 items-start gap-3 sm:items-center">
                    <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-danger sm:mt-0" aria-hidden />
                    <p className="text-sm text-ink-500 line-through decoration-ink-300">{p.problem}</p>
                  </div>
                  <div className="hidden text-ink-300 sm:block">
                    <ArrowRight size={16} />
                  </div>
                  <div className="flex flex-1 items-start gap-3 sm:items-center">
                    <span className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-success-soft text-success sm:mt-0">
                      <Check size={11} strokeWidth={3} />
                    </span>
                    <p className="text-sm font-medium text-ink-900">{p.solution}</p>
                  </div>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Feature grid */}
      <section className="bg-surface px-6 py-16 sm:py-24">
        <div className="mx-auto max-w-6xl">
          <Reveal className="mx-auto max-w-xl text-center">
            <h2 className="text-2xl font-semibold tracking-tight text-ink-900 sm:text-3xl">
              One system. Everything your business needs.
            </h2>
            <p className="mt-2 text-ink-500">
              Every piece talks to every other piece — a sale updates stock, a booking creates an order, a payment updates your reports.
            </p>
          </Reveal>

          <div className="mt-12 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f, i) => (
              <Reveal key={f.title} delay={(i % 6) * 60}>
                <div className="group h-full rounded-xl border border-border bg-canvas p-5 transition duration-300 hover:-translate-y-1 hover:border-accent/30 hover:shadow-panel">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent-soft text-accent-hover transition-transform duration-300 group-hover:scale-110">
                    <f.icon size={19} strokeWidth={1.75} />
                  </div>
                  <p className="mt-4 text-sm font-semibold text-ink-900">{f.title}</p>
                  <p className="mt-1.5 text-sm leading-relaxed text-ink-500">{f.desc}</p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Runs everywhere */}
      <section className="px-6 py-16 sm:py-24">
        <div className="mx-auto max-w-4xl">
          <Reveal className="text-center">
            <h2 className="text-2xl font-semibold tracking-tight text-ink-900 sm:text-3xl">Runs however you work</h2>
            <p className="mt-2 text-ink-500">No app store required, no IT department needed.</p>
          </Reveal>

          <div className="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Reveal delay={0}>
              <div className="animate-float-slow flex h-full flex-col items-center gap-3 rounded-xl border border-border bg-surface p-6 text-center shadow-card">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-info-soft text-info">
                  <Globe size={20} />
                </div>
                <p className="text-sm font-semibold text-ink-900">Any browser</p>
                <p className="text-xs text-ink-500">Log in from any computer — nothing to install.</p>
              </div>
            </Reveal>
            <Reveal delay={100}>
              <div className="animate-float-slower flex h-full flex-col items-center gap-3 rounded-xl border border-border bg-surface p-6 text-center shadow-card">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-accent-soft text-accent-hover">
                  <Monitor size={20} />
                </div>
                <p className="text-sm font-semibold text-ink-900">Desktop app</p>
                <p className="text-xs text-ink-500">A single file for the shop computer — double-click and go.</p>
              </div>
            </Reveal>
            <Reveal delay={200}>
              <div className="animate-float-slow flex h-full flex-col items-center gap-3 rounded-xl border border-border bg-surface p-6 text-center shadow-card">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-success-soft text-success">
                  <Smartphone size={20} />
                </div>
                <p className="text-sm font-semibold text-ink-900">Phone & tablet</p>
                <p className="text-xs text-ink-500">Install to the home screen — opens full-screen like a native app.</p>
              </div>
            </Reveal>
          </div>
        </div>
      </section>

      {/* Final CTA */}
      <section className="px-6 pb-20 sm:pb-28">
        <Reveal className="mx-auto max-w-4xl">
          <div className="relative isolate overflow-hidden rounded-2xl bg-accent px-6 py-14 text-center shadow-panel sm:px-12 sm:py-16">
            <div className="pointer-events-none absolute inset-0 -z-10" aria-hidden>
              <div className="animate-blob-drift absolute -right-10 -top-10 h-64 w-64 rounded-full bg-white/10 blur-3xl" />
              <div className="animate-blob-drift absolute -bottom-10 -left-10 h-64 w-64 rounded-full bg-white/10 blur-3xl [animation-delay:-8s]" />
            </div>
            <h2 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">
              Ready to stop running your business by hand?
            </h2>
            <p className="mx-auto mt-3 max-w-md text-sm text-white/80 sm:text-base">
              Set up your business in a few minutes. Your first sale, booking, or
              stock count can happen today.
            </p>
            <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Link
                href="/register"
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-white px-6 py-3.5 text-sm font-semibold text-accent shadow-card transition hover:bg-white/90 sm:w-auto"
              >
                Register your business
                <ArrowRight size={16} />
              </Link>
              <Link
                href="/login"
                className="inline-flex w-full items-center justify-center rounded-lg border border-white/30 px-6 py-3.5 text-sm font-medium text-white transition hover:bg-white/10 sm:w-auto"
              >
                Log in
              </Link>
            </div>
          </div>
        </Reveal>
      </section>

      {/* Footer */}
      <footer className="border-t border-border px-6 py-8">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 sm:flex-row">
          <div className="flex items-center gap-2 text-ink-500">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/branding/tallia-icon-mark.svg" alt="" width={18} height={18} />
            <span className="text-xs">Powered by Ratel Systems</span>
          </div>
          <p className="text-xs text-ink-300">&copy; {new Date().getFullYear()} Ratel Systems. All rights reserved.</p>
        </div>
      </footer>
    </main>
  );
}
