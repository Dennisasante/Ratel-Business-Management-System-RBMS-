import type { Metadata } from "next";
import { PlatformAuthProvider } from "@/lib/platformAuth";

// Overrides the root manifest (which points start_url at /dashboard, the
// business-owner experience) so Super Admin can install /platform as its
// own separate app instead of inheriting the wrong launch target.
export const metadata: Metadata = {
  title: "Tallia Super Admin",
  manifest: "/platform-manifest.json",
};

export default function PlatformLayout({ children }: { children: React.ReactNode }) {
  return <PlatformAuthProvider>{children}</PlatformAuthProvider>;
}
