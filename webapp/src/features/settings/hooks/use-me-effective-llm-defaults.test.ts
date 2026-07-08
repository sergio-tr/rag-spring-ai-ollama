import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  meEffectiveLlmDefaultsQueryKey,
  useMeEffectiveLlmDefaults,
} from "./use-me-effective-llm-defaults";

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
  return { wrapper: Wrapper };
}

describe("useMeEffectiveLlmDefaults", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads effective LLM defaults from the product API", async () => {
    const payload = {
      effectiveProvider: "OPENAI_COMPATIBLE" as const,
      chatModel: "gpt-main",
      classifierModelId: "default",
      temperature: 0.1,
      additionalParameters: { topP: 1, think: false },
    };
    apiFetch.mockResolvedValueOnce(payload);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useMeEffectiveLlmDefaults(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(payload);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/me\/llm\/effective-defaults$/));
  });

  it("uses stable query key", () => {
    expect(meEffectiveLlmDefaultsQueryKey).toEqual(["me", "llm", "effective-defaults"]);
  });
});
