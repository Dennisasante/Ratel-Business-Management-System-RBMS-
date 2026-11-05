"use client";

import { KeyboardEvent, ReactNode, useRef } from "react";
import { LucideIcon } from "lucide-react";

export interface TabItem {
  id: string;
  label: string;
  icon?: LucideIcon;
  // Optional small count/label shown after the name, e.g. a number of unread items.
  badge?: ReactNode;
}

/**
 * An underlined tab bar. Pair each tab with a panel that has
 * role="tabpanel", id={tabPanelId(id)} and aria-labelledby={tabButtonId(id)}.
 * Scrolls sideways on a narrow screen rather than wrapping, and supports
 * Left/Right/Home/End from the keyboard.
 */
export const tabButtonId = (id: string) => `tab-${id}`;
export const tabPanelId = (id: string) => `tabpanel-${id}`;

export default function Tabs({
  tabs,
  active,
  onChange,
  ariaLabel,
}: {
  tabs: TabItem[];
  active: string;
  onChange: (id: string) => void;
  ariaLabel: string;
}) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  function handleKeyDown(e: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = -1;
    if (e.key === "ArrowRight") next = (index + 1) % tabs.length;
    else if (e.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = tabs.length - 1;
    if (next === -1) return;
    e.preventDefault();
    onChange(tabs[next].id);
    refs.current[tabs[next].id]?.focus();
  }

  return (
    <div role="tablist" aria-label={ariaLabel} className="flex gap-1 overflow-x-auto border-b border-border">
      {tabs.map((tab, index) => {
        const selected = tab.id === active;
        const Icon = tab.icon;
        return (
          <button
            key={tab.id}
            ref={(el) => {
              refs.current[tab.id] = el;
            }}
            id={tabButtonId(tab.id)}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={tabPanelId(tab.id)}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.id)}
            onKeyDown={(e) => handleKeyDown(e, index)}
            className={`-mb-px flex shrink-0 items-center gap-1.5 whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition ${
              selected ? "border-accent text-accent-hover" : "border-transparent text-ink-500 hover:text-ink-900"
            }`}
          >
            {Icon && <Icon size={15} />}
            {tab.label}
            {tab.badge != null && <span className="rounded-full bg-canvas px-1.5 text-xs text-ink-700">{tab.badge}</span>}
          </button>
        );
      })}
    </div>
  );
}
