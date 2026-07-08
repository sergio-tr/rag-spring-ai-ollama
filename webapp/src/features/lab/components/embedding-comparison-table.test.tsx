import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { EmbeddingComparisonTable } from "./embedding-comparison-table";
import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";

describe("EmbeddingComparisonTable", () => {
  const rows: ComparisonRow[] = [
    {
      axisValue: "bge-m3",
      embeddingModelId: "bge-m3",
      modelLabel: "gemma4:12b",
      meanRecallAt1: 0.38,
      meanRecallAt3: 0.5,
      meanRecallAt5: 0.62,
      meanMrr: 0.41,
      meanNdcgAt5: 0.44,
      meanLatencyMs: 827,
      p95LatencyMs: 910,
      executed: 60,
      failed: 0,
    },
    {
      axisValue: "snowflake-arctic-embed2",
      embeddingModelId: "snowflake-arctic-embed2",
      modelLabel: "gemma4:12b",
      meanRecallAt1: 0.3,
      executed: 60,
    },
  ];

  it("shows retrieval metric headers", () => {
    render(
      <IntlTestProvider>
        <EmbeddingComparisonTable
          rows={rows}
          comparisonAxis="EMBEDDING_MODEL"
          selectedKey={null}
          onSelectRow={() => undefined}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByText("Recall@1")).toBeInTheDocument();
    expect(screen.getByText("MRR")).toBeInTheDocument();
    expect(screen.getByText("nDCG@5")).toBeInTheDocument();
    expect(screen.queryByText("Correctness")).not.toBeInTheDocument();
  });

  it("shows embedding model labels instead of downstream LLM ids", () => {
    render(
      <IntlTestProvider>
        <EmbeddingComparisonTable
          rows={rows}
          comparisonAxis="EMBEDDING_MODEL"
          selectedKey={null}
          onSelectRow={() => undefined}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByText("bge-m3")).toBeInTheDocument();
    expect(screen.getByText("snowflake-arctic-embed2")).toBeInTheDocument();
    expect(screen.queryByText("gemma4:12b")).not.toBeInTheDocument();
  });

  it("paginates large comparison tables so later rows remain accessible", async () => {
    const user = userEvent.setup();
    const manyRows: ComparisonRow[] = Array.from({ length: 30 }, (_, index) => ({
      axisValue: `model-${index + 1}`,
      embeddingModelId: `model-${index + 1}`,
      executed: 10,
    }));
    render(
      <IntlTestProvider>
        <EmbeddingComparisonTable
          rows={manyRows}
          comparisonAxis="EMBEDDING_MODEL"
          selectedKey={null}
          onSelectRow={() => undefined}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByText("model-1")).toBeInTheDocument();
    expect(screen.queryByText("model-30")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("lab-embedding-comparison-pagination-next"));
    expect(screen.getByText("model-30")).toBeInTheDocument();
  });
});
