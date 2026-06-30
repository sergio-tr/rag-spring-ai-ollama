import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useMeSelectableLlmModels } from "./use-me-selectable-llm-models";

vi.mock("@/lib/api-client", () => ({
  apiProductPath: (p: string) => `/api/v5${p}`,
  apiFetch: vi.fn(),
}));

import { apiFetch } from "@/lib/api-client";

describe("useMeSelectableLlmModels", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
  });

  it("loads selectable models from /me/llm/selectable-models", async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      effectiveProvider: "OPENAI_COMPATIBLE",
      capability: "CHAT",
      models: [
        {
          modelName: "gpt-oss:20b",
          displayName: "gpt-oss:20b",
          selectable: true,
          disabledReason: null,
          disabledReasonCode: null,
          usableAsDefault: true,
          runtimeStatus: "UNKNOWN",
        },
      ],
    });

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: qc }, children);

    const { result } = renderHook(() => useMeSelectableLlmModels("CHAT"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/me\/llm\/selectable-models\?capability=CHAT$/),
    );
    expect(result.current.data?.models).toHaveLength(1);
  });
});
