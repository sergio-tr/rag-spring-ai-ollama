import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  useConversationMessages,
  useConversations,
  useCreateConversation,
  useDeleteConversation,
  usePatchConversation,
} from "./use-conversations";

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

describe("use-conversations", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
  });

  it("useConversations fetches when projectId set", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useConversations("p1"), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("useConversationMessages fetches when id set", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useConversationMessages("c1"), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("useCreateConversation posts and invalidates", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: "c2",
      title: "N",
      updatedAt: "",
    });
    const { result } = renderHook(() => useCreateConversation("p1"), { wrapper: wrap(qc) });
    await result.current.mutateAsync();
    expect(apiFetch).toHaveBeenCalled();
  });

  it("usePatchConversation patches conversation", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: "c1",
      title: "T",
      updatedAt: "",
    });
    const { result } = renderHook(() => usePatchConversation("p1"), { wrapper: wrap(qc) });
    await result.current.mutateAsync({
      conversationId: "c1",
      body: { title: "T" },
    });
    expect(apiFetch).toHaveBeenCalled();
  });

  it("useDeleteConversation sends DELETE and invalidates caches", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useDeleteConversation("p1"), { wrapper: wrap(qc) });
    await result.current.mutateAsync("c1");
    expect(apiFetch).toHaveBeenCalledWith("/conversations/c1", expect.objectContaining({ method: "DELETE" }));
  });
});
