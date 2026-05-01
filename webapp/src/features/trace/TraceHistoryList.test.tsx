import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { useTraceStore } from "@/features/trace/trace.store";
import { TraceHistoryList } from "./TraceHistoryList";

describe("TraceHistoryList", () => {
  beforeEach(() => {
    useTraceStore.getState().clearTraceEvents();
  });

  it("renders empty copy when there are no events", () => {
    render(
      <IntlTestProvider locale="en">
        <TraceHistoryList />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/No recent steps recorded yet/i)).toBeInTheDocument();
  });

  it("lists events from the store without exposing metadata values", () => {
    useTraceStore.getState().addTraceEvent({
      section: "chat",
      action: "send_started",
      message: "Your message was queued.",
      status: "in_progress",
      metadata: { correlationId: "secret-correlation-xyz" },
    });
    render(
      <IntlTestProvider locale="en">
        <TraceHistoryList />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("trace-history-list")).toBeInTheDocument();
    expect(screen.getByText("Your message was queued.")).toBeInTheDocument();
    expect(screen.getByText("send_started")).toBeInTheDocument();
    expect(screen.queryByText("secret-correlation-xyz")).not.toBeInTheDocument();
  });
});
