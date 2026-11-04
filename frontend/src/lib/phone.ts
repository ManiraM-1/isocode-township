export const PHONE_PREFIX = "+91";

export function phoneDigits(input: string): string {
  let digits = input.replace(/\D/g, "");
  if (digits.length === 12 && digits.startsWith("91")) {
    digits = digits.slice(2);
  } else if (digits.length === 11 && digits.startsWith("0")) {
    digits = digits.slice(1);
  }
  return digits;
}

export function phoneError(input: string): string | null {
  const digits = phoneDigits(input);
  if (digits.length === 0) return "Enter your mobile number";
  if (digits.length !== 10) return "Enter a 10-digit Indian mobile number";
  if (!/^[6-9]/.test(digits)) return "Indian mobile numbers start with 6, 7, 8 or 9";
  return null;
}

export function toE164(input: string): string {
  return PHONE_PREFIX + phoneDigits(input);
}

export function maskPhone(e164: string): string {
  const digits = phoneDigits(e164);
  return `${PHONE_PREFIX} ${digits.slice(0, 2)}••• ••${digits.slice(-3)}`;
}
