import { ReactNode } from "react";
import { LucideIcon } from "lucide-react";
import Card from "./Card";

type StatTone = "accent" | "success" | "danger" | "info";

const TONE_CLASSES: Record<StatTone, { bar: string; iconBg: string; icon: string }> = {
  accent: { bar: "bg-accent", iconBg: "bg-accent-soft", icon: "text-accent-hover" },
  success: { bar: "bg-success", iconBg: "bg-success-soft", icon: "text-success" },
  danger: { bar: "bg-danger", iconBg: "bg-danger-soft", icon: "text-danger" },
  info: { bar: "bg-info", iconBg: "bg-info-soft", icon: "text-info" },
};

export default function StatCard({
  label,
  value,
  hint,
  icon: Icon,
  tone = "accent",
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  icon: LucideIcon;
  tone?: StatTone;
}) {
  const t = TONE_CLASSES[tone];
  return (
    <Card className="relative overflow-hidden p-5 transition hover:shadow-panel">
      <span className={`absolute inset-y-0 left-0 w-1 ${t.bar}`} aria-hidden />
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-ink-500">{label}</p>
          <p className="tabular mt-1 text-2xl font-semibold text-ink-900">{value}</p>
          {hint && <p className="mt-1 text-xs text-ink-500">{hint}</p>}
        </div>
        <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${t.iconBg} ${t.icon} shadow-sm`}>
          <Icon size={20} strokeWidth={1.75} />
        </div>
      </div>
    </Card>
  );
}
