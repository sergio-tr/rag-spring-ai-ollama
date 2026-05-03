import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useModelsCatalog } from "./use-models-catalog";
import { useRagPresets } from "./use-rag-presets";

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

describe("useModelsCatalog / useRagPresets", () => {
  const qc = createTestQueryClient();

  beforeEach(() => vi.mocked(apiFetch).mockReset());

  it("loads models catalog", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      ollamaReachable: true,
      installedModelNames: [],
      allowlist: [],
    });
    const { result } = renderHook(() => useModelsCatalog(), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("loads rag presets", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useRagPresets(), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});
