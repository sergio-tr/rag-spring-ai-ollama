import { afterEach, describe, expect, it, vi } from "vitest";
import {
  isAdvancedStructuredSearchIndexingEnabled,
  listSelectableProjectMaterializationStrategies,
} from "./project-materialization-strategies";

describe("project materialization strategies", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("hides STRUCTURED_SEARCH from normal project creation by default", () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING", undefined);
    expect(isAdvancedStructuredSearchIndexingEnabled()).toBe(false);
    expect(listSelectableProjectMaterializationStrategies()).toEqual([
      "CHUNK_LEVEL",
      "DOCUMENT_LEVEL",
      "HYBRID",
    ]);
  });

  it("shows STRUCTURED_SEARCH only when advanced flag is enabled", () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING", "true");
    expect(listSelectableProjectMaterializationStrategies()).toContain("STRUCTURED_SEARCH");
  });
});
