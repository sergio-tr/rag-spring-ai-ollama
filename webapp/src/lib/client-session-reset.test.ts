import { QueryClient } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SIDEBAR_STORAGE_KEY } from "@/components/layout/sidebar-persistence";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { useAccountExportSessionStore } from "@/features/settings/store/account-export-session.store";
import { useTraceStore } from "@/features/trace/trace.store";
import { getClientSessionEpoch } from "@/lib/client-session-epoch";
import { resetClientSessionState, resetRegisteredClientSessionState } from "@/lib/client-session-reset";
import * as queryClientRegistry from "@/lib/query-client-registry";
import { registerAppQueryClient } from "@/lib/query-client-registry";
import { useAppStore } from "@/store/app.store";
import { useChatExplainStore } from "@/store/chat-explain.store";

describe("resetClientSessionState", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 } },
    });

    localStorage.clear();
    sessionStorage.clear();

    useAppStore.setState({ activeProject: null });
    useLabJobSessionStore.setState({
      records: [],
      pendingResume: null,
      resumeNonce: 0,
      forgetWatchNonce: 0,
    });
    useAccountExportSessionStore.getState().__resetForTests();
    useChatExplainStore.setState({
      lastDone: null,
      streamingText: "",
      isStreaming: false,
    });
    useChatToolbarStore.setState({ api: null });
    useTraceStore.setState({ events: [] });
  });

  it("clears React Query cache", async () => {
    const cancelQueries = vi.spyOn(queryClient, "cancelQueries").mockResolvedValue();
    queryClient.setQueryData(["projects"], [{ id: "p1", name: "Alpha" }]);
    expect(queryClient.getQueryData(["projects"])).toBeDefined();

    await resetClientSessionState({ queryClient });

    expect(cancelQueries).toHaveBeenCalled();
    expect(queryClient.getQueryData(["projects"])).toBeUndefined();
  });

  it("clears activeProject and rag-app persistence", async () => {
    useAppStore.getState().setActiveProject({ id: "p1", name: "Alpha" });
    localStorage.setItem(
      "rag-app",
      JSON.stringify({ state: { activeProject: { id: "p1", name: "Alpha" } }, version: 0 }),
    );

    await resetClientSessionState({ queryClient });

    expect(useAppStore.getState().activeProject).toBeNull();
    expect(localStorage.getItem("rag-app")).toBeNull();
  });

  it("clears lab job session", async () => {
    useLabJobSessionStore.setState({
      records: [
        {
          jobId: "job-1",
          sectionKey: "classifier-eval",
          accepted: {
            jobId: "job-1",
            status: "QUEUED",
            pollPath: "/lab/jobs/job-1",
            streamPath: "/lab/jobs/job-1/events",
          },
          evaluationRunId: null,
          followMode: "poll",
          startedAtMs: Date.now(),
          lastUpdatedMs: Date.now(),
          lastStatus: null,
          stoppedWatching: false,
          staleNotFound: false,
          pollTimedOut: false,
          dismissedTerminal: false,
        },
      ],
      pendingResume: { sectionKey: "classifier-eval", jobId: "job-1" },
      resumeNonce: 2,
      forgetWatchNonce: 1,
    });
    sessionStorage.setItem("rag-lab-jobs", "{}");

    await resetClientSessionState({ queryClient });

    expect(useLabJobSessionStore.getState().records).toEqual([]);
    expect(useLabJobSessionStore.getState().pendingResume).toBeNull();
    expect(sessionStorage.getItem("rag-lab-jobs")).toBeNull();
  });

  it("clears chat explain state", async () => {
    useChatExplainStore.setState({
      lastDone: { answer: "secret" } as never,
      streamingText: "partial",
      isStreaming: true,
    });

    await resetClientSessionState({ queryClient });

    expect(useChatExplainStore.getState().lastDone).toBeNull();
    expect(useChatExplainStore.getState().streamingText).toBe("");
    expect(useChatExplainStore.getState().isStreaming).toBe(false);
  });

  it("clears prefixed localStorage keys", async () => {
    localStorage.setItem("chat-last-conversation-v1:proj-1", "conv-1");
    localStorage.setItem("chat-llm-model-preference-v1:proj-1", "gpt-4");
    localStorage.setItem("lab:evaluation-draft:v1:classifier", "{}");
    localStorage.setItem("rag-lab-form-v1:eval", "{}");

    await resetClientSessionState({ queryClient });

    expect(localStorage.getItem("chat-last-conversation-v1:proj-1")).toBeNull();
    expect(localStorage.getItem("chat-llm-model-preference-v1:proj-1")).toBeNull();
    expect(localStorage.getItem("lab:evaluation-draft:v1:classifier")).toBeNull();
    expect(localStorage.getItem("rag-lab-form-v1:eval")).toBeNull();
  });

  it("clears expandedProjectIds from rag-sidebar while preserving layout prefs", async () => {
    localStorage.setItem(
      SIDEBAR_STORAGE_KEY,
      JSON.stringify({
        projectsCollapsed: true,
        expandedProjectIds: ["proj-a", "proj-b"],
        shellCollapsed: true,
        sidebarWidthPx: 300,
      }),
    );

    await resetClientSessionState({ queryClient });

    const sidebar = JSON.parse(localStorage.getItem(SIDEBAR_STORAGE_KEY) ?? "{}");
    expect(sidebar.expandedProjectIds).toEqual([]);
    expect(sidebar.shellCollapsed).toBe(true);
    expect(sidebar.sidebarWidthPx).toBe(300);
    expect(sidebar.projectsCollapsed).toBe(true);
  });

  it("clears user-scoped sessionStorage keys", async () => {
    sessionStorage.setItem("rag-lab-jobs", "{}");
    sessionStorage.setItem("rag-account-export-session-v1", "{}");
    sessionStorage.setItem("settings:last-pathname", "/settings/account");

    await resetClientSessionState({ queryClient });

    expect(sessionStorage.getItem("rag-lab-jobs")).toBeNull();
    expect(sessionStorage.getItem("rag-account-export-session-v1")).toBeNull();
    expect(sessionStorage.getItem("settings:last-pathname")).toBeNull();
  });

  it("preserves theme in localStorage", async () => {
    localStorage.setItem("theme", "dark");

    await resetClientSessionState({ queryClient });

    expect(localStorage.getItem("theme")).toBe("dark");
  });

  it("bumps client session epoch so project query keys cannot reuse stale cache", async () => {
    const before = getClientSessionEpoch();
    await resetClientSessionState({ queryClient });
    expect(getClientSessionEpoch()).toBe(before + 1);
  });

  it("skips persisted UI wipe when wipePersistedUi is false", async () => {
    localStorage.setItem("chat-last-conversation-v1:proj-1", "conv-1");

    await resetClientSessionState({ queryClient, wipePersistedUi: false });

    expect(localStorage.getItem("chat-last-conversation-v1:proj-1")).toBe("conv-1");
  });
});

describe("resetRegisteredClientSessionState", () => {
  it("uses registered query client when none is passed", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 } },
    });
    registerAppQueryClient(queryClient);
    queryClient.setQueryData(["projects"], [{ id: "p1" }]);

    await resetRegisteredClientSessionState();

    expect(queryClient.getQueryData(["projects"])).toBeUndefined();
  });

  it("no-ops when resolveAppQueryClient returns null", async () => {
    const spy = vi.spyOn(queryClientRegistry, "resolveAppQueryClient").mockReturnValue(null);
    await expect(resetRegisteredClientSessionState()).resolves.toBeUndefined();
    spy.mockRestore();
  });

  it("prefers explicit query client over registered", async () => {
    const registered = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 } },
    });
    const explicit = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 } },
    });
    registerAppQueryClient(registered);
    registered.setQueryData(["registered"], true);
    explicit.setQueryData(["explicit"], true);

    await resetRegisteredClientSessionState({ queryClient: explicit });

    expect(explicit.getQueryData(["explicit"])).toBeUndefined();
    expect(registered.getQueryData(["registered"])).toBe(true);
  });
});
