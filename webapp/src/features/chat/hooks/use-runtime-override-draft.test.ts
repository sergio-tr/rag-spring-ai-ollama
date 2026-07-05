import { describe, expect, it } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useRuntimeOverrideDraft } from "./use-runtime-override-draft";

describe("useRuntimeOverrideDraft", () => {
  it("returns only the changed key while keeping merged draft locally", () => {
    const { result, rerender } = renderHook(
      ({ persisted, pending }) => useRuntimeOverrideDraft(persisted, "c1", pending),
      {
        initialProps: { persisted: {} as Record<string, unknown>, pending: false },
      },
    );

    act(() => {
      const first = result.current.applyBooleanPatch("expansionEnabled", true);
      expect(first).toEqual({ expansionEnabled: true });
    });

    rerender({ persisted: {}, pending: true });

    act(() => {
      const second = result.current.applyBooleanPatch("nerEnabled", true);
      expect(second).toEqual({ nerEnabled: true });
      expect(result.current.getSnapshot()).toEqual({
        expansionEnabled: true,
        nerEnabled: true,
      });
    });
  });

  it("resets draft from persisted override when patch completes", () => {
    const { result, rerender } = renderHook(
      ({ persisted, pending }) => useRuntimeOverrideDraft(persisted, "c1", pending),
      {
        initialProps: {
          persisted: { expansionEnabled: true } as Record<string, unknown>,
          pending: true,
        },
      },
    );

    act(() => {
      result.current.applyBooleanPatch("nerEnabled", true);
    });

    rerender({
      persisted: { expansionEnabled: true, nerEnabled: true },
      pending: false,
    });

    act(() => {
      const next = result.current.applyBooleanPatch("memoryEnabled", true);
      expect(next).toEqual({ memoryEnabled: true });
    });
  });

  it("resets draft when conversation changes", () => {
    const { result, rerender } = renderHook(
      ({ conversationId, persisted }) => useRuntimeOverrideDraft(persisted, conversationId, false),
      {
        initialProps: {
          conversationId: "c1",
          persisted: { expansionEnabled: true } as Record<string, unknown>,
        },
      },
    );

    act(() => {
      result.current.applyBooleanPatch("nerEnabled", true);
    });

    rerender({ conversationId: "c2", persisted: {} });

    act(() => {
      expect(result.current.getSnapshot()).toEqual({});
    });
  });
});
