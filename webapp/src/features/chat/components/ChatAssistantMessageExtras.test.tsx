import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatAssistantMessageExtras } from "./ChatAssistantMessageExtras";
import type { MessageDto, ChatSourceDto } from "@/types/api";

function chatSource(overrides: Partial<ChatSourceDto> = {}): ChatSourceDto {
  return {
    documentId: "d1",
    projectDocumentId: null,
    filename: "doc.pdf",
    snippet: null,
    distance: null,
    distanceLabel: null,
    chunkIndex: null,
    detectedDate: null,
    metadata: null,
    ...overrides,
  };
}

function renderExtras(message: MessageDto) {
  return render(
    <IntlTestProvider>
      <ChatAssistantMessageExtras message={message} />
    </IntlTestProvider>,
  );
}

describe("ChatAssistantMessageExtras", () => {
  it("does not render for pending assistant messages", () => {
    renderExtras({
      id: "a-pending",
      role: "ASSISTANT",
      content: "Working…",
      createdAt: "",
      sources: [],
      queryType: null,
      pipelineSteps: null,
      status: "PROCESSING",
    });
    expect(screen.queryByTestId("chat-message-metadata")).not.toBeInTheDocument();
    expect(screen.queryByText(/No sources available/i)).not.toBeInTheDocument();
  });

  it("shows collapsed Details for completed answers", () => {
    renderExtras({
      id: "a-done",
      role: "ASSISTANT",
      content: "Done",
      createdAt: "",
      sources: [chatSource()],
      queryType: "DOCUMENT",
      pipelineSteps: [],
      status: "DONE",
      executionMetadata: { traceId: "trace-1" },
    });
    expect(screen.getByTestId("chat-message-metadata-toggle")).toHaveTextContent(/Answer quality checks/i);
    expect(screen.queryByText(/More information/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("chat-sources")).not.toBeVisible();
    const trace = screen.queryByTestId("chat-trace");
    if (trace) {
      expect(trace).not.toBeVisible();
    }
  });

  it("reveals sources and trace when expanded", async () => {
    const user = userEvent.setup();
    renderExtras({
      id: "a-done",
      role: "ASSISTANT",
      content: "Done",
      createdAt: "",
      sources: [chatSource()],
      queryType: "DOCUMENT",
      pipelineSteps: [],
      status: "DONE",
      executionMetadata: { traceId: "trace-1", workflowName: "wf" },
    });
    await user.click(screen.getByTestId("chat-message-metadata-toggle"));
    const panel = screen.getByTestId("chat-message-metadata-panel");
    expect(within(panel).getByTestId("chat-sources")).toBeVisible();
    const traceDisclosure = within(panel).getByTestId("chat-trace-disclosure");
    expect(traceDisclosure).not.toHaveAttribute("open");
    await user.click(within(traceDisclosure).getByText(/Advanced technical details/i));
    expect(within(panel).getByTestId("chat-trace")).toBeVisible();
    expect(within(panel).getByTestId("chat-sources")).toHaveTextContent(/Source documents/i);
    expect(within(panel).getByTestId("chat-sources")).toHaveTextContent("doc.pdf");
    expect(within(panel).getByTestId("chat-trace")).toHaveTextContent("trace-1");
  });

  it("shows short no-sources copy only inside expanded panel after completion", async () => {
    const user = userEvent.setup();
    renderExtras({
      id: "a-empty",
      role: "ASSISTANT",
      content: "Direct answer",
      createdAt: "",
      sources: [],
      queryType: null,
      pipelineSteps: null,
      status: "DONE",
    });
    expect(screen.queryByText(/No sources available for this answer/i)).not.toBeVisible();
    await user.click(screen.getByTestId("chat-message-metadata-toggle"));
    expect(screen.getByTestId("chat-sources")).toHaveTextContent(/No sources available for this answer/i);
  });

  it("groups duplicate filenames in expanded panel @SourceDedup", async () => {
    const user = userEvent.setup();
    renderExtras({
      id: "a-dedup",
      role: "ASSISTANT",
      content: "Done",
      createdAt: "",
      sources: [
        chatSource({ filename: "acta.pdf", chunkIndex: 1 }),
        chatSource({ filename: "acta.pdf", chunkIndex: 2 }),
      ],
      queryType: "DOCUMENT",
      pipelineSteps: [],
      status: "DONE",
    });
    await user.click(screen.getByTestId("chat-message-metadata-toggle"));
    const groups = screen.getAllByTestId("chat-source-group");
    expect(groups).toHaveLength(1);
    expect(screen.getByTestId("chat-source-chunks-toggle")).toHaveTextContent(/2 chunks/i);
  });
});
