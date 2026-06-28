import { describe, it, expect } from "vitest";
import { shouldFetchLatestLabRun } from "./lab-run-resumption";

describe("shouldFetchLatestLabRun", () => {
  it("returns false while active jobs are loading", () => {
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: true,
        resumptionDecisionKind: "none",
        running: false,
        watchLive: false,
      }),
    ).toBe(false);
  });

  it("returns false during auto_follow or session_only recovery", () => {
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: false,
        resumptionDecisionKind: "auto_follow",
        running: false,
        watchLive: false,
      }),
    ).toBe(false);
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: false,
        resumptionDecisionKind: "session_only",
        running: false,
        watchLive: false,
      }),
    ).toBe(false);
  });

  it("returns true when backend recovery settled and not watching", () => {
    expect(
      shouldFetchLatestLabRun({
        activeJobsLoading: false,
        resumptionDecisionKind: "none",
        running: false,
        watchLive: false,
      }),
    ).toBe(true);
  });
});
