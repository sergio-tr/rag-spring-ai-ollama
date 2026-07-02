"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  buildProjectCompatiblePresetsPath,
  projectCompatiblePresetsQueryKey,
  useProjectCompatiblePresets,
} from "@/features/chat/hooks/use-project-compatible-presets";
import { apiFetch } from "@/lib/api-client";

const profileMock = vi.hoisted(() => ({
  useProjectIndexProfile: vi.fn(),
}));

vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: (...args: unknown[]) => profileMock.useProjectIndexProfile(...args),
}));

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return {
    ...actual,
    apiFetch: vi.fn(),
  };
});

function wrap(qc: QueryClient) {
  function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: qc }, children);
  }
  Wrapper.displayName = "QueryClientWrapper";
  return Wrapper;
}

describe("useProjectCompatiblePresets", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    profileMock.useProjectIndexProfile.mockReturnValue({
      data: { embeddingModelId: "mxbai-embed-large" },
      isLoading: false,
      isError: false,
    });
  });

  it("calls compatible-presets with projectId and embedding model from index profile", async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      projectId: "p1",
      effectiveEmbeddingModelId: "mxbai-embed-large",
      hasActiveIndex: true,
      readyDocumentCount: 1,
      activeSnapshotCapabilities: null,
      productPresets: [],
      experimentalPresets: [],
    });
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useProjectCompatiblePresets("p1"), { wrapper: wrap(qc) });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiFetch).toHaveBeenCalledWith(
      buildProjectCompatiblePresetsPath("p1", "mxbai-embed-large"),
    );
    expect(projectCompatiblePresetsQueryKey("p1", "mxbai-embed-large")).toEqual([
      "projects",
      "p1",
      "compatible-presets",
      "mxbai-embed-large",
    ]);
  });

  it("does not fetch when projectId is missing", async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useProjectCompatiblePresets(""), { wrapper: wrap(qc) });

    await waitFor(() => expect(result.current.fetchStatus).toBe("idle"));
    expect(apiFetch).not.toHaveBeenCalled();
    expect(result.current.data).toBeUndefined();
  });

  it("surfaces API errors without fallback data", async () => {
    vi.mocked(apiFetch).mockRejectedValue(new Error("404"));
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useProjectCompatiblePresets("p1"), { wrapper: wrap(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.data).toBeUndefined();
  });

  it("refetches when projectId changes", async () => {
    vi.mocked(apiFetch).mockImplementation(async (url) => {
      const path = String(url);
      if (path.includes("/projects/p1/")) {
        return {
          projectId: "p1",
          effectiveEmbeddingModelId: null,
          hasActiveIndex: false,
          readyDocumentCount: 0,
          activeSnapshotCapabilities: null,
          productPresets: [
            {
              preset: { id: "p1-only", name: "Project one preset" },
              indexRequirements: null,
              compatibility: {
                selectable: true,
                disabledReasonCode: null,
                disabledReason: null,
                indexRequirements: null,
                compatibleWithActiveIndex: true,
              },
            },
          ],
          experimentalPresets: [],
        };
      }
      return {
        projectId: "p2",
        effectiveEmbeddingModelId: null,
        hasActiveIndex: false,
        readyDocumentCount: 0,
        activeSnapshotCapabilities: null,
        productPresets: [
          {
            preset: { id: "p2-only", name: "Project two preset" },
            indexRequirements: null,
            compatibility: {
              selectable: true,
              disabledReasonCode: null,
              disabledReason: null,
              indexRequirements: null,
              compatibleWithActiveIndex: true,
            },
          },
        ],
        experimentalPresets: [],
      };
    });

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result, rerender } = renderHook(
      ({ projectId }: { projectId: string }) => useProjectCompatiblePresets(projectId),
      {
        wrapper: wrap(qc),
        initialProps: { projectId: "p1" },
      },
    );

    await waitFor(() => expect(result.current.data?.productPresets[0]?.preset.id).toBe("p1-only"));
    rerender({ projectId: "p2" });
    await waitFor(() => expect(result.current.data?.productPresets[0]?.preset.id).toBe("p2-only"));
  });
});
