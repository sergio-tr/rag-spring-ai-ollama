import { describe, it, expect, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useAppStore } from "@/store/app.store";
import { useSyncActiveProjectWithList } from "./use-sync-active-project";

describe("useSyncActiveProjectWithList", () => {
  beforeEach(() => {
    useAppStore.setState({ activeProject: null });
  });

  it("clears active when list is empty but was loaded", () => {
    useAppStore.setState({ activeProject: { id: "x", name: "X" } });
    renderHook(() => useSyncActiveProjectWithList([]));
    expect(useAppStore.getState().activeProject).toBeNull();
  });

  it("clears active when id missing from list", () => {
    useAppStore.setState({ activeProject: { id: "gone", name: "G" } });
    renderHook(() =>
      useSyncActiveProjectWithList([
        { id: "a", name: "A", docCount: 0, convCount: 0, updatedAt: "" },
      ]),
    );
    expect(useAppStore.getState().activeProject).toBeNull();
  });

  it("no-op when items undefined", () => {
    useAppStore.setState({ activeProject: { id: "a", name: "A" } });
    renderHook(() => useSyncActiveProjectWithList(undefined));
    expect(useAppStore.getState().activeProject?.id).toBe("a");
  });
});
