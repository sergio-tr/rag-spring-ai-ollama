import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { LlmComparisonTable } from "./llm-comparison-table";
import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";

describe("LlmComparisonTable", () => {
  it("paginates model comparison rows", async () => {
    const user = userEvent.setup();
    const manyRows: ComparisonRow[] = Array.from({ length: 30 }, (_, index) => ({
      axisValue: `llm-${index + 1}`,
      llmModelId: `llm-${index + 1}`,
      executed: 12,
      failed: 0,
    }));
    render(
      <IntlTestProvider>
        <LlmComparisonTable
          rows={manyRows}
          comparisonAxis="LLM_MODEL"
          selectedKey={null}
          onSelectRow={() => undefined}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByText("llm-1")).toBeInTheDocument();
    expect(screen.queryByText("llm-30")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("lab-llm-comparison-pagination-next"));
    expect(screen.getByText("llm-30")).toBeInTheDocument();
  });

  it("clicking correctness header reorders rows", async () => {
    const user = userEvent.setup();
    const rows: ComparisonRow[] = [
      { axisValue: "low", llmModelId: "low", meanCorrectness: 0.2, executed: 1 },
      { axisValue: "high", llmModelId: "high", meanCorrectness: 0.9, executed: 1 },
    ];
    render(
      <IntlTestProvider>
        <LlmComparisonTable
          rows={rows}
          comparisonAxis="LLM_MODEL"
          selectedKey={null}
          onSelectRow={() => undefined}
        />
      </IntlTestProvider>,
    );
    const bodyText = () => screen.getByTestId("lab-llm-comparison-table").textContent ?? "";
    expect(bodyText()).toMatch(/high[\s\S]*low|low[\s\S]*high/);
    await user.click(screen.getByTestId("lab-sort-header-correctness"));
    expect(bodyText().indexOf("high")).toBeLessThan(bodyText().indexOf("low"));
    await user.click(screen.getByTestId("lab-sort-header-correctness"));
    expect(bodyText().indexOf("low")).toBeLessThan(bodyText().indexOf("high"));
  });
});
