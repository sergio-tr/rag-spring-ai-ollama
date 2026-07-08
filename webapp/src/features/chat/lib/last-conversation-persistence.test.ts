import { afterEach, describe, expect, it } from "vitest";
import {
  clearLastConversationId,
  readLastConversationId,
  writeLastConversationId,
} from "./last-conversation-persistence";

const PROJECT = "11111111-1111-4111-8111-111111111111";
const CONV = "22222222-2222-4222-8222-222222222222";

describe("last-conversation-persistence @LastConversation", () => {
  afterEach(() => {
    clearLastConversationId(PROJECT);
  });

  it("writes and reads last conversation per project", () => {
    writeLastConversationId(PROJECT, CONV);
    expect(readLastConversationId(PROJECT)).toBe(CONV);
  });

  it("clears only matching conversation when specified", () => {
    writeLastConversationId(PROJECT, CONV);
    clearLastConversationId(PROJECT, "other-id");
    expect(readLastConversationId(PROJECT)).toBe(CONV);
    clearLastConversationId(PROJECT, CONV);
    expect(readLastConversationId(PROJECT)).toBeNull();
  });
});
