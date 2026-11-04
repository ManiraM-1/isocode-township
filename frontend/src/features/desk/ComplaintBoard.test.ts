import { describe, expect, it } from "vitest";
import { towerCode } from "./ComplaintBoard";

describe("tower filter", () => {
  it("turns what staff type into a tower code", () => {
    expect(towerCode("7")).toBe("T07");
    expect(towerCode("t07")).toBe("T07");
    expect(towerCode("T21")).toBe("T21");
    expect(towerCode("")).toBe("");
  });
});
