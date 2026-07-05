import { describe, expect, it } from "vitest";
import {
  buildSavePayloadRespectingEffectiveDefaults,
  clearConfigOverrideKeys,
  isAdditionalParameterInherited,
  isFieldInherited,
  mergeEffectiveIntoFormValues,
  readEffectiveEmbeddingField,
  readEffectiveLlmParameter,
} from "./effective-config-form-values";

describe("effective-config-form-values", () => {
  const llmEffective = {
    effectiveProvider: "OPENAI_COMPATIBLE" as const,
    chatModel: "gpt-main",
    classifierModelId: "default",
    temperature: 0.1,
    additionalParameters: { topP: 1, think: false },
  };

  const embeddingEffective = {
    effectiveProvider: "OPENAI_COMPATIBLE" as const,
    embeddingModel: "bge-m3",
    embeddingOptions: { encodingFormat: "float", dimensions: 1024, timeoutSeconds: 30 },
    retrievalOptions: { topK: 10, similarityThreshold: 0.35, materializationStrategy: "CHUNK_LEVEL" },
    indexingOptions: { batchSize: 16, maxInputChars: 2048, normalize: false },
  };

  it("merges effective values into empty user config form state", () => {
    const keys = ["llmModel", "llmTemperature", "topK", "embeddingModel"];
    const merged = mergeEffectiveIntoFormValues({}, keys, llmEffective, embeddingEffective, "OPENAI_COMPATIBLE");
    expect(merged.formValues.llmModel).toBe("gpt-main");
    expect(merged.formValues.llmTemperature).toBe(0.1);
    expect(merged.formValues.topK).toBe(10);
    expect(merged.formValues.embeddingModel).toBe("bge-m3");
  });

  it("does not overwrite explicit user overrides when merging", () => {
    const keys = ["llmTemperature", "topK"];
    const merged = mergeEffectiveIntoFormValues(
      { llmTemperature: 0.9, topK: 4 },
      keys,
      llmEffective,
      embeddingEffective,
      "OPENAI_COMPATIBLE",
    );
    expect(merged.formValues.llmTemperature).toBe(0.9);
    expect(merged.formValues.topK).toBe(4);
  });

  it("omits inherited values from save patch when nothing stored", () => {
    const keys = ["llmTemperature", "topK"];
    const payload = buildSavePayloadRespectingEffectiveDefaults(
      {},
      { llmTemperature: 0.1, topK: 10 },
      {},
      keys,
      llmEffective,
      embeddingEffective,
      "OPENAI_COMPATIBLE",
    );
    expect(payload).toEqual({});
  });

  it("repeated save keeps stored retrieval overrides in patch semantics", () => {
    const keys = ["topK", "similarityThreshold"];
    const stored = { topK: 12, similarityThreshold: 0.15 };
    const payload = buildSavePayloadRespectingEffectiveDefaults(
      stored,
      { topK: 12, similarityThreshold: 0.15 },
      {},
      keys,
      llmEffective,
      embeddingEffective,
      "OPENAI_COMPATIBLE",
    );
    expect(payload).toEqual({});
  });

  it("persists values that differ from effective defaults", () => {
    const keys = ["llmTemperature", "topK", "similarityThreshold"];
    const payload = buildSavePayloadRespectingEffectiveDefaults(
      {},
      { llmTemperature: 0.7, topK: 12, similarityThreshold: 0.15 },
      {},
      keys,
      llmEffective,
      embeddingEffective,
      "OPENAI_COMPATIBLE",
    );
    expect(payload.llmTemperature).toBe(0.7);
    expect(payload.topK).toBe(12);
    expect(payload.similarityThreshold).toBe(0.15);
  });

  it("clears only requested override keys", () => {
    const cleared = clearConfigOverrideKeys(
      { llmModel: "x", topK: 3, llmSystemPrompt: "stay" },
      ["llmModel", "topK"],
    );
    expect(cleared).toEqual({ llmSystemPrompt: "stay" });
  });

  it("reads effective embedding field from API response", () => {
    expect(readEffectiveEmbeddingField(undefined, embeddingEffective, "topK")).toBe(10);
    expect(readEffectiveEmbeddingField({ topK: 4 }, embeddingEffective, "topK")).toBe(4);
    expect(readEffectiveEmbeddingField(undefined, embeddingEffective, "embeddingNormalize")).toBe(false);
    expect(readEffectiveEmbeddingField(undefined, embeddingEffective, "unknownKey")).toBeUndefined();
  });

  it("reads effective LLM parameters from config and API defaults", () => {
    expect(
      readEffectiveLlmParameter({ llmTemperature: 0.8 }, llmEffective, "OPENAI_COMPATIBLE", "topLevel", "llmTemperature"),
    ).toBe(0.8);
    expect(
      readEffectiveLlmParameter({}, llmEffective, "OPENAI_COMPATIBLE", "topLevel", "llmTemperature"),
    ).toBe(0.1);
    expect(
      readEffectiveLlmParameter({}, llmEffective, "OPENAI_COMPATIBLE", "additional", "topP"),
    ).toBe(1);
    expect(
      readEffectiveLlmParameter({}, llmEffective, "OPENAI_COMPATIBLE", "topLevel", "llmModel"),
    ).toBe("gpt-main");
  });

  it("detects inherited top-level and additional parameter fields", () => {
    expect(isFieldInherited({}, "topK")).toBe(true);
    expect(isFieldInherited({ topK: 5 }, "topK")).toBe(false);
    expect(isAdditionalParameterInherited({}, "topP")).toBe(true);
    expect(isAdditionalParameterInherited({ llmAdditionalParameters: { topP: 0.5 } }, "topP")).toBe(false);
  });

  it("merges additional LLM parameters from effective defaults", () => {
    const keys = ["llmAdditionalParameters"];
    const merged = mergeEffectiveIntoFormValues({}, keys, llmEffective, embeddingEffective, "OPENAI_COMPATIBLE");
    expect(merged.additionalParameters.topP).toBe(1);
    expect(merged.additionalParameters.think).toBe(false);
  });

  it("persists additional parameters that differ from effective defaults", () => {
    const keys = ["llmAdditionalParameters"];
    const payload = buildSavePayloadRespectingEffectiveDefaults(
      {},
      {},
      { topP: 0.5, think: false },
      keys,
      llmEffective,
      embeddingEffective,
      "OPENAI_COMPATIBLE",
    );
    expect(payload.llmAdditionalParameters).toEqual({ topP: 0.5 });
  });

  it("omits classifier model when it matches effective default", () => {
    const keys = ["classifierModelId"];
    const payload = buildSavePayloadRespectingEffectiveDefaults(
      {},
      { classifierModelId: "default" },
      {},
      keys,
      llmEffective,
      embeddingEffective,
      "OPENAI_COMPATIBLE",
    );
    expect(payload.classifierModelId).toBeUndefined();
  });

  it("merges classifier model from effective defaults and persists overrides", () => {
    const keys = ["classifierModelId", "llmModel"];
    const merged = mergeEffectiveIntoFormValues({}, keys, llmEffective, embeddingEffective, "OPENAI_COMPATIBLE");
    expect(merged.formValues.classifierModelId).toBe("default");

    expect(
      readEffectiveLlmParameter({}, llmEffective, "OPENAI_COMPATIBLE", "topLevel", "classifierModelId"),
    ).toBe("default");
    expect(
      readEffectiveLlmParameter({}, llmEffective, "OPENAI_COMPATIBLE", "topLevel", "unknownKey"),
    ).toBeUndefined();

    const payload = buildSavePayloadRespectingEffectiveDefaults(
      {},
      { classifierModelId: "custom-classifier", llmModel: "other-chat" },
      {},
      keys,
      llmEffective,
      embeddingEffective,
      "OPENAI_COMPATIBLE",
    );
    expect(payload.classifierModelId).toBe("custom-classifier");
    expect(payload.llmModel).toBe("other-chat");
  });
});
