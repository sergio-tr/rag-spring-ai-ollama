import { describe, expect, it } from "vitest";
import {
  conversationIdFromChatUrl,
  isProjectConversationCreateRequest,
} from "./new-conversation-request";

describe("isProjectConversationCreateRequest", () => {
  it("matches POST to project conversations", () => {
    expect(
      isProjectConversationCreateRequest(
        "POST",
        "https://host/api/v5/projects/abc/conversations",
      ),
    ).toBe(true);
  });

  it("rejects GET and message posts", () => {
    expect(isProjectConversationCreateRequest("GET", "https://host/api/v5/projects/a/conversations")).toBe(
      false,
    );
    expect(
      isProjectConversationCreateRequest(
        "POST",
        "https://host/api/v5/conversations/c1/messages",
      ),
    ).toBe(false);
  });
});

describe("conversationIdFromChatUrl", () => {
  it("reads conversationId query param", () => {
    expect(
      conversationIdFromChatUrl(
        "https://host/en/chat?projectId=p1&conversationId=550e8400-e29b-41d4-a716-446655440000",
      ),
    ).toBe("550e8400-e29b-41d4-a716-446655440000");
  });

  it("returns null when missing or invalid", () => {
    expect(conversationIdFromChatUrl("https://host/en/chat?projectId=p1")).toBeNull();
    expect(conversationIdFromChatUrl("https://host/en/chat?conversationId=not-uuid")).toBeNull();
  });
});
