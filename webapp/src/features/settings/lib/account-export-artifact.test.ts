import { describe, expect, it } from "vitest";
import { pickExportArtifactId } from "./account-export-artifact";

describe("pickExportArtifactId", () => {
  it("reads exportArtifactId string from result", () => {
    expect(pickExportArtifactId({ exportArtifactId: "abc" })).toBe("abc");
  });

  it("returns undefined when missing or wrong type", () => {
    expect(pickExportArtifactId(null)).toBeUndefined();
    expect(pickExportArtifactId({})).toBeUndefined();
    expect(pickExportArtifactId({ exportArtifactId: 12 as unknown as string })).toBeUndefined();
  });
});
