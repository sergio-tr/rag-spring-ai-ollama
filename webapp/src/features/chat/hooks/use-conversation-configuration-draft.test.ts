import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useConversationConfigurationDraft } from "./use-conversation-configuration-draft";

describe("useConversationConfigurationDraft", () => {
  it("applyBooleanPatch returns only the changed key for backend merge", () => {
    const { result } = renderHook(() =>
      useConversationConfigurationDraft({ useAdvisor: false }, "c1", false),
    );

    let patch: Record<string, unknown> = {};
    act(() => {
      patch = result.current.applyBooleanPatch("rankerEnabled", false);
    });

    expect(patch).toEqual({ rankerEnabled: false });
    expect(result.current.getSnapshot()).toEqual({ useAdvisor: false, rankerEnabled: false });
  });

  it("applyPatch returns only the incoming patch keys", () => {
    const { result } = renderHook(() =>
      useConversationConfigurationDraft({}, "c1", false),
    );

    let patch: Record<string, unknown> = {};
    act(() => {
      patch = result.current.applyPatch({ topK: 12, similarityThreshold: 0.4 });
    });

    expect(patch).toEqual({ topK: 12, similarityThreshold: 0.4 });
  });
});
