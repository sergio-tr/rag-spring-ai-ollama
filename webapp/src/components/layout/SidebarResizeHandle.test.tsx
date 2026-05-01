import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SidebarResizeHandle } from "./SidebarResizeHandle";

describe("SidebarResizeHandle", () => {
  it("calls onDragDelta with keyboard arrow steps", async () => {
    const user = userEvent.setup();
    const onDragDelta = vi.fn();
    render(
      <SidebarResizeHandle ariaLabel="Resize sidebar" onDragDelta={onDragDelta} keyboardStepPx={10} />,
    );
    const control = screen.getByRole("button", { name: /resize sidebar/i });
    control.focus();
    await user.keyboard("{ArrowRight}");
    await user.keyboard("{ArrowLeft}");
    expect(onDragDelta).toHaveBeenCalledWith(10);
    expect(onDragDelta).toHaveBeenCalledWith(-10);
  });

  it("does not call onDragDelta when disabled", async () => {
    const user = userEvent.setup();
    const onDragDelta = vi.fn();
    render(
      <SidebarResizeHandle ariaLabel="Resize" disabled onDragDelta={onDragDelta} />,
    );
    const control = screen.getByRole("button", { name: /^resize$/i });
    control.focus();
    await user.keyboard("{ArrowRight}");
    expect(onDragDelta).not.toHaveBeenCalled();
  });
});
