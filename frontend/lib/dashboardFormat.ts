// Small shared formatters for the Dashboard's financial cards/tables — kept
// in one place so "GH₵0.00"/"20.0%" read identically everywhere on the page
// (see the Dashboard spec's own "consistent formatting" requirement).

export function formatGHS(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "GH₵0.00";
  return `GH₵${value.toFixed(2)}`;
}

/** Null means "not computable" (e.g. zero revenue) — rendered as an em dash, never a misleading 0%. */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return `${value.toFixed(1)}%`;
}
