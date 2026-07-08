import { describe, expect, it } from "vitest";
import { resolveFieldScope } from "./assistant-config-scope";

describe("resolveFieldScope", () => {
  it("marks manual override keys as conversation scope", () => {
    expect(resolveFieldScope("topK", ["topK"])).toBe("conversation");
  });

  it("marks pinned conversation model as conversation scope", () => {
    expect(
      resolveFieldScope("llmModel", [], { conversationModelKey: "gpt-oss:20b" }),
    ).toBe("conversation");
  });

  it("marks pinned conversation classifier as conversation scope", () => {
    expect(
      resolveFieldScope("classifierModelId", [], { conversationClassifierKey: "clf-1" }),
    ).toBe("conversation");
  });

  it("defaults to project scope for inherited effective values", () => {
    expect(resolveFieldScope("similarityThreshold", [])).toBe("project");
  });
});
