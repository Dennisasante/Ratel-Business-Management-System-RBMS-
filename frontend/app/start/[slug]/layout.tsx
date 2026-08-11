import type { Metadata } from "next";

// A thin server component whose only job is generateMetadata — the page
// itself is a client component and can't export metadata directly. Overrides
// the root layout's generic /manifest.json with this business's own, so
// installing from here lands back on /start/{slug}, not the dashboard.
export async function generateMetadata({ params }: { params: { slug: string } }): Promise<Metadata> {
  return { manifest: `/start/${params.slug}/manifest.webmanifest` };
}

export default function StartSlugLayout({ children }: { children: React.ReactNode }) {
  return children;
}
