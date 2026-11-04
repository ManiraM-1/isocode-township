import { describe, expect, it } from "vitest";
import { amountError } from "./PayPage";

describe("payment amount", () => {
  it("accepts rupees with up to two decimals, with or without grouping", () => {
    expect(amountError("5660.00")).toBeNull();
    expect(amountError("1,23,456.5")).toBeNull();
    expect(amountError("500")).toBeNull();
  });

  it("rejects empty, zero, negative and over-precise amounts", () => {
    expect(amountError("")).toBe("Enter an amount");
    expect(amountError("0.00")).toBe("Amount must be more than zero");
    expect(amountError("-5")).toBe("Enter an amount in rupees, with at most two decimals");
    expect(amountError("10.005")).toBe("Enter an amount in rupees, with at most two decimals");
  });
});
