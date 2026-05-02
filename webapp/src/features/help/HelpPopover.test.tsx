import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { HelpPopover } from "./HelpPopover";

vi.mock("@/navigation", () => ({
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

describe("HelpPopover", () => {
  it("opens on trigger activation and exposes title/message", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="en">
        <HelpPopover
          triggerAriaLabel="Help topic trigger"
          title="Topic title"
          message="Short help."
          details="Extra detail."
        />
      </IntlTestProvider>,
    );
    const trigger = screen.getByRole("button", { name: /Help topic trigger/i });
    expect(trigger).toBeInTheDocument();
    await user.click(trigger);
    expect(screen.getByText("Topic title")).toBeVisible();
    expect(screen.getByText("Short help.")).toBeVisible();
    expect(screen.getByText("Extra detail.")).toBeVisible();
  });

  it("closes when Escape is pressed", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="en">
        <HelpPopover triggerAriaLabel="Help escape" title="T" message="M" />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /Help escape/i }));
    expect(screen.getByText("T")).toBeVisible();
    await user.keyboard("{Escape}");
    expect(screen.queryByText("M")).not.toBeInTheDocument();
  });
});
