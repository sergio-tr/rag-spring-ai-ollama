"use client";

import {
  LabComparisonTable,
  LabComparisonTableHead,
  LabComparisonTh,
} from "@/features/lab/components/lab-comparison-table";
import { LabTablePaginationBar, useLabTablePagination } from "@/features/lab/components/lab-table-pagination";
import {
  formatComparisonScore,
  formatSupportStatusLabel,
  resolveComparisonRowLabel,
  resolvePresetKeyFromComparisonRow,
  type ComparisonRow,
} from "@/features/lab/lib/lab-benchmark-labels";
import {
  formatLatencyMs,
  formatMetricNumber,
  formatRatioPercent,
} from "@/features/lab/lib/lab-comparison-metrics";
import { paginateRows } from "@/features/lab/lib/lab-table-pagination";
import { useTranslations } from "next-intl";

export type RagComparisonTableProps = {
  rows: ComparisonRow[];
  comparisonAxis: string;
  selectedKey: string | null;
  onSelectRow: (presetKey: string) => void;
};

export function RagComparisonTable({
  rows,
  comparisonAxis,
  selectedKey,
  onSelectRow,
}: RagComparisonTableProps) {
  const t = useTranslations("Lab");
  const pagination = useLabTablePagination({
    rowCount: rows.length,
    resetKey: `${comparisonAxis}:${rows.length}`,
  });
  const slice = paginateRows(rows, pagination.page, pagination.pageSize);

  return (
    <div className="space-y-2">
      <LabComparisonTable testId="lab-rag-comparison-table" maxHeightClassName="max-h-72">
        <LabComparisonTableHead>
          <tr>
            <LabComparisonTh>{t("benchmarkColPreset")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColPlanned")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColExecuted")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColNoContext")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColCoverage")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColGlobalScore")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColCorrectness")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColFaithfulness")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColHallucination")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColRecallAt5")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColMrr")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColNdcgAt5")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColMeanLatency")}</LabComparisonTh>
            <LabComparisonTh>{t("benchmarkColErrors")}</LabComparisonTh>
          </tr>
        </LabComparisonTableHead>
        <tbody>
          {slice.pageRows.map((row, idx) => {
            const presetKey = resolvePresetKeyFromComparisonRow(row);
            const label = resolveComparisonRowLabel(row, comparisonAxis);
            const selected = presetKey.length > 0 && selectedKey === presetKey;
            const planned = row.totalItems ?? 0;
            const executed = row.executed ?? 0;
            const noContext = row.noContextCount ?? 0;
            const errors = (row.failed ?? 0) + (row.errorCount ?? 0);
            return (
              <tr
                key={`${presetKey || label}-${idx}`}
                className={
                  selected
                    ? "border-border cursor-pointer border-t bg-primary/10"
                    : "border-border hover:bg-muted/30 cursor-pointer border-t"
                }
                data-testid={`lab-comparison-row-${idx}`}
                data-comparison-key={presetKey || undefined}
                onClick={() => {
                  if (!presetKey) return;
                  onSelectRow(presetKey);
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
                <td className="p-2 font-mono">{String(planned || "—")}</td>
                <td className="p-2 font-mono">{String(executed || "—")}</td>
                <td className="p-2 font-mono">{planned > 0 ? String(noContext) : "—"}</td>
                <td className="p-2 font-mono">{formatRatioPercent(executed, planned)}</td>
                <td className="p-2 font-mono" title={t("benchmarkColGlobalScoreTooltip")}>
                  {formatComparisonScore(row.scoreGlobal)}
                </td>
                <td className="p-2 font-mono">
                  {formatMetricNumber(row.meanCorrectness ?? row.meanExactMatch)}
                </td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanFaithfulness)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanHallucinationRate)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanRecallAt5)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanMrr)}</td>
                <td className="p-2 font-mono">{formatMetricNumber(row.meanNdcgAt5)}</td>
                <td className="p-2 font-mono">{formatLatencyMs(row.meanLatencyMs)}</td>
                <td className="p-2 font-mono">{String(errors)}</td>
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
        testId="lab-rag-comparison-pagination"
      />
    </div>
  );
}
