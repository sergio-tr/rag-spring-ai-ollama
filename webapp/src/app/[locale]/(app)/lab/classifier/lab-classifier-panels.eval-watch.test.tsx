import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { AsyncTaskStatusDto } from "@/types/api";

const followLabJobMock = vi.hoisted(() => vi.fn());

vi.mock("@/lib/lab-job-follow", () => ({
  followLabJob: (...args: unknown[]) => followLabJobMock(...args),
}));

import * as apiClient from "@/lib/api-client";
import { LabJobPollTimeoutError } from "@/lib/async-task";
import { LabClassifierEvalPanel } from "./lab-classifier-panels";

describe("LabClassifierEvalPanel poll timeout + resume watching", () => {
  beforeEach(() => {
    followLabJobMock.mockReset();
    vi.spyOn(apiClient, "apiFetch").mockResolvedValue({
      jobId: "eval-timeout",
      status: "ACCEPTED",
      pollPath: "/lab/jobs/eval-timeout",
      streamPath: "/lab/jobs/eval-timeout/events",
    } as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("after poll watchdog timeout, Resume watching triggers a second followLabJob call", async () => {
    const user = userEvent.setup();
    const queued: AsyncTaskStatusDto = {
      id: "eval-timeout",
      taskType: "LAB",
      status: "QUEUED",
      progressText: null,
      result: null,
      errorMessage: null,
      terminal: false,
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: null,
    };
    followLabJobMock
      .mockRejectedValueOnce(new LabJobPollTimeoutError(queued))
      .mockResolvedValueOnce({
        ...queued,
        status: "SUCCEEDED",
        terminal: true,
        result: { ok: true },
      } as never);

    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabClassifierEvalPanel classifierOk />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole("button", { name: /Evaluate/i }));

    await waitFor(() => expect(screen.getByRole("button", { name: /Resume watching/i })).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /Resume watching/i }));

    await waitFor(() => expect(followLabJobMock).toHaveBeenCalledTimes(2));
  });
});
