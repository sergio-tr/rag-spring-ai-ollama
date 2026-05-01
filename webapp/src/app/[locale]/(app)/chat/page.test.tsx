import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ConversationDto } from "@/types/api";
import ChatPage from "./page";

vi.mock("@/navigation", () => ({
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/en/chat",
}));

const followLabJob = vi.fn().mockResolvedValue(undefined);
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

vi.mock("@/store/app.store", () => ({
  useAppStore: (sel: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    sel({ activeProject: { id: "p1", name: "P" } }),
}));

const mockConvRows: ConversationDto[] = [
  {
    id: "c1",
    title: "T1",
    updatedAt: "",
    presetId: null,
    effectivePresetId: null,
    documentFilter: [],
  },
];

/** Mutable snapshot pointer — replace `.data` with a new array to simulate refetch. */
const conversationsQueryResult = { data: mockConvRows as ConversationDto[] };

const patchMutate = vi.fn();
const createMutateAsync = vi.fn().mockResolvedValue({
  id: "c2",
  title: "New",
  updatedAt: "",
  presetId: null,
});

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useConversations: () => ({ data: conversationsQueryResult.data }),
  useCreateConversation: () => ({
    mutateAsync: createMutateAsync,
    isPending: false,
  }),
  useConversationMessages: () => ({ data: mockMessages, refetch: vi.fn() }),
  usePatchConversation: () => ({
    mutate: patchMutate,
    isPending: false,
    isError: false,
  }),
  useMoveConversation: () => ({
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
  }),
}));

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => ({
    data: [
      {
        id: "d1",
        fileName: "f.pdf",
        status: "READY" as const,
        chunkCount: 1,
        errorMessage: null,
        uploadedAt: "",
        reindexedAt: null,
      },
    ],
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
  useRagPresets: () => ({ data: ragPresetsData, isError: false }),
}));

const mockMessages = [
  {
    id: "m1",
    role: "USER" as const,
    content: "hi",
    createdAt: "",
    sources: null,
    queryType: null,
    pipelineSteps: null,
    status: "DONE",
  },
  {
    id: "m2",
    role: "ASSISTANT" as const,
    content: "Answering without retrieval due to a temporary issue.",
    createdAt: "",
    sources: null,
    queryType: null,
    pipelineSteps: null,
    status: "ERROR",
  },
];

vi.mock("@/store/chat-explain.store", () => ({
  useChatExplainStore: (sel: (s: Record<string, unknown>) => unknown) =>
    sel({
      setLastDone: vi.fn(),
      setStreamingText: vi.fn(),
      resetStreaming: vi.fn(),
      setStreaming: vi.fn(),
      isStreaming: false,
      streamingText: "",
    }),
}));

function renderChat() {
  const qc = createTestQueryClient();
  const view = render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <ChatPage />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
  return { qc, ...view };
}

describe("ChatPage", () => {
  beforeEach(() => {
    mockConvRows.length = 0;
    mockConvRows.push({
      id: "c1",
      title: "T1",
      updatedAt: "",
      presetId: null,
      effectivePresetId: null,
      documentFilter: [],
    });
    conversationsQueryResult.data = mockConvRows;
    ragPresetsData = [
      { id: "pr1", name: "P", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" },
    ];
    patchMutate.mockClear();
    createMutateAsync.mockClear();
    vi.mocked(apiFetch).mockImplementation(async (url: string | { toString(): string }) => {
      const u = typeof url === "string" ? url : url.toString();
      if (u.includes("/draft")) return { content: "" };
      if (u.includes("/messages") && !u.includes("/retry")) {
        return { jobId: "j1", status: "RUNNING", pollPath: "/x", streamPath: "/y" };
      }
      return {};
    });
    followLabJob.mockImplementation(async (_a: unknown, onChunk: (s: { result?: { streamedAnswer?: string } }) => void) => {
      onChunk({ result: { streamedAnswer: "partial" } });
    });
    Element.prototype.scrollIntoView = vi.fn();
  });

  it("renders chat UI and sends a message", async () => {
    const user = userEvent.setup();
    renderChat();
    expect(screen.getByRole("button", { name: /New conversation/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "hello");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    await waitFor(() => expect(followLabJob).toHaveBeenCalled());
  });

  it("sends Buenos dias to the active conversation messages endpoint", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /T1/i }));
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

  it("shows preset server default label instead of None when catalog omits the resolved id", async () => {
    ragPresetsData = [];
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    const presetSelect = await screen.findByRole("combobox", { name: /Preset/i });
    expect(presetSelect).toHaveValue("cafe0001-0001-4001-8001-000000000001");
    expect(screen.getByRole("option", { name: /^Server default$/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /^None$/i })).not.toBeInTheDocument();
    expect(patchMutate).not.toHaveBeenCalled();
  });

  it("fires document filter patch exactly once when toggling limit retrieval", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    const limitCb = await screen.findByRole("checkbox", { name: /Limit retrieval to selected documents/i });
    await user.click(limitCb);
    await waitFor(() =>
      expect(patchMutate).toHaveBeenCalledWith({
        conversationId: "c1",
        body: { documentFilter: ["d1"] },
      }),
    );
    expect(patchMutate).toHaveBeenCalledTimes(1);
  });

  it("does not toggle limit retrieval when conversation rows refresh with the same filter", async () => {
    const user = userEvent.setup();
    const { rerender, qc } = renderChat();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    const limitCb = await screen.findByRole("checkbox", { name: /Limit retrieval to selected documents/i });
    expect(limitCb).not.toBeChecked();
    patchMutate.mockClear();

    mockConvRows[0] = {
      ...mockConvRows[0],
      documentFilter: [],
    };
    conversationsQueryResult.data = [...mockConvRows];

    rerender(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <ChatPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    const limitAfter = await screen.findByRole("checkbox", { name: /Limit retrieval to selected documents/i });
    expect(limitAfter).not.toBeChecked();
    expect(patchMutate).not.toHaveBeenCalled();
  });

  it("sanitizes HTML gateway bodies in send errors", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (url: string | { toString(): string }) => {
      const u = typeof url === "string" ? url : url.toString();
      if (u.includes("/draft")) return { content: "" };
      if (u.includes("/conversations/c1/messages") && u.endsWith("/messages")) {
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
    await user.click(screen.getByRole("button", { name: /T1/i }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "x");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByText(/Gateway error: upstream service unavailable/i)).toBeInTheDocument();
    expect(screen.queryByText(/502 Bad Gateway/i)).not.toBeInTheDocument();
    const sendBtn = screen.getByRole("button", { name: /^Send$/i });
    expect(sendBtn).not.toBeDisabled();
  });

  it("surfaces friendly ApiError messages from controlled failures", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (url: string | { toString(): string }) => {
      const u = typeof url === "string" ? url : url.toString();
      if (u.includes("/draft")) return { content: "" };
      if (u.includes("/conversations/c1/messages") && u.endsWith("/messages")) {
        throw new ApiError(503, "The service used a direct answer fallback.", { kind: "http" });
      }
      return {};
    });
    renderChat();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "q");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    expect(await screen.findByText(/direct answer fallback/i)).toBeInTheDocument();
  });

  it("renders assistant bodies for backend-controlled ERROR turns", async () => {
    const user = userEvent.setup();
    renderChat();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    expect(
      await screen.findByText(/Answering without retrieval due to a temporary issue/i),
    ).toBeInTheDocument();
  });

  it("retries assistant on ERROR status after opening the conversation", async () => {
    const user = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (url: string | { toString(): string }) => {
      const u = typeof url === "string" ? url : url.toString();
      if (u.includes("/retry")) {
        return { jobId: "j2", status: "RUNNING", pollPath: "/x", streamPath: "/y" };
      }
      if (u.includes("/draft")) return { content: "" };
      if (u.includes("/messages") && u.includes("POST")) {
        return { jobId: "j1", status: "RUNNING", pollPath: "/x", streamPath: "/y" };
      }
      return {};
    });
    renderChat();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    await user.click(screen.getByRole("button", { name: /^Retry$/i }));
    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith("/conversations/c1/messages/m2/retry", expect.any(Object)),
    );
  });
});
