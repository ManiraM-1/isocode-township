import { describe, expect, it } from "vitest";
import { classificationLabel, describeEvent, fitWithin } from "./complaints";
import { TimelineEntry } from "./types";

function entry(type: string, from: string | null = null, to: string | null = null): TimelineEntry {
  return { type, from, to, actorName: "Anjali Deshmukh", actorRole: "HELPDESK", note: null, at: "2026-11-03T04:30:00Z" };
}

describe("photo resize", () => {
  it("keeps the longest side within the limit and the aspect ratio", () => {
    expect(fitWithin(4032, 3024, 1600)).toEqual({ width: 1600, height: 1200 });
    expect(fitWithin(3024, 4032, 1600)).toEqual({ width: 1200, height: 1600 });
    expect(fitWithin(1200, 900, 1600)).toEqual({ width: 1200, height: 900 });
  });
});

describe("timeline wording", () => {
  it("describes category corrections in plain words", () => {
    expect(describeEvent(entry("CATEGORY_CHANGED", "OTHER/MEDIUM", "PLUMBING/HIGH"))).toBe(
      "Category changed to Plumbing, high",
    );
    expect(describeEvent(entry("CATEGORY_CHANGED", "LIFT/HIGH", "LIFT/HIGH"))).toBe("Category confirmed: Lift, high");
    expect(classificationLabel("PEST_CONTROL/LOW")).toBe("Pest control, low");
  });

  it("describes status and assignment changes", () => {
    expect(describeEvent(entry("STATUS_CHANGED", "ASSIGNED", "IN_PROGRESS"))).toBe("Marked in progress");
    expect(describeEvent(entry("AUTO_ASSIGNED", null, "Ravi Patil"))).toBe("Assigned to Ravi Patil");
    expect(describeEvent(entry("LINKED", null, "CMP-2611-0004"))).toBe("Linked with CMP-2611-0004");
  });
});
