import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  useConversationMessages,
  useConversations,
  useCreateConversation,
  usePatchConversation,
} from "./use-conversations";

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
  return { wrapper: Wrapper, qc };
}

const conv = { id: "c1", title: "T", updatedAt: "" };

describe("use-conversations hooks", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("useConversations does not fetch without projectId", () => {
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useConversations(undefined), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("useConversations loads list when projectId is set", async () => {
    apiFetch.mockResolvedValueOnce([conv]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useConversations("p1"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([conv]);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/projects\/p1\/conversations$/));
  });

  it("useCreateConversation posts and invalidates conversations", async () => {
    apiFetch.mockResolvedValueOnce(conv);
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useCreateConversation("p1"), { wrapper });
    await result.current.mutateAsync();
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/projects\/p1\/conversations$/),
      expect.objectContaining({ method: "POST" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["conversations", "p1"] });
  });

  it("useConversationMessages does not fetch without conversationId", () => {
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useConversationMessages(undefined), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("useConversationMessages loads messages", async () => {
    const messages = [
      {
        id: "m1",
        role: "USER" as const,
        content: "hi",
        createdAt: "",
        sources: null,
        queryType: null,
        pipelineSteps: null,
      },
    ];
    apiFetch.mockResolvedValueOnce(messages);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useConversationMessages("c1"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(messages);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/conversations\/c1\/messages$/));
  });

  it("usePatchConversation patches and invalidates conversations", async () => {
    apiFetch.mockResolvedValueOnce(conv);
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => usePatchConversation("p1"), { wrapper });
    await result.current.mutateAsync({ conversationId: "c1", body: { title: "New" } });
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/conversations\/c1$/),
      expect.objectContaining({ method: "PATCH" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["conversations", "p1"] });
  });
});
