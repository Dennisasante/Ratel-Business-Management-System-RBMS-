"use client";

import { Calendar } from "lucide-react";
import { DATE_RANGE_PRESETS, DateRangePreset, DateRangeValue, computeDateRange } from "@/lib/dateRangePresets";

const inputClass =
  "rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20";

/**
 * The one date-range filter control every list page uses — Today/Yesterday/
 * This week/Last 7 days/This month/Last 30 days/Last 90 days/All time/Custom,
 * rendered as pill buttons (matching the AI dashboard's own TabChip look).
 * Every page starts on "Today" (see defaultDateRangeValue) rather than an
 * unfiltered full list.
 */
export default function DateRangeFilter({
  value,
  onChange,
}: {
  value: DateRangeValue;
  onChange: (value: DateRangeValue) => void;
}) {
  function selectPreset(preset: DateRangePreset) {
    if (preset === "custom") {
      // Seed the custom inputs with the currently-active range so switching
      // to "Custom" doesn't blank them out from under you.
      onChange({ preset, from: value.from, to: value.to });
      return;
    }
    const { from, to } = computeDateRange(preset);
    onChange({ preset, from, to });
  }

  return (
    <div className="flex flex-col gap-2">
      {/* One scrollable line, even on a narrow phone — never wraps to a second
          row. Scrollbar hidden (still swipeable/scrollable) so it reads as a
          single clean strip of pills rather than a visible scroll widget. */}
      <div className="flex items-center gap-2 overflow-x-auto whitespace-nowrap pb-0.5 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {DATE_RANGE_PRESETS.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            onClick={() => selectPreset(key)}
            className={`flex shrink-0 items-center gap-1 rounded-full border px-3 py-1 text-xs font-medium transition ${
              value.preset === key
                ? "border-accent bg-accent-soft text-accent-hover"
                : "border-border bg-surface text-ink-700 hover:border-border-strong"
            }`}
          >
            {key === "custom" && <Calendar size={12} />}
            {label}
          </button>
        ))}
      </div>
      {value.preset === "custom" && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="date"
            value={value.from ?? ""}
            onChange={(e) => onChange({ preset: "custom", from: e.target.value || null, to: value.to })}
            className={inputClass}
          />
          <span className="text-xs text-ink-500">to</span>
          <input
            type="date"
            value={value.to ?? ""}
            onChange={(e) => onChange({ preset: "custom", from: value.from, to: e.target.value || null })}
            className={inputClass}
          />
        </div>
      )}
    </div>
  );
}
