import type { Metadata } from "next";

// A thin server component whose only job is generateMetadata — the page
// itself is a client component and can't export metadata directly. Overrides
// the root layout's generic /manifest.json with this business's own, so
// installing from here lands back on /book/{slug}, not the dashboard.
export async function generateMetadata({ params }: { params: { slug: string } }): Promise<Metadata> {
  return { manifest: `/book/${params.slug}/manifest.webmanifest` };
}

export default function BookSlugLayout({ children }: { children: React.ReactNode }) {
  return children;
}
