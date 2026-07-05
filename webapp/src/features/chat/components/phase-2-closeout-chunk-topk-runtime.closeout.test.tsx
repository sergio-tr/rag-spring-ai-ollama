import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatAssistantMessageExtras } from "./ChatAssistantMessageExtras";
import type { MessageDto } from "@/types/api";

function messageWithRuntimeTrace(): MessageDto {
  return {
    id: "phase2-topk-runtime",
    role: "ASSISTANT",
    content: "ok",
    createdAt: "",
    sources: [],
    queryType: null,
    pipelineSteps: null,
    status: "DONE",
    executionMetadata: {
      traceId: "trace-topk-8",
      retrievalEffectiveTopK: 8,
      retrievalEffectiveSimilarityThreshold: 0.1,
      retrievalDenseFetchLimit: 50,
      retrievalDenseCandidateCount: 8,
      retrievalAfterFilterCount: 8,
      retrievalAfterCompressionCount: 3,
      retrievalContextReductionReason: "section_merge",
    },
  };
}

describe("phase 2 closeout chunk topk runtime", () => {
  it("shows effective topK and reduction reason in technical trace", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ChatAssistantMessageExtras message={messageWithRuntimeTrace()} />
      </IntlTestProvider>,
    );

    await user.click(screen.getByTestId("chat-message-metadata-toggle"));
    const traceDisclosure = screen.getByTestId("chat-trace-disclosure");
    await user.click(within(traceDisclosure).getByText(/Advanced technical details/i));

    const trace = screen.getByTestId("chat-trace");
    expect(trace).toHaveTextContent("effectiveTopK=8");
    expect(trace).toHaveTextContent("threshold=0.1");
    expect(trace).toHaveTextContent("dense=8");
    expect(trace).toHaveTextContent("afterFilter=8");
    expect(trace).toHaveTextContent("final=3");
    expect(screen.getByTestId("chat-trace-reduction-reason")).toHaveTextContent(/Section merge/i);
  });
});
