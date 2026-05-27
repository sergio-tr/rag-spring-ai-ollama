import { describe, expect, it } from "vitest";
import type { MessageDto } from "@/types/api";
import {
  applyUserMessageEditOptimistic,
  messageSeq,
  sortMessagesBySeq,
} from "./chat-message-order";

function msg(
  id: string,
  role: "USER" | "ASSISTANT",
  content: string,
  seq?: number,
  createdAt = "2025-01-01T00:00:00.000Z",
): MessageDto {
  return {
    id,
    role,
    content,
    seq,
    createdAt,
    sources: null,
    queryType: null,
    pipelineSteps: null,
  };
}

describe("sortMessagesBySeq", () => {
  it("orders by seq ascending regardless of createdAt", () => {
    const input = [
      msg("a", "ASSISTANT", "b", 2, "2025-01-02T00:00:00.000Z"),
      msg("u", "USER", "a", 1, "2025-01-03T00:00:00.000Z"),
    ];
    const sorted = sortMessagesBySeq(input);
    expect(sorted.map((m) => m.id)).toEqual(["u", "a"]);
  });

  it("does not reorder when already sorted by seq", () => {
    const input = [msg("u", "USER", "hi", 1), msg("a", "ASSISTANT", "yo", 2)];
    expect(sortMessagesBySeq(input).map((m) => m.id)).toEqual(["u", "a"]);
  });
});

describe("applyUserMessageEditOptimistic", () => {
  it("updates user content and removes tail messages after edited seq", () => {
    const input = [
      msg("u1", "USER", "old", 1),
      msg("a1", "ASSISTANT", "answer", 2),
    ];
    const next = applyUserMessageEditOptimistic(input, "u1", "new");
    expect(next).toHaveLength(1);
    expect(next[0].content).toBe("new");
    expect(next[0].id).toBe("u1");
  });

  it("keeps earlier turns when editing a later user message", () => {
    const input = [
      msg("u0", "USER", "first", 1),
      msg("a0", "ASSISTANT", "r0", 2),
      msg("u1", "USER", "old", 3),
      msg("a1", "ASSISTANT", "r1", 4),
    ];
    const next = applyUserMessageEditOptimistic(input, "u1", "edited");
    expect(next.map((m) => m.id)).toEqual(["u0", "a0", "u1"]);
    expect(next.find((m) => m.id === "u1")?.content).toBe("edited");
  });
});

describe("messageSeq", () => {
  it("falls back to index when seq missing", () => {
    const m = msg("x", "USER", "c");
    expect(messageSeq(m, 7)).toBe(7);
  });
});
