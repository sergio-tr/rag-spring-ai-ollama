import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { AppShell } from "./AppShell";

/** Locale-free pathname as returned by next-intl navigation helpers. */
let pathnameMock = "/chat";

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

vi.mock("@/components/layout/AppContextBreadcrumb", () => ({
  AppContextBreadcrumbGate: () => <div data-testid="breadcrumb-mock" />,
}));

vi.mock("@/components/layout/AppSectionActions", () => ({
  AppSectionActionsGate: () => <div data-testid="section-actions-mock" />,
}));

vi.mock("@/components/layout/use-sidebar-shell", () => ({
  useSidebarShell: () => ({
    railCollapsed: false,
    expandedWidthPx: 260,
    viewportWidthPx: 1200,
    toggleRailCollapsed: vi.fn(),
    applyResizeDelta: vi.fn(),
  }),
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
  it("renders primary toolbar inside the main column", () => {
    pathnameMock = "/projects";
    render(
      <IntlTestProvider>
        <AppShell panelBody={null}>
          <span>x</span>
        </AppShell>
      </IntlTestProvider>,
    );
    const main = document.querySelector("main");
    expect(main?.querySelector('[data-testid="app-main-toolbar"]')).toBeTruthy();
  });

  it("does not render Sign out in the main toolbar (sidebar/footer only)", () => {
    pathnameMock = "/projects";
    render(
      <IntlTestProvider>
        <AppShell panelBody={null}>
          <span>x</span>
        </AppShell>
      </IntlTestProvider>,
    );
    const toolbar = screen.getByTestId("app-main-toolbar");
    expect(within(toolbar).queryByRole("button", { name: /sign out/i })).not.toBeInTheDocument();
  });

  beforeEach(() => {
    pathnameMock = "/chat";
  });

  it("uses a wider readable max-width container on chat routes", () => {
    pathnameMock = "/chat";
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
    pathnameMock = "/projects";
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
