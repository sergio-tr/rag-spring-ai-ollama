import { describe, expect, it } from "vitest";
import {
  appliedModelParameters,
  unsupportedModelParameters,
} from "./provider-aware-llm-parameters";

describe("provider-aware-llm-parameters", () => {
  it("exposes only temperature for configured model provider", () => {
    const applied = appliedModelParameters("OPENAI_COMPATIBLE");
    expect(applied.map((p) => p.id)).toEqual(["temperature"]);
    const unsupported = unsupportedModelParameters("OPENAI_COMPATIBLE");
    expect(unsupported.some((p) => p.id === "top_p")).toBe(true);
    expect(unsupported.some((p) => p.id === "presence_penalty")).toBe(true);
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
});
