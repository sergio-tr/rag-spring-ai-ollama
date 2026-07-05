import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProjectCreateFeedbackBanner } from "./ProjectCreateFeedbackBanner";
import { useProjectCreateFeedbackStore } from "@/features/projects/lib/project-create-feedback-state";

describe("ProjectCreateFeedbackBanner", () => {
  beforeEach(() => {
    useProjectCreateFeedbackStore.setState({ feedback: null });
  });

  it("renders nothing when feedback is absent", () => {
    render(<ProjectCreateFeedbackBanner />);
    expect(screen.queryByTestId("project-create-feedback")).not.toBeInTheDocument();
  });

  it("shows warning messages only", () => {
    useProjectCreateFeedbackStore.setState({
      feedback: { warning: "Project created. Activation did not complete." },
    });
    render(<ProjectCreateFeedbackBanner />);
    expect(screen.getByTestId("project-create-feedback")).toHaveTextContent(
      "Activation did not complete.",
    );
    expect(screen.getByTestId("project-create-feedback")).not.toHaveTextContent(
      'Project "%s" created.',
    );
  });
});
