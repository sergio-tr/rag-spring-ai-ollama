import { describe, expect, it } from "vitest";
import { resolveChatScopedProjectId } from "./resolve-chat-scoped-project";

describe("resolveChatScopedProjectId", () => {
  it("prefers url project over active store", () => {
    expect(resolveChatScopedProjectId("url-p", "store-p")).toEqual({
      projectId: "url-p",
      projectSyncPending: true,
    });
  });

  it("falls back to active store when url is absent", () => {
    expect(resolveChatScopedProjectId(null, "store-p")).toEqual({
      projectId: "store-p",
      projectSyncPending: false,
    });
  });

  it("is not pending when url matches active store", () => {
    expect(resolveChatScopedProjectId("p1", "p1")).toEqual({
      projectId: "p1",
      projectSyncPending: false,
    });
  });
});
