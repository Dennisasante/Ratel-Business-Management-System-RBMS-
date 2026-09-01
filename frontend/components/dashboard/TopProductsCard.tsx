"use client";

import { useState } from "react";
import Link from "next/link";
import Card from "@/components/ui/Card";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";
import { formatGHS, formatPercent } from "@/lib/dashboardFormat";
import { TopProduct, TopProductRankMetric } from "@/lib/api";

const RANK_OPTIONS: { key: TopProductRankMetric; label: string }[] = [
  { key: "REVENUE", label: "Revenue" },
  { key: "UNITS_SOLD", label: "Units sold" },
  { key: "GROSS_PROFIT", label: "Gross profit" },
  { key: "MARGIN", label: "Margin" },
];

/** Section 4 — ranks by whichever metric is selected; parent refetches on change (see Dashboard page). */
export default function TopProductsCard({
  products,
  rankBy,
  onRankByChange,
}: {
  products: TopProduct[] | null;
  rankBy: TopProductRankMetric;
  onRankByChange: (metric: TopProductRankMetric) => void;
}) {
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-ink-900">Top products</h2>
          <p className="mt-0.5 text-xs text-ink-500">Completed sales only, for the selected period.</p>
        </div>
        <select
          value={rankBy}
          onChange={(e) => onRankByChange(e.target.value as TopProductRankMetric)}
          className="rounded-lg border border-border bg-surface px-2.5 py-1.5 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        >
          {RANK_OPTIONS.map((o) => (
            <option key={o.key} value={o.key}>
              By {o.label.toLowerCase()}
            </option>
          ))}
        </select>
      </div>

      {!products ? (
        <div className="mt-4 animate-pulse space-y-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-9 rounded-lg bg-canvas" />
          ))}
        </div>
      ) : products.length === 0 ? (
        <p className="mt-4 text-sm text-ink-500">No product sales in this period yet.</p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <Table>
            <THead>
              <Tr>
                <Th>Product</Th>
                <Th>Units</Th>
                <Th>Revenue</Th>
                <Th>Gross profit</Th>
                <Th>Margin</Th>
              </Tr>
            </THead>
            <TBody>
              {products.map((p) => (
                <Tr key={p.productId}>
                  <Td className="font-medium text-ink-900">
                    <Link href="/dashboard/inventory" className="hover:underline">
                      {p.productName}
                    </Link>
                  </Td>
                  <Td className="tabular">{p.unitsSold}</Td>
                  <Td className="tabular">{formatGHS(p.revenue)}</Td>
                  <Td className="tabular">{p.grossProfit === null ? "—" : formatGHS(p.grossProfit)}</Td>
                  <Td className="tabular">{formatPercent(p.grossMarginPercent)}</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        </div>
      )}
    </Card>
  );
}
