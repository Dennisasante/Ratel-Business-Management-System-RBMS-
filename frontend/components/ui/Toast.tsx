"use client";

import { useEffect } from "react";
import { CheckCircle2 } from "lucide-react";

interface ToastProps {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  onDismiss: () => void;
  durationMs?: number;
}

// Fixed-position, self-dismissing — used for reversible actions (archive) where an
// inline "Undo" affordance beats a confirm-before-you-act dialog.
export default function Toast({ message, actionLabel, onAction, onDismiss, durationMs = 6000 }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(timer);
  }, [onDismiss, durationMs]);

  return (
    <div className="animate-toast-in fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 rounded-lg border border-border bg-ink-900 px-4 py-3 text-sm text-white shadow-panel">
      <CheckCircle2 size={16} className="shrink-0 text-success" />
      <span>{message}</span>
      {actionLabel && onAction && (
        <button
          onClick={() => {
            onAction();
            onDismiss();
          }}
          className="font-medium text-accent-soft hover:underline"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}
