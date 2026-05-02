"use client";

import { useCallback, useRef } from "react";
import { cn } from "@/lib/utils";

type SidebarResizeHandleProps = Readonly<{
  className?: string;
  disabled?: boolean;
  /** Horizontal delta in pixels (positive grows sidebar width toward main content). */
  onDragDelta: (deltaXPx: number) => void;
  /** StepWidth keyboard nudge (default 12px). */
  keyboardStepPx?: number;
  ariaLabel: string;
}>;

/**
 * Draggable vertical sash between sidebar and main column (desktop only).
 */
export function SidebarResizeHandle({
  className,
  disabled,
  onDragDelta,
  keyboardStepPx = 12,
  ariaLabel,
}: SidebarResizeHandleProps) {
  const draggingRef = useRef(false);

  const endDrag = useCallback((ev: React.PointerEvent<HTMLElement>) => {
    if (!draggingRef.current) return;
    draggingRef.current = false;
    try {
      ev.currentTarget.releasePointerCapture(ev.pointerId);
    } catch {
      /* ignore */
    }
  }, []);

  const onPointerDown = useCallback(
    (ev: React.PointerEvent<HTMLButtonElement>) => {
      if (disabled) return;
      ev.preventDefault();
      draggingRef.current = true;
      ev.currentTarget.setPointerCapture(ev.pointerId);
    },
    [disabled],
  );

  const onPointerMove = useCallback(
    (ev: React.PointerEvent<HTMLButtonElement>) => {
      if (!draggingRef.current || disabled) return;
      if (ev.movementX !== 0) {
        onDragDelta(ev.movementX);
      }
    },
    [disabled, onDragDelta],
  );

  const onKeyDown = useCallback(
    (ev: React.KeyboardEvent<HTMLButtonElement>) => {
      if (disabled) return;
      if (ev.key === "ArrowLeft") {
        ev.preventDefault();
        onDragDelta(-keyboardStepPx);
      } else if (ev.key === "ArrowRight") {
        ev.preventDefault();
        onDragDelta(keyboardStepPx);
      }
    },
    [disabled, keyboardStepPx, onDragDelta],
  );

  return (
    <button
      type="button"
      aria-label={ariaLabel}
      disabled={disabled}
      className={cn(
        "bg-border hover:bg-primary/30 active:bg-primary/40 h-full w-2 shrink-0 cursor-col-resize border-0 p-0",
        "focus-visible:ring-ring focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none",
        disabled && "cursor-not-allowed opacity-40",
        className,
      )}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onLostPointerCapture={() => {
        draggingRef.current = false;
      }}
      onKeyDown={onKeyDown}
    />
  );
}
