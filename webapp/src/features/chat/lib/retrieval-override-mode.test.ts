import { describe, expect, it } from "vitest";
import {
  buildInitialRuntimeOverrideForNewConversation,
  buildRetrievalModePatch,
  buildRuntimeOverrideForRetrievalMode,
  inferRetrievalOverrideMode,
  readPersistedRetrievalOverrideMode,
  RETRIEVAL_OVERRIDE_MODE_KEY,
} from "./retrieval-override-mode";

describe("retrieval-override-mode", () => {
  const assistant = { topK: 8, similarityThreshold: 0.25 };
  const project = { topK: 10, similarityThreshold: 0.35 };

  it("infers preset mode when override keys are absent", () => {
    expect(inferRetrievalOverrideMode({}, assistant, project)).toBe("preset");
  });

  it("prefers explicit persisted mode over value inference", () => {
    expect(
      inferRetrievalOverrideMode(
        { [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom", topK: 8, similarityThreshold: 0.25 },
        assistant,
        project,
      ),
    ).toBe("custom");
    expect(
      inferRetrievalOverrideMode({ [RETRIEVAL_OVERRIDE_MODE_KEY]: "assistant_defaults" }, assistant, project),
    ).toBe("assistant_defaults");
    expect(
      inferRetrievalOverrideMode({ [RETRIEVAL_OVERRIDE_MODE_KEY]: "project_settings" }, assistant, project),
    ).toBe("project_settings");
  });

  it("infers project settings mode when values match and mode is absent", () => {
    expect(inferRetrievalOverrideMode({ topK: 10, similarityThreshold: 0.35 }, assistant, project)).toBe(
      "project_settings",
    );
  });

  it("infers assistant defaults mode when values match and mode is absent", () => {
    expect(inferRetrievalOverrideMode({ topK: 8, similarityThreshold: 0.25 }, assistant, project)).toBe(
      "assistant_defaults",
    );
  });

  it("infers custom mode for mismatched values", () => {
    expect(inferRetrievalOverrideMode({ topK: 9, similarityThreshold: 0.25 }, assistant, project)).toBe("custom");
  });

  it("buildRetrievalModePatch sends explicit preset mode marker", () => {
    expect(buildRetrievalModePatch("preset", { topK: 9, [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom" })).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "preset",
    });
  });

  it("buildRetrievalModePatch does not include numeric keys for assistant or project modes", () => {
    expect(buildRetrievalModePatch("assistant_defaults", {}, null, assistant)).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "assistant_defaults",
    });
    expect(buildRetrievalModePatch("project_settings", {})).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "project_settings",
    });
  });

  it("clears retrieval keys and mode metadata for preset mode snapshot helper", () => {
    expect(
      buildRuntimeOverrideForRetrievalMode(
        { topK: 9, [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom", useAdvisor: true },
        "preset",
      ),
    ).toEqual({
      useAdvisor: true,
    });
  });

  it("keeps mode metadata without numeric keys for assistant and project modes", () => {
    expect(buildRuntimeOverrideForRetrievalMode({}, "assistant_defaults", assistant)).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "assistant_defaults",
    });
    expect(buildRuntimeOverrideForRetrievalMode({}, "project_settings")).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "project_settings",
    });
  });

  it("always seeds custom mode with explicit metadata and values", () => {
    expect(
      buildRetrievalModePatch("custom", {}, { topK: 5, similarityThreshold: 0.9 }, assistant),
    ).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
      topK: 5,
      similarityThreshold: 0.9,
    });
  });

  it("preserves existing custom values when switching to custom mode", () => {
    expect(buildRetrievalModePatch("custom", { topK: 12, similarityThreshold: 0.4 }, null, assistant)).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
      topK: 12,
      similarityThreshold: 0.4,
    });
  });

  it("builds initial runtime override only when requested without numeric keys", () => {
    expect(buildInitialRuntimeOverrideForNewConversation(false, assistant)).toBeUndefined();
    expect(buildInitialRuntimeOverrideForNewConversation(true, assistant)).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "assistant_defaults",
    });
  });

  it("reads persisted mode when present", () => {
    expect(readPersistedRetrievalOverrideMode({ [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom" })).toBe("custom");
    expect(readPersistedRetrievalOverrideMode({ [RETRIEVAL_OVERRIDE_MODE_KEY]: "project_settings" })).toBe(
      "project_settings",
    );
    expect(readPersistedRetrievalOverrideMode({})).toBeNull();
  });
});
