import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { ActivityHelpSheet } from "./ActivityHelpSheet";
import { useTraceStore } from "@/features/trace/trace.store";

vi.mock("@/features/rag/ExplainabilityPanel", () => ({
  ExplainabilityPanel: () => <div data-testid="explain-mock">explain</div>,
}));

describe("ActivityHelpSheet", () => {
  it("renders trace rows from the store when open", async () => {
    useTraceStore.getState().clearTraceEvents();
    useTraceStore.getState().addTraceEvent({
      section: "global",
      action: "open",
      message: "Sheet opened.",
      status: "info",
    });
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    const { rerender } = render(
      <IntlTestProvider locale="en">
        <ActivityHelpSheet open={false} onOpenChange={onOpenChange} isChatRoute={false} intro={<span>Intro</span>} />
      </IntlTestProvider>,
    );
    expect(screen.queryByText("Sheet opened.")).not.toBeInTheDocument();
    rerender(
      <IntlTestProvider locale="en">
        <ActivityHelpSheet open onOpenChange={onOpenChange} isChatRoute={false} intro={<span>Intro</span>} />
      </IntlTestProvider>,
    );
    expect(screen.getByText("Intro")).toBeVisible();
    expect(screen.getByText("Sheet opened.")).toBeVisible();
    await user.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenCalled();
  });

  it("includes explainability section on chat routes when open", () => {
    render(
      <IntlTestProvider locale="en">
        <ActivityHelpSheet open onOpenChange={() => {}} isChatRoute={true} intro={<span>x</span>} />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("explain-mock")).toBeInTheDocument();
  });
});
