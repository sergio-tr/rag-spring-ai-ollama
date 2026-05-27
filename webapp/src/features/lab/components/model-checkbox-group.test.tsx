import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ModelCheckboxGroup } from "./model-checkbox-group";

describe("ModelCheckboxGroup", () => {
  it("renders empty state when no models", () => {
    render(
      <ModelCheckboxGroup
        id="models"
        label="Models"
        availableModelIds={[]}
        selectedIds={[]}
        onChange={vi.fn()}
        testIdPrefix="lab-llm"
      />,
    );
    expect(screen.getByText("No models available.")).toBeInTheDocument();
  });

  it("shows stale selections as unavailable", () => {
    render(
      <ModelCheckboxGroup
        id="models"
        label="Models"
        availableModelIds={["llama3.1"]}
        selectedIds={["llama3.1", "removed-model"]}
        onChange={vi.fn()}
        testIdPrefix="lab-llm"
      />,
    );
    expect(screen.getByTestId("lab-llm-stale-removed-model")).toBeDisabled();
    expect(screen.getByText("(unavailable)")).toBeInTheDocument();
  });

  it("selects and deselects models", () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <ModelCheckboxGroup
        id="models"
        label="Models"
        availableModelIds={["llama3.1", "mistral"]}
        selectedIds={[]}
        onChange={onChange}
        testIdPrefix="lab-llm"
      />,
    );

    fireEvent.click(screen.getByTestId("lab-llm-llama3.1"));
    expect(onChange).toHaveBeenCalledWith(["llama3.1"]);

    rerender(
      <ModelCheckboxGroup
        id="models"
        label="Models"
        availableModelIds={["llama3.1", "mistral"]}
        selectedIds={["llama3.1"]}
        onChange={onChange}
        testIdPrefix="lab-llm"
      />,
    );

    fireEvent.click(screen.getByTestId("lab-llm-llama3.1"));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it("ignores toggle for blank model id and renders hint when provided", () => {
    const onChange = vi.fn();
    const { container } = render(
      <ModelCheckboxGroup
        id="models"
        label="Models"
        availableModelIds={["  "]}
        selectedIds={[]}
        onChange={onChange}
        testIdPrefix="lab-llm"
        hint="Pick at least one model"
        disabled
      />,
    );
    expect(screen.getByText("Pick at least one model")).toBeInTheDocument();
    const checkbox = container.querySelector<HTMLInputElement>('input[type="checkbox"]');
    expect(checkbox).toBeTruthy();
    expect(checkbox).toBeDisabled();
    fireEvent.click(checkbox!);
    expect(onChange).not.toHaveBeenCalled();
  });
});
