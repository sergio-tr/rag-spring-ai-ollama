import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SidebarResizeHandle } from "./SidebarResizeHandle";

describe("SidebarResizeHandle", () => {
  it("calls onDragDelta on ArrowLeft/ArrowRight", () => {
    const onDragDelta = vi.fn();
    render(<SidebarResizeHandle ariaLabel="resize" onDragDelta={onDragDelta} keyboardStepPx={10} />);
    const btn = screen.getByRole("button", { name: "resize" });

    fireEvent.keyDown(btn, { key: "ArrowLeft" });
    fireEvent.keyDown(btn, { key: "ArrowRight" });

    expect(onDragDelta).toHaveBeenCalledWith(-10);
    expect(onDragDelta).toHaveBeenCalledWith(10);
  });

  it("does nothing when disabled", () => {
    const onDragDelta = vi.fn();
    render(<SidebarResizeHandle ariaLabel="resize" onDragDelta={onDragDelta} disabled />);
    const btn = screen.getByRole("button", { name: "resize" });

    fireEvent.keyDown(btn, { key: "ArrowLeft" });
    fireEvent.pointerDown(btn, { pointerId: 1 });
    fireEvent.pointerMove(btn, { pointerId: 1, movementX: 12 });

    expect(onDragDelta).not.toHaveBeenCalled();
  });

  it("drags and calls onDragDelta on movementX", () => {
    const onDragDelta = vi.fn();
    render(<SidebarResizeHandle ariaLabel="resize" onDragDelta={onDragDelta} />);
    const btn = screen.getByRole("button", { name: "resize" });

    // jsdom/happy-dom doesn't fully implement pointer capture, but movementX branch is what we care about.
    fireEvent.pointerDown(btn, { pointerId: 1 });
    fireEvent.pointerMove(btn, { pointerId: 1, movementX: 7 });
    fireEvent.pointerUp(btn, { pointerId: 1 });

    expect(onDragDelta).toHaveBeenCalledWith(7);
  });
});
