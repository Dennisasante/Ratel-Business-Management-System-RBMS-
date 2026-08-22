"use client";

import { ReactNode } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  // Overrides the default max-w-md — e.g. "max-w-3xl" for content that needs
  // more room (a PDF preview, say) than the usual form-sized dialog.
  maxWidthClassName?: string;
}

// Portaled to document.body rather than rendered inline — a modal opened from
// inside a <form> (e.g. CustomerPicker's quick-add, used inside
// ServiceOrderForm/StaffBookingForm/the Sales cart) would otherwise nest its
// own <form> (CustomerForm) inside that outer <form>, which is invalid HTML.
// Browsers collapse that nesting unpredictably, so submitting the inner form
// can also trigger the outer form's submit — closing everything at once.
export default function Modal({ title, onClose, children, maxWidthClassName = "max-w-md" }: ModalProps) {
  return createPortal(
    <div
      className="animate-overlay-in fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      onClick={onClose}
    >
      <div
        className={`animate-modal-in max-h-[90vh] w-full ${maxWidthClassName} overflow-y-auto rounded-xl border border-border bg-surface p-6 shadow-panel`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-ink-900">{title}</h2>
          <button
            onClick={onClose}
            aria-label="Close"
            className="rounded-md p-1 text-ink-500 hover:bg-canvas hover:text-ink-900"
          >
            <X size={18} />
          </button>
        </div>
        <div className="mt-4">{children}</div>
      </div>
    </div>,
    document.body
  );
}
