import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useSyncActiveProjectFromDocumentsUrl } from "@/features/projects/hooks/use-sync-active-project-from-documents-url";

const fromUrlParam = vi.fn();

vi.mock("@/features/projects/hooks/use-sync-active-project-from-url-param", () => ({
  useSyncActiveProjectFromUrlParam: (...args: unknown[]) => fromUrlParam(...args),
}));

describe("useSyncActiveProjectFromDocumentsUrl", () => {
  beforeEach(() => {
    fromUrlParam.mockClear();
  });

  it("delegates to useSyncActiveProjectFromUrlParam with /documents", () => {
    renderHook(() => useSyncActiveProjectFromDocumentsUrl("proj-z"));
    expect(fromUrlParam).toHaveBeenCalledWith("proj-z", "/documents");
  });

  it("passes through null ids", () => {
    renderHook(() => useSyncActiveProjectFromDocumentsUrl(null));
    expect(fromUrlParam).toHaveBeenCalledWith(null, "/documents");
  });
});
