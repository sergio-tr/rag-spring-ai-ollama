import { describe, it, expect } from "vitest";
import { buildProjectScopedChatHref, buildProjectScopedDocumentsHref } from "./open-project-navigation";

describe("buildProjectScopedDocumentsHref", () => {
  it("includes projectId query param", () => {
    expect(buildProjectScopedDocumentsHref("p9")).toBe("/documents?projectId=p9");
  });
});

describe("buildProjectScopedChatHref", () => {
  it("includes projectId and optional conversationId", () => {
    expect(buildProjectScopedChatHref("p1")).toBe("/chat?projectId=p1");
    expect(buildProjectScopedChatHref("p1", "c9")).toBe("/chat?projectId=p1&conversationId=c9");
  });

  it("omits conversationId when null or empty string", () => {
    expect(buildProjectScopedChatHref("p1", null)).toBe("/chat?projectId=p1");
    expect(buildProjectScopedChatHref("p1", "")).toBe("/chat?projectId=p1");
  });
});
