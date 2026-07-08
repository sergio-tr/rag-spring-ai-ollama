import { afterEach, describe, expect, it } from "vitest";
import type { ConversationDto } from "@/types/api";
import { writeLastConversationId, clearLastConversationId } from "./last-conversation-persistence";
import { resolveInitialConversationId } from "./resolve-initial-conversation";

const PROJECT = "44444444-4444-4444-8444-444444444444";

function conv(id: string, updatedAt: string): ConversationDto {
  return {
    id,
    title: id,
    updatedAt,
  };
}

describe("resolveInitialConversationId @LastConversation", () => {
  afterEach(() => {
    clearLastConversationId(PROJECT);
  });

  it("prefers explicit URL conversation", () => {
    const list = [conv("a", "2026-01-02"), conv("b", "2026-01-03")];
    expect(resolveInitialConversationId(list, PROJECT, "a")).toBe("a");
  });

  it("falls back to persisted last conversation", () => {
    writeLastConversationId(PROJECT, "persisted");
    const list = [conv("older", "2026-01-01"), conv("persisted", "2026-01-02")];
    expect(resolveInitialConversationId(list, PROJECT, null)).toBe("persisted");
  });

  it("falls back to most recently updated conversation", () => {
    const list = [conv("older", "2026-01-01"), conv("newest", "2026-01-05")];
    expect(resolveInitialConversationId(list, PROJECT, null)).toBe("newest");
  });
});
