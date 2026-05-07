import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { CHAT_DETERMINISTIC_DEFAULT_PRESET_ID } from "@/features/chat/lib/conversation-preset-ui";
import {
  mergeConversationPatchOptimistic,
  useConversationMessages,
  useConversations,
  useCreateConversation,
  useMoveConversation,
  usePatchConversation,
} from "./use-conversations";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

const setActiveProject = vi.fn();
const getState = vi.fn(() => ({ activeProject: { id: "p1", name: "P1" } }));
function useAppStore(sel: (s: { setActiveProject: typeof setActiveProject }) => unknown) {
  return sel({ setActiveProject });
}
useAppStore.getState = getState;

vi.mock("@/store/app.store", () => ({
  useAppStore,
}));

const apiFetch = vi.mocked(apiClient.apiFetch);

function createWrapper() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 60_000 },
      mutations: { retry: false },
    },
  });
  function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: qc }, children);
  }
  return { wrapper: Wrapper, qc };
}

const conv = { id: "c1", title: "T", updatedAt: "" };

describe("use-conversations hooks", () => {
  it("mergeConversationPatchOptimistic aligns effectivePresetId with presetId", () => {
    const row = {
      id: "c1",
      title: "t",
      updatedAt: "",
      presetId: null as string | null,
      effectivePresetId: null as string | null,
      documentFilter: [] as string[],
    };
    const next = mergeConversationPatchOptimistic(row, { presetId: "pid-1" });
    expect(next.presetId).toBe("pid-1");
    expect(next.effectivePresetId).toBe("pid-1");
  });

  it("mergeConversationPatchOptimistic clears preset and restores deterministic effective id", () => {
    const row = {
      id: "c1",
      title: "t",
      updatedAt: "",
      presetId: "x",
      effectivePresetId: "x",
      documentFilter: [] as string[],
    };
    const next = mergeConversationPatchOptimistic(row, { clearPreset: true });
    expect(next.presetId).toBeNull();
    expect(next.effectivePresetId).toBe(CHAT_DETERMINISTIC_DEFAULT_PRESET_ID);
  });

  beforeEach(() => {
    apiFetch.mockReset();
    setActiveProject.mockReset();
    getState.mockReset();
    getState.mockReturnValue({ activeProject: { id: "p1", name: "P1" } });
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
    await result.current.mutateAsync(undefined);
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

  it("usePatchConversation patches and merges the conversation row into query cache", async () => {
    const row = {
      id: "c1",
      title: "Old",
      updatedAt: "t0",
      presetId: null as string | null,
      documentFilter: [] as string[],
    };
    apiFetch.mockResolvedValueOnce({
      id: "c1",
      title: "New",
      updatedAt: "t1",
      presetId: null,
      documentFilter: [],
    });
    const { wrapper, qc } = createWrapper();
    qc.setQueryData(["conversations", "p1"], [row]);
    const { result } = renderHook(() => usePatchConversation("p1"), { wrapper });
    await result.current.mutateAsync({ conversationId: "c1", body: { title: "New" } });
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/conversations\/c1$/),
      expect.objectContaining({ method: "PATCH" }),
    );
    expect(qc.getQueryData<typeof row[]>(["conversations", "p1"])?.[0]?.title).toBe("New");
    expect(qc.getQueryData<typeof row[]>(["conversations", "p1"])?.[0]?.updatedAt).toBe("t1");
  });

  it("useMoveConversation invalidates related keys and switches active project when moving from the active one", async () => {
    apiFetch.mockResolvedValueOnce(undefined);
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useMoveConversation(), { wrapper });
    await result.current.mutateAsync({
      sourceProjectId: "p1",
      conversationId: "c1",
      destinationProjectId: "p2",
      destinationProjectName: "P2",
    });

    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringContaining("/projects/p1/conversations/c1/move?"),
      expect.objectContaining({ method: "POST" }),
    );
    // Spot-check invalidations for key groups
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["conversations", "p1"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["conversations", "p2"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["messages", "c1"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
    expect(setActiveProject).toHaveBeenCalledWith({ id: "p2", name: "P2" });
  });

  it("useMoveConversation does not switch active project when moving from a different project", async () => {
    apiFetch.mockResolvedValueOnce(undefined);
    getState.mockReturnValue({ activeProject: { id: "pX", name: "PX" } });
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useMoveConversation(), { wrapper });
    await result.current.mutateAsync({
      sourceProjectId: "p1",
      conversationId: "c1",
      destinationProjectId: "p2",
      destinationProjectName: "P2",
    });
    expect(setActiveProject).not.toHaveBeenCalled();
  });
});
