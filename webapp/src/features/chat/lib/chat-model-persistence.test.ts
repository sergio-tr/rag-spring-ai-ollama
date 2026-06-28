import { describe, expect, it, beforeEach } from "vitest";

import {
  readProjectClassifierModelPreference,
  readProjectLlmModelPreference,
  writeProjectClassifierModelPreference,
  writeProjectLlmModelPreference,
} from "./chat-model-persistence";

describe("chat-model-persistence", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("round-trips LLM preference per project", () => {
    expect(readProjectLlmModelPreference("p1")).toBe("");
    writeProjectLlmModelPreference("p1", "llama3.2");
    expect(readProjectLlmModelPreference("p1")).toBe("llama3.2");
    writeProjectLlmModelPreference("p1", "");
    expect(readProjectLlmModelPreference("p1")).toBe("");
  });

  it("round-trips classifier preference per project", () => {
    writeProjectClassifierModelPreference("p2", "clf-v1");
    expect(readProjectClassifierModelPreference("p2")).toBe("clf-v1");
    expect(readProjectClassifierModelPreference("p1")).toBe("");
  });

  it("ignores empty project id", () => {
    writeProjectLlmModelPreference(null, "x");
    expect(readProjectLlmModelPreference(null)).toBe("");
  });
});
