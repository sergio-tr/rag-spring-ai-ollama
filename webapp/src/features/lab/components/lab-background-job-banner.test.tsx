import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabBackgroundJobBanner } from "./lab-background-job-banner";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";

describe("LabBackgroundJobBanner", () => {
  beforeEach(() => {
    useLabJobSessionStore.getState().clearBackgroundHint();
  });

  it("renders nothing without stopped-waiting hint", () => {
    const { container } = render(
      <IntlTestProvider>
        <LabBackgroundJobBanner />
      </IntlTestProvider>,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders actionable dismiss when hint is set", async () => {
    const user = userEvent.setup();
    useLabJobSessionStore.getState().setBackgroundHint({ jobId: "job-xyz", stoppedWaiting: true });
    render(
      <IntlTestProvider>
        <LabBackgroundJobBanner />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-background-job-banner")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/job-xyz/i);
    await user.click(screen.getByRole("button", { name: /Dismiss/i }));
    expect(useLabJobSessionStore.getState().backgroundHint).toBeNull();
  });
});
