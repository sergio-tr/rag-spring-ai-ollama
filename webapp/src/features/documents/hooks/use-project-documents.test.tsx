import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { ProjectDocumentDto } from "@/types/api";
import { apiFetch } from "@/lib/api-client";
import { useDeleteAllProjectDocuments } from "./use-project-documents";

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiProductPath: (p: string) => p,
  };
});

const doc = (id: string, fileName: string): ProjectDocumentDto => ({
  id,
  fileName,
  status: "READY",
  chunkCount: 1,
  errorMessage: null,
  uploadedAt: "2026-01-01T00:00:00Z",
  reindexedAt: null,
  corpusScope: "PROJECT_SHARED",
  conversationId: null,
  currentIndexSnapshotId: null,
  indexSignatureHash: null,
  storagePresent: true,
});

describe("useDeleteAllProjectDocuments", () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  }

  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    qc.clear();
  });

  it("lists documents then issues one DELETE per id for the same project only", async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce([doc("d1", "a.pdf"), doc("d2", "b.pdf")])
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce(undefined);

    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");

    const { result } = renderHook(() => useDeleteAllProjectDocuments("p-scope"), { wrapper });
    await result.current.mutateAsync();

    expect(apiFetch).toHaveBeenCalledTimes(3);
    expect(String(vi.mocked(apiFetch).mock.calls[1]?.[0])).toContain("/projects/p-scope/documents/d1");
    expect(String(vi.mocked(apiFetch).mock.calls[2]?.[0])).toContain("/projects/p-scope/documents/d2");
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p-scope"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
  });
});
