import { describe, expect, it } from "vitest";
import {
  buildInitialRuntimeOverrideForNewConversation,
  buildRuntimeOverrideForRetrievalMode,
  inferRetrievalOverrideMode,
} from "./retrieval-override-mode";

describe("retrieval-override-mode", () => {
  const assistant = { topK: 8, similarityThreshold: 0.25 };

  it("infers preset mode when override keys are absent", () => {
    expect(inferRetrievalOverrideMode({}, assistant)).toBe("preset");
  });

  it("infers assistant defaults mode when values match", () => {
    expect(inferRetrievalOverrideMode({ topK: 8, similarityThreshold: 0.25 }, assistant)).toBe(
      "assistant_defaults",
    );
  });

  it("infers custom mode for mismatched values", () => {
    expect(inferRetrievalOverrideMode({ topK: 9, similarityThreshold: 0.25 }, assistant)).toBe("custom");
  });

  it("clears retrieval keys for preset mode", () => {
    expect(buildRuntimeOverrideForRetrievalMode({ topK: 9, useAdvisor: true }, "preset")).toEqual({
      useAdvisor: true,
    });
  });

  it("writes assistant defaults into override", () => {
    expect(buildRuntimeOverrideForRetrievalMode({}, "assistant_defaults", assistant)).toEqual(assistant);
  });

  it("builds initial runtime override only when requested", () => {
    expect(buildInitialRuntimeOverrideForNewConversation(false, assistant)).toBeUndefined();
    expect(buildInitialRuntimeOverrideForNewConversation(true, assistant)).toEqual(assistant);
  });
});
