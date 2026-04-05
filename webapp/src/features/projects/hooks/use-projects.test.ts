import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
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

describe("use-projects hooks", () => {
  beforeEach(() => {
    apiFetch.mockReset();
    useAppStore.setState({ activeProject: null });
  });

  it("useProjectList loads data and uses query params in the URL", async () => {
    const body = { items: [summary("p1", "A")], total: 1 };
    apiFetch.mockResolvedValueOnce(body);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProjectList(1, 10), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(body);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/page=1&size=10/));
  });

  it("useCreateProject posts, activates, updates store and invalidates projects", async () => {
    apiFetch
      .mockResolvedValueOnce(summary("new1", "Created"))
      .mockResolvedValueOnce({ activeProjectId: "new1" });
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useCreateProject(), { wrapper });
    await result.current.mutateAsync({ name: "Created" });
    expect(useAppStore.getState().activeProject).toEqual({ id: "new1", name: "Created" });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["config", "project", "new1"] });
  });

  it("useCreateProject clears active project on 401 ApiError", async () => {
    useAppStore.getState().setActiveProject({ id: "x", name: "X" });
    apiFetch.mockRejectedValueOnce(new apiClient.ApiError(401, "Unauthorized"));
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateProject(), { wrapper });
    await expect(result.current.mutateAsync({ name: "Bad" })).rejects.toBeDefined();
    expect(useAppStore.getState().activeProject).toBeNull();
  });

  it("usePatchProject updates active project name when it matches", async () => {
    useAppStore.getState().setActiveProject({ id: "p1", name: "Old" });
    apiFetch.mockResolvedValueOnce(summary("p1", "NewName"));
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => usePatchProject(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: "p1", name: "NewName" });
    });
    expect(useAppStore.getState().activeProject).toEqual({ id: "p1", name: "NewName" });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
  });

  it("usePatchProject does not change store when patching another project", async () => {
    useAppStore.getState().setActiveProject({ id: "active", name: "A" });
    apiFetch.mockResolvedValueOnce(summary("other", "O"));
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => usePatchProject(), { wrapper });
    await result.current.mutateAsync({ id: "other", name: "O" });
    expect(useAppStore.getState().activeProject).toEqual({ id: "active", name: "A" });
  });

  it("useDeleteProject clears active project when deleting the active id", async () => {
    useAppStore.getState().setActiveProject({ id: "del", name: "D" });
    apiFetch.mockResolvedValueOnce(undefined);
    const { wrapper, qc } = createWrapper();
    const removeSpy = vi.spyOn(qc, "removeQueries");
    const { result } = renderHook(() => useDeleteProject(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync("del");
    });
    expect(useAppStore.getState().activeProject).toBeNull();
    expect(removeSpy).toHaveBeenCalledWith({ queryKey: ["config", "project", "del"] });
    expect(removeSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "del"] });
  });

  it("useActivateProject sets active and invalidates queries", async () => {
    apiFetch.mockResolvedValueOnce({ activeProjectId: "a1" });
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useActivateProject(), { wrapper });
    await result.current.mutateAsync({ id: "a1", name: "Alpha" });
    expect(useAppStore.getState().activeProject).toEqual({ id: "a1", name: "Alpha" });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["config", "project", "a1"] });
  });

  it("useActivateProject clears active on 401", async () => {
    useAppStore.getState().setActiveProject({ id: "a1", name: "A" });
    apiFetch.mockRejectedValueOnce(new apiClient.ApiError(401, "Unauthorized"));
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useActivateProject(), { wrapper });
    await expect(result.current.mutateAsync({ id: "a1", name: "A" })).rejects.toBeDefined();
    expect(useAppStore.getState().activeProject).toBeNull();
  });
});
