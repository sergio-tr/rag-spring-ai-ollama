import { describe, expect, it } from "vitest";
import {
  appliedModelParameterIds,
  appliedModelParameters,
  normalizeLlmProvider,
  unsupportedModelParameters,
} from "./provider-aware-llm-parameters";

describe("provider-aware-llm-parameters", () => {
  it("exposes OpenAI-compatible applied parameters aligned with chat mapper", () => {
    const applied = appliedModelParameters("OPENAI_COMPATIBLE");
    expect(applied.map((p) => p.id)).toEqual(
      expect.arrayContaining([
        "temperature",
        "top_p",
        "seed",
        "max_tokens",
        "presence_penalty",
        "frequency_penalty",
        "response_format",
        "stop",
        "think",
      ]),
    );
    const unsupported = unsupportedModelParameters("OPENAI_COMPATIBLE");
    expect(unsupported.some((p) => p.id === "top_k")).toBe(true);
    expect(unsupported.some((p) => p.id === "num_ctx")).toBe(true);
  });

  it("exposes ollama-applied parameters for local model provider", () => {
    const applied = appliedModelParameters("OLLAMA_NATIVE");
    expect(applied.map((p) => p.id)).toEqual(
      expect.arrayContaining(["temperature", "top_p", "top_k", "num_ctx", "num_predict", "seed"]),
    );
    expect(applied.some((p) => p.id === "presence_penalty")).toBe(false);
  });

  it("lists remote-only penalties as unsupported for local provider", () => {
    const unsupported = unsupportedModelParameters("OLLAMA_NATIVE");
    expect(unsupported.some((p) => p.id === "presence_penalty")).toBe(true);
    expect(unsupported.some((p) => p.id === "frequency_penalty")).toBe(true);
  });

  it("normalizes known providers and rejects unknown values", () => {
    expect(normalizeLlmProvider("OPENAI_COMPATIBLE")).toBe("OPENAI_COMPATIBLE");
    expect(normalizeLlmProvider("OLLAMA_NATIVE")).toBe("OLLAMA_NATIVE");
    expect(normalizeLlmProvider("unknown")).toBeNull();
    expect(normalizeLlmProvider(null)).toBeNull();
  });

  it("falls back to temperature-only applied parameters when provider is unset", () => {
    expect(appliedModelParameters(null).map((p) => p.id)).toEqual(["temperature"]);
    expect(unsupportedModelParameters(null).some((p) => p.id === "top_p")).toBe(true);
    expect(appliedModelParameterIds("OPENAI_COMPATIBLE")).toEqual(
      new Set([
        "temperature",
        "top_p",
        "seed",
        "max_tokens",
        "presence_penalty",
        "frequency_penalty",
        "response_format",
        "stop",
        "think",
      ]),
    );
  });
});
