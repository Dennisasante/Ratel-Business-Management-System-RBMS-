// Shared "Powered by Ratel Systems" mark — used in the sidebar footer and on
// printed receipts, so the platform keeps a quiet presence without competing
// with the business's own branding.
export default function PoweredByRatel({ className = "", iconSize = 14 }: { className?: string; iconSize?: number }) {
  return (
    <div className={`flex items-center gap-1.5 ${className}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src="/branding/ratel-icon.png"
        alt=""
        width={iconSize}
        height={iconSize}
        className="shrink-0"
        style={{ width: iconSize, height: iconSize }}
      />
      <span>Powered by Ratel Systems</span>
    </div>
  );
}
