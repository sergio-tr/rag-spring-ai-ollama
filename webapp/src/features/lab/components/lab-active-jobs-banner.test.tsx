import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabActiveJobsBanner } from "./lab-active-jobs-banner";
import { ApiError } from "@/lib/api-client";

const apiMock = vi.hoisted(() => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

const jobsMock = vi.hoisted(() => ({
  useActiveLabJobs: vi.fn(),
}));

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
}));

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return { ...actual, apiFetch: apiMock.apiFetch, apiProductPath: apiMock.apiProductPath };
});

vi.mock("@/features/lab/hooks/use-active-lab-jobs", () => jobsMock);
vi.mock("@/navigation", () => ({ useRouter: () => routerMock }));

describe("LabActiveJobsBanner", () => {
  beforeEach(() => {
    apiMock.apiFetch.mockReset();
    jobsMock.useActiveLabJobs.mockReset();
    routerMock.push.mockReset();
  });

  it("renders nothing when there are no active jobs and not loading", () => {
    jobsMock.useActiveLabJobs.mockReturnValue({ data: [], isLoading: false, refetch: vi.fn() });
    const { container } = render(
      <IntlTestProvider>
        <LabActiveJobsBanner />
      </IntlTestProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("routes to the matching section and shows human-readable benchmark label", () => {
    jobsMock.useActiveLabJobs.mockReturnValue({
      isLoading: false,
      data: [{ jobId: "j1", benchmarkKind: "RAG_PRESET_END_TO_END", status: "RUNNING", cancellable: false }],
      refetch: vi.fn(),
    });
    render(
      <IntlTestProvider>
        <LabActiveJobsBanner />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-active-job-row-j1")).toHaveTextContent(/Retrieval evaluation/i);
    expect(screen.queryByText(/RAG_PRESET_END_TO_END/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /view progress/i }));
    expect(routerMock.push).toHaveBeenCalledWith("/lab/evaluation/rag");
  });

  it("requests cancellation via confirm dialog and shows 409 too-late hint", async () => {
    const refetch = vi.fn();
    jobsMock.useActiveLabJobs.mockReturnValue({
      isLoading: false,
      data: [{ jobId: "j1", benchmarkKind: "LLM_JUDGE_QA", status: "RUNNING", cancellable: true }],
      refetch,
    });
    apiMock.apiFetch.mockRejectedValueOnce(new ApiError(409, "too_late"));
    render(
      <IntlTestProvider>
        <LabActiveJobsBanner />
      </IntlTestProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: /stop evaluation/i }));
    fireEvent.click(screen.getByTestId("lab-job-stop-confirm-button"));
    expect(await screen.findByText(/already finished/i)).toBeInTheDocument();
    expect(refetch).not.toHaveBeenCalled();
  });
});

