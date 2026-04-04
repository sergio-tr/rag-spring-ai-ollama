import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  useDeleteProjectDocument,
  useProjectDocuments,
  useUploadProjectDocument,
} from "./use-project-documents";

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

import { apiFetch } from "@/lib/api-client";

function wrap(qc: ReturnType<typeof createTestQueryClient>) {
  return function W({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  };
}

describe("use-project-documents", () => {
  const qc = createTestQueryClient();

  beforeEach(() => vi.mocked(apiFetch).mockReset());

  it("lists documents", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useProjectDocuments("p1"), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("uploads document", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: "d1",
      fileName: "a.txt",
      status: "READY",
      chunkCount: 0,
      errorMessage: null,
      uploadedAt: "",
      reindexedAt: null,
    });
    const { result } = renderHook(() => useUploadProjectDocument("p1"), { wrapper: wrap(qc) });
    await result.current.mutateAsync(new File(["x"], "a.txt"));
  });

  it("upload throws without project", async () => {
    const { result } = renderHook(() => useUploadProjectDocument(undefined), { wrapper: wrap(qc) });
    await expect(result.current.mutateAsync(new File([], "a.txt"))).rejects.toThrow("no_project");
  });

  it("deletes document", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useDeleteProjectDocument("p1"), { wrapper: wrap(qc) });
    await result.current.mutateAsync("d1");
  });
});
