import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { AppShell } from "./AppShell";

let pathnameMock = "/en/chat";

vi.mock("@/navigation", () => ({
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => pathnameMock,
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: () => null,
}));

vi.mock("@/components/layout/AppSidebar", () => ({
  AppSidebar: () => <div data-testid="sidebar-mock" />,
}));

vi.mock("@/components/layout/CollapsiblePanel", () => ({
  CollapsiblePanel: ({ children }: { children: React.ReactNode }) => (
    <aside data-testid="panel-mock">{children}</aside>
  ),
}));

vi.mock("@/components/layout/ThemeLanguageMenu", () => ({
  ThemeLanguageMenu: () => null,
}));

vi.mock("@/components/auth/SessionExpiredBridge", () => ({
  SessionExpiredBridge: () => null,
}));

vi.mock("@/features/auth/lib/session-client", () => ({
  clearSessionCookie: vi.fn(),
}));

vi.mock("@/features/rag/ExplainabilityPanel", () => ({
  ExplainabilityPanel: () => <div data-testid="explain-mock" />,
}));

describe("AppShell", () => {
  beforeEach(() => {
    pathnameMock = "/en/chat";
  });

  it("uses a wider readable max-width container on chat routes", () => {
    pathnameMock = "/en/chat";
    const { container } = render(
      <IntlTestProvider>
        <AppShell panelBody={null}>
          <span>chat-child</span>
        </AppShell>
      </IntlTestProvider>,
    );
    expect(screen.getByText("chat-child")).toBeInTheDocument();
    const shell = container.querySelector("main .max-w-6xl");
    expect(shell).toBeTruthy();
    expect(shell?.className).toMatch(/max-w-6xl/);
    expect(shell?.className).toMatch(/mx-auto/);
  });

  it("uses the standard max-width container on non-chat routes", () => {
    pathnameMock = "/en/projects";
    const { container } = render(
      <IntlTestProvider>
        <AppShell panelBody={null}>
          <span>projects-child</span>
        </AppShell>
      </IntlTestProvider>,
    );
    expect(screen.getByText("projects-child")).toBeInTheDocument();
    const shell = container.querySelector("main .max-w-5xl");
    expect(shell).toBeTruthy();
    expect(shell?.className).not.toMatch(/max-w-6xl/);
  });
});
