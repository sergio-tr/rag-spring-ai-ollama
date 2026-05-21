import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  projectIndexProfileQueryKey,
  useProjectIndexProfile,
  useUpsertProjectIndexProfile,
} from "./use-project-index-profile";

const apiMock = vi.hoisted(() => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return { ...actual, apiFetch: apiMock.apiFetch, apiProductPath: apiMock.apiProductPath };
});

function wrapper({ children }: { children: ReactNode }) {
  const qc = createTestQueryClient();
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe("useProjectIndexProfile", () => {
  beforeEach(() => {
    apiMock.apiFetch.mockReset();
  });

  it("stays disabled when projectId is missing", () => {
    const { result } = renderHook(() => useProjectIndexProfile(null), { wrapper });
    expect(result.current.isFetching).toBe(false);
    expect(result.current.fetchStatus).toBe("idle");
    expect(apiMock.apiFetch).not.toHaveBeenCalled();
  });

  it("loads index profile for a project", async () => {
    apiMock.apiFetch.mockResolvedValueOnce({
      projectId: "p1",
      materializationStrategy: "CHUNK_LEVEL",
      metadataEnabled: false,
      metadataProfile: null,
      embeddingModelId: "emb",
      chunkMaxChars: 400,
      chunkOverlap: null,
      profileHash: "h1",
      createdAt: "",
      updatedAt: "",
    });
    const { result } = renderHook(() => useProjectIndexProfile("p1"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiMock.apiFetch).toHaveBeenCalledWith("/projects/p1/index-profile");
    expect(result.current.data?.projectId).toBe("p1");
  });
});

describe("useUpsertProjectIndexProfile", () => {
  beforeEach(() => {
    apiMock.apiFetch.mockReset();
  });

  it("PUTs profile and invalidates the query cache", async () => {
    apiMock.apiFetch.mockResolvedValueOnce({
      projectId: "p2",
      materializationStrategy: "CHUNK_LEVEL",
      metadataEnabled: true,
      metadataProfile: "default",
      embeddingModelId: "emb",
      chunkMaxChars: 500,
      chunkOverlap: 10,
      profileHash: "h2",
      createdAt: "",
      updatedAt: "",
    });
    const qc = createTestQueryClient();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const wrapperWithSpy = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useUpsertProjectIndexProfile("p2"), { wrapper: wrapperWithSpy });
    await result.current.mutateAsync({ chunkMaxChars: 500 });
    expect(apiMock.apiFetch).toHaveBeenCalledWith(
      "/projects/p2/index-profile",
      expect.objectContaining({ method: "PUT" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: projectIndexProfileQueryKey("p2") });
  });
});
