import { describe, expect, it } from "vitest";
import { formatMinutes, formatRupees, groupIndian } from "./format";

describe("formatRupees", () => {
  it("uses Indian grouping with two decimals", () => {
    expect(formatRupees("123456.00")).toBe("₹1,23,456.00");
    expect(formatRupees("12345678.5")).toBe("₹1,23,45,678.50");
    expect(formatRupees("999.99")).toBe("₹999.99");
    expect(formatRupees("1000")).toBe("₹1,000.00");
  });

  it("shows credit with a minus sign and zero without one", () => {
    expect(formatRupees("-2000.00")).toBe("−₹2,000.00");
    expect(formatRupees("-0.00")).toBe("₹0.00");
    expect(formatRupees("0")).toBe("₹0.00");
  });

  it("never goes through floating point for strings", () => {
    expect(formatRupees("10000000000.07")).toBe("₹10,00,00,00,000.07");
  });

  it("can drop the symbol and shows a dash for missing values", () => {
    expect(formatRupees("5660.00", { symbol: false })).toBe("5,660.00");
    expect(formatRupees(null)).toBe("—");
  });

  it("groups digits the Indian way", () => {
    expect(groupIndian("100000")).toBe("1,00,000");
    expect(groupIndian("1000")).toBe("1,000");
  });
});

describe("formatMinutes", () => {
  it("reads naturally", () => {
    expect(formatMinutes(45)).toBe("45 min");
    expect(formatMinutes(95)).toBe("1 h 35 min");
    expect(formatMinutes(1440)).toBe("24 h");
    expect(formatMinutes(4320)).toBe("3 days");
    expect(formatMinutes(null)).toBe("—");
  });
});
