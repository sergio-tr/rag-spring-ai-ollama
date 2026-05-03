import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { QueryClient } from "@tanstack/react-query";
import type { ConversationDto } from "@/types/api";
import {
  useConversationMessages,
  useConversations,
  useCreateConversation,
  useDeleteConversation,
  usePatchConversation,
} from "./use-conversations";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return {
    ...actual,
    apiFetch: vi.fn(),
  };
});

import { ApiError, apiFetch } from "@/lib/api-client";

function makeWrapper(client: QueryClient) {
  return function W({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe("use-conversations", () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 60_000 },
        mutations: { retry: false },
      },
    });
    vi.mocked(apiFetch).mockReset();
  });

  it("useConversations fetches when projectId set", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useConversations("p1"), { wrapper: makeWrapper(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("useConversationMessages fetches when id set", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useConversationMessages("c1"), { wrapper: makeWrapper(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("useCreateConversation posts and invalidates", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: "c2",
      title: "N",
      updatedAt: "",
    });
    const { result } = renderHook(() => useCreateConversation("p1"), { wrapper: makeWrapper(qc) });
    await result.current.mutateAsync();
    expect(apiFetch).toHaveBeenCalled();
  });

  it("usePatchConversation patches conversation", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: "c1",
      title: "T",
      updatedAt: "",
    });
    const { result } = renderHook(() => usePatchConversation("p1"), { wrapper: makeWrapper(qc) });
    await result.current.mutateAsync({
      conversationId: "c1",
      body: { title: "T" },
    });
    expect(apiFetch).toHaveBeenCalled();
  });

  it("usePatchConversation applies optimistic documentFilter then replaces with server response", async () => {
    const rows: ConversationDto[] = [
      {
        id: "c1",
        title: "T",
        updatedAt: "",
        presetId: null,
        documentFilter: [],
      },
    ];
    qc.setQueryData(["conversations", "p1"], rows);
    let resolvePatch!: (v: ConversationDto) => void;
    vi.mocked(apiFetch).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePatch = resolve as (v: ConversationDto) => void;
        }),
    );
    const { result } = renderHook(() => usePatchConversation("p1"), { wrapper: makeWrapper(qc) });
    const pending = result.current.mutateAsync({
      conversationId: "c1",
      body: { documentFilter: ["d1"] },
    });
    await waitFor(() => {
      expect(qc.getQueryData<ConversationDto[]>(["conversations", "p1"])?.[0].documentFilter).toEqual(["d1"]);
    });
    resolvePatch!({
      id: "c1",
      title: "T",
      updatedAt: "2026-02-02",
      presetId: null,
      documentFilter: ["d1"],
    });
    await pending;
    expect(qc.getQueryData<ConversationDto[]>(["conversations", "p1"])?.[0].updatedAt).toBe("2026-02-02");
  });

  it("usePatchConversation rolls back cache when PATCH fails", async () => {
    const rows: ConversationDto[] = [
      {
        id: "c1",
        title: "T",
        updatedAt: "",
        presetId: null,
        documentFilter: [],
      },
    ];
    qc.setQueryData(["conversations", "p1"], structuredClone(rows));
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(400, "bad filter", { kind: "http" }));
    const { result } = renderHook(() => usePatchConversation("p1"), { wrapper: makeWrapper(qc) });
    await expect(
      result.current.mutateAsync({
        conversationId: "c1",
        body: { documentFilter: ["d9"] },
      }),
    ).rejects.toBeTruthy();
    expect(qc.getQueryData<ConversationDto[]>(["conversations", "p1"])?.[0].documentFilter).toEqual([]);
  });

  it("useDeleteConversation sends DELETE and invalidates caches", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useDeleteConversation("p1"), { wrapper: makeWrapper(qc) });
    await result.current.mutateAsync("c1");
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/conversations\/c1$/),
      expect.objectContaining({ method: "DELETE" }),
    );
  });
});
