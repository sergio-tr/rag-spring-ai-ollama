import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import { fireEvent } from "@testing-library/react";
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
import { useChatConfigurationPanelStore } from "@/features/chat/store/chat-configuration-panel.store";
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

function setMatchMediaDesktop(isDesktop: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: isDesktop ? query.includes("min-width") : false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

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

const DEFAULT_EFFECTIVE_PRESET_ID = "cafe0001-0001-4001-8001-000000000003";

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
  useProjectDocumentsForConversation: () => ({
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
  useUploadConversationOverlayDocument: () => ({
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
  if (method === "GET" && /\/projects\/p1\/conversations\/?$/.test(u)) {
    return Promise.resolve(structuredClone(mockConvRows));
  }
  const runtimeStateMatch =
    method === "GET" ? u.match(/\/conversations\/([^/]+)\/runtime-state\/?$/) : null;
  if (runtimeStateMatch) {
    const conversationId = runtimeStateMatch[1];
    const conv = mockConvRows.find((c) => c.id === conversationId) ?? null;
    const selectedPresetId = conv?.presetId ?? null;
    const effectivePresetId = conv?.effectivePresetId ?? DEFAULT_EFFECTIVE_PRESET_ID;
    const runtimeOverride = conv?.runtimeOverride ?? {};
    const baseEffectiveConfig: Record<string, unknown> = (() => {
      // Minimal runtime-state stub: vary baseEffectiveConfig by selected preset so UI can render
      // accumulative flags deterministically in tests.
      if (selectedPresetId === "cafe0001-0001-4001-8001-000000000018") {
        // P8 (advanced retrieval)
        return {
          useRetrieval: true,
          metadataEnabled: true,
          expansionEnabled: true,
          toolsEnabled: true,
          reasoningEnabled: true,
          rankerEnabled: true,
          postRetrievalEnabled: true,
          functionCallingEnabled: false,
          useAdvisor: false,
          adaptiveRoutingEnabled: false,
          judgeEnabled: false,
          clarificationEnabled: false,
          memoryEnabled: false,
        };
      }
      if (selectedPresetId === "cafe0001-0001-4001-8001-000000000024") {
        // P12 (judge-enhanced)
        return {
          useRetrieval: true,
          metadataEnabled: true,
          expansionEnabled: true,
          toolsEnabled: true,
          reasoningEnabled: true,
          rankerEnabled: true,
          postRetrievalEnabled: true,
          functionCallingEnabled: true,
          useAdvisor: true,
          adaptiveRoutingEnabled: true,
          judgeEnabled: true,
          clarificationEnabled: false,
          memoryEnabled: false,
        };
      }
      // Default baseline for most tests.
      return {
        useRetrieval: true,
        rankerEnabled: false,
        memoryEnabled: false,
      };
    })();
    const effectiveConfig: Record<string, unknown> = { ...baseEffectiveConfig, ...runtimeOverride };
    const manualOverrideKeys = Object.keys(runtimeOverride);
    return Promise.resolve({
      conversationId,
      selectedPresetId,
      effectivePresetId,
      preset: {
        kind: selectedPresetId ? "PRODUCT" : "DEFAULT",
        code: null,
        label: selectedPresetId ? "Selected preset" : "Recommended Default",
        chatSelectable: true,
        supported: true,
        supportStatus: null,
        reasonIfUnsupported: null,
      },
      baseEffectiveConfig,
      effectiveConfig,
      runtimeOverride,
      manualOverrideKeys,
      isCustom: manualOverrideKeys.length > 0,
      validation: { valid: true, supported: true, errors: [], warnings: [] },
      selectedWorkflow: "dense_chunk_workflow",
      indexCompatibility: null,
      requiresReindex: false,
    });
  }
  if (method === "GET" && u.includes("/projects/p1/index-profile")) {
    return Promise.resolve({
      projectId: "p1",
      materializationStrategy: "CHUNK_LEVEL",
      metadataEnabled: false,
      metadataProfile: null,
      embeddingModelId: "mxbai-embed-large",
      chunkMaxChars: 400,
      chunkOverlap: null,
      profileHash: "hash",
      createdAt: "",
      updatedAt: "",
    });
  }
  if (method === "GET" && u.includes("/projects/p1/knowledge/snapshots/active")) {
    return Promise.resolve(null);
  }
  if (method === "GET" && u.includes("/lab/classifier/models")) {
    return Promise.resolve([]);
  }
  if (method === "GET" && u.includes("/chat/presets/catalog")) {
    return Promise.resolve({
      productPresets: [...ragPresetsData],
      experimentalPresets: [
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000014",
          code: "P4",
          family: "S2",
          label: "Chunk + metadata retrieval",
          description: "Minimal dev preset row",
          requiredCapabilities: ["USE_RETRIEVAL", "METADATA"],
          supported: true,
          supportStatus: "EXECUTABLE",
          reasonIfUnsupported: null,
          requiresMultiTurn: false,
          mapsToRuntimeCapabilities: { code: "P4" },
          allowedOutcomes: ["EXECUTED", "FAILED", "SKIPPED"],
          chatSelectable: true,
          labSelectable: true,
          labOnly: false,
        },
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000016",
          code: "P6",
          family: "S2",
          label: "P6 preset",
          description: "Minimal dev preset row",
          requiredCapabilities: ["USE_RETRIEVAL", "TOOLS"],
          supported: true,
          supportStatus: "EXECUTABLE",
          reasonIfUnsupported: null,
          requiresMultiTurn: false,
          mapsToRuntimeCapabilities: { code: "P6" },
          allowedOutcomes: ["EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"],
          chatSelectable: true,
          labSelectable: true,
          labOnly: false,
        },
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000018",
          code: "P8",
          family: "S2",
          label: "P8 preset",
          description: "Minimal dev preset row",
          requiredCapabilities: ["USE_RETRIEVAL", "RANKER", "POST_RETRIEVAL"],
          supported: true,
          supportStatus: "EXECUTABLE",
          reasonIfUnsupported: null,
          requiresMultiTurn: false,
          mapsToRuntimeCapabilities: { code: "P8" },
          allowedOutcomes: ["EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"],
          chatSelectable: true,
          labSelectable: true,
          labOnly: false,
        },
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000023",
          code: "P11",
          family: "S4",
          label: "Adaptive routing",
          description: "Minimal dev preset row",
          requiredCapabilities: ["ADAPTIVE_ROUTING"],
          supported: true,
          supportStatus: "EXECUTABLE",
          reasonIfUnsupported: null,
          requiresMultiTurn: false,
          mapsToRuntimeCapabilities: { code: "P11" },
          allowedOutcomes: ["EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"],
          chatSelectable: true,
          labSelectable: true,
          labOnly: false,
        },
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000024",
          code: "P12",
          family: "S4",
          label: "Judge-enhanced",
          description: "Minimal dev preset row",
          requiredCapabilities: ["JUDGE"],
          supported: true,
          supportStatus: "EXECUTABLE",
          reasonIfUnsupported: null,
          requiresMultiTurn: false,
          mapsToRuntimeCapabilities: { code: "P12" },
          allowedOutcomes: ["EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"],
          chatSelectable: true,
          labSelectable: true,
          labOnly: false,
        },
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000021",
          code: "P13",
          family: "S4",
          label: "Clarification loop",
          description: "Minimal dev preset row",
          requiredCapabilities: ["CLARIFICATION"],
          supported: true,
          supportStatus: "REQUIRES_MULTI_TURN",
          reasonIfUnsupported: null,
          requiresMultiTurn: true,
          mapsToRuntimeCapabilities: { code: "P13" },
          allowedOutcomes: ["EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"],
          chatSelectable: true,
          labSelectable: false,
          labOnly: false,
        },
        {
          productPresetId: "cafe0001-0001-4001-8001-000000000022",
          code: "P14",
          family: "S4",
          label: "Memory flow",
          description: "Minimal dev preset row",
          requiredCapabilities: ["MEMORY"],
          supported: true,
          supportStatus: "REQUIRES_MULTI_TURN",
          reasonIfUnsupported: null,
          requiresMultiTurn: true,
          mapsToRuntimeCapabilities: { code: "P14" },
          allowedOutcomes: ["EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"],
          chatSelectable: true,
          labSelectable: false,
          labOnly: false,
        },
      ],
    });
  }
  if (method === "GET" && u.includes("/runtime-config/capabilities")) {
    return Promise.resolve({
      capabilities: [
        {
          key: "useRetrieval",
          label: "Use retrieval",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 1,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Retrieval",
          implemented: true,
          configurable: true,
          requires: [],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
        {
          key: "reasoningEnabled",
          label: "Reasoning",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 2,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Advanced",
          implemented: true,
          configurable: true,
          requires: [],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
        {
          key: "rankerEnabled",
          label: "Ranker",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 3,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Advanced",
          implemented: true,
          configurable: true,
          requires: ["useRetrieval"],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
        {
          key: "postRetrievalEnabled",
          label: "Post-retrieval",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 4,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Advanced",
          implemented: true,
          configurable: true,
          requires: ["useRetrieval"],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
        {
          key: "functionCallingEnabled",
          label: "Function calling",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 5,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Advanced",
          implemented: true,
          configurable: true,
          requires: ["useRetrieval"],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
        {
          key: "useAdvisor",
          label: "Advisor",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 6,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Advanced",
          implemented: true,
          configurable: true,
          requires: ["useRetrieval"],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
        {
          key: "adaptiveRoutingEnabled",
          label: "Adaptive routing",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 7,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Advanced",
          implemented: true,
          configurable: true,
          requires: ["useRetrieval"],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
        {
          key: "judgeEnabled",
          label: "Judge",
          description: "desc",
          category: "RUNTIME_HOT_SWAPPABLE",
          visibleInChat: true,
          configurableInChat: true,
          engineWired: true,
          supportMode: "SUPPORTED",
          displayOrder: 8,
          requiresIndexSnapshot: false,
          requiresReindexWhenChanged: false,
          group: "Advanced",
          implemented: true,
          configurable: true,
          requires: ["useRetrieval"],
          excludes: [],
          reasonIfDisabled: null,
          reasonIfNotImplemented: null,
          options: {},
        },
      ],
    });
  }
  if (method === "POST" && u.includes("/runtime-config/validate")) {
    return Promise.resolve({
      valid: true,
      supported: true,
      effectiveConfig: {},
      errors: [],
      warnings: [],
      selectedWorkflow: "DirectLlmWorkflow",
    });
  }
  if (method === "POST" && /\/documents\/[^/]+\/retry-ingest$/.test(u)) {
    return Promise.resolve({
      id: "d-up",
      fileName: "up.pdf",
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
    });
  }
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
  const btn = screen.getByTestId("chat-config-trigger");
  await waitFor(() => expect(btn).toBeEnabled());
  // Wait for useMediaQuery hydration: desktop must control the side panel, not the mobile drawer.
  await waitFor(() => expect(btn).toHaveAttribute("aria-controls", "chat-configuration-side-panel"));
  // Trigger toggles; avoid a second click closing an already-open panel (multi-step flows call this twice).
  if (btn.getAttribute("aria-expanded") !== "true") {
    await user.click(btn);
  }
  await screen.findByTestId("chat-configuration-side-panel");
}

/** Opens the desktop panel and expands edit controls (compact summary is collapsed by default). */
async function openChatConfigurationEdit(user: ReturnType<typeof userEvent.setup>) {
  await openChatToolbarOverflow(user);
  const panel = await screen.findByTestId("chat-configuration-side-panel");
  if (!within(panel).queryByTestId("chat-llm-model-select")) {
    await user.click(within(panel).getByTestId("chat-config-edit-button"));
  }
}

async function expandChatMessageMetadata(user: ReturnType<typeof userEvent.setup>) {
  const toggle = await screen.findByTestId("chat-message-metadata-toggle");
  await user.click(toggle);
  await screen.findByTestId("chat-message-metadata-panel");
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
    setMatchMediaDesktop(true);
    window.localStorage.clear();
    useChatConfigurationPanelStore.setState({ open: false, hydrated: false });
    useChatToolbarStore.getState().setApi(null);
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
      runtimeOverride: {},
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

  it("T-M5-FE-trace: renders documentBound and date grounding metadata", async () => {
    const user = userEvent.setup();
    chatMessagesStore = [
      {
        id: "a-trace",
        role: "ASSISTANT",
        content: "Trace sample",
        createdAt: "",
        sources: [],
        queryType: "DOCUMENT",
        pipelineSteps: [],
        status: "DONE",
        executionMetadata: {
          traceId: "trace-m5",
          workflowName: "ChunkDenseMetadataWorkflow",
          requestedDate: "2025-02-24",
          exactDocumentMatch: true,
          documentBound: true,
          dateMismatchDetected: false,
          candidateSourceCountBeforeDateFilter: 3,
          candidateSourceCountAfterDateFilter: 1,
          groundingPolicyApplied: "DATE_AWARE_SOURCE_GROUNDING",
        },
      },
    ];

    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));

    await expandChatMessageMetadata(user);
    expect(await screen.findByTestId("chat-trace")).toHaveTextContent("trace-m5");
    expect(screen.getByTestId("chat-trace")).toHaveTextContent("Document bound");
    expect(screen.getByTestId("chat-trace")).toHaveTextContent("true");
    expect(screen.getByTestId("chat-trace")).toHaveTextContent("2025-02-24");
    expect(screen.getByTestId("chat-trace")).toHaveTextContent("Exact document match");
    expect(screen.getByTestId("chat-trace")).toHaveTextContent("3 -> 1");
  });

  it("T-M6-FE-trace: renders classifier contract metadata", async () => {
    const user = userEvent.setup();
    chatMessagesStore = [
      {
        id: "a-clf",
        role: "ASSISTANT",
        content: "Classifier trace",
        createdAt: "",
        sources: [],
        queryType: "DOCUMENT",
        pipelineSteps: [],
        status: "DONE",
        executionMetadata: {
          traceId: "trace-m6",
          classifierStatus: "OK",
          classifierLabel: "COUNT_DOCUMENTS",
          predictedQueryType: "COUNT_DOCUMENTS",
          classifierModelIdUsed: "default",
          classifierFallback: false,
          classifierFallbackReason: "",
        },
      },
    ];

    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));

    await expandChatMessageMetadata(user);
    const trace = await screen.findByTestId("chat-trace");
    expect(trace).toHaveTextContent("trace-m6");
    expect(trace).toHaveTextContent("Classifier status");
    expect(trace).toHaveTextContent("Predicted query type");
    expect(trace).toHaveTextContent("COUNT_DOCUMENTS");
    expect(trace).toHaveTextContent("default");
  });

  it("shows trace metadata and i18n trace heading", async () => {
    const user = userEvent.setup();
    chatMessagesStore = [
      {
        id: "a-trace",
        role: "ASSISTANT",
        content: "Demo trace",
        createdAt: "",
        sources: [
          {
            documentId: "doc-trace",
            projectDocumentId: "pd-trace",
            filename: "doc.pdf",
            snippet: "snippet",
            distance: 0.1,
            distanceLabel: "distance",
            chunkIndex: 0,
            detectedDate: null,
            metadata: {},
          },
        ],
        queryType: "DOCUMENT",
        pipelineSteps: [],
        status: "DONE",
        executionMetadata: {
          traceId: "trace-demo",
          workflowName: "DemoWorkflow",
        },
      },
    ];

    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));

    await expandChatMessageMetadata(user);
    expect(await screen.findByTestId("chat-trace")).toHaveTextContent(/Message trace/i);
    expect(screen.queryByTestId("chat-trace-jaeger-not-run")).not.toBeInTheDocument();
    expect(screen.getByTestId("chat-sources")).toHaveTextContent(/Sources \(1\)/i);
  });

  it("T-M5-FE-sources: renders filename, date, chunk, and date warning", async () => {
    const user = userEvent.setup();
    chatMessagesStore = [
      {
        id: "u1",
        role: "USER",
        content: "acta 25/02/2026",
        createdAt: "",
        sources: null,
        queryType: null,
        pipelineSteps: null,
        status: "DONE",
      },
      {
        id: "a1",
        role: "ASSISTANT",
        content: "No exact acta was found.",
        createdAt: "",
        sources: [
          {
            documentId: "doc-1",
            projectDocumentId: "d1",
            filename: "ACTA 5.pdf",
            snippet: "meeting fragment",
            distance: 0.12,
            distanceLabel: "distance",
            chunkIndex: 3,
            detectedDate: "2025-02-25",
            metadata: { chunkId: "chunk-abc" },
          },
        ],
        queryType: "DOCUMENT",
        pipelineSteps: [],
        status: "DONE",
        executionMetadata: {
          traceId: "trace-1",
          workflowName: "dense",
          requestedDate: "2026-02-25",
          selectedSnapshotIds: ["snap-1"],
          retrievalAfterCompressionCount: 1,
          dateMismatchDetected: true,
          exactDocumentMatch: false,
          topSourceDate: "2025-02-25",
          closestAvailableDate: "ACTA 5.pdf (2025-02-25)",
          candidateSourceCountBeforeDateFilter: 2,
          candidateSourceCountAfterDateFilter: 1,
          groundingPolicyApplied: "true",
        },
      },
    ];

    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));

    expect(await screen.findByTestId("chat-page")).toBeInTheDocument();
    expect(screen.getByTestId("chat-answer")).toHaveTextContent("No exact acta");
    expect(screen.getByTestId("chat-message-metadata-toggle")).toHaveTextContent(/More information/i);
    expect(screen.getByTestId("chat-sources")).not.toBeVisible();
    await expandChatMessageMetadata(user);
    expect(screen.getByTestId("chat-sources")).toBeVisible();
    expect(screen.getByTestId("chat-sources")).toHaveTextContent("ACTA 5.pdf");
    expect(screen.getByTestId("chat-sources")).toHaveTextContent("date=2025-02-25");
    expect(screen.getByTestId("chat-sources")).toHaveTextContent("chunk=3");
    expect(screen.getAllByTestId("chat-date-warning")[0]).toHaveTextContent(/requested date/i);
    expect(screen.getByTestId("chat-message-input")).toBeInTheDocument();
  });

  it("T-M4-FE-chat-send: disables composer and send when runtime-state has blocking issues", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation((url: string | { toString(): string }, init?: RequestInit) => {
      const u = typeof url === "string" ? url : url.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "GET" && u.includes("/runtime-state")) {
        return Promise.resolve({
          conversationId: "c1",
          selectedPresetId: null,
          effectivePresetId: DEFAULT_EFFECTIVE_PRESET_ID,
          preset: {
            kind: "DEFAULT",
            code: null,
            label: "Recommended Default",
            chatSelectable: true,
            supported: true,
            supportStatus: null,
            reasonIfUnsupported: null,
          },
          baseEffectiveConfig: {},
          effectiveConfig: {},
          runtimeOverride: {},
          manualOverrideKeys: [],
          isCustom: false,
          isValid: false,
          blockingIssues: [
            {
              code: "NO_ACTIVE_INDEX",
              field: "presetId",
              message: "Create or reindex project with compatible profile.",
              severity: "ERROR",
            },
          ],
          validation: { valid: false, supported: false, errors: [], warnings: [] },
          selectedWorkflow: null,
          indexCompatibility: null,
          requiresReindex: true,
        });
      }
      return defaultApiFetch(url, init);
    });

    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));

    expect(await screen.findByTestId("chat-runtime-blocking-input-message")).toHaveTextContent(/requires a new compatible index snapshot/i);
    expect(screen.getByTestId("chat-message-composer")).toBeDisabled();
    expect(screen.getByTestId("chat-send-button")).toBeDisabled();
  });

  it("reverts model UI and refetches runtime-state when PATCH fails", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation((url: string | { toString(): string }, init?: RequestInit) => {
      const u = typeof url === "string" ? url : url.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "PATCH" && /\/conversations\/c1\/?$/.test(u)) {
        throw new ApiError(422, "Model is not available", { kind: "http" });
      }
      return defaultApiFetch(url, init);
    });

    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    const panel = await screen.findByTestId("chat-configuration-side-panel");
    const modelSelect = within(panel).getByRole("combobox", { name: /^LLM model$/i });

    await user.selectOptions(modelSelect, "llama");

    await waitFor(() => expect(modelSelect).toHaveValue(""));
    expect(screen.getByText(/Model is not available/i)).toBeInTheDocument();
    await waitFor(() => {
      const runtimeCalls = vi
        .mocked(apiFetch)
        .mock.calls.filter((call) => String(call[0]).includes("/runtime-state"));
      expect(runtimeCalls.length).toBeGreaterThan(1);
    });
  });

  it("shows experimental preset label (P4/P6) when selectedPresetId is experimental (never Recommended Default)", async () => {
    const user = userEvent.setup();
    // Pretend the conversation already persisted an experimental preset id.
    mockConvRows[0].presetId = "cafe0001-0001-4001-8001-000000000014";
    mockConvRows[0].effectivePresetId = "cafe0001-0001-4001-8001-000000000014";

    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);

    const presetSelect = screen.getByRole("combobox", { name: /^Preset$/i }) as HTMLSelectElement;
    expect(presetSelect.value).toBe("cafe0001-0001-4001-8001-000000000014");
    const selected = Array.from(presetSelect.options).find((o) => o.selected);
    expect(selected?.text ?? "").toContain("P4 — Chunk + metadata retrieval");
    expect(selected?.text ?? "").not.toMatch(/Recommended/i);
  });

  it("selecting compatible experimental preset persists presetId via PATCH", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);

    const presetSelect = screen.getByRole("combobox", { name: /^Preset$/i }) as HTMLSelectElement;
    await user.selectOptions(presetSelect, "cafe0001-0001-4001-8001-000000000014");

    const calls = patchConversationApiCalls();
    expect(calls.length).toBeGreaterThan(0);
    const last = calls[calls.length - 1];
    const init = last[1] as RequestInit;
    expect(JSON.parse(String(init.body))).toEqual(
      expect.objectContaining({ presetId: "cafe0001-0001-4001-8001-000000000014" }),
    );
  });

  it("does not show raw multi-turn enum codes in experimental preset option labels", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);

    const presetSelect = screen.getByRole("combobox", { name: /^Preset$/i }) as HTMLSelectElement;
    const optionTexts = Array.from(presetSelect.options)
      .map((o) => o.text)
      .filter(Boolean);

    expect(optionTexts.some((t) => t.includes("P13 — Clarification loop"))).toBe(true);
    expect(optionTexts.some((t) => t.includes("P14 — Memory flow"))).toBe(true);
    for (const text of optionTexts) {
      expect(text).not.toMatch(/REQUIRES_MULTI_TURN|FUTURE_MULTI_TURN|\[NOT_SUPPORTED/);
    }
  });

  it("shows accumulated flags in runtime toggles when P8 is selected", async () => {
    const user = userEvent.setup();
    mockConvRows[0].presetId = "cafe0001-0001-4001-8001-000000000018";
    mockConvRows[0].effectivePresetId = "cafe0001-0001-4001-8001-000000000018";
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);

    await user.click(screen.getByTestId("chat-config-runtime-collapsible"));
    await user.click(screen.getByTestId("chat-config-runtime-refresh-effective"));
    expect(screen.getByRole("checkbox", { name: /Use retrieval/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Ranker/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Post-retrieval/i })).toBeChecked();
  });

  it("shows retrieval+metadata+advanced+FC+advisors+adaptive+judge when P12 is selected", async () => {
    const user = userEvent.setup();
    mockConvRows[0].presetId = "cafe0001-0001-4001-8001-000000000024";
    mockConvRows[0].effectivePresetId = "cafe0001-0001-4001-8001-000000000024";
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);

    await user.click(screen.getByTestId("chat-config-runtime-collapsible"));
    await user.click(screen.getByTestId("chat-config-runtime-refresh-effective"));
    expect(screen.getByRole("checkbox", { name: /Use retrieval/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Function calling/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Advisor/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Adaptive routing/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Judge/i })).toBeChecked();
  });

  it("marks conversation as Custom after saving any runtime override, and clears on Clear", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);

    // Open advanced config, refresh effective config and flip a boolean (auto-saves to runtimeOverride).
    await user.click(screen.getByTestId("chat-config-runtime-collapsible"));
    await user.click(screen.getByTestId("chat-config-runtime-refresh-effective"));
    const useRetrieval = screen.getByRole("checkbox", { name: /Use retrieval/i });
    await user.click(useRetrieval);

    // Toggling should PATCH runtimeOverride and make the Custom badge visible.
    await waitFor(() => {
      expect(screen.getByText(/^Custom$/i)).toBeInTheDocument();
    });

    // Clear should remove runtimeOverride and hide the badge.
    await user.click(screen.getByRole("button", { name: /^Clear$/i }));
    await waitFor(() => {
      expect(screen.queryByText(/^Custom$/i)).not.toBeInTheDocument();
    });
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

  it("shows Unknown preset label when selected preset id is not in either catalog (never None)", async () => {
    ragPresetsData = [
      { id: "pr1", name: "P", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" },
    ];
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    // Ignore unrelated initial effects (draft load, etc.).
    vi.mocked(apiFetch).mockClear();
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    // R1: selected preset is backend-authoritative; when user did not select a preset, the select value is empty.
    expect(presetSelect).toHaveValue("");
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
    await openChatConfigurationEdit(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    expect(presetSelect).toHaveValue("pr1");
    expect(screen.getByRole("option", { name: /^P$/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
  });

  it("disables preset select and explains empty product catalog while still showing experimental presets", async () => {
    ragPresetsData = [];
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    expect(presetSelect).toBeDisabled();
    expect(screen.getAllByRole("status").some((n) => /No presets are available/i.test(n.textContent ?? ""))).toBe(true);
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
    // Experimental group still loads from unified catalog.
    expect(screen.getByRole("option", { name: /^P4 — Chunk \+ metadata retrieval$/ })).toBeInTheDocument();
  });

  it("when conversation omits preset ids, shows Recommended Default (no local fallback)", async () => {
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
    await openChatConfigurationEdit(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    await waitFor(() => expect(presetSelect).toHaveValue(""));
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
  });

  it("fires document filter patch exactly once when toggling limit retrieval", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    const limitCb = await screen.findByRole("checkbox", { name: /Limit retrieval to selected documents/i });
    vi.mocked(apiFetch).mockClear();
    await user.click(limitCb);
    await waitFor(() => {
      expect(patchConversationApiCalls()).toHaveLength(1);
      const [, init] = patchConversationApiCalls()[0];
      const body = JSON.parse(String(init?.body ?? "{}")) as { documentFilter: string[] };
      expect([...(body.documentFilter ?? [])].sort()).toEqual(["d1", "d2"]);
    });
  });

  it("keeps chat settings panel open while changing preset and model controls", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    await user.selectOptions(presetSelect, "pr1");
    expect(screen.getByRole("combobox", { name: /^LLM model$/i })).toBeInTheDocument();
    const panel = screen.getByTestId("chat-configuration-side-panel");
    expect(within(panel).getByRole("heading", { name: /Chat configuration/i })).toBeInTheDocument();
  });

  it("toggles the persistent right side panel with ⋮ on desktop", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));

    expect(screen.queryByTestId("chat-configuration-side-panel")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("chat-config-trigger"));
    const panel = screen.getByTestId("chat-configuration-side-panel");
    expect(panel).toBeInTheDocument();
    const workspace = screen.getByTestId("chat-main-workspace");
    expect(workspace).toContainElement(panel);
    expect(workspace).toContainElement(screen.getByTestId("chat-readable-column"));
    await user.click(screen.getByTestId("chat-config-trigger"));
    expect(screen.queryByTestId("chat-configuration-side-panel")).not.toBeInTheDocument();
  });

  describe("chat layout width on desktop", () => {
    it("uses centered narrow layout when configuration panel is closed", async () => {
      const user = userEvent.setup();
      renderChat();
      await user.click(screen.getByRole("button", { name: /^T1$/ }));

      const workspace = screen.getByTestId("chat-main-workspace");
      const readable = screen.getByTestId("chat-readable-column");

      expect(workspace).toHaveAttribute("data-chat-layout-mode", "centered");
      expect(screen.queryByTestId("chat-configuration-side-panel")).not.toBeInTheDocument();
      expect(readable.className).toMatch(/md:max-w-\[min\(50%,48rem\)\]/);
      expect(readable.className).toMatch(/md:flex-none/);
      expect(readable.className).toMatch(/md:mx-auto/);
      expect(readable.className).not.toMatch(/md:flex-1/);
    });

    it("uses split two-column layout when configuration panel is open", async () => {
      const user = userEvent.setup();
      renderChat();
      await user.click(screen.getByRole("button", { name: /^T1$/ }));
      await user.click(screen.getByTestId("chat-config-trigger"));

      const workspace = screen.getByTestId("chat-main-workspace");
      const readable = screen.getByTestId("chat-readable-column");
      const panel = screen.getByTestId("chat-configuration-side-panel");

      expect(workspace).toHaveAttribute("data-chat-layout-mode", "split");
      expect(workspace).toContainElement(panel);
      expect(readable.className).toMatch(/md:flex-1/);
      expect(readable.className).not.toMatch(/md:max-w-\[min\(50%,48rem\)\]/);
    });

    it("keeps the message thread inside the readable column, not inside the config panel", async () => {
      const user = userEvent.setup();
      renderChat();
      await user.click(screen.getByRole("button", { name: /^T1$/ }));
      await user.click(screen.getByTestId("chat-config-trigger"));

      const readable = screen.getByTestId("chat-readable-column");
      const thread = screen.getByTestId("chat-thread-dropzone");
      const panel = screen.getByTestId("chat-configuration-side-panel");

      expect(readable).toContainElement(thread);
      expect(panel).not.toContainElement(thread);
    });

    it("returns to centered layout after closing the configuration panel", async () => {
      const user = userEvent.setup();
      renderChat();
      await user.click(screen.getByRole("button", { name: /^T1$/ }));
      await user.click(screen.getByTestId("chat-config-trigger"));
      await user.click(screen.getByTestId("chat-config-trigger"));

      const workspace = screen.getByTestId("chat-main-workspace");
      const readable = screen.getByTestId("chat-readable-column");

      expect(workspace).toHaveAttribute("data-chat-layout-mode", "centered");
      expect(screen.queryByTestId("chat-configuration-side-panel")).not.toBeInTheDocument();
      expect(readable.className).toMatch(/md:flex-none/);
      expect(readable.className).not.toMatch(/md:flex-1/);
    });
  });

  it("keeps chat thread height stable when opening configuration on desktop", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const thread = screen.getByTestId("chat-thread-dropzone");
    const before = thread.getBoundingClientRect().height;
    await openChatToolbarOverflow(user);
    const after = thread.getBoundingClientRect().height;
    expect(after).toBe(before);
  });

  it("does not show no-sources copy while assistant message is pending", async () => {
    const user = userEvent.setup();
    chatMessagesStore = [
      {
        id: "a-pending",
        role: "ASSISTANT",
        content: "Working on it…",
        createdAt: "",
        sources: [],
        queryType: null,
        pipelineSteps: null,
        status: "PROCESSING",
      },
    ];
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    expect(screen.queryByTestId("chat-message-metadata")).not.toBeInTheDocument();
    expect(screen.queryByText(/No sources were returned/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/No sources available/i)).not.toBeInTheDocument();
  });

  it("shows short no-sources message inside collapsed metadata after completion", async () => {
    const user = userEvent.setup();
    chatMessagesStore = [
      {
        id: "a-empty",
        role: "ASSISTANT",
        content: "Direct answer",
        createdAt: "",
        sources: [],
        queryType: null,
        pipelineSteps: null,
        status: "DONE",
      },
    ];
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    expect(screen.getByTestId("chat-message-metadata-toggle")).toBeInTheDocument();
    expect(screen.queryByText(/No sources available for this answer/i)).not.toBeVisible();
    await expandChatMessageMetadata(user);
    expect(screen.getByTestId("chat-sources")).toHaveTextContent(/No sources available for this answer/i);
  });

  it("persists open state of the desktop panel in localStorage", async () => {
    window.localStorage.setItem("chat-config-panel-open-v1", "true");
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    expect(await screen.findByTestId("chat-configuration-side-panel")).toBeInTheDocument();

    await user.click(screen.getByTestId("chat-config-trigger"));
    expect(window.localStorage.getItem("chat-config-panel-open-v1")).toBe("false");
  });

  it("uses drawer/sheet on mobile when ⋮ is clicked", async () => {
    setMatchMediaDesktop(false);
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));

    await user.click(screen.getByTestId("chat-config-trigger"));
    expect(screen.queryByTestId("chat-configuration-side-panel")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("chat-config-edit-button"));
    expect(await screen.findByRole("heading", { level: 3, name: "Document scope" })).toBeInTheDocument();
  });

  it("renders Chat actions sections with padded body and sticky footer", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);

    const panel = screen.getByTestId("chat-configuration-side-panel");
    expect(panel).toBeInTheDocument();
    expect(within(panel).getByRole("heading", { name: /Chat configuration/i })).toBeInTheDocument();

    expect(screen.queryByText(/Unavailable capabilities/i)).not.toBeInTheDocument();

    // Edit mode exposes Document scope + Model & preset; index caps live under Technical details; runtime is Advanced options.
    const headings = within(panel)
      .getAllByRole("heading", { level: 3 })
      .map((h) => h.textContent?.trim() ?? "");
    const idx = (s: string) => headings.findIndex((h) => h === s);
    expect(idx("Document scope")).toBeGreaterThanOrEqual(0);
    expect(idx("Model & preset")).toBeGreaterThanOrEqual(0);
    expect(idx("Document scope")).toBeLessThan(idx("Model & preset"));
    expect(idx("Advanced options")).toBeGreaterThan(idx("Model & preset"));
    expect(within(panel).getByTestId("chat-config-technical-details")).toBeInTheDocument();

    // Padding sanity: inner body is padded.
    const body = panel.querySelector("div.min-h-0.flex-1.overflow-y-auto") as HTMLElement | null;
    expect(body?.className ?? "").toMatch(/\bpx-4\b/);
    expect(body?.className ?? "").toMatch(/\bpy-4\b/);
  });

  it("advanced configuration collapsible opens and closes with aria-expanded", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);

    const toggle = screen.getByTestId("chat-config-runtime-collapsible");
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
  });

  it("delete chat button stays after move button (destructive at end)", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);

    const move = screen.getByTestId("chat-move-project-button");
    const del = screen.getByTestId("chat-delete-menu-item");
    const pos = move.compareDocumentPosition(del);
    expect(pos & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("uploads documents directly from chat settings add documents control", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    const uploadInput = await screen.findByLabelText(/Upload files to project/i);
    const file = new File(["x"], "from-settings.doc", { type: "application/msword" });
    await user.upload(uploadInput, file);
    await waitFor(() => expect(uploadMutateAsyncMock).toHaveBeenCalledWith(file));
  });

  it("supports drag & drop upload into the chat thread area", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    const dropTarget = await screen.findByTestId("chat-thread-dropzone");
    const file = new File(["x"], "dropped.pdf", { type: "application/pdf" });
    fireEvent.dragOver(dropTarget, { dataTransfer: { files: [file] } });
    fireEvent.drop(dropTarget, { dataTransfer: { files: [file] } });
    await waitFor(() => expect(uploadMutateAsyncMock).toHaveBeenCalledWith(file));
  });

  it("opens manage documents sheet from chat controls", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(/Documents for this chat/i)).toBeInTheDocument();
  });

  it("selecting a document in the sheet enables limit retrieval for that id", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockClear();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
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
    await openChatConfigurationEdit(user);
    await user.click(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i }));
    await waitFor(() => expect(patchConversationApiCalls().length).toBeGreaterThanOrEqual(1));
    vi.mocked(apiFetch).mockClear();
    await openChatConfigurationEdit(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    const dlg = await screen.findByRole("dialog");
    const input = await within(dlg).findByLabelText(/Upload files to project/i);
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
    await openChatConfigurationEdit(user);
    await user.click(screen.getByRole("button", { name: /Manage project documents/i }));
    const dlg = await screen.findByRole("dialog");
    const input = await within(dlg).findByLabelText(/Upload files to project/i);
    await user.upload(input, new File(["x"], "big.bin"));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("removes a selected document via documents sheet when limiting retrieval", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
    await user.click(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i }));
    await waitFor(() => expect(patchConversationApiCalls().length).toBeGreaterThanOrEqual(1));
    vi.mocked(apiFetch).mockClear();
    await openChatConfigurationEdit(user);
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
    await openChatConfigurationEdit(user);
    const cb = await screen.findByRole("checkbox", { name: /Limit retrieval to selected documents/i });
    expect(cb).toBeDisabled();
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
    await openChatConfigurationEdit(user);
    await user.click(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/documentFilter rejected/i);
    await openChatConfigurationEdit(user);
    expect(screen.getByRole("checkbox", { name: /Limit retrieval to selected documents/i })).not.toBeChecked();
  });

  it("does not toggle limit retrieval when conversation rows refresh with the same filter", async () => {
    const user = userEvent.setup();
    const { qc } = renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatConfigurationEdit(user);
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
      if (method === "GET" && u.includes("/runtime-state")) {
        return { validation: { valid: true, supported: true, errors: [] } };
      }
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
      if (method === "GET" && u.includes("/runtime-state")) {
        return { validation: { valid: true, supported: true, errors: [] } };
      }
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
    await user.click(screen.getByTestId("chat-delete-menu-item"));
    expect(await screen.findByRole("heading", { name: /Delete this chat/i })).toBeInTheDocument();
  });

  it("cancel on delete dialog does not call delete API", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByTestId("chat-delete-menu-item"));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Cancel$/i }));
    expect(deleteMutateAsync).not.toHaveBeenCalled();
  });

  it("confirm delete calls API and navigates to project chat URL without conversation", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /^T1$/ }));
    await openChatToolbarOverflow(user);
    await user.click(screen.getByTestId("chat-delete-menu-item"));
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
