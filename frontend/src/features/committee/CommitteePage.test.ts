import { describe, expect, it } from "vitest";
import { currentMonth, meetsTarget } from "./CommitteePage";

describe("committee targets", () => {
  it("compares rates with their targets in the right direction", () => {
    expect(meetsTarget("92.0", "90", "atLeast")).toBe(true);
    expect(meetsTarget("69.8", "90", "atLeast")).toBe(false);
    expect(meetsTarget("7.9", "8", "below")).toBe(true);
    expect(meetsTarget("8.0", "8", "below")).toBe(false);
    expect(meetsTarget(null, "8", "below")).toBeNull();
  });

  it("uses the month in India, not the browser's zone", () => {
    expect(currentMonth(new Date("2026-10-31T19:00:00Z"))).toBe("2026-11");
    expect(currentMonth(new Date("2026-10-31T18:00:00Z"))).toBe("2026-10");
  });
});
