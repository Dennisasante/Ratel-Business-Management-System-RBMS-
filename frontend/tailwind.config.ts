import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        canvas: "#F3F4F6", // cool neutral page background — not the warm-cream default
        surface: "#FFFFFF",
        border: {
          DEFAULT: "#E4E6EB",
          strong: "#D2D5DC",
        },
        ink: {
          900: "#14161A", // primary text
          700: "#3A3F4A",
          500: "#6B7280", // secondary text
          300: "#9AA0AC",
        },
        sidebar: {
          DEFAULT: "#1B1F27", // deep charcoal — the shell's dark half
          hover: "#262B35",
          border: "#2C313B",
          text: "#C6CAD3",
          "text-active": "#FFFFFF",
        },
        // Skulba brand palette: primary blue for primary actions/links/focus (the
        // "accent" token below is intentionally named for its Tailwind role — every
        // Button/Badge/focus-ring in the app already keys off `accent-*`, so retuning
        // its value here recolors the whole app without touching those call sites).
        accent: {
          DEFAULT: "#004aad",
          hover: "#003785",
          soft: "#E6EDF9",
        },
        // The brand's red — reserved for destructive actions (Cancel/Remove/Delete)
        // and the CANCELLED status pill, so it reads as "stop/attention" everywhere.
        danger: {
          DEFAULT: "#db1a1a",
          hover: "#b71414",
          soft: "#FBEAEA",
        },
        success: {
          DEFAULT: "#157F52",
          soft: "#E4F5EE",
        },
        // Teal rather than another blue — kept clearly apart from the primary
        // accent so status pills never fight the primary color.
        info: {
          DEFAULT: "#0E7C86",
          hover: "#0B6169",
          soft: "#E2F3F4",
        },
        // Fifth status hue (PICKED_UP) — violet reads as "closed out, in a good way"
        // without reusing green (COMPLETED) or blue (IN_PROGRESS).
        violet: {
          DEFAULT: "#6D28D9",
          hover: "#5b21b6",
          soft: "#EFE7FC",
        },
      },
      fontFamily: {
        sans: [
          "Inter",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
      },
      boxShadow: {
        card: "0 1px 2px rgba(20, 22, 26, 0.04), 0 1px 1px rgba(20, 22, 26, 0.03)",
        panel: "0 4px 16px rgba(20, 22, 26, 0.08)",
      },
      borderRadius: {
        lg: "10px",
        xl: "14px",
      },
    },
  },
  plugins: [],
};

export default config;
