import { describe, expect, it } from "vitest";
import { buildRetrievalModePatch } from "./retrieval-override-mode";

describe("chat retrieval source closeout", () => {
  const assistant = { topK: 8, similarityThreshold: 0.25 };

  it("preset mode patch does not include numeric overrides", () => {
    expect(buildRetrievalModePatch("preset", { topK: 9, similarityThreshold: 0.5 })).toEqual({
      retrievalOverrideMode: "preset",
    });
  });

  it("custom mode patch includes both values", () => {
    expect(
      buildRetrievalModePatch("custom", {}, { topK: 5, similarityThreshold: 0.9 }, assistant),
    ).toEqual({
      retrievalOverrideMode: "custom",
      topK: 5,
      similarityThreshold: 0.9,
    });
  });

  it("assistant and project modes do not send numeric keys", () => {
    expect(buildRetrievalModePatch("assistant_defaults", { topK: 1 })).toEqual({
      retrievalOverrideMode: "assistant_defaults",
    });
    expect(buildRetrievalModePatch("project_settings", { similarityThreshold: 0.1 })).toEqual({
      retrievalOverrideMode: "project_settings",
    });
  });
});
