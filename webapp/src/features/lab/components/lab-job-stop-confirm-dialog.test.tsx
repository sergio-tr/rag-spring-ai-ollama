import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabJobStopConfirmDialog } from "./lab-job-stop-confirm-dialog";

describe("LabJobStopConfirmDialog", () => {
  it("calls onConfirm when user confirms stop", async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(
      <IntlTestProvider>
        <LabJobStopConfirmDialog open onOpenChange={() => {}} onConfirm={onConfirm} jobIdFragment="abcd1234" />
      </IntlTestProvider>,
    );

    fireEvent.click(screen.getByTestId("lab-job-stop-confirm-button"));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledTimes(1));
  });
});
