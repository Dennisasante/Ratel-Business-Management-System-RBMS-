import { NextResponse } from "next/server";

// Next 14.2's nested manifest.ts file convention turned out not to be wired
// up for dynamic segments (confirmed live — it 404s), so this is a plain
// Route Handler instead, linked in from layout.tsx's generateMetadata.
// Runs server-side, so it can't reuse lib/api.ts's request() helper (that
// issues relative fetch()es meant to be rewritten by middleware.ts in the
// browser) — talk to the backend directly, same base URL middleware.ts
// itself resolves to.
const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8090";

export async function GET(_request: Request, { params }: { params: { slug: string } }) {
  let businessName = "Ratel";
  try {
    const res = await fetch(`${API_BASE}/api/public/start/by-slug/${params.slug}`, { cache: "no-store" });
    if (res.ok) {
      const config = await res.json();
      businessName = config.businessName;
    }
  } catch {
    // Network hiccup or backend down — fall back to the generic name below
    // rather than failing the manifest request outright.
  }

  return NextResponse.json(
    {
      name: businessName,
      short_name: businessName,
      description: `${businessName} on Ratel.`,
      start_url: `/start/${params.slug}`,
      scope: `/start/${params.slug}`,
      display: "standalone",
      orientation: "any",
      background_color: "#fdfaf6",
      theme_color: "#a76545",
      icons: [
        { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
        { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
        { src: "/icons/icon-maskable-192.png", sizes: "192x192", type: "image/png", purpose: "maskable" },
        { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
      ],
    },
    { headers: { "Content-Type": "application/manifest+json" } }
  );
}
