import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { LabRagTaskLlmCallout } from "./lab-rag-task-llm-callout";

describe("LabRagTaskLlmCallout", () => {
  it("shows task-level LLM guidance with settings link", () => {
    render(
      <IntlTestProvider locale="en">
        <LabRagTaskLlmCallout />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-rag-task-llm-callout")).toBeInTheDocument();
    expect(screen.getByText("Task-level LLM settings")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "User settings" })).toHaveAttribute("href", "/settings/user");
  });
});
