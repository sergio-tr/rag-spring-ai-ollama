import { describe, it, expect } from "vitest";
import { shouldFetchLatestLabRun } from "./lab-run-recovery";

describe("shouldFetchLatestLabRun", () => {
  it("returns false while active jobs are loading", () => {
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: true,
        recoveryDecisionKind: "none",
        running: false,
        watchLive: false,
      }),
    ).toBe(false);
  });

  it("returns false during auto_follow or session_only recovery", () => {
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: false,
        recoveryDecisionKind: "auto_follow",
        running: false,
        watchLive: false,
      }),
    ).toBe(false);
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: false,
        recoveryDecisionKind: "session_only",
        running: false,
        watchLive: false,
      }),
    ).toBe(false);
  });

  it("returns true when backend recovery settled and not watching", () => {
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: false,
        recoveryDecisionKind: "none",
        running: false,
        watchLive: false,
      }),
    ).toBe(true);
  });
});
