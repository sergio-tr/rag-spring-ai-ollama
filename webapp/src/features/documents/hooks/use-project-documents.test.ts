import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useDeleteProjectDocument, useProjectDocuments, useUploadProjectDocument } from "./use-project-documents";

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

const doc = {
  id: "d1",
  fileName: "a.pdf",
  status: "READY" as const,
  chunkCount: 0,
  errorMessage: null,
  uploadedAt: "",
  reindexedAt: null,
};

describe("use-project-documents hooks", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("useProjectDocuments does not fetch when projectId is undefined", () => {
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProjectDocuments(undefined), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("useProjectDocuments loads documents when projectId is set", async () => {
    apiFetch.mockResolvedValueOnce([doc]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProjectDocuments("p1"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([doc]);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/projects\/p1\/documents$/));
  });

  it("useUploadProjectDocument posts file and invalidates document query", async () => {
    apiFetch.mockResolvedValueOnce(doc);
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUploadProjectDocument("p1"), { wrapper });
    const file = new File(["x"], "x.txt", { type: "text/plain" });
    await result.current.mutateAsync(file);
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/projects\/p1\/documents$/),
      expect.objectContaining({ method: "POST", body: expect.any(FormData) }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p1"] });
  });

  it("useDeleteProjectDocument deletes and invalidates document query", async () => {
    apiFetch.mockResolvedValueOnce(undefined);
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteProjectDocument("p1"), { wrapper });
    await result.current.mutateAsync("d1");
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/projects\/p1\/documents\/d1$/),
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p1"] });
  });
});
