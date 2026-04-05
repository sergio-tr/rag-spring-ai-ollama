import type { ReactNode } from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { AppSidebar } from "./AppSidebar";

vi.mock("@/navigation", () => ({
  Link: ({ href, children, className }: { href: string; children: ReactNode; className?: string }) => (
    <a href={href} className={className}>
      {children}
    </a>
  ),
  usePathname: () => "/projects",
}));

describe("AppSidebar", () => {
  it("renders main navigation links", () => {
    render(
      <IntlTestProvider>
        <AppSidebar />
      </IntlTestProvider>,
    );
    expect(screen.getByLabelText("Main")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /projects/i })).toHaveAttribute("href", "/projects");
    expect(screen.getByRole("link", { name: /settings/i })).toHaveAttribute("href", "/settings");
  });
});
