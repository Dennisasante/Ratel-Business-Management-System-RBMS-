import { ReactNode } from "react";

type BadgeTone = "neutral" | "accent" | "success" | "danger" | "info" | "violet";

const TONE_CLASSES: Record<BadgeTone, string> = {
  neutral: "bg-canvas text-ink-700 border-border",
  accent: "bg-accent-soft text-accent-hover border-transparent",
  success: "bg-success-soft text-success border-transparent",
  danger: "bg-danger-soft text-danger border-transparent",
  info: "bg-info-soft text-info border-transparent",
  violet: "bg-violet-soft text-violet border-transparent",
};

export default function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: BadgeTone;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium transition-colors duration-300 ${TONE_CLASSES[tone]}`}
    >
      {children}
    </span>
  );
}
