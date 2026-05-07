import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { useChatExplainStore } from "@/store/chat-explain.store";
import { ExplainabilityPanel } from "./ExplainabilityPanel";

describe("ExplainabilityPanel", () => {
  beforeEach(() => {
    useChatExplainStore.setState({
      lastDone: null,
      isStreaming: false,
      streamingText: "",
    });
  });

  it("shows streaming hint when busy", () => {
    useChatExplainStore.setState({ isStreaming: true });
    render(
      <IntlTestProvider>
        <ExplainabilityPanel />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/Generating answer/i)).toBeInTheDocument();
  });

  it("renders query type, pipeline, and sources when present", () => {
    useChatExplainStore.setState({
      isStreaming: false,
      lastDone: {
        answer: "a",
        queryType: "RAG",
        usedTool: false,
        toolUsed: null,
        sources: [{ doc: 1 }],
        pipelineSteps: [{ name: "retrieve", detail: "d" }],
      },
    });
    render(
      <IntlTestProvider>
        <ExplainabilityPanel />
      </IntlTestProvider>,
    );
    expect(screen.getByText("RAG")).toBeInTheDocument();
    expect(screen.getByText("retrieve")).toBeInTheDocument();
  });

  it("renders runtime telemetry when present", () => {
    useChatExplainStore.setState({
      isStreaming: false,
      lastDone: {
        answer: "a",
        queryType: "PLAIN",
        usedTool: false,
        toolUsed: null,
        sources: [],
        pipelineSteps: [],
        runtimeTelemetry: { memoryOutcome: "CONDENSED", routingRouteKind: "RETRIEVAL_WORKFLOW_ROUTE" },
      },
    });
    render(
      <IntlTestProvider>
        <ExplainabilityPanel />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("explain-runtime-telemetry")).toBeInTheDocument();
    expect(screen.getByText("memoryOutcome")).toBeInTheDocument();
  });
});
