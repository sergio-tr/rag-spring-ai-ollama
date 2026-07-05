import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import type { ReactNode } from "react";
import { useProjectCreateFeedbackNotifier } from "./use-project-create-feedback-notifier";
import { useProjectCreateFeedbackStore } from "@/features/projects/lib/project-create-feedback-state";

function wrapper({ children }: { children: ReactNode }) {
  return <IntlTestProvider locale="en">{children}</IntlTestProvider>;
}

describe("useProjectCreateFeedbackNotifier", () => {
  beforeEach(() => {
    useProjectCreateFeedbackStore.setState({ feedback: null });
  });

  it("stores activate warning for sidebar and section entry points", () => {
    const { result } = renderHook(() => useProjectCreateFeedbackNotifier(), { wrapper });
    act(() => {
      result.current({
        project: { id: "p1", name: "Sidebar project", docCount: 0, convCount: 0, updatedAt: "" },
        activateFailed: true,
      });
    });
    const feedback = useProjectCreateFeedbackStore.getState().feedback;
    expect(feedback?.warning).toMatch(/Activation did not complete/i);
  });

  it("does not show feedback on a clean success", () => {
    const { result } = renderHook(() => useProjectCreateFeedbackNotifier(), { wrapper });
    act(() => {
      result.current({
        project: { id: "p3", name: "Clean", docCount: 0, convCount: 0, updatedAt: "" },
      });
    });
    expect(useProjectCreateFeedbackStore.getState().feedback).toBeNull();
  });

  it("stores refresh warning without treating it as failure", () => {
    const { result } = renderHook(() => useProjectCreateFeedbackNotifier(), { wrapper });
    act(() => {
      result.current({
        project: { id: "p2", name: "Refresh warn", docCount: 0, convCount: 0, updatedAt: "" },
        refreshFailed: true,
      });
    });
    const feedback = useProjectCreateFeedbackStore.getState().feedback;
    expect(feedback?.warning).toMatch(/could not be refreshed/i);
  });
});
