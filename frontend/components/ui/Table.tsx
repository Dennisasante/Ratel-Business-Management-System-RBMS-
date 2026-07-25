import { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from "react";

export function Table({ children }: { children: React.ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-left text-sm">{children}</table>
    </div>
  );
}

export function THead({ children }: { children: React.ReactNode }) {
  return <thead className="border-b border-border bg-canvas text-xs uppercase tracking-wide text-ink-500">{children}</thead>;
}

export function TBody({ children }: { children: React.ReactNode }) {
  return <tbody className="divide-y divide-border">{children}</tbody>;
}

export function Tr({ className = "", ...props }: HTMLAttributes<HTMLTableRowElement>) {
  // animate-row-in only plays on first mount (new rows), since React never remounts
  // an existing row it can match by key — so this fades in newly-added rows without
  // replaying on every re-render of rows that were already there.
  return <tr className={`animate-row-in transition-colors hover:bg-canvas/60 ${className}`} {...props} />;
}

export function Th({ className = "", ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th className={`px-4 py-3 font-medium ${className}`} {...props} />;
}

export function Td({ className = "", ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={`px-4 py-3 align-middle text-ink-900 ${className}`} {...props} />;
}
