import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useSyncActiveProjectFromChatUrl } from "@/features/projects/hooks/use-sync-active-project-from-chat-url";

const fromUrlParam = vi.fn();

vi.mock("@/features/projects/hooks/use-sync-active-project-from-url-param", () => ({
  useSyncActiveProjectFromUrlParam: (...args: unknown[]) => fromUrlParam(...args),
}));

describe("useSyncActiveProjectFromChatUrl", () => {
  beforeEach(() => {
    fromUrlParam.mockClear();
  });

  it("delegates to useSyncActiveProjectFromUrlParam with /chat", () => {
    renderHook(() => useSyncActiveProjectFromChatUrl("proj-a"));
    expect(fromUrlParam).toHaveBeenCalledWith("proj-a", "/chat");
  });

  it("passes through undefined ids", () => {
    renderHook(() => useSyncActiveProjectFromChatUrl(undefined));
    expect(fromUrlParam).toHaveBeenCalledWith(undefined, "/chat");
  });
});
