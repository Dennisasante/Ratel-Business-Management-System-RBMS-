import { PlatformAuthProvider } from "@/lib/platformAuth";

export default function PlatformLayout({ children }: { children: React.ReactNode }) {
  return <PlatformAuthProvider>{children}</PlatformAuthProvider>;
}
