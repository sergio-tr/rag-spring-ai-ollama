import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { CollapsiblePanel } from "./CollapsiblePanel";

describe("CollapsiblePanel", () => {
  it("renders title and reflects open state for assistive tech", () => {
    render(
      <IntlTestProvider>
        <CollapsiblePanel open>
          <span>Hint body</span>
        </CollapsiblePanel>
      </IntlTestProvider>,
    );
    expect(screen.getByRole("complementary")).toHaveAttribute("aria-hidden", "false");
    expect(screen.getByText("Tips")).toBeInTheDocument();
    expect(screen.getByText("Hint body")).toBeInTheDocument();
  });

  it("hides content from accessibility tree when closed", () => {
    render(
      <IntlTestProvider>
        <CollapsiblePanel open={false}>X</CollapsiblePanel>
      </IntlTestProvider>,
    );
    expect(screen.getByRole("complementary", { hidden: true })).toHaveAttribute("aria-hidden", "true");
  });
});
