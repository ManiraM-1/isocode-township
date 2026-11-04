import { formatRupees } from "../lib/format";

type MoneyProps = {
  amount: string | number | null | undefined;
  className?: string;
  symbol?: boolean;
  align?: "right" | "left";
};

export function Money({ amount, className = "", symbol = true, align = "right" }: MoneyProps) {
  return (
    <span className={`tabular-nums ${align === "right" ? "text-right" : ""} ${className}`}>
      {formatRupees(amount, { symbol })}
    </span>
  );
}
