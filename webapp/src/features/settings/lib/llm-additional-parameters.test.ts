import { describe, expect, it } from "vitest";
import {
  mergeAdditionalParametersIntoPayload,
  readAdditionalParameters,
  readParameterValue,
  readTemperature,
} from "./llm-additional-parameters";
import { LLM_ADDITIONAL_PARAMETERS_KEY, LLM_TEMPERATURE_KEY } from "./provider-aware-llm-parameters";

describe("llm-additional-parameters", () => {
  it("reads additional parameters from nested config map", () => {
    expect(
      readAdditionalParameters({
        [LLM_ADDITIONAL_PARAMETERS_KEY]: { topP: 0.8, seed: 42 },
      }),
    ).toEqual({ topP: 0.8, seed: 42 });
  });

  it("returns empty map for missing or invalid additional parameter payloads", () => {
    expect(readAdditionalParameters(undefined)).toEqual({});
    expect(readAdditionalParameters({ [LLM_ADDITIONAL_PARAMETERS_KEY]: null })).toEqual({});
    expect(readAdditionalParameters({ [LLM_ADDITIONAL_PARAMETERS_KEY]: "bad" })).toEqual({});
    expect(readAdditionalParameters({ [LLM_ADDITIONAL_PARAMETERS_KEY]: [] })).toEqual({});
  });

  it("reads temperature from top-level or legacy key", () => {
    expect(readTemperature({ [LLM_TEMPERATURE_KEY]: 0.4 })).toBe(0.4);
    expect(readTemperature({ temperature: 0.6 })).toBe(0.6);
    expect(readTemperature({ [LLM_TEMPERATURE_KEY]: Number.NaN })).toBeUndefined();
    expect(readTemperature(undefined)).toBeUndefined();
  });

  it("merges cleaned additional parameters into save payload", () => {
    expect(
      mergeAdditionalParametersIntoPayload(
        { topK: 5, [LLM_ADDITIONAL_PARAMETERS_KEY]: { stale: true } },
        { topP: 0.9, seed: undefined, empty: "", missing: null },
      ),
    ).toEqual({ topK: 5, [LLM_ADDITIONAL_PARAMETERS_KEY]: { topP: 0.9 } });
  });

  it("removes additional parameters key when all values are empty", () => {
    expect(
      mergeAdditionalParametersIntoPayload(
        { topK: 5, [LLM_ADDITIONAL_PARAMETERS_KEY]: { topP: 0.2 } },
        { topP: "", seed: null },
      ),
    ).toEqual({ topK: 5 });
  });

  it("reads parameter values from top-level and additional storage", () => {
    const config = {
      [LLM_TEMPERATURE_KEY]: 0.3,
      [LLM_ADDITIONAL_PARAMETERS_KEY]: { topP: 0.7 },
      custom: "value",
    };
    expect(readParameterValue(config, "topLevel", LLM_TEMPERATURE_KEY)).toBe(0.3);
    expect(readParameterValue(config, "additional", "topP")).toBe(0.7);
    expect(readParameterValue(config, "topLevel", "custom")).toBe("value");
  });
});
