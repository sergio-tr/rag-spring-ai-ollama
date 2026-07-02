"use client";

import {
  LabComparisonTable,
  LabComparisonTableHead,
  LabComparisonTh,
} from "@/features/lab/components/lab-comparison-table";
import { LabTablePaginationBar, useLabTablePagination } from "@/features/lab/components/lab-table-pagination";
import {
  formatSupportStatusLabel,
  resolveComparisonRowLabel,
  type ComparisonRow,
} from "@/features/lab/lib/lab-benchmark-labels";
import {
  formatLatencyMs,
  formatMetricNumber,
} from "@/features/lab/lib/lab-comparison-metrics";
import { paginateRows } from "@/features/lab/lib/lab-table-pagination";
import { useTranslations } from "next-intl";

export type EmbeddingComparisonTableProps = {
  rows: ComparisonRow[];
  comparisonAxis: string;
  selectedKey: string | null;
  onSelectRow: (axisValue: string) => void;
};

export function EmbeddingComparisonTable({
  rows,
  comparisonAxis,
  selectedKey,
  onSelectRow,
}: EmbeddingComparisonTableProps) {
  const t = useTranslations("Lab");
  const pagination = useLabTablePagination({
    rowCount: rows.length,
    resetKey: `${comparisonAxis}:${rows.length}`,
  });
  const slice = paginateRows(rows, pagination.page, pagination.pageSize);

  return (
    <div className="space-y-2">
      <LabComparisonTable testId="lab-embedding-comparison-table">
        <LabComparisonTableHead>
          <tr>
            <LabComparisonTh>{t("benchmarkColEmbeddingModel")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColRecallAt1")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColRecallAt3")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColRecallAt5")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColMrr")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColNdcgAt5")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColMeanLatency")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColP95Latency")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColErrors")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColExecutedItems")}</LabComparisonTh>
          </tr>
        </LabComparisonTableHead>
        <tbody>
          {slice.pageRows.map((row, idx) => {
            const axisValue =
              typeof row.axisValue === "string" && row.axisValue.trim()
                ? row.axisValue.trim()
                : typeof row.embeddingModelId === "string" && row.embeddingModelId.trim()
                  ? row.embeddingModelId.trim()
                  : resolveComparisonRowLabel(row, comparisonAxis);
            const label = resolveComparisonRowLabel(row, comparisonAxis);
            const selected = axisValue.length > 0 && selectedKey === axisValue;
            const errors = (row.failed ?? 0) + (row.errorCount ?? 0);
            return (
              <tr
                key={`${axisValue || label}-${idx}`}
                className={
                  selected
                    ? "border-border cursor-pointer border-t bg-primary/10"
                    : "border-border hover:bg-muted/30 cursor-pointer border-t"
                }
                data-testid={`lab-comparison-row-${idx}`}
                data-comparison-key={axisValue || undefined}
                onClick={() => {
                  if (!axisValue) return;
                  onSelectRow(axisValue);
                }}
              >
                <td className="p-2">
                  <div>{label}</div>
                  {formatSupportStatusLabel(
                    typeof row.benchmarkSupportStatus === "string" ? row.benchmarkSupportStatus : undefined,
                    t,
                  ) ? (
                    <div className="text-muted-foreground text-[10px]">
                      {formatSupportStatusLabel(
                        typeof row.benchmarkSupportStatus === "string" ? row.benchmarkSupportStatus : undefined,
                        t,
                      )}
                    </div>
                  ) : null}
                </td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanRecallAt1)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanRecallAt3)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanRecallAt5)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanMrr)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanNdcgAt5)}</td>
                <td className="p-2 font-mono">{formatLatencyMs(row.meanLatencyMs)}</td>
                <td className="p-2 font-mono">{formatLatencyMs(row.p95LatencyMs)}</td>
                <td className="p-2 font-mono">{String(errors)}</td>
                <td className="p-2 font-mono">{String(row.executed ?? "—")}</td>
              </tr>
            );
          })}
        </tbody>
      </LabComparisonTable>
      <LabTablePaginationBar
        page={slice.page}
        pageSize={slice.pageSize}
        totalRows={slice.totalRows}
        totalPages={slice.totalPages}
        rangeStart={slice.rangeStart}
        rangeEnd={slice.rangeEnd}
        onPageChange={pagination.setPage}
        onPageSizeChange={pagination.setPageSize}
        testId="lab-embedding-comparison-pagination"
      />
    </div>
  );
}
