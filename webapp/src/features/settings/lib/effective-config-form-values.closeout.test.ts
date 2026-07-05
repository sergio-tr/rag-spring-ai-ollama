import { describe, expect, it } from "vitest";
import {
  buildStoredOverridesPatch,
  type SettingsSaveContext,
} from "./effective-config-form-values";

const embeddingEffective = {
  effectiveProvider: "OPENAI_COMPATIBLE" as const,
  embeddingModel: "bge-m3",
  embeddingOptions: { encodingFormat: "float", dimensions: 1024, timeoutSeconds: 30 },
  retrievalOptions: { topK: 8, similarityThreshold: 0.1, materializationStrategy: "CHUNK_LEVEL" },
  indexingOptions: { batchSize: 16, maxInputChars: 2048, normalize: false },
};

const llmEffective = {
  effectiveProvider: "OPENAI_COMPATIBLE" as const,
  chatModel: "gpt-main",
  classifierModelId: "default",
  temperature: 0.1,
  additionalParameters: {},
};

function userCtx(
  stored: Record<string, unknown>,
  values: Record<string, number>,
): SettingsSaveContext {
  return {
    mode: "user",
    stored,
    values,
    additionalParameters: {},
    editableKeys: ["topK", "similarityThreshold"],
    llmEffective,
    embeddingEffective,
    provider: "OPENAI_COMPATIBLE",
  };
}

describe("buildStoredOverridesPatch closeout", () => {
  it("user retrieval settings repeated save persists values", () => {
    const stored = { topK: 12, similarityThreshold: 0.15 };
    const first = buildStoredOverridesPatch(userCtx(stored, { topK: 12, similarityThreshold: 0.15 }));
    expect(first).toEqual({});

    const second = buildStoredOverridesPatch(userCtx(stored, { topK: 12, similarityThreshold: 0.15 }));
    expect(second).toEqual({});
  });

  it("persists user retrieval override that differs from system baseline", () => {
    const patch = buildStoredOverridesPatch(userCtx({}, { topK: 12, similarityThreshold: 0.15 }));
    expect(patch).toEqual({ topK: 12, similarityThreshold: 0.15 });
  });

  it("project retrieval settings repeated save persists values", () => {
    const stored = { topK: 10, similarityThreshold: 0.2 };
    const ctx: SettingsSaveContext = {
      mode: "project",
      stored,
      values: { topK: 10, similarityThreshold: 0.2 },
      additionalParameters: {},
      editableKeys: ["topK", "similarityThreshold"],
      llmEffective,
      embeddingEffective,
      userStored: { topK: 8, similarityThreshold: 0.1 },
      provider: "OPENAI_COMPATIBLE",
    };
    expect(buildStoredOverridesPatch(ctx)).toEqual({});
  });

  it("saving user does not include project keys in patch", () => {
    const patch = buildStoredOverridesPatch(userCtx({}, { topK: 12, similarityThreshold: 0.15 }));
    expect(patch).not.toHaveProperty("expansionEnabled");
    expect(patch).not.toHaveProperty("useRetrieval");
  });

  it("project patch only contains project delta", () => {
    const ctx: SettingsSaveContext = {
      mode: "project",
      stored: {},
      values: { topK: 11, similarityThreshold: 0.22 },
      additionalParameters: {},
      editableKeys: ["topK", "similarityThreshold"],
      llmEffective,
      embeddingEffective,
      userStored: { topK: 8, similarityThreshold: 0.1 },
      provider: "OPENAI_COMPATIBLE",
    };
    expect(buildStoredOverridesPatch(ctx)).toEqual({ topK: 11, similarityThreshold: 0.22 });
  });

  it("user retrieval override matching system baseline is preserved on repeated save", () => {
    const stored = { topK: 8, similarityThreshold: 0.1 };
    const patch = buildStoredOverridesPatch(userCtx(stored, { topK: 8, similarityThreshold: 0.1 }));
    expect(patch).toEqual({});
  });

  it("project A and B stored configs are independent objects", () => {
    const patchA = buildStoredOverridesPatch({
      mode: "project",
      stored: { topK: 5 },
      values: { topK: 6, similarityThreshold: 0.1 },
      additionalParameters: {},
      editableKeys: ["topK", "similarityThreshold"],
      llmEffective,
      embeddingEffective,
      userStored: {},
      provider: "OPENAI_COMPATIBLE",
    });
    const patchB = buildStoredOverridesPatch({
      mode: "project",
      stored: { topK: 9 },
      values: { topK: 9, similarityThreshold: 0.1 },
      additionalParameters: {},
      editableKeys: ["topK", "similarityThreshold"],
      llmEffective,
      embeddingEffective,
      userStored: {},
      provider: "OPENAI_COMPATIBLE",
    });
    expect(patchA).toEqual({ topK: 6, similarityThreshold: 0.1 });
    expect(patchB).toEqual({ topK: 9, similarityThreshold: 0.1 });
  });
});
