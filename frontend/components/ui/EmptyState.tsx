import { ReactNode } from "react";
import { LucideIcon } from "lucide-react";

export default function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 px-6 py-16 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-accent-soft text-accent-hover">
        <Icon size={22} strokeWidth={1.75} />
      </div>
      <div>
        <p className="font-medium text-ink-900">{title}</p>
        <p className="mt-1 text-sm text-ink-500">{description}</p>
      </div>
      {action}
    </div>
  );
}
