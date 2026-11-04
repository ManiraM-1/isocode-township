import { describe, expect, it } from "vitest";
import { statementFileError } from "./PaymentsPage";

describe("statement file checks", () => {
  it("accepts a CSV up to 5 MB", () => {
    expect(statementFileError({ name: "October.CSV", size: 2048 })).toBeNull();
  });

  it("explains what is wrong with other files", () => {
    expect(statementFileError({ name: "statement.xlsx", size: 2048 })).toBe("Choose a .csv file exported from the bank.");
    expect(statementFileError({ name: "empty.csv", size: 0 })).toBe("That file is empty.");
    expect(statementFileError({ name: "big.csv", size: 5 * 1024 * 1024 + 1 })).toBe(
      "The file is larger than 5 MB. Split the statement and upload each part.",
    );
  });
});
