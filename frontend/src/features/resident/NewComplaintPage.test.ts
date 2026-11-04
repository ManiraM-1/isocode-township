import { describe, expect, it } from "vitest";
import { descriptionError } from "./NewComplaintPage";

describe("complaint description", () => {
  it("asks for 10 to 1000 characters", () => {
    expect(descriptionError("   ")).toBe("Describe the problem");
    expect(descriptionError("Leak")).toBe("Add a little more detail (at least 10 characters)");
    expect(descriptionError("Water leaking from the ceiling")).toBeNull();
    expect(descriptionError("x".repeat(1001))).toBe("Keep it under 1000 characters");
  });
});
