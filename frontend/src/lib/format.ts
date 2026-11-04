const ZONE = "Asia/Kolkata";

export function groupIndian(integerDigits: string): string {
  if (integerDigits.length <= 3) {
    return integerDigits;
  }
  const lastThree = integerDigits.slice(-3);
  const rest = integerDigits.slice(0, -3);
  return rest.replace(/\B(?=(\d{2})+(?!\d))/g, ",") + "," + lastThree;
}

export function formatRupees(amount: string | number | null | undefined, options: { symbol?: boolean } = {}): string {
  if (amount === null || amount === undefined || amount === "") {
    return "—";
  }
  const text = typeof amount === "number" ? amount.toFixed(2) : amount.trim();
  const match = /^(-)?(\d+)(?:\.(\d+))?$/.exec(text);
  if (!match) {
    return text;
  }
  const [, minus, integer, fraction = ""] = match;
  const paise = (fraction + "00").slice(0, 2);
  const symbol = options.symbol === false ? "" : "₹";
  const sign = minus && /[1-9]/.test(integer + paise) ? "−" : "";
  return `${sign}${symbol}${groupIndian(integer.replace(/^0+(?=\d)/, ""))}.${paise}`;
}

export function isNegative(amount: string | null | undefined): boolean {
  return !!amount && amount.trim().startsWith("-") && /[1-9]/.test(amount);
}

export function isPositive(amount: string | null | undefined): boolean {
  return !!amount && !amount.trim().startsWith("-") && /[1-9]/.test(amount);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = value.length === 10 ? new Date(value + "T00:00:00+05:30") : new Date(value);
  return date.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: ZONE });
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  return new Date(value).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
    timeZone: ZONE,
  });
}

export function formatMonth(month: string): string {
  const [year, monthNumber] = month.split("-").map(Number);
  return new Date(Date.UTC(year, monthNumber - 1, 1)).toLocaleDateString("en-IN", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

export function timeSince(value: string, now: Date = new Date()): string {
  const minutes = Math.max(0, Math.round((now.getTime() - new Date(value).getTime()) / 60000));
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  return days === 1 ? "1 day ago" : `${days} days ago`;
}

export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) return "—";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours < 48) return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
  const days = Math.floor(hours / 24);
  const restHours = hours % 24;
  return restHours === 0 ? `${days} days` : `${days} days ${restHours} h`;
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((word, index) => (index === 0 ? word.charAt(0).toUpperCase() + word.slice(1) : word))
    .join(" ");
}
