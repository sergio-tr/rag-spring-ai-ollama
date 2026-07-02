import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useAppStore } from "@/store/app.store";
import { useCreateProject, useProjectList } from "@/features/projects/hooks/use-projects";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

const apiFetch = vi.mocked(apiClient.apiFetch);

function createWrapper() {
  const qc = createTestQueryClient();
  function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: qc }, children);
  }
  return { wrapper: Wrapper, qc };
}

const summary = (id: string, name: string) => ({
  id,
  name,
  docCount: 0,
  convCount: 0,
  updatedAt: "t",
});

describe("ProjectCreationStateSync", () => {
  beforeEach(() => {
    apiFetch.mockReset();
    useAppStore.setState({ activeProject: null });
  });

  it("prepends created project to list cache before refetch completes", async () => {
    const { wrapper, qc } = createWrapper();
    qc.setQueryData(["projects", 0, 24], { items: [summary("old", "Old")], total: 1 });

    apiFetch
      .mockResolvedValueOnce({ items: [summary("old", "Old")], total: 1 })
      .mockResolvedValueOnce(summary("new", "New"))
      .mockResolvedValueOnce({ activeProjectId: "new" })
      .mockResolvedValueOnce({
        items: [summary("new", "New"), summary("old", "Old")],
        total: 2,
      });

    const listHook = renderHook(() => useProjectList(0, 24), { wrapper });
    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));

    const createHook = renderHook(() => useCreateProject(), { wrapper });
    await act(async () => {
      await createHook.result.current.mutateAsync({ name: "New" });
    });

    const cached = qc.getQueryData<{ items: { id: string }[] }>(["projects", 0, 24]);
    expect(cached?.items.map((p) => p.id)).toEqual(["new", "old"]);
    expect(useAppStore.getState().activeProject).toEqual({ id: "new", name: "New" });
  });

  it("keeps cached project visible when refetch fails after create", async () => {
    const { wrapper, qc } = createWrapper();
    qc.setQueryData(["projects", 0, 24], { items: [], total: 0 });

    apiFetch
      .mockResolvedValueOnce({ items: [], total: 0 })
      .mockResolvedValueOnce(summary("stay", "Stay"))
      .mockResolvedValueOnce({ activeProjectId: "stay" });
    vi.spyOn(qc, "invalidateQueries").mockRejectedValueOnce(new Error("list down"));

    const listHook = renderHook(() => useProjectList(0, 24), { wrapper });
    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));

    const createHook = renderHook(() => useCreateProject(), { wrapper });
    const outcome = await act(async () =>
      createHook.result.current.mutateAsync({ name: "Stay" }),
    );

    expect(outcome.refreshFailed).toBe(true);
    const cached = qc.getQueryData<{ items: { id: string }[] }>(["projects", 0, 24]);
    expect(cached?.items.some((p) => p.id === "stay")).toBe(true);
    expect(createHook.result.current.isError).toBe(false);
  });
});
