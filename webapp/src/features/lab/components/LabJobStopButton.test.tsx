import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabJobStopConfirmDialog } from "./lab-job-stop-confirm-dialog";

describe("LabJobStopButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("disables confirm while cancel request is in flight", async () => {
    let resolveCancel: (() => void) | undefined;
    const onConfirm = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveCancel = resolve;
        }),
    );
    render(
      <IntlTestProvider>
        <LabJobStopConfirmDialog open onOpenChange={() => {}} onConfirm={onConfirm} jobIdFragment="abcd1234" />
      </IntlTestProvider>,
    );

    const confirm = screen.getByTestId("lab-job-stop-confirm-button");
    fireEvent.click(confirm);
    await waitFor(() => expect(onConfirm).toHaveBeenCalledTimes(1));
    expect(confirm).toBeDisabled();
    expect(screen.getByText(/Stopping/i)).toBeInTheDocument();

    resolveCancel?.();
    await waitFor(() => expect(confirm).not.toBeDisabled());
  });

  it("shows cancelling label on confirm button while pending", async () => {
    const onConfirm = vi.fn().mockImplementation(
      () => new Promise<void>((resolve) => setTimeout(resolve, 50)),
    );
    render(
      <IntlTestProvider>
        <LabJobStopConfirmDialog open onOpenChange={() => {}} onConfirm={onConfirm} />
      </IntlTestProvider>,
    );

    fireEvent.click(screen.getByTestId("lab-job-stop-confirm-button"));
    expect(screen.getByTestId("lab-job-stop-confirm-button")).toHaveTextContent(/Stopping/i);
    await waitFor(() => expect(onConfirm).toHaveBeenCalledTimes(1));
  });
});
