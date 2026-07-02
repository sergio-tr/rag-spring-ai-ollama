import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { RagComparisonTable } from "./rag-comparison-table";
import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";

describe("RagComparisonTable", () => {
  it("shows coverage and NO_CONTEXT counts", () => {
    const rows: ComparisonRow[] = [
      {
        presetKey: "P3",
        presetLabel: "Chunk-level dense retrieval",
        comparisonLabel: "P3 — Chunk-level dense retrieval",
        totalItems: 7,
        executed: 7,
        noContextCount: 1,
        scoreGlobal: 0.8,
        meanCorrectness: 0.9,
        failed: 0,
      },
      {
        presetKey: "P8",
        presetLabel: "Advanced retrieval",
        comparisonLabel: "P8 — Advanced retrieval",
        totalItems: 7,
        executed: 5,
        noContextCount: 2,
        scoreGlobal: 0.6,
        failed: 1,
      },
    ];

    render(
      <IntlTestProvider>
        <RagComparisonTable
          rows={rows}
          comparisonAxis="PRESET_CODE"
          selectedKey={null}
          onSelectRow={() => undefined}
        />
      </IntlTestProvider>,
    );

    expect(screen.getByText("100.0%")).toBeInTheDocument();
    expect(screen.getByText("71.4%")).toBeInTheDocument();
    const table = screen.getByTestId("lab-rag-comparison-table");
    expect(table.textContent).toContain("1");
    expect(table.textContent).toContain("2");
  });

  it("paginates preset comparison rows", async () => {
    const user = userEvent.setup();
    const manyRows: ComparisonRow[] = Array.from({ length: 30 }, (_, index) => ({
      presetKey: `P${index + 1}`,
      presetLabel: `Preset ${index + 1}`,
      comparisonLabel: `P${index + 1} — Preset ${index + 1}`,
      totalItems: 7,
      executed: 7,
      noContextCount: 0,
      scoreGlobal: 0.5,
      failed: 0,
    }));
    render(
      <IntlTestProvider>
        <RagComparisonTable
          rows={manyRows}
          comparisonAxis="PRESET_CODE"
          selectedKey={null}
          onSelectRow={() => undefined}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByText("P1 — Preset 1")).toBeInTheDocument();
    expect(screen.queryByText("P30 — Preset 30")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("lab-rag-comparison-pagination-next"));
    expect(screen.getByText("P30 — Preset 30")).toBeInTheDocument();
  });
});
