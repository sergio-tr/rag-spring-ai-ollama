import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  useConfigSchemaQuery,
  useDeleteProjectRagConfig,
  useProjectRagConfigQuery,
  usePutProjectRagConfig,
  usePutUserRagConfig,
  useUserRagConfigQuery,
} from "./use-rag-config";

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

describe("use-rag-config", () => {
  const qc = createTestQueryClient();

  beforeEach(() => vi.mocked(apiFetch).mockReset());

  it("loads schema", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ version: 1, fields: [] });
    const { result } = renderHook(() => useConfigSchemaQuery(), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("loads user config", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    const { result } = renderHook(() => useUserRagConfigQuery(), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("loads project config", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    const { result } = renderHook(() => useProjectRagConfigQuery("p1"), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("puts user config", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    const { result } = renderHook(() => usePutUserRagConfig(), { wrapper: wrap(qc) });
    await result.current.mutateAsync({ k: 1 });
  });

  it("puts project config", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({});
    const { result } = renderHook(() => usePutProjectRagConfig("p1"), { wrapper: wrap(qc) });
    await result.current.mutateAsync({ k: 1 });
  });

  it("deletes project config", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useDeleteProjectRagConfig("p1"), { wrapper: wrap(qc) });
    await result.current.mutateAsync();
  });
});
