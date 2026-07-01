import { describe, expect, it } from "vitest";
import { stripHiddenReasoningBlocks } from "./strip-hidden-reasoning";

describe("stripHiddenReasoningBlocks", () => {
  it("removes think blocks", () => {
    const open = "<" + "think" + ">";
    const close = "<" + "/" + "think" + ">";
    const input = `${open}secret reasoning${close}\n\nVisible answer`;
    expect(stripHiddenReasoningBlocks(input)).toBe("Visible answer");
  });

  it("removes reasoning tags", () => {
    expect(stripHiddenReasoningBlocks("<reasoning>x</reasoning>Hello")).toBe("Hello");
  });

  it("returns plain text unchanged", () => {
    expect(stripHiddenReasoningBlocks("Plain answer")).toBe("Plain answer");
  });
});
