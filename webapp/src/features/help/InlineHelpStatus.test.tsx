import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import type { TraceStatus } from "@/features/trace/trace-types";
import { InlineHelpStatus } from "./InlineHelpStatus";

describe("InlineHelpStatus", () => {
  it.each([
    ["info", "Idle"],
    ["in_progress", "Running"],
    ["success", "Saved"],
    ["warning", "Retry suggested"],
    ["error", "Failed"],
  ] as ReadonlyArray<[TraceStatus, string]>)("renders %s variant with label", (status, label) => {
    render(
      <IntlTestProvider locale="en">
        <InlineHelpStatus status={status} label={label} />
      </IntlTestProvider>,
    );
    const live = screen.getByRole("status");
    expect(live).toHaveTextContent(label);
    if (status === "in_progress") {
      expect(live).toHaveAttribute("aria-live", "polite");
    } else {
      expect(live).not.toHaveAttribute("aria-live");
    }
  });
});
