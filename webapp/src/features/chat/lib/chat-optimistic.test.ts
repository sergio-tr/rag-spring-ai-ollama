import { describe, expect, it } from "vitest";
import type { MessageDto } from "@/types/api";
import { optimisticConsumed } from "./chat-optimistic";

describe("optimisticConsumed", () => {
  it("returns false when optimistic text is empty", () => {
    expect(optimisticConsumed([], "")).toBe(false);
    expect(optimisticConsumed(undefined, null)).toBe(false);
  });

  it("returns true when a USER message matches trimmed optimistic text", () => {
    const messages: MessageDto[] = [
      {
        id: "1",
        role: "USER",
        seq: 1,
        content: "  hello  ",
        createdAt: "",
        sources: null,
        queryType: null,
        pipelineSteps: null,
      },
    ];
    expect(optimisticConsumed(messages, "hello")).toBe(true);
  });

  it("returns false when only ASSISTANT matches", () => {
    const messages: MessageDto[] = [
      {
        id: "1",
        role: "ASSISTANT",
        seq: 2,
        content: "hello",
        createdAt: "",
        sources: null,
        queryType: null,
        pipelineSteps: null,
      },
    ];
    expect(optimisticConsumed(messages, "hello")).toBe(false);
  });
});
