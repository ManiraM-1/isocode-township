import { describe, expect, it } from "vitest";
import { maskPhone, phoneError, toE164 } from "./phone";

describe("phone validation", () => {
  it("accepts a 10-digit Indian mobile number in common forms", () => {
    expect(phoneError("9000000002")).toBeNull();
    expect(phoneError("90000 00002")).toBeNull();
    expect(phoneError("+91 90000 00002")).toBeNull();
    expect(phoneError("09000000002")).toBeNull();
    expect(toE164("+91 90000 00002")).toBe("+919000000002");
  });

  it("explains what is wrong", () => {
    expect(phoneError("")).toBe("Enter your mobile number");
    expect(phoneError("90000")).toBe("Enter a 10-digit Indian mobile number");
    expect(phoneError("5000000002")).toBe("Indian mobile numbers start with 6, 7, 8 or 9");
  });

  it("masks the number for display", () => {
    expect(maskPhone("+919000000002")).toBe("+91 90••• ••002");
  });
});
