// Shared date-range preset logic used by every list page's date filter
// (Sales, Activity Log, Payment Transactions, and — going forward — every
// other filterable list). One source of truth for what "Today"/"This week"/
// etc. actually mean, so every page buckets dates identically.
//
// Dates are UTC calendar days throughout (matching this app's existing
// convention elsewhere — e.g. the digest cron's own "no offset needed for
// Ghana" reasoning) — never the browser's local timezone, so a page looks
// the same regardless of which timezone the person viewing it is in.
// "from"/"to" are plain YYYY-MM-DD strings (or null for "all time"),
// matching the LocalDate query params every date-filterable endpoint
// already accepts (see ActivityLogController/PlatformBusinessController).

export type DateRangePreset =
  | "today"
  | "yesterday"
  | "thisWeek"
  | "last7Days"
  | "thisMonth"
  | "last30Days"
  | "last90Days"
  | "all"
  | "custom";

export interface DateRangeValue {
  preset: DateRangePreset;
  from: string | null;
  to: string | null;
}

export const DATE_RANGE_PRESETS: { key: DateRangePreset; label: string }[] = [
  { key: "today", label: "Today" },
  { key: "yesterday", label: "Yesterday" },
  { key: "thisWeek", label: "This week" },
  { key: "last7Days", label: "Last 7 days" },
  { key: "thisMonth", label: "This month" },
  { key: "last30Days", label: "Last 30 days" },
  { key: "last90Days", label: "Last 90 days" },
  { key: "all", label: "All time" },
  { key: "custom", label: "Custom range" },
];

function toDateStr(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function todayUtc(): Date {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
}

/** Computes the {from, to} calendar-date bounds (inclusive) for a preset. Custom passes its own from/to straight through. */
export function computeDateRange(
  preset: DateRangePreset,
  custom?: { from: string | null; to: string | null }
): { from: string | null; to: string | null } {
  const today = todayUtc();
  const todayStr = toDateStr(today);

  switch (preset) {
    case "today":
      return { from: todayStr, to: todayStr };
    case "yesterday": {
      const d = new Date(today);
      d.setUTCDate(d.getUTCDate() - 1);
      const s = toDateStr(d);
      return { from: s, to: s };
    }
    case "thisWeek": {
      // Monday-start week, matching this app's existing weekly-digest convention.
      const dayOfWeek = today.getUTCDay(); // 0 = Sunday
      const daysSinceMonday = (dayOfWeek + 6) % 7;
      const monday = new Date(today);
      monday.setUTCDate(today.getUTCDate() - daysSinceMonday);
      return { from: toDateStr(monday), to: todayStr };
    }
    case "last7Days": {
      const d = new Date(today);
      d.setUTCDate(d.getUTCDate() - 6);
      return { from: toDateStr(d), to: todayStr };
    }
    case "thisMonth": {
      const d = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 1));
      return { from: toDateStr(d), to: todayStr };
    }
    case "last30Days": {
      const d = new Date(today);
      d.setUTCDate(d.getUTCDate() - 29);
      return { from: toDateStr(d), to: todayStr };
    }
    case "last90Days": {
      const d = new Date(today);
      d.setUTCDate(d.getUTCDate() - 89);
      return { from: toDateStr(d), to: todayStr };
    }
    case "all":
      return { from: null, to: null };
    case "custom":
      return { from: custom?.from ?? null, to: custom?.to ?? null };
  }
}

/** The value every page should start with — Today, never the unfiltered full list. */
export function defaultDateRangeValue(): DateRangeValue {
  const { from, to } = computeDateRange("today");
  return { preset: "today", from, to };
}
