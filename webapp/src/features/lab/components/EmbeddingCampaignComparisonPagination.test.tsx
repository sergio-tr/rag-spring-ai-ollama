import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { EmbeddingComparisonTable } from "@/features/lab/components/embedding-comparison-table";
import { LlmComparisonTable } from "@/features/lab/components/llm-comparison-table";
import { RagComparisonTable } from "@/features/lab/components/rag-comparison-table";
import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";

function makeRows(count: number, prefix: string): ComparisonRow[] {
  return Array.from({ length: count }, (_, index) => ({
    axisValue: `${prefix}-${index}`,
    embeddingModelId: `${prefix}-${index}`,
    llmModelId: `${prefix}-${index}`,
    presetCode: `${prefix}-${index}`,
    presetLabel: `${prefix}-${index}`,
    executed: 1,
    totalItems: 1,
    meanRecallAt1: 0.5,
  }));
}

describe("EmbeddingCampaignComparisonPagination", () => {
  it("renders exactly one pagination bar for the embedding comparison table", () => {
    render(
      <IntlTestProvider locale="en">
        <EmbeddingComparisonTable
          rows={makeRows(30, "emb")}
          comparisonAxis="EMBEDDING_MODEL"
          selectedKey={null}
          onSelectRow={vi.fn()}
        />
      </IntlTestProvider>,
    );

    expect(screen.getAllByTestId("lab-embedding-comparison-pagination")).toHaveLength(1);
    expect(screen.queryByTestId("lab-fallback-comparison-pagination")).not.toBeInTheDocument();
  });

  it("renders exactly one pagination bar for the LLM comparison table", () => {
    render(
      <IntlTestProvider locale="en">
        <LlmComparisonTable
          rows={makeRows(30, "llm")}
          comparisonAxis="LLM_MODEL"
          selectedKey={null}
          onSelectRow={vi.fn()}
        />
      </IntlTestProvider>,
    );

    expect(screen.getAllByTestId("lab-llm-comparison-pagination")).toHaveLength(1);
  });

  it("renders exactly one pagination bar for the RAG comparison table", () => {
    render(
      <IntlTestProvider locale="en">
        <RagComparisonTable
          rows={makeRows(30, "rag")}
          comparisonAxis="PRESET"
          selectedKey={null}
          onSelectRow={vi.fn()}
        />
      </IntlTestProvider>,
    );

    const pagination = screen.getByTestId("lab-rag-comparison-pagination");
    expect(pagination).toBeInTheDocument();
    expect(within(pagination).getByTestId("lab-rag-comparison-pagination-page-size")).toBeInTheDocument();
    expect(screen.getAllByTestId("lab-rag-comparison-pagination")).toHaveLength(1);
  });
});
