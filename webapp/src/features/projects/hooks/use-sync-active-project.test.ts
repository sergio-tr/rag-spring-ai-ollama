import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useSyncActiveProjectWithList } from "./use-sync-active-project";
import { useAppStore } from "@/store/app.store";
import type { ProjectSummary } from "@/types/api";

function project(id: string, name: string): ProjectSummary {
  return { id, name, docCount: 0, convCount: 0, updatedAt: "" };
}

describe("useSyncActiveProjectWithList", () => {
  beforeEach(() => {
    useAppStore.setState({ activeProject: null });
  });

  it("does not clear active project while items are undefined", async () => {
    useAppStore.getState().setActiveProject({ id: "a", name: "A" });
    const { rerender } = renderHook(
      ({ items }: { items: ProjectSummary[] | undefined }) => useSyncActiveProjectWithList(items),
      { initialProps: { items: undefined as ProjectSummary[] | undefined } },
    );
    await waitFor(() => {
      expect(useAppStore.getState().activeProject).toEqual({ id: "a", name: "A" });
    });
    rerender({ items: undefined });
    expect(useAppStore.getState().activeProject).toEqual({ id: "a", name: "A" });
  });

  it("clears active project when loaded list is empty", async () => {
    useAppStore.getState().setActiveProject({ id: "a", name: "A" });
    const { rerender } = renderHook(
      ({ items }: { items: ProjectSummary[] | undefined }) => useSyncActiveProjectWithList(items),
      { initialProps: { items: undefined as ProjectSummary[] | undefined } },
    );
    rerender({ items: [] });
    await waitFor(() => {
      expect(useAppStore.getState().activeProject).toBeNull();
    });
  });

  it("clears active project when it is not in the loaded list", async () => {
    useAppStore.getState().setActiveProject({ id: "gone", name: "Gone" });
    const { rerender } = renderHook(
      ({ items }: { items: ProjectSummary[] | undefined }) => useSyncActiveProjectWithList(items),
      { initialProps: { items: undefined as ProjectSummary[] | undefined } },
    );
    rerender({ items: [project("other", "Other")] });
    await waitFor(() => {
      expect(useAppStore.getState().activeProject).toBeNull();
    });
  });

  it("keeps active project when it remains in the loaded list", async () => {
    useAppStore.getState().setActiveProject({ id: "keep", name: "Keep" });
    const { rerender } = renderHook(
      ({ items }: { items: ProjectSummary[] | undefined }) => useSyncActiveProjectWithList(items),
      { initialProps: { items: undefined as ProjectSummary[] | undefined } },
    );
    rerender({ items: [project("keep", "Keep"), project("x", "X")] });
    await waitFor(() => {
      expect(useAppStore.getState().activeProject).toEqual({ id: "keep", name: "Keep" });
    });
  });

  it("does nothing when there is no active project", async () => {
    const { rerender } = renderHook(
      ({ items }: { items: ProjectSummary[] | undefined }) => useSyncActiveProjectWithList(items),
      { initialProps: { items: undefined as ProjectSummary[] | undefined } },
    );
    rerender({ items: [] });
    await waitFor(() => {
      expect(useAppStore.getState().activeProject).toBeNull();
    });
  });
});
