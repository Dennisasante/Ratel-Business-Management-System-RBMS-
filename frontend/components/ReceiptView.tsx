"use client";

import { useState } from "react";
import { Printer } from "lucide-react";
import PoweredByRatel from "@/components/PoweredByRatel";

type PaperWidth = "58" | "80";

// mm-accurate widths at print time (via the injected @page rule below); the
// pixel values are just a same-ratio on-screen preview so the toggle is
// visibly meaningful before you ever hit print.
const PAPER_PX: Record<PaperWidth, number> = { "58": 220, "80": 300 };

interface ReceiptLineItem {
  name: string;
  quantity: number;
  unitPrice: number;
  discountAmount?: number;
  subtotal: number;
}

interface ReceiptViewProps {
  businessName: string;
  businessLogoUrl?: string | null;
  businessLocation?: string | null;
  businessContactPhone?: string | null;
  documentLabel: string; // e.g. "Receipt #204" or "Service Order #12"
  timestamp: string; // ISO
  metaLines: string[]; // e.g. ["Served by: Ama", "Customer: Walk-in"]
  items: ReceiptLineItem[];
  total: number;
  footerLines?: string[]; // e.g. ["Payment: CASH"]
  currencySymbol?: string;
}

export default function ReceiptView({
  businessName,
  businessLogoUrl,
  businessLocation,
  businessContactPhone,
  documentLabel,
  timestamp,
  metaLines,
  items,
  total,
  footerLines = [],
  currencySymbol = "GH₵",
}: ReceiptViewProps) {
  const [width, setWidth] = useState<PaperWidth>("80");

  return (
    <div className="flex min-h-screen flex-col items-center bg-canvas py-8 print:bg-white print:py-0">
      {/* Controls — never printed */}
      <div className="mb-6 flex items-center gap-3 print:hidden">
        <div className="flex rounded-lg border border-border bg-surface p-0.5 text-sm">
          <button
            onClick={() => setWidth("58")}
            className={`rounded-md px-3 py-1.5 font-medium transition ${
              width === "58" ? "bg-accent-soft text-accent-hover" : "text-ink-500 hover:text-ink-900"
            }`}
          >
            58mm
          </button>
          <button
            onClick={() => setWidth("80")}
            className={`rounded-md px-3 py-1.5 font-medium transition ${
              width === "80" ? "bg-accent-soft text-accent-hover" : "text-ink-500 hover:text-ink-900"
            }`}
          >
            80mm
          </button>
        </div>
        <button
          onClick={() => window.print()}
          className="inline-flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white shadow-card transition hover:bg-accent-hover"
        >
          <Printer size={16} /> Print receipt
        </button>
      </div>

      {/* @page controls the printed page's physical size for this width */}
      <style>{`@page { size: ${width}mm auto; margin: 0; }`}</style>

      <div
        className="bg-white font-mono text-[11px] leading-relaxed text-black shadow-card print:shadow-none"
        style={{ width: PAPER_PX[width], padding: "12px 10px" }}
      >
        <div className="flex flex-col items-center text-center">
          {businessLogoUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={businessLogoUrl} alt="" className="mb-1.5 h-10 w-10 rounded object-cover" />
          )}
          <p className="text-sm font-bold">{businessName}</p>
          {businessLocation && <p>{businessLocation}</p>}
          {businessContactPhone && <p>{businessContactPhone}</p>}
        </div>

        <Divider />

        <p>{documentLabel}</p>
        {metaLines.map((line) => (
          <p key={line}>{line}</p>
        ))}
        <p>{new Date(timestamp).toLocaleString()}</p>

        <Divider />

        {items.map((item, i) => (
          <div key={i} className="mb-1">
            <div className="flex justify-between">
              <span>{item.name}</span>
            </div>
            <div className="flex justify-between text-black/70">
              <span>
                {item.quantity} × {currencySymbol}
                {item.unitPrice.toFixed(2)}
              </span>
              <span>
                {currencySymbol}
                {item.subtotal.toFixed(2)}
              </span>
            </div>
            {!!item.discountAmount && item.discountAmount > 0 && (
              <div className="flex justify-between text-black/70">
                <span>Discount</span>
                <span>
                  -{currencySymbol}
                  {item.discountAmount.toFixed(2)}
                </span>
              </div>
            )}
          </div>
        ))}

        <Divider />

        <div className="flex justify-between text-sm font-bold">
          <span>TOTAL</span>
          <span>
            {currencySymbol}
            {total.toFixed(2)}
          </span>
        </div>

        {footerLines.length > 0 && (
          <>
            <Divider />
            {footerLines.map((line) => (
              <p key={line}>{line}</p>
            ))}
          </>
        )}

        <Divider />
        <p className="text-center">Thank you!</p>
        <div className="mt-3 flex justify-center opacity-60">
          <PoweredByRatel className="text-[9px]" iconSize={10} />
        </div>
      </div>
    </div>
  );
}

function Divider() {
  return <div className="my-2 border-t border-dashed border-black/40" />;
}
