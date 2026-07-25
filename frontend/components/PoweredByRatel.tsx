// Shared "Powered by Ratel" mark — used in the sidebar footer and on printed
// receipts, so the platform keeps a quiet presence without competing with the
// business's own branding. Swap /public/branding/ratel-logo.svg for the real
// Ratel logo whenever it's ready; nothing else needs to change.
export default function PoweredByRatel({ className = "", iconSize = 14 }: { className?: string; iconSize?: number }) {
  return (
    <div className={`flex items-center gap-1.5 ${className}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src="/branding/ratel-logo.svg"
        alt=""
        width={iconSize}
        height={iconSize}
        className="shrink-0 rounded-sm"
        style={{ width: iconSize, height: iconSize }}
      />
      <span>Powered by Ratel</span>
    </div>
  );
}
