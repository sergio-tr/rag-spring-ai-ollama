import type { AsyncTaskStatusDto } from "@/types/api";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { IntlTestProvider } from "@/test-utils/intl";
import { useTraceStore } from "@/features/trace/trace.store";
import * as asyncTask from "@/lib/async-task";
import { LabJobPollTimeoutError } from "@/lib/async-task";
import { useAccountExportSessionStore } from "@/features/settings/store/account-export-session.store";

const apiFetchMock = vi.fn();
const apiDownloadBlobMock = vi.fn();

vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return {
    ...actual,
    apiFetch: (...args: unknown[]) => apiFetchMock(...args) as ReturnType<typeof actual.apiFetch>,
    apiDownloadBlob: (...args: unknown[]) =>
      apiDownloadBlobMock(...args) as ReturnType<typeof actual.apiDownloadBlob>,
  };
});

import { ACCOUNT_EXPORT_TRACE_STOPPED_WATCHING } from "@/features/settings/lib/account-export-trace";
import { AccountExportPanel } from "./AccountExportPanel";

const JOB_ID = "550e8400-e29b-41d4-a716-446655440001";

function taskDto(partial: Partial<AsyncTaskStatusDto>): AsyncTaskStatusDto {
  return {
    id: JOB_ID,
    taskType: "ACCOUNT_EXPORT",
    status: "QUEUED",
    progressText: null,
    result: null,
    errorMessage: null,
    terminal: false,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    startedAt: null,
    completedAt: null,
    ...partial,
  };
}

describe("AccountExportPanel", () => {
  let pollSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorage.clear();
    useAccountExportSessionStore.persist.clearStorage();
    useAccountExportSessionStore.getState().__resetForTests();
    useTraceStore.getState().clearTraceEvents();
    apiFetchMock.mockReset();
    apiDownloadBlobMock.mockReset();
    pollSpy = vi.spyOn(asyncTask, "pollAccountJob");
  });

  afterEach(() => {
    pollSpy.mockRestore();
  });

  it("runs queued/running ticks then shows download when succeeded", async () => {
    const user = userEvent.setup();
    apiFetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
      const u = String(url);
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "POST" && u.includes("/me/account/export")) {
        return {
          jobId: JOB_ID,
          status: "ACCEPTED",
          pollPath: `/api/v5/me/account/jobs/${JOB_ID}`,
        };
      }
      throw new Error(`unexpected ${method} ${u}`);
    });

    const succeeded = taskDto({
      status: "SUCCEEDED",
      terminal: true,
      result: { exportArtifactId: "artifact-1" },
      completedAt: "2025-01-01T00:02:00Z",
    });

    pollSpy.mockImplementation(async (_jobId: string, onTick: (s: AsyncTaskStatusDto) => void) => {
      onTick(taskDto({ status: "QUEUED" }));
      onTick(taskDto({ status: "RUNNING", progressText: "Writing ZIP…", startedAt: "2025-01-01T00:00:01Z" }));
      onTick(succeeded);
      return succeeded;
    });

    apiDownloadBlobMock.mockResolvedValue(new Blob(["zip-bytes"], { type: "application/zip" }));

    render(
      <IntlTestProvider>
        <AccountExportPanel />
      </IntlTestProvider>,
    );

    await user.click(screen.getByTestId("account-export-request"));

    await waitFor(() => {
      expect(screen.getByTestId("account-export-download")).toBeInTheDocument();
    });

    await user.click(screen.getByTestId("account-export-download"));

    await waitFor(() => {
      expect(apiDownloadBlobMock).toHaveBeenCalled();
    });

    const traceActions = useTraceStore.getState().events.map((e) => e.action);
    expect(traceActions).toContain("account_export_queued");
    expect(traceActions).toContain("account_export_running");
    expect(traceActions).toContain("account_export_completed");
  });

  it("shows a controlled error when the async task fails", async () => {
    const user = userEvent.setup();
    apiFetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
      const u = String(url);
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "POST" && u.includes("/me/account/export")) {
        return {
          jobId: JOB_ID,
          status: "ACCEPTED",
          pollPath: `/api/v5/me/account/jobs/${JOB_ID}`,
        };
      }
      throw new Error(`unexpected ${method} ${u}`);
    });

    const failed = taskDto({
      status: "FAILED",
      terminal: true,
      errorMessage: "disk full",
      completedAt: "2025-01-01T00:02:00Z",
    });

    pollSpy.mockImplementation(async (_jobId: string, onTick: (s: AsyncTaskStatusDto) => void) => {
      onTick(failed);
      throw new Error(failed.errorMessage ?? "Job failed");
    });

    render(
      <IntlTestProvider>
        <AccountExportPanel />
      </IntlTestProvider>,
    );

    await user.click(screen.getByTestId("account-export-request"));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/disk full/i);
    });

    expect(useTraceStore.getState().events.some((e) => e.action === "account_export_failed")).toBe(true);
  });

  it("surfaces poll timeout with resume and emits stopped-watching trace", async () => {
    const user = userEvent.setup();
    apiFetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
      const u = String(url);
      const method = (init?.method ?? "GET").toUpperCase();
      if (method === "POST" && u.includes("/me/account/export")) {
        return {
          jobId: JOB_ID,
          status: "ACCEPTED",
          pollPath: `/api/v5/me/account/jobs/${JOB_ID}`,
        };
      }
      throw new Error(`unexpected ${method} ${u}`);
    });

    const queued = taskDto({ status: "QUEUED" });
    pollSpy.mockImplementation(async () => {
      throw new LabJobPollTimeoutError(queued);
    });

    render(
      <IntlTestProvider>
        <AccountExportPanel />
      </IntlTestProvider>,
    );

    await user.click(screen.getByTestId("account-export-request"));

    await waitFor(() => {
      expect(screen.getByTestId("account-export-resume")).toBeInTheDocument();
    });

    expect(screen.getByTestId("account-export-status")).toHaveTextContent(/Stopped checking after several minutes/i);

    const traceActions = useTraceStore.getState().events.map((e) => e.action);
    expect(traceActions).toContain("account_export_queued");
    expect(traceActions).toContain(ACCOUNT_EXPORT_TRACE_STOPPED_WATCHING);
  });

  it("auto-resumes polling when a non-terminal job exists in the persisted session", async () => {
    const queued = taskDto({ status: "QUEUED" });
    const succeeded = taskDto({
      status: "SUCCEEDED",
      terminal: true,
      result: { exportArtifactId: "artifact-remount" },
    });

    pollSpy.mockImplementation(async (_jobId: string, onTick: (s: AsyncTaskStatusDto) => void) => {
      onTick(queued);
      onTick(succeeded);
      return succeeded;
    });

    act(() => {
      useAccountExportSessionStore.getState().resetForNewExport({
        jobId: JOB_ID,
        status: "ACCEPTED",
        pollPath: `/api/v5/me/account/jobs/${JOB_ID}`,
      });
      useAccountExportSessionStore.getState().patchFromTick(queued);
    });

    render(
      <IntlTestProvider>
        <AccountExportPanel />
      </IntlTestProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("account-export-download")).toBeInTheDocument();
    });

    expect(pollSpy).toHaveBeenCalled();
  });
});
