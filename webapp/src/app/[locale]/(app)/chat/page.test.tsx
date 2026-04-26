import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
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

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

import { apiFetch } from "@/lib/api-client";

vi.mock("@/store/app.store", () => ({
  useAppStore: (sel: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    sel({ activeProject: { id: "p1", name: "P" } }),
}));

const mockConvs = [
  { id: "c1", title: "T1", updatedAt: "", presetId: null as string | null, documentFilter: undefined },
];
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
    content: "ok",
    createdAt: "",
    sources: null,
    queryType: null,
    pipelineSteps: null,
    status: "ERROR",
  },
];

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useConversations: () => ({ data: mockConvs }),
  useCreateConversation: () => ({
    mutateAsync: vi.fn().mockResolvedValue({ id: "c2", title: "New", updatedAt: "", presetId: null }),
    isPending: false,
  }),
  useConversationMessages: () => ({ data: mockMessages, refetch: vi.fn() }),
  usePatchConversation: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useMoveConversation: () => ({
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
  }),
}));

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => ({
    data: [{ id: "d1", fileName: "f.pdf", status: "READY" as const, chunkCount: 1, errorMessage: null, uploadedAt: "", reindexedAt: null }],
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

vi.mock("@/features/chat/hooks/use-rag-presets", () => ({
  useRagPresets: () => ({ data: [{ id: "pr1", name: "P", description: null, tags: [], values: {}, system: false, createdAt: "", updatedAt: "" }], isError: false }),
}));

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

describe("ChatPage", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
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
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <ChatPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("button", { name: /New conversation/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /T1/i }));
    const input = await screen.findByPlaceholderText(/Message/i);
    await user.type(input, "hello");
    await user.click(screen.getByRole("button", { name: /^Send$/i }));
    await waitFor(() => expect(followLabJob).toHaveBeenCalled());
  });

  it("retries assistant on ERROR status", async () => {
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
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <ChatPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /retryAssistant|Retry/i }));
    await waitFor(() => expect(apiFetch).toHaveBeenCalled());
  });
});
