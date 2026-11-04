/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        canvas: "#FAF8F4",
        surface: "#FFFFFF",
        ink: { DEFAULT: "#1C1B19", soft: "#55524C", faint: "#6F6B63" },
        line: { DEFAULT: "#E7E2D8", strong: "#D3CCBE" },
        accent: { DEFAULT: "#1F5C45", hover: "#184A37", soft: "#E6EFEA", ink: "#FFFFFF" },
        danger: { DEFAULT: "#B4232C", soft: "#FBEAEA" },
        caution: { DEFAULT: "#9A5B00", soft: "#FDF1DC" },
      },
      fontFamily: {
        sans: ["'Inter Variable'", "system-ui", "-apple-system", "Segoe UI", "sans-serif"],
      },
      borderRadius: { card: "14px" },
      boxShadow: { card: "0 1px 2px rgba(28,27,25,0.04), 0 1px 1px rgba(28,27,25,0.03)" },
      minHeight: { touch: "44px" },
      minWidth: { touch: "44px" },
    },
  },
  plugins: [],
};
