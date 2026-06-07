import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useMediaQuery } from "./use-media-query";

describe("useMediaQuery", () => {
  beforeEach(() => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === "(min-width: 768px)",
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns initial match state from matchMedia", () => {
    const { result } = renderHook(() => useMediaQuery("(min-width: 768px)"));
    expect(result.current).toBe(true);
  });

  it("updates when the media query change event fires", () => {
    let onChange: (() => void) | undefined;
    const mql = {
      matches: false,
      media: "",
      addEventListener: (_: string, fn: () => void) => {
        onChange = fn;
      },
      removeEventListener: vi.fn(),
    };
    window.matchMedia = vi.fn().mockReturnValue(mql);

    const { result } = renderHook(() => useMediaQuery("(max-width: 1px)"));
    expect(result.current).toBe(false);

    act(() => {
      mql.matches = true;
      onChange?.();
    });
    expect(result.current).toBe(true);
  });

  it("skips subscribe and cleanup when matchMedia lacks event APIs", () => {
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      media: "",
    });

    const { result, unmount } = renderHook(() => useMediaQuery("(min-width: 0px)"));
    expect(result.current).toBe(true);
    expect(() => unmount()).not.toThrow();
  });
});
