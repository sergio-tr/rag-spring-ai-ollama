import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  useConfigSchemaQuery,
  useDeleteProjectRagConfig,
  useProjectRagConfigQuery,
  usePutProjectRagConfig,
  usePutUserRagConfig,
  useUserRagConfigQuery,
} from "./use-rag-config";

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

describe("use-rag-config hooks", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("useConfigSchemaQuery loads schema", async () => {
    const schema = { version: 1, fields: [{ key: "k", type: "int", userEditable: true }] };
    apiFetch.mockResolvedValueOnce(schema);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useConfigSchemaQuery(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(schema);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/config\/schema$/));
  });

  it("useUserRagConfigQuery loads user config", async () => {
    const cfg = { topK: 4 };
    apiFetch.mockResolvedValueOnce(cfg);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useUserRagConfigQuery(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(cfg);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/config\/user$/));
  });

  it("useProjectRagConfigQuery is disabled without projectId", () => {
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProjectRagConfigQuery(undefined), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("useProjectRagConfigQuery loads project config", async () => {
    apiFetch.mockResolvedValueOnce({ x: 1 });
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProjectRagConfigQuery("p9"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/config\/project\/p9$/));
  });

  it("usePutUserRagConfig puts and invalidates user config query", async () => {
    apiFetch.mockResolvedValueOnce({ saved: true });
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => usePutUserRagConfig(), { wrapper });
    await result.current.mutateAsync({ topK: 5 });
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/config\/user$/),
      expect.objectContaining({ method: "PUT" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["config", "user"] });
  });

  it("useUserStoredRagConfigQuery loads stored user config", async () => {
    const cfg = { topK: 4 };
    apiFetch.mockResolvedValueOnce(cfg);
    const { wrapper } = createWrapper();
    const { useUserStoredRagConfigQuery } = await import("./use-rag-config");
    const { result } = renderHook(() => useUserStoredRagConfigQuery(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(cfg);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/config\/user\/stored$/));
  });

  it("usePutProjectRagConfig puts and invalidates project config query", async () => {
    apiFetch.mockResolvedValueOnce({ ok: true });
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => usePutProjectRagConfig("p2"), { wrapper });
    await result.current.mutateAsync({ foo: "bar" });
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/config\/project\/p2$/),
      expect.objectContaining({ method: "PUT" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["config", "project", "p2"] });
  });

  it("useDeleteProjectRagConfig deletes and invalidates project config query", async () => {
    apiFetch.mockResolvedValueOnce(undefined);
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteProjectRagConfig("p3"), { wrapper });
    await result.current.mutateAsync();
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/config\/project\/p3$/),
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["config", "project", "p3"] });
  });
});
