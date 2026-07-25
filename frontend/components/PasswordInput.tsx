"use client";

import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";

// For the handful of raw (non-FormField) password inputs, mostly in the platform
// admin pages, which use different label/input theming than the business dashboard.
interface PasswordInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  placeholder?: string;
  inputClassName: string;
  iconClassName?: string;
  labelClassName?: string;
}

export default function PasswordInput({
  label,
  value,
  onChange,
  required = false,
  placeholder,
  inputClassName,
  iconClassName = "text-ink-500 hover:text-ink-900",
  labelClassName = "text-sm font-medium text-ink-700",
}: PasswordInputProps) {
  const [revealed, setRevealed] = useState(false);

  return (
    <div className="flex flex-col gap-1.5">
      <label className={labelClassName}>{label}</label>
      <div className="relative">
        <input
          type={revealed ? "text" : "password"}
          required={required}
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
          className={`w-full pr-10 ${inputClassName}`}
        />
        <button
          type="button"
          onClick={() => setRevealed((v) => !v)}
          className={`absolute inset-y-0 right-0 flex items-center px-3 ${iconClassName}`}
          aria-label={revealed ? "Hide password" : "Show password"}
          tabIndex={-1}
        >
          {revealed ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
    </div>
  );
}
