import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { useSidebarShell } from "./use-sidebar-shell";

const persistenceMock = vi.hoisted(() => ({
  readSidebarPersistence: vi.fn(),
  patchSidebarPersistence: vi.fn(),
}));

vi.mock("@/components/layout/sidebar-persistence", () => persistenceMock);

function ShellProbe() {
  const shell = useSidebarShell();
  return (
    <div>
      <div data-testid="rail">{String(shell.railCollapsed)}</div>
      <div data-testid="w">{shell.expandedWidthPx}</div>
      <div data-testid="vw">{shell.viewportWidthPx}</div>
      <button type="button" onClick={shell.toggleRailCollapsed}>
        toggle
      </button>
      <button type="button" onClick={() => shell.applyResizeDelta(50)}>
        grow
      </button>
    </div>
  );
}

describe("useSidebarShell", () => {
  beforeEach(() => {
    persistenceMock.readSidebarPersistence.mockReset();
    persistenceMock.patchSidebarPersistence.mockReset();
  });

  it("hydrates persisted values and clamps width to viewport", async () => {
    persistenceMock.readSidebarPersistence.mockReturnValue({
      shellCollapsed: true,
      sidebarWidthPx: 9000,
    });
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 600 });

    render(<ShellProbe />);

    // Hydration happens in a microtask.
    await waitFor(() => expect(screen.getByTestId("rail")).toHaveTextContent("true"));
    expect(screen.getByTestId("vw")).toHaveTextContent("600");
    // maxSidebarWidthForViewport(600) = floor(600*0.33)=198 → clamped to MIN=200
    expect(screen.getByTestId("w")).toHaveTextContent("200");
  });

  it("toggleRailCollapsed patches persistence", async () => {
    persistenceMock.readSidebarPersistence.mockReturnValue({});
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 1200 });

    render(<ShellProbe />);
    await waitFor(() => expect(screen.getByTestId("vw")).toHaveTextContent("1200"));

    fireEvent.click(screen.getByText("toggle"));
    expect(persistenceMock.patchSidebarPersistence).toHaveBeenCalledWith({ shellCollapsed: true });
  });

  it("applyResizeDelta is ignored while railCollapsed", async () => {
    persistenceMock.readSidebarPersistence.mockReturnValue({ shellCollapsed: true, sidebarWidthPx: 260 });
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 1200 });

    render(<ShellProbe />);
    await waitFor(() => expect(screen.getByTestId("rail")).toHaveTextContent("true"));

    fireEvent.click(screen.getByText("grow"));
    expect(persistenceMock.patchSidebarPersistence).not.toHaveBeenCalledWith(expect.objectContaining({ sidebarWidthPx: expect.any(Number) }));
  });
});

