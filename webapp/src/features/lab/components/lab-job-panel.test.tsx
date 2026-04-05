import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabJobPanel } from "./lab-job-panel";

describe("LabJobPanel", () => {
  it("returns null when no job", () => {
    const { container } = render(
      <IntlTestProvider>
        <LabJobPanel accepted={null} taskStatus={null} />
      </IntlTestProvider>,
    );
    expect(container.firstChild).toBeNull();
  });

  it("shows job metadata and copies id", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
    render(
      <IntlTestProvider>
        <LabJobPanel
          accepted={{ jobId: "j1", status: "x", pollPath: "/p", streamPath: "/s" }}
          taskStatus={{
            id: "j1",
            taskType: "t",
            status: "RUNNING",
            progressText: null,
            result: null,
            errorMessage: null,
            terminal: false,
            createdAt: "",
            updatedAt: "",
            startedAt: null,
            completedAt: null,
          }}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-job-panel")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /copy/i }));
    expect(writeText).toHaveBeenCalledWith("j1");
  });
});
