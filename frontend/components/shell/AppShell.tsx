"use client";

import { useState, useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import Sidebar from "./Sidebar";
import Topbar from "./Topbar";
import ReadOnlyBanner from "./ReadOnlyBanner";
import OnboardingTour from "@/components/OnboardingTour";

// Pages built around a wide, many-column table (Service Orders being the
// worst offender — 8 columns including an Actions cell) get more room than
// the standard reading-width container; form-centric pages stay narrow and
// centered, which reads better for those.
const WIDE_PATH_PREFIXES = [
  "/dashboard/service-orders",
  "/dashboard/sales",
  "/dashboard/purchase-orders",
  "/dashboard/payments",
  "/dashboard/customers",
  "/dashboard/inventory",
  "/dashboard/bookings",
];

export default function AppShell({ children }: { children: React.ReactNode }) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const pathname = usePathname();
  const router = useRouter();
  const { session } = useAuth();
  const isWide = WIDE_PATH_PREFIXES.some((prefix) => pathname?.startsWith(prefix));

  // Close the mobile drawer automatically whenever the route changes.
  useEffect(() => {
    setDrawerOpen(false);
  }, [pathname]);

  // Staff logging in with a temporary password (or after a Super Admin reset)
  // can't use anything else until they set their own password.
  useEffect(() => {
    if (session?.mustChangePassword && pathname !== "/dashboard/change-password") {
      router.replace("/dashboard/change-password");
    }
  }, [session, pathname, router]);

  return (
    <div className="flex h-screen overflow-hidden bg-canvas">
      <OnboardingTour />
      {/* Desktop sidebar */}
      <aside className="hidden w-64 shrink-0 lg:block">
        <Sidebar />
      </aside>

      {/* Mobile drawer */}
      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setDrawerOpen(false)}
            aria-hidden
          />
          <div className="absolute inset-y-0 left-0 w-64 shadow-panel">
            <Sidebar onNavigate={() => setDrawerOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onMenuClick={() => setDrawerOpen(true)} />
        <ReadOnlyBanner />
        <main className="flex-1 overflow-y-auto px-4 py-6 sm:px-6 lg:px-8">
          <div className={`mx-auto ${isWide ? "max-w-screen-2xl" : "max-w-6xl"}`}>{children}</div>
        </main>
      </div>
    </div>
  );
}
