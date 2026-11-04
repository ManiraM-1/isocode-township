import { describe, expect, it } from "vitest";
import { decisionLabel } from "./Badge";
import { Decision } from "../lib/types";

const BACKEND_DECISIONS: Decision[] = [
  "DUPLICATE",
  "NON_CREDIT",
  "POSSIBLE_DUPLICATE",
  "RECONCILED_GATEWAY",
  "CONFLICT",
  "AUTO_POSTED",
  "SUGGESTED",
  "UNMATCHED",
  "PARSE_ERROR",
];

describe("match decision labels", () => {
  it("has a readable label for every decision the backend stores", () => {
    for (const decision of BACKEND_DECISIONS) {
      expect(decisionLabel(decision)).not.toBe(decision);
    }
    expect(decisionLabel("RECONCILED_GATEWAY")).toBe("Reconciled");
  });

  it("falls back to the raw value instead of crashing", () => {
    expect(decisionLabel("SOMETHING_NEW" as Decision)).toBe("SOMETHING_NEW");
  });
});
