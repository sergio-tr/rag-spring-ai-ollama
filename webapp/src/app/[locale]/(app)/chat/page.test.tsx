import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import type {
  AsyncTaskStatusDto,
  ConversationDto,
  MessageDto,
  PatchConversationBody,
  ProjectDocumentDto,
} from "@/types/api";
import { mergeConversationPatchOptimistic } from "@/features/chat/hooks/use-conversations";
import { ChatToolbarOverflowMenu } from "@/features/chat/components/ChatToolbarOverflowMenu";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import ChatPage from "./page";

/** Stable references — page effects depend on these store actions; new vi.fn() each render would clear optimistic UI every paint. */
const chatExplainMocks = vi.hoisted(() => ({
  setLastDone: vi.fn(),
  setStreamingText: vi.fn(),
  resetStreaming: vi.fn(),
  setStreaming: vi.fn(),
}));

const routerPushMock = vi.fn();

vi.mock("@/navigation", () => ({
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
  useRouter: () => ({ push: routerPushMock, refresh: vi.fn() }),
  usePathname: () => "/en/chat",
}));

vi.mock("@/features/projects/hooks/use-sync-active-project-from-chat-url", () => ({
  useSyncActiveProjectFromChatUrl: () => {},
}));

const followLabJob = vi.fn();
vi.mock("@/lib/lab-job-follow", () => ({ followLabJob: (...a: unknown[]) => followLabJob(...a) }));

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiProductPath: (p: string) => p,
  };
});

import { ApiError, apiFetch, createHttpApiError } from "@/lib/api-client";
import { useTraceStore } from "@/features/trace/trace.store";

vi.mock("@/store/app.store", () => ({
  useAppStore: (sel: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    sel({ activeProject: { id: "p1", name: "P" } }),
}));

const DEFAULT_EFFECTIVE_PRESET_ID = "cafe0001-0001-4001-8001-000000000001";

const mockConvRows: ConversationDto[] = [
  {
    id: "c1",
    title: "T1",
    updatedAt: "",
    presetId: null,
    effectivePresetId: DEFAULT_EFFECTIVE_PRESET_ID,
    documentFilter: [],
  },
];

const deleteMutateAsync = vi.fn().mockResolvedValue(undefined);
const createMutateAsync = vi.fn().mockResolvedValue({
  id: "c2",
  title: "New",
  updatedAt: "",
  presetId: null,
});

vi.mock("@/features/chat/hooks/use-conversations", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/chat/hooks/use-conversations")>();
  return {
    ...actual,
    useCreateConversation: () => ({
      mutateAsync: createMutateAsync,
      isPending: false,
    }),
    useDeleteConversation: () => ({
      mutateAsync: deleteMutateAsync,
      isPending: false,
      reset: vi.fn(),
    }),
    useMoveConversation: () => ({
      mutateAsync: vi.fn().mockResolvedValue(undefined),
      isPending: false,
      isError: false,
    }),
  };
});

const docsMocks = vi.hoisted(() => {
  const projectDocsDataRef: { current: ProjectDocumentDto[] } = {
    current: [
      {
        id: "d1",
        fileName: "f.pdf",
        status: "READY" as const,
        chunkCount: 1,
        errorMessage: null,
        uploadedAt: "",
        reindexedAt: null,
        corpusScope: "PROJECT_SHARED" as const,
        conversationId: null,
        currentIndexSnapshotId: null,
        indexSignatureHash: null,
        storagePresent: true,
      },
      {
        id: "d2",
        fileName: "second.pdf",
        status: "READY" as const,
        chunkCount: 1,
        errorMessage: null,
        uploadedAt: "",
        reindexedAt: null,
        corpusScope: "PROJECT_SHARED" as const,
        conversationId: null,
        currentIndexSnapshotId: null,
        indexSignatureHash: null,
        storagePresent: true,
      },
    ],
  };
  const uploadMutateAsyncMock = vi.fn();
  const projectDocsRefetchMock = vi.fn(async () => ({ data: projectDocsDataRef.current }));
  return { projectDocsDataRef, uploadMutateAsyncMock, projectDocsRefetchMock };
});

const { uploadMutateAsyncMock, projectDocsRefetchMock, projectDocsDataRef } = docsMocks;

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => ({
    get data() {
      return projectDocsDataRef.current;
    },
    refetch: projectDocsRefetchMock,
  }),
  useUploadProjectDocument: () => ({
    mutateAsync: uploadMutateAsyncMock,
    isPending: false,
    isError: false,
    error: null,
    reset: vi.fn(),
  }),
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useProjectList: () => ({
    data: {
      items: [
        {
          id: "p1",
          name: "P",
          docCount: 0,
          convCount: 0,
          updatedAt: "",
          colorHex: "#ff0000",
          iconKey: "folder",
        },
      ],
      total: 1,
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/chat/hooks/use-models-catalog", () => ({
  useModelsCatalog: () => ({
    data: {
      ollamaReachable: true,
      installedModelNames: [],
      allowlist: [{ name: "llama", type: "LLM" as const, inAllowlist: true, installedInOllama: true }],
    },
    isError: false,
  }),
}));

/** Default presets catalog (tests override via mockRagPresetsFactory). */
let ragPresetsData: {
  id: string;
  name: string;
  description: null;
  tags: never[];
  values: Record<string, unknown>;
  system: boolean;
  createdAt: string;
  updatedAt: string;
}[] = [{ id: "pr1", name: "P", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" }];

vi.mock("@/features/chat/hooks/use-rag-presets", () => ({
  useRagPresets: () => ({
    data: ragPresetsData,
    isError: false,
    isLoading: false,
  }),
}));

const initialChatMessages: MessageDto[] = [
  {
    id: "m1",
    role: "USER",
    content: "hi",
    createdAt: "",
    sources: null,
    queryType: null,
    pipelineSteps: null,
    status: "DONE",
  },
  {
    id: "m2",
    role: "ASSISTANT",
    content: "Answering without retrieval due to a temporary issue.",
    createdAt: "",
    sources: null,
    queryType: null,
    pipelineSteps: null,
    status: "ERROR",
  },
];

let chatMessagesStore: MessageDto[] = [];

const terminalSucceeded: AsyncTaskStatusDto = {
  id: "t1",
  taskType: "LAB",
  status: "SUCCEEDED",
  progressText: null,
  result: null,
  errorMessage: null,
  terminal: true,
  createdAt: "",
  updatedAt: "",
  startedAt: null,
  completedAt: null,
};

const terminalFailedWithHtmlMessage: AsyncTaskStatusDto = {
  ...terminalSucceeded,
  status: "FAILED",
  errorMessage: "<html><body>502 Bad Gateway</body></html>",
};

const terminalFailedDocScope: AsyncTaskStatusDto = {
  ...terminalSucceeded,
  status: "FAILED",
  failureCode: "CHAT_DOCUMENT_SCOPE_EMPTY",
  errorMessage: "technical-detail-should-not-display-when-code-mapped",
};

function patchConversationApiCalls() {
  return vi.mocked(apiFetch).mock.calls.filter((call) => {
    const url = call[0] as string | URL;
    const init = call[1] as RequestInit | undefined;
    const u = typeof url === "string" ? url : url.toString();
    const method = (init?.method ?? "GET").toUpperCase();
    return (
      method === "PATCH" && /\/conversations\/[^/]+\/?$/.test(u) && !u.includes("/messages")
    );
  });
}

function defaultApiFetch(url: string | { toString(): string }, init?: RequestInit): Promise<unknown> {
  const u = typeof url === "string" ? url : url.toString();
  const method = (init?.method ?? "GET").toUpperCase();
  if (method === "PATCH" && /\/conversations\/([^/]+)\/?$/.test(u) && !u.includes("/messages")) {
    const idMatch = u.match(/\/conversations\/([^/]+)\/?$/);
    const cid = idMatch?.[1];
    const body = JSON.parse(String(init?.body ?? "{}")) as PatchConversationBody;
    const row = mockConvRows.find((c) => c.id === cid);
    if (!row) return Promise.resolve({});
    const merged = mergeConversationPatchOptimistic(row, body);
    Object.assign(row, merged);
    return Promise.resolve({ ...merged });
  }
  if (u.includes("/draft")) return Promise.resolve({ content: "" });
  if (u.includes("/conversations/c1/messages") && method === "GET") {
    return Promise.resolve([...chatMessagesStore]);
  }
  if (method === "POST" && u.includes("/conversations/c1/messages")) {
    if (u.includes("/retry")) {
      return Promise.resolve({ jobId: "j2", status: "RUNNING", pollPath: "/x", streamPath: "/y" });
    }
    const body = JSON.parse(String(init?.body ?? "{}")) as { content?: string };
    chatMessagesStore = [
      ...chatMessagesStore,
      {
        id: `u-${chatMessagesStore.length + 1}`,
        role: "USER",
        content: body.content ?? "",
        createdAt: "",
        sources: null,
        queryType: null,
        pipelineSteps: null,
        status: "DONE",
      },
    ];
    return Promise.resolve({
      jobId: "j1",
      status: "RUNNING",
      pollPath: "/x",
      streamPath: "/y",
    });
  }
  return Promise.resolve({});
}

vi.mock("@/store/chat-explain.store", () => ({
  useChatExplainStore: (sel: (s: Record<string, unknown>) => unknown) =>
    sel({
      setLastDone: chatExplainMocks.setLastDone,
      setStreamingText: chatExplainMocks.setStreamingText,
      resetStreaming: chatExplainMocks.resetStreaming,
      setStreaming: chatExplainMocks.setStreaming,
      isStreaming: false,
      streamingText: "",
    }),
}));

/** Mirrors AppShell: toolbar overflow reads chat state from the chat page via the chat toolbar store. */
function ChatPageWithToolbar() {
  return (
    <>
      <ChatToolbarOverflowMenu />
      <ChatPage />
    </>
  );
}

async function openChatToolbarOverflow(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByTestId("chat-actions-menu-trigger"));
}

function renderChat() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: Infinity },
      mutations: { retry: false },
    },
  });
  qc.setQueryData(["conversations", "p1"], structuredClone(mockConvRows));
  const view = render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <ChatPageWithToolbar />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
  return { qc, ...view };
}

describe("ChatPage", () => {
  beforeEach(() => {
    uploadMutateAsyncMock.mockReset();
    uploadMutateAsyncMock.mockResolvedValue({
      id: "d-up",
      fileName: "up.pdf",
      status: "READY",
      chunkCount: 0,
      errorMessage: null,
      uploadedAt: "",
      reindexedAt: null,
      corpusScope: "PROJECT_SHARED",
      conversationId: null,
      currentIndexSnapshotId: null,
      indexSignatureHash: null,
      storagePresent: true,
    });
    projectDocsRefetchMock.mockClear();
    projectDocsRefetchMock.mockImplementation(async () => ({ data: projectDocsDataRef.current }));
    projectDocsDataRef.current = [
      {
        id: "d1",
        fileName: "f.pdf",
        status: "READY",
        chunkCount: 1,
        errorMessage: null,
        uploadedAt: "",
        reindexedAt: null,
        corpusScope: "PROJECT_SHARED",
        conversationId: null,
        currentIndexSnapshotId: null,
        indexSignatureHash: null,
        storagePresent: true,
      },
      {
        id: "d2",
        fileName: "second.pdf",
        status: "READY",
        chunkCount: 1,
        errorMessage: null,
        uploadedAt: "",
        reindexedAt: null,
        corpusScope: "PROJECT_SHARED",
        conversationId: null,
        currentIndexSnapshotId: null,
        indexSignatureHash: null,
        storagePresent: true,
      },
    ];
    chatMessagesStore = structuredClone(initialChatMessages);
    mockConvRows.length = 0;
    mockConvRows.push({
      id: "c1",
      title: "T1",
      updatedAt: "",
      presetId: null,
      effectivePresetId: DEFAULT_EFFECTIVE_PRESET_ID,
      documentFilter: [],
    });
    ragPresetsData = [
      { id: "pr1", name: "P", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" },
    ];
    deleteMutateAsync.mockClear();
    routerPushMock.mockClear();
    createMutateAsync.mockClear();
    useTraceStore.getState().clearTraceEvents();
    vi.mocked(apiFetch).mockImplementation(defaultApiFetch);
    followLabJob.mockImplementation(async (_a: unknown, onChunk: (s: { result?: { streamedAnswer?: string } }) => void) => {
      onChunk({ result: { streamedAnswer: "partial" } });
      return terminalSucceeded;
    });
    Element.prototype.scrollIntoView = vi.fn();
  });

  it("exposes readable chat column shell for layout regression", async () => {
    renderChat();
    expect(await screen.findByTestId("chat-readable-column")).toBeInTheDocument();
  });

  it("renders chat UI and sends a message", async () => {
    const user = userEvent.setup();
    renderChat();
    expect(screen.getByRole("button", { name: /New conversation/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "hello");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    await waitFor(() => expect(followLabJob).toHaveBeenCalled());
  });

  it("shows optimistic user message and assistant status before POST resolves", async () => {
    let resolvePost!: (v: unknown) => void;
    vi.mocked(apiFetch).mockImplementation((url: string | { toString(): string }, init?: RequestInit) => {
      const u = typeof url === "string" ? url : url.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (u.includes("/draft")) return Promise.resolve({ content: "" });
      if (u.includes("/conversations/c1/messages") && method === "GET") {
        return Promise.resolve([...chatMessagesStore]);
      }
      if (method === "POST" && u.includes("/conversations/c1/messages") && !u.includes("/retry")) {
        return new Promise((resolve) => {
          resolvePost = resolve;
        });
      }
      return defaultApiFetch(url, init);
    });

    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "hello");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByTestId("chat-optimistic-user")).toHaveTextContent("hello");
    expect(await screen.findByRole("status")).toBeInTheDocument();
    expect(input).toHaveValue("");
    chatMessagesStore.push({
      id: "ux",
      role: "USER",
      content: "hello",
      createdAt: "",
      sources: null,
      queryType: null,
      pipelineSteps: null,
      status: "DONE",
    });
    resolvePost({
      jobId: "j1",
      status: "RUNNING",
      pollPath: "/x",
      streamPath: "/y",
    });
    await waitFor(() => expect(followLabJob).toHaveBeenCalled());
  });

  it("records trace events for submit, assistant start, and completion", async () => {
    const addTrace = vi.spyOn(useTraceStore.getState(), "addTraceEvent");
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "hello");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    await waitFor(() => expect(followLabJob).toHaveBeenCalled());
    expect(addTrace).toHaveBeenCalledWith(
      expect.objectContaining({ section: "chat", action: "message_submitted", status: "info" }),
    );
    expect(addTrace).toHaveBeenCalledWith(
      expect.objectContaining({
        section: "chat",
        action: "assistant_processing_started",
        status: "in_progress",
        metadata: { jobId: "j1" },
      }),
    );
    expect(addTrace).toHaveBeenCalledWith(
      expect.objectContaining({
        section: "chat",
        action: "assistant_response_received",
        status: "success",
        metadata: { jobId: "j1" },
      }),
    );
    addTrace.mockRestore();
  });

  it("drops optimistic bubble after server list includes the message without duplicate text nodes", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "hello");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    await waitFor(() => expect(screen.queryByTestId("chat-optimistic-user")).not.toBeInTheDocument());
    const helloRows = screen.getAllByText("hello");
    expect(helloRows.length).toBe(1);
  });

  it("on POST failure keeps optimistic user bubble and shows controlled error", async () => {
    followLabJob.mockClear();
    vi.mocked(apiFetch).mockImplementation(async (url: string | { toString(): string }, init?: RequestInit) => {
      const u = typeof url === "string" ? url : url.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "POST" && u.includes("/conversations/c1/messages") && !u.includes("/retry")) {
        throw new ApiError(429, "Too many requests", { kind: "http" });
      }
      return defaultApiFetch(url, init);
    });
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "hello fail");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByTestId("chat-optimistic-user")).toHaveTextContent("hello fail");
    expect(await screen.findByRole("alert")).toHaveTextContent(/Too many requests/i);
    expect(input).toHaveValue("hello fail");
    expect(followLabJob).not.toHaveBeenCalled();
  });

  it("disables Send while message POST is in flight", async () => {
    followLabJob.mockClear();
    let resolvePost!: (v: unknown) => void;
    vi.mocked(apiFetch).mockImplementation((url: string | { toString(): string }, init?: RequestInit) => {
      const u = typeof url === "string" ? url : url.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (u.includes("/draft")) return Promise.resolve({ content: "" });
      if (u.includes("/conversations/c1/messages") && method === "GET") {
        return Promise.resolve([...chatMessagesStore]);
      }
      if (method === "POST" && u.includes("/conversations/c1/messages") && !u.includes("/retry")) {
        return new Promise((resolve) => {
          resolvePost = resolve;
        });
      }
      return defaultApiFetch(url, init);
    });
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "wait");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByTestId("chat-optimistic-user")).toHaveTextContent("wait");
    const sendBtn = screen.getByRole("button", { name: /^Send$/i });
    // Serialized send: button stays disabled until POST resolves (input is also cleared).
    expect(sendBtn).toBeDisabled();
    chatMessagesStore.push({
      id: "ux-wait",
      role: "USER",
      content: "wait",
      createdAt: "",
      sources: null,
      queryType: null,
      pipelineSteps: null,
      status: "DONE",
    });
    resolvePost({
      jobId: "j1",
      status: "RUNNING",
      pollPath: "/x",
      streamPath: "/y",
    });
    await waitFor(() => expect(followLabJob).toHaveBeenCalled());
    await user.type(input, "next");
    expect(sendBtn).not.toBeDisabled();
  });

  it("shows mapped user message when terminal FAILED exposes failureCode", async () => {
    followLabJob.mockClear();
    followLabJob.mockImplementation(async () => terminalFailedDocScope);
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "q");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/selected documents/i);
    expect(screen.queryByText(/technical-detail-should-not-display/i)).not.toBeInTheDocument();
  });

  it("does not surface raw HTML from terminal FAILED job errorMessage", async () => {
    followLabJob.mockClear();
    followLabJob.mockImplementation(async () => terminalFailedWithHtmlMessage);
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "q");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/could not finish/i);
    expect(screen.queryByText(/502 Bad Gateway/i)).not.toBeInTheDocument();
  });

  it("sends Buenos dias to the active conversation messages endpoint", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "Buenos dias");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        "/conversations/c1/messages",
        expect.objectContaining({
          method: "POST",
          body: expect.stringContaining("Buenos dias"),
        }),
      ),
    );
  });

  it("shows recommended default label instead of None when catalog omits the resolved id", async () => {
    ragPresetsData = [
      { id: "pr1", name: "P", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" },
    ];
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    expect(presetSelect).toHaveValue("cafe0001-0001-4001-8001-000000000001");
    expect(screen.getByRole("option", { name: /^Recommended default$/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
    expect(patchConversationApiCalls()).toHaveLength(0);
  });

  it("shows preset catalog name when conversation presetId matches catalog", async () => {
    mockConvRows[0] = {
      ...mockConvRows[0],
      presetId: "pr1",
      effectivePresetId: DEFAULT_EFFECTIVE_PRESET_ID,
    };
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    expect(presetSelect).toHaveValue("pr1");
    expect(screen.getByRole("option", { name: /^P$/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
  });

  it("disables preset select and explains empty catalog without showing None", async () => {
    ragPresetsData = [];
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    expect(presetSelect).toBeDisabled();
    expect(screen.getByRole("option", { name: /^Default configuration$/ })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/No presets are available/i);
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
  });

  it("when conversation omits preset ids, selects first system preset from catalog", async () => {
    mockConvRows[0] = {
      ...mockConvRows[0],
      presetId: null,
      effectivePresetId: null,
    };
    ragPresetsData = [
      { id: "a", name: "A", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" },
      { id: "s", name: "Sys", description: null, tags: [], values: {}, system: true, createdAt: "", updatedAt: "" },
    ];
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    await waitFor(() => expect(presetSelect).toHaveValue("s"));
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
  });

  it("fires document filter patch exactly once when toggling limit retrieval", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    const limitCb = await screen.findByRole("checkbox", { name: /Limit retrieval to selected documents/i });
    await user.click(limitCb);
    await waitFor(() => {
      expect(patchConversationApiCalls()).toHaveLength(1);
      const [, init] = patchConversationApiCalls()[0];
      const body = JSON.parse(String(init?.body ?? "{}")) as { documentFilter: string[] };
      expect([...(body.documentFilter ?? [])].sort()).toEqual(["d1", "d2"]);
    });
  });

  it("opens manage documents sheet from chat controls", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(/Documents for this chat/i)).toBeInTheDocument();
  });

  it("selecting a document in the sheet enables limit retrieval for that id", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockClear();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    const sheetCb = await screen.findByRole("checkbox", { name: /Include second\.pdf/i });
    await user.click(sheetCb);
    await waitFor(() => {
      expect(patchConversationApiCalls().length).toBeGreaterThanOrEqual(1);
      const [, init] = patchConversationApiCalls()[patchConversationApiCalls().length - 1];
      expect(JSON.parse(String(init?.body ?? "{}"))).toEqual({ documentFilter: ["d2"] });
    });
  });

  it("upload merges READY document into filter when limit retrieval is already on", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i }));
    await waitFor(() => expect(patchConversationApiCalls().length).toBeGreaterThanOrEqual(1));
    vi.mocked(apiFetch).mockClear();
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    const input = await screen.findByLabelText(/Upload files to project/i);
    const file = new File(["x"], "new.doc", { type: "application/msword" });
    await user.upload(input, file);
    await waitFor(() => expect(uploadMutateAsyncMock).toHaveBeenCalledWith(file));
    await waitFor(() => {
      const patches = patchConversationApiCalls();
      expect(patches.length).toBeGreaterThanOrEqual(1);
      const [, init] = patches[patches.length - 1];
      const body = JSON.parse(String(init?.body ?? "{}")) as { documentFilter: string[] };
      expect(body.documentFilter?.sort()).toEqual(["d-up", "d1", "d2"].sort());
    });
  });

  it("shows controlled error when chat upload fails", async () => {
    uploadMutateAsyncMock.mockRejectedValueOnce(new ApiError(413, "Payload too large", { kind: "http" }));
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    const input = await screen.findByLabelText(/Upload files to project/i);
    await user.upload(input, new File(["x"], "big.bin"));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("removes a selected document via documents sheet when limiting retrieval", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i }));
    await waitFor(() => expect(patchConversationApiCalls().length).toBeGreaterThanOrEqual(1));
    vi.mocked(apiFetch).mockClear();
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    const togglePdf = await screen.findByRole("checkbox", { name: /Include f\.pdf/i });
    await user.click(togglePdf);
    await waitFor(() => {
      expect(patchConversationApiCalls().length).toBeGreaterThanOrEqual(1);
      const [, init] = patchConversationApiCalls()[patchConversationApiCalls().length - 1];
      expect(JSON.parse(String(init?.body ?? "{}"))).toEqual({ documentFilter: ["d2"] });
    });
  });

  it("shows status hint when no READY documents exist for limit retrieval", async () => {
    projectDocsDataRef.current = [
      {
        id: "dx",
        fileName: "pending.pdf",
        status: "INGESTING",
        chunkCount: 0,
        errorMessage: null,
        uploadedAt: "",
        reindexedAt: null,
        corpusScope: "PROJECT_SHARED",
        conversationId: null,
        currentIndexSnapshotId: null,
        indexSignatureHash: null,
        storagePresent: true,
      },
    ];
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i }));
    await waitFor(() =>
      expect(useChatToolbarStore.getState().api?.limitDocsToggleNotice ?? "").toMatch(/READY/i),
    );
    expect(useChatToolbarStore.getState().api?.limitDocs).toBe(false);
  });

  it("shows controlled error when limit retrieval PATCH fails", async () => {
    vi.mocked(apiFetch).mockImplementation(async (url, init) => {
      const u = typeof url === "string" ? url : (url as URL).toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (
        method === "PATCH" &&
        /\/conversations\/c1\/?$/.test(u) &&
        !u.includes("/messages")
      ) {
        throw new ApiError(400, "documentFilter rejected", { kind: "http" });
      }
      return defaultApiFetch(url, init);
    });
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/documentFilter rejected/i);
    await openChatToolbarOverflow(user);
    expect(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i })).not.toBeChecked();
  });

  it("does not toggle limit retrieval when conversation rows refresh with the same filter", async () => {
    const user = userEvent.setup();
    const { qc } = renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    const limitCb = await screen.findByRole("checkbox", { name: /Limit retrieval to selected documents/i });
    expect(limitCb).not.toBeChecked();
    const patchCountBefore = patchConversationApiCalls().length;

    mockConvRows[0] = {
      ...mockConvRows[0],
      documentFilter: [],
    };
    qc.setQueryData(["conversations", "p1"], structuredClone(mockConvRows));

    await waitFor(() => expect(patchConversationApiCalls().length).toBe(patchCountBefore));
    await waitFor(() => expect(useChatToolbarStore.getState().api?.limitDocs).toBe(false));
  });

  it("sanitizes HTML gateway bodies in send errors", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (url: string | { toString(): string }, init?: RequestInit) => {
      const u = typeof url === "string" ? url : url.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (u.includes("/draft")) return { content: "" };
      if (u.includes("/conversations/c1/messages") && method === "GET") {
        return [...chatMessagesStore];
      }
      if (method === "POST" && u.includes("/conversations/c1/messages") && !u.includes("/retry")) {
        throw createHttpApiError({
          status: 502,
          bodyText: "<html><head></head><body>502 Bad Gateway</body></html>",
          headers: new Headers({ "content-type": "text/html" }),
          requestUrl: u,
          method: "POST",
        });
      }
      return {};
    });
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "x");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByTestId("chat-optimistic-user")).toHaveTextContent("x");
    expect(
      screen.getAllByText(/Gateway error: upstream service unavailable/i).length,
    ).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText(/502 Bad Gateway/i)).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toBeInTheDocument();
    const sendBtn = screen.getByRole("button", { name: /^Send$/i });
    expect(sendBtn).not.toBeDisabled();
    expect(useTraceStore.getState().events.some((e) => e.action === "message_submit_failed")).toBe(true);
  });

  it("surfaces friendly ApiError messages from controlled failures", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (url: string | { toString(): string }, init?: RequestInit) => {
      const u = typeof url === "string" ? url : url.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (u.includes("/draft")) return { content: "" };
      if (u.includes("/conversations/c1/messages") && method === "GET") {
        return [...chatMessagesStore];
      }
      if (method === "POST" && u.includes("/conversations/c1/messages") && !u.includes("/retry")) {
        throw new ApiError(503, "The service used a direct answer fallback.", { kind: "http" });
      }
      return {};
    });
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "q");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByTestId("chat-optimistic-user")).toHaveTextContent("q");
    expect(screen.getAllByText(/direct answer fallback/i).length).toBeGreaterThanOrEqual(1);
  });

  it("renders assistant bodies for backend-controlled ERROR turns", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    expect(
      await screen.findByText(/Answering without retrieval due to a temporary issue/i),
    ).toBeInTheDocument();
  });

  it("retries assistant on ERROR status after opening the conversation", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await user.click(screen.getByRole("button", { name: /^Retry$/i }));
    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith("/conversations/c1/messages/m2/retry", expect.any(Object)),
    );
  });

  it("opens delete confirmation from toolbar overflow", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("menuitem", { name: /^Delete chat$/i }));
    expect(await screen.findByRole("heading", { name: /Delete this chat/i })).toBeInTheDocument();
  });

  it("cancel on delete dialog does not call delete API", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("menuitem", { name: /^Delete chat$/i }));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Cancel$/i }));
    expect(deleteMutateAsync).not.toHaveBeenCalled();
  });

  it("confirm delete calls API and navigates to project chat URL without conversation", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByRole("menuitem", { name: /^Delete chat$/i }));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Delete chat$/i }));
    expect(deleteMutateAsync).toHaveBeenCalledWith("c1");
    expect(routerPushMock).toHaveBeenCalledWith("/chat?projectId=p1");
  });

  it("opens delete confirmation from conversation list trash control", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /Delete chat: T1/i }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });
});
