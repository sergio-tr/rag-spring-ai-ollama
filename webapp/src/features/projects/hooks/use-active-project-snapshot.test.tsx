import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useActiveProjectSnapshot } from "./use-active-project-snapshot";

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

describe("useActiveProjectSnapshot", () => {
  beforeEach(() => {
    apiMock.apiFetch.mockReset();
  });

  it("does not fetch when projectId is missing", () => {
    const { result } = renderHook(() => useActiveProjectSnapshot(undefined), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(apiMock.apiFetch).not.toHaveBeenCalled();
  });

  it("loads active knowledge snapshot", async () => {
    apiMock.apiFetch.mockResolvedValueOnce({
      id: "snap-1",
      signatureHash: "abc",
      scopeType: "PROJECT",
      status: "ACTIVE",
      indexProfileHash: "h1",
      indexProfile: {},
      createdAt: "",
      updatedAt: "",
    });
    const { result } = renderHook(() => useActiveProjectSnapshot("p1"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiMock.apiFetch).toHaveBeenCalledWith("/projects/p1/knowledge/snapshots/active");
    expect(result.current.data?.id).toBe("snap-1");
  });
});
