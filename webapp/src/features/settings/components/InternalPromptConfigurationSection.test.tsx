import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { InternalPromptConfigurationSection } from "@/features/settings/components/InternalPromptConfigurationSection";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock("@/features/settings/hooks/use-prompt-catalog", () => ({
  usePromptCatalogQuery: () => ({
    isLoading: false,
    isError: false,
    data: {
      groups: [
        {
          id: "query_rewrite",
          componentLabel: "Query rewrite",
          description: "Rewrite template",
          defaultContent: "Rewrite %s",
          requiredVariables: ["%s"],
          runtimeEditable: true,
        },
        {
          id: "metadata_filter_and_list",
          componentLabel: "Metadata filter",
          description: "Catalog metadata",
          defaultContent: "Filter %s %s",
          requiredVariables: ["%s"],
          runtimeEditable: true,
        },
      ],
    },
  }),
}));

describe("InternalPromptConfigurationSection", () => {
  it("renders editable prompt groups prefilled with defaults", () => {
    render(
      <InternalPromptConfigurationSection
        configValues={{}}
        onChange={() => {}}
      />,
    );

    expect(screen.getByTestId("internal-prompt-configuration")).toBeInTheDocument();
    expect(screen.getByTestId("internal-prompt-group-query_rewrite")).toBeInTheDocument();
    expect(screen.getByTestId("internal-prompt-group-metadata_filter_and_list")).toBeInTheDocument();
  });
});
