import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  initialSnapshotFromAccepted,
  type PersistedLabJobRecord,
} from "@/features/lab/lib/lab-job-persistence";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabBackgroundJobBanner } from "./lab-background-job-banner";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import type { LabJobAcceptedDto } from "@/types/api";

const push = vi.fn();

vi.mock("@/navigation", () => ({
  usePathname: () => "/en/lab/classifier",
  useRouter: () => ({ push }),
}));

function baseAccepted(jobId: string): LabJobAcceptedDto {
  return {
    jobId,
    status: "QUEUED",
    pollPath: `/lab/jobs/${jobId}`,
    streamPath: `/lab/jobs/${jobId}/events`,
  };
}

function makeRecord(partial: Partial<PersistedLabJobRecord> & Pick<PersistedLabJobRecord, "jobId" | "sectionKey">): PersistedLabJobRecord {
  const accepted = partial.accepted ?? baseAccepted(partial.jobId);
  const now = Date.now();
  return {
    jobId: partial.jobId,
    sectionKey: partial.sectionKey,
    accepted,
    followMode: partial.followMode ?? "poll",
    startedAtMs: partial.startedAtMs ?? now,
    lastUpdatedMs: partial.lastUpdatedMs ?? now,
    lastStatus: partial.lastStatus ?? initialSnapshotFromAccepted(accepted, "LAB"),
    stoppedWatching: partial.stoppedWatching ?? false,
    staleNotFound: partial.staleNotFound ?? false,
    pollTimedOut: partial.pollTimedOut ?? false,
    dismissedTerminal: partial.dismissedTerminal ?? false,
  };
}

describe("LabBackgroundJobBanner", () => {
  beforeEach(() => {
    push.mockClear();
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0, forgetWatchNonce: 0 });
    useLabJobSessionStore.persist.clearStorage();
  });

  it("renders nothing when there are no lab job records", () => {
    const { container } = render(
      <IntlTestProvider>
        <LabBackgroundJobBanner />
      </IntlTestProvider>,
    );
    expect(container.firstChild).toBeNull();
  });

  it("shows stale job messaging and clears the record", async () => {
    const user = userEvent.setup();
    useLabJobSessionStore.setState({
      records: [
        makeRecord({
          jobId: "job-stale",
          sectionKey: "classifier-eval",
          staleNotFound: true,
        }),
      ],
    });
    render(
      <IntlTestProvider>
        <LabBackgroundJobBanner />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-job-session-banner")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/job-stale/i);
    await user.click(screen.getByRole("button", { name: /Clear stale job/i }));
    expect(useLabJobSessionStore.getState().records).toHaveLength(0);
  });

  it("resume queues navigation + nonce bump for stopped-watching jobs", async () => {
    const user = userEvent.setup();
    useLabJobSessionStore.setState({
      records: [
        makeRecord({
          jobId: "job-sw",
          sectionKey: "classifier-eval",
          stoppedWatching: true,
          lastStatus: initialSnapshotFromAccepted(baseAccepted("job-sw"), "LAB"),
        }),
      ],
    });
    render(
      <IntlTestProvider>
        <LabBackgroundJobBanner />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /Resume watching/i }));
    expect(useLabJobSessionStore.getState().resumeNonce).toBeGreaterThan(0);
    expect(push).toHaveBeenCalledWith("/lab/classifier");
  });

  it("stop watching clears session record and bumps forget nonce", async () => {
    const user = userEvent.setup();
    useLabJobSessionStore.setState({
      records: [
        makeRecord({
          jobId: "job-live",
          sectionKey: "evaluation-llm",
          lastStatus: {
            id: "job-live",
            taskType: "LAB",
            status: "RUNNING",
            terminal: false,
            progressText: null,
            errorMessage: null,
            failureCode: null,
            result: null,
          },
        }),
      ],
      forgetWatchNonce: 0,
    });
    render(
      <IntlTestProvider>
        <LabBackgroundJobBanner />
      </IntlTestProvider>,
    );
    await user.click(screen.getByRole("button", { name: /Stop watching/i }));
    expect(useLabJobSessionStore.getState().records).toHaveLength(0);
    expect(useLabJobSessionStore.getState().forgetWatchNonce).toBe(1);
  });
});
