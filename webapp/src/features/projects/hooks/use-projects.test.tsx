import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useAppStore } from "@/store/app.store";
import {
  useActivateProject,
  useCreateProject,
  useDeleteProject,
  usePatchProject,
  useProjectList,
} from "./use-projects";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return { ...actual, apiFetch: vi.fn() };
});

import { ApiError, apiFetch } from "@/lib/api-client";

function wrapper(qc: ReturnType<typeof createTestQueryClient>) {
  return function W({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  };
}

describe("use-projects hooks", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    useAppStore.setState({ activeProject: null });
  });

  it("useProjectList fetches projects", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ items: [], total: 0 });
    const { result } = renderHook(() => useProjectList(0, 24), {
      wrapper: wrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.total).toBe(0);
  });

  it("useCreateProject activates and invalidates on success", async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce({ id: "p1", name: "A", docCount: 0, convCount: 0, updatedAt: "" })
      .mockResolvedValueOnce({ activeProjectId: "p1" });
    const { result } = renderHook(() => useCreateProject(), { wrapper: wrapper(qc) });
    await result.current.mutateAsync({ name: "A" });
    expect(useAppStore.getState().activeProject?.id).toBe("p1");
  });

  it("usePatchProject updates active project name when ids match", async () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "Old" } });
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: "p1",
      name: "New",
      docCount: 0,
      convCount: 0,
      updatedAt: "",
    });
    const { result } = renderHook(() => usePatchProject(), { wrapper: wrapper(qc) });
    await act(async () => {
      await result.current.mutateAsync({ id: "p1", name: "New" });
    });
    expect(useAppStore.getState().activeProject?.name).toBe("New");
  });

  it("useDeleteProject clears active when deleting current", async () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "A" } });
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useDeleteProject(), { wrapper: wrapper(qc) });
    await act(async () => {
      await result.current.mutateAsync("p1");
    });
    expect(useAppStore.getState().activeProject).toBeNull();
  });

  it("useActivateProject sets active project", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ activeProjectId: "p2" });
    const { result } = renderHook(() => useActivateProject(), { wrapper: wrapper(qc) });
    await result.current.mutateAsync({ id: "p2", name: "B" });
    expect(useAppStore.getState().activeProject?.id).toBe("p2");
  });

  it("useCreateProject clears active project on 401", async () => {
    useAppStore.setState({ activeProject: { id: "p9", name: "X" } });
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(401, ""));
    const { result } = renderHook(() => useCreateProject(), { wrapper: wrapper(qc) });
    await expect(result.current.mutateAsync({ name: "fail" })).rejects.toBeDefined();
    expect(useAppStore.getState().activeProject).toBeNull();
  });

  it("useActivateProject clears active on 401", async () => {
    useAppStore.setState({ activeProject: { id: "p9", name: "X" } });
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(401, ""));
    const { result } = renderHook(() => useActivateProject(), { wrapper: wrapper(qc) });
    await expect(result.current.mutateAsync({ id: "p2", name: "B" })).rejects.toBeDefined();
    expect(useAppStore.getState().activeProject).toBeNull();
  });
});
