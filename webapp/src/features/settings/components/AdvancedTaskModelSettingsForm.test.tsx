import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { AdvancedTaskModelSettingsForm } from "@/features/settings/components/AdvancedTaskModelSettingsForm";

const catalogTasks = [
  {
    id: "final_answer",
    role: "FINAL_ANSWER",
    label: "Final answer",
    inheritsMainModelByDefault: true,
    operationName: "final-answer",
    defaultModelId: "gemma4:12b",
    defaultParameters: {
      temperature: 0.1,
      topP: 1,
      maxTokens: 1024,
      responseFormat: "text",
      think: false,
    },
    supportedParameters: ["temperature", "topP", "maxTokens", "responseFormat", "think"],
  },
  {
    id: "query_rewrite",
    role: "QUERY_REWRITE",
    label: "Query rewrite",
    inheritsMainModelByDefault: false,
    operationName: "query-rewrite",
    defaultModelId: "qwen3.5:9b",
    defaultParameters: {
      temperature: 0,
      topP: 1,
      maxTokens: 256,
      responseFormat: "text",
      think: false,
    },
    supportedParameters: ["temperature", "topP", "maxTokens"],
  },
];

vi.mock("@/features/settings/hooks/use-prompt-catalog", () => ({
  useTaskLlmCatalogQuery: () => ({
    isLoading: false,
    data: { version: 2, tasks: catalogTasks, overridesMapKey: "taskLlmOverrides" },
  }),
}));

vi.mock("@/features/chat/hooks/use-me-selectable-llm-models", () => ({
  useMeSelectableLlmModels: () => ({
    data: {
      models: [
        { modelName: "gemma4:12b", displayName: "Gemma 4 12B", selectable: true },
        { modelName: "qwen3.5:9b", displayName: "Qwen 3.5 9B", selectable: true },
      ],
    },
    isLoading: false,
  }),
}));

describe("AdvancedTaskModelSettingsForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders full parameter fields for each role, not only temperature", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <AdvancedTaskModelSettingsForm configValues={{}} onChange={vi.fn()} />
      </IntlTestProvider>,
    );

    expect(screen.getByTestId("advanced-task-model-settings")).toBeInTheDocument();
    expect(screen.getByTestId("task-llm-row-final_answer")).toBeInTheDocument();
    expect(screen.getByTestId("task-llm-row-query_rewrite")).toBeInTheDocument();

    await user.click(screen.getByTestId("task-llm-row-query_rewrite"));

    const params = screen.getByTestId("task-role-parameters-query_rewrite");
    expect(within(params).getByTestId("task-hp-temperature-query_rewrite")).toBeInTheDocument();
    expect(within(params).getByTestId("task-hp-top-p-query_rewrite")).toBeInTheDocument();
    expect(within(params).getByTestId("task-hp-max-tokens-query_rewrite")).toBeInTheDocument();
    expect(within(params).getByTestId("task-hp-response-format-query_rewrite")).toBeInTheDocument();
    expect(within(params).getByTestId("task-hp-think-query_rewrite")).toBeInTheDocument();
  });

  it("shows effective defaults in summary line", () => {
    render(
      <IntlTestProvider>
        <AdvancedTaskModelSettingsForm configValues={{}} onChange={vi.fn()} />
      </IntlTestProvider>,
    );

    const summary = screen.getByTestId("task-role-summary-final_answer");
    expect(summary.textContent).toContain("FINAL_ANSWER");
    expect(summary.textContent).toContain("temp 0.1");
    expect(summary.textContent).toContain("max 1024");
    expect(summary.textContent).not.toContain("codellama");
  });

  it("truncates long role summary lines in a width-constrained container", () => {
    render(
      <IntlTestProvider>
        <AdvancedTaskModelSettingsForm configValues={{}} onChange={vi.fn()} />
      </IntlTestProvider>,
    );

    const summary = screen.getByTestId("task-role-summary-final_answer");
    expect(summary.className).toMatch(/truncate/);
    expect(summary).toHaveAttribute("title");
  });

  it("reset all clears task overrides via onChange", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <IntlTestProvider>
        <AdvancedTaskModelSettingsForm
          configValues={{
            taskLlmOverrides: {
              query_rewrite: { model: "custom", temperature: 0.5 },
            },
          }}
          onChange={onChange}
        />
      </IntlTestProvider>,
    );

    await user.click(screen.getByTestId("task-model-reset-all"));
    await user.click(screen.getByTestId("task-model-reset-all-confirm"));

    expect(onChange).toHaveBeenCalledWith({});
  });
});
