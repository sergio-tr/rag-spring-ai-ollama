import { describe, it, expect, beforeEach, vi } from "vitest";
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

  it("renders trace message as plain text without injecting HTML nodes", () => {
    useTraceStore.getState().addTraceEvent({
      section: "global",
      action: "sanitized_display",
      message: '<img src="x" alt="">evil',
      status: "info",
    });
    const { container } = render(
      <IntlTestProvider locale="en">
        <TraceHistoryList />
      </IntlTestProvider>,
    );
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText(/<img/i)).toBeInTheDocument();
  });

  it("renders all status badge variants (success/error/warning/default)", () => {
    const s = useTraceStore.getState();
    s.addTraceEvent({ section: "lab", action: "a1", message: "m1", status: "success" });
    s.addTraceEvent({ section: "lab", action: "a2", message: "m2", status: "error" });
    s.addTraceEvent({ section: "lab", action: "a3", message: "m3", status: "warning" });
    s.addTraceEvent({ section: "lab", action: "a4", message: "m4", status: "in_progress" });
    render(
      <IntlTestProvider locale="en">
        <TraceHistoryList />
      </IntlTestProvider>,
    );
    // Status labels are rendered as text; badge variants are exercised via the switch.
    expect(screen.getByText("success")).toBeInTheDocument();
    expect(screen.getByText("error")).toBeInTheDocument();
    expect(screen.getByText("warning")).toBeInTheDocument();
    expect(screen.getByText("in progress")).toBeInTheDocument();
  });

  it("falls back to raw timestamp string when date formatting fails", () => {
    // Force Intl formatting to throw so TraceHistoryList uses the catch fallback (raw iso string).
    const mockFormatter = {
      format: () => {
        throw new RangeError("boom");
      },
      // Minimal surface for Intl.DateTimeFormat; extra members are not used by the component.
    } as unknown as Intl.DateTimeFormat;
    const spy = vi.spyOn(Intl, "DateTimeFormat").mockReturnValue(mockFormatter);
    const iso = "2026-05-08T12:34:56.789Z";
    const dateSpy = vi.spyOn(Date.prototype, "toISOString").mockReturnValue(iso);
    const s = useTraceStore.getState();
    s.addTraceEvent({ section: "global", action: "bad_time", message: "x", status: "info" });
    render(
      <IntlTestProvider locale="en">
        <TraceHistoryList />
      </IntlTestProvider>,
    );
    expect(screen.getByText(iso)).toBeInTheDocument();
    dateSpy.mockRestore();
    spy.mockRestore();
  });
});
