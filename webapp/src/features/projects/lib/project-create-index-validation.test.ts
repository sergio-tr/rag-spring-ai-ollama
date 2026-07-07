import { describe, expect, it } from "vitest";
import { getProjectCreateIndexCombinationFeedback } from "./project-create-index-validation";

describe("getProjectCreateIndexCombinationFeedback", () => {
  it("CHUNK_LEVEL + metadata=false allows submit without warning", () => {
    expect(getProjectCreateIndexCombinationFeedback("CHUNK_LEVEL", false)).toEqual({ blocked: false });
  });

  it("CHUNK_LEVEL + metadata=true allows submit without warning", () => {
    expect(getProjectCreateIndexCombinationFeedback("CHUNK_LEVEL", true)).toEqual({ blocked: false });
  });

  it("DOCUMENT_LEVEL + metadata=false allows submit without warning", () => {
    expect(getProjectCreateIndexCombinationFeedback("DOCUMENT_LEVEL", false)).toEqual({ blocked: false });
  });

  it("DOCUMENT_LEVEL + metadata=true allows submit without warning", () => {
    expect(getProjectCreateIndexCombinationFeedback("DOCUMENT_LEVEL", true)).toEqual({ blocked: false });
  });

  it("HYBRID + metadata=false shows warning and allows submit", () => {
    expect(getProjectCreateIndexCombinationFeedback("HYBRID", false)).toEqual({
      blocked: false,
      warningMessageKey: "hybridWithoutMetadataWarning",
    });
  });

  it("HYBRID + metadata=true shows no warning and allows submit", () => {
    expect(getProjectCreateIndexCombinationFeedback("HYBRID", true)).toEqual({ blocked: false });
  });

  it("STRUCTURED_SEARCH + metadata=false blocks submit", () => {
    expect(getProjectCreateIndexCombinationFeedback("STRUCTURED_SEARCH", false)).toEqual({
      blocked: true,
      blockMessageKey: "structuredSearchRequiresMetadata",
    });
  });

  it("STRUCTURED_SEARCH + metadata=true allows submit with informational warning", () => {
    expect(getProjectCreateIndexCombinationFeedback("STRUCTURED_SEARCH", true)).toEqual({
      blocked: false,
      warningMessageKey: "structuredSearchInfoWarning",
    });
  });
});
