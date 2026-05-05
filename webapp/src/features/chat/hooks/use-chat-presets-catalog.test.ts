"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import React from "react";
import { describe, expect, it, vi } from "vitest";
import { useChatPresetsCatalog } from "@/features/chat/hooks/use-chat-presets-catalog";
import { apiFetch } from "@/lib/api-client";

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

describe("useChatPresetsCatalog", () => {
  it("loads unified catalog from /chat/presets/catalog", async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      productPresets: [],
      experimentalPresets: [],
    });
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useChatPresetsCatalog(), { wrapper: wrap(qc) });
    await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/chat\/presets\/catalog$/));
  });
});

