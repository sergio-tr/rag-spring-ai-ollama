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
import { sortComparisonRowsByKey } from "@/features/lab/lib/result-table-row";
import { toggleTableSort, type TableSortState } from "@/features/lab/lib/lab-table-sort";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";

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
  const [sort, setSort] = useState<TableSortState>(null);
  const sortedRows = useMemo(() => sortComparisonRowsByKey(rows, sort, comparisonAxis), [rows, sort, comparisonAxis]);
  const pagination = useLabTablePagination({
    rowCount: sortedRows.length,
    resetKey: `${comparisonAxis}:${sortedRows.length}:${sort?.key ?? "default"}:${sort?.direction ?? ""}`,
  });
  const slice = paginateRows(sortedRows, pagination.page, pagination.pageSize);
  const onSort = (key: string) => setSort((current) => toggleTableSort(current, key));

  return (
    <div className="space-y-2">
      <LabComparisonTable testId="lab-embedding-comparison-table">
        <LabComparisonTableHead>
          <tr>
            <LabComparisonTh sortKey="embeddingModel" sortState={sort} onSort={onSort}>
              {t("benchmarkColEmbeddingModel")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="recallAt1" sortState={sort} onSort={onSort}>
              {t("benchmarkColRecallAt1")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="recallAt3" sortState={sort} onSort={onSort}>
              {t("benchmarkColRecallAt3")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="recallAt5" sortState={sort} onSort={onSort}>
              {t("benchmarkColRecallAt5")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="mrr" sortState={sort} onSort={onSort}>
              {t("benchmarkColMrr")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="ndcgAt5" sortState={sort} onSort={onSort}>
              {t("benchmarkColNdcgAt5")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="meanLatency" sortState={sort} onSort={onSort}>
              {t("benchmarkColMeanLatency")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="p95Latency" sortState={sort} onSort={onSort}>
              {t("benchmarkColP95Latency")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="errors" sortState={sort} onSort={onSort}>
              {t("benchmarkColErrors")}
            </LabComparisonTh>
            <LabComparisonTh sortKey="executed" sortState={sort} onSort={onSort}>
              {t("benchmarkColExecutedItems")}
            </LabComparisonTh>
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
                <td className="p-2 font-mono">{String(row.executed ?? "-")}</td>
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
