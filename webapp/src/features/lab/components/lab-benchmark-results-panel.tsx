"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  downloadMvpExport,
  fetchLabEvaluationRun,
  fetchMvpItemsBundle,
  fetchMvpRollupsJson,
} from "@/features/lab/lib/lab-benchmark-results-api";
import {
  readGlobalOutcomeCounts,
  readMvpItemOperational,
  readMvpItems,
  readOnExecutedSummary,
} from "@/features/lab/lib/lab-benchmark-mvp-utils";
import { getSafeApiErrorMessage } from "@/lib/api-client";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";

const OUTCOME_ORDER = ["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"] as const;

const PRIMARY_OUTCOME_KEYS = new Set<string>(OUTCOME_ORDER);

export type LabBenchmarkResultsPanelProps = {
  evaluationRunId: string | null;
  /** When false, the panel does not fetch (job still running or failed). */
  loadEnabled: boolean;
};

/**
 * After a successful benchmark async task, loads run metadata, MVP rollups, and per-item MVP rows for UX (not the legacy CSV).
 */
export function LabBenchmarkResultsPanel({ evaluationRunId, loadEnabled }: LabBenchmarkResultsPanelProps) {
  const t = useTranslations("Lab");
  const runId = evaluationRunId?.trim() ?? "";
  const enabled = loadEnabled && runId.length > 0;

  const query = useQuery({
    queryKey: ["lab", "benchmark-mvp-results", runId],
    enabled,
    queryFn: async () => {
      const [run, rollups, itemsBundle] = await Promise.all([
        fetchLabEvaluationRun(runId),
        fetchMvpRollupsJson(runId),
        fetchMvpItemsBundle(runId),
      ]);
      return { run, rollups, itemsBundle };
    },
  });

  if (!enabled) {
    return null;
  }

  if (query.isPending) {
    return (
      <div className="text-muted-foreground text-sm" data-testid="lab-benchmark-results-loading">
        {t("benchmarkResultsLoading")}
      </div>
    );
  }

  if (query.isError) {
    return (
      <p className="text-destructive text-sm" role="alert" data-testid="lab-benchmark-results-error">
        {t("benchmarkResultsError", { message: getSafeApiErrorMessage(query.error) })}
      </p>
    );
  }

  const payload = query.data;
  if (!payload) {
    return null;
  }

  const occ = readGlobalOutcomeCounts(payload.rollups);
  const macroExecuted = readOnExecutedSummary(payload.rollups.globalMacro);
  const mvpRows = readMvpItems(payload.itemsBundle);

  return (
    <div className="space-y-4 rounded-md border bg-muted/20 p-4" data-testid="lab-benchmark-results-panel">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <Label className="text-base">{t("benchmarkResultsTitle")}</Label>
          <p className="text-muted-foreground text-xs">
            {t("benchmarkResultsRunLine", {
              id: payload.run.id.slice(0, 8),
              status: payload.run.status,
              kind: payload.run.benchmarkKind ?? "—",
            })}
          </p>
          {macroExecuted && macroExecuted.n > 0 ? (
            <p className="text-muted-foreground text-xs">
              {t("benchmarkResultsExecutedSummary", {
                n: macroExecuted.n,
                exact:
                  macroExecuted.meanNormalizedExactMatch != null
                    ? macroExecuted.meanNormalizedExactMatch.toFixed(3)
                    : "—",
              })}
            </p>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="lab-export-mvp-csv"
            onClick={() => void downloadMvpExport(payload.run.id, "items.csv")}
          >
            {t("benchmarkExportMvpCsv")}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="lab-export-mvp-items-json"
            onClick={() => void downloadMvpExport(payload.run.id, "items.json")}
          >
            {t("benchmarkExportMvpItemsJson")}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="lab-export-mvp-rollups-json"
            onClick={() => void downloadMvpExport(payload.run.id, "rollups.json")}
          >
            {t("benchmarkExportMvpRollupsJson")}
          </Button>
        </div>
      </div>

      <div className="space-y-2">
        <span className="text-muted-foreground text-xs font-medium">{t("benchmarkOutcomesTitle")}</span>
        <div className="flex flex-wrap gap-2">
          {OUTCOME_ORDER.map((key) => {
            const n = occ[key] ?? 0;
            return (
              <span
                key={key}
                className="bg-background rounded-md border px-2 py-1 font-mono text-xs"
                data-testid={`lab-outcome-${key}`}
              >
                {t(`benchmarkOutcome.${key}`, { count: n })}
              </span>
            );
          })}
          {Object.entries(occ)
            .filter(([k]) => !PRIMARY_OUTCOME_KEYS.has(k))
            .map(([k, n]) => (
              <span key={k} className="bg-background rounded-md border px-2 py-1 font-mono text-xs">
                {k}: {n}
              </span>
            ))}
        </div>
      </div>

      <div className="space-y-2">
        <span className="text-muted-foreground text-xs font-medium">{t("benchmarkPerItemTitle")}</span>
        <div className="max-h-56 overflow-auto rounded-md border">
          <table className="w-full text-left text-xs">
            <thead className="bg-muted/50 sticky top-0">
              <tr>
                <th className="p-2 font-medium">{t("benchmarkColItem")}</th>
                <th className="p-2 font-medium">{t("benchmarkColOutcome")}</th>
                <th className="p-2 font-medium">{t("benchmarkColNote")}</th>
              </tr>
            </thead>
            <tbody>
              {mvpRows.slice(0, 80).map((row, idx) => {
                const id =
                  row && typeof row === "object" && typeof (row as Record<string, unknown>).id === "string"
                    ? String((row as Record<string, unknown>).id)
                    : `row-${idx}`;
                const qRaw =
                  row && typeof row === "object" && typeof (row as Record<string, unknown>).questionText === "string"
                    ? (row as Record<string, unknown>).questionText
                    : "";
                const q = typeof qRaw === "string" ? qRaw : "";
                const snippet = q.length > 96 ? `${q.slice(0, 96)}…` : q;
                const op = readMvpItemOperational(row);
                const outcome = op?.outcome ?? "—";
                const note: string =
                  outcome === "NOT_SUPPORTED" && typeof op?.unsupportedReason === "string"
                    ? op.unsupportedReason
                    : outcome === "FAILED"
                      ? t("benchmarkFailedHint")
                      : "—";
                return (
                  <tr key={id} className="border-t border-border">
                    <td className="p-2 align-top">{snippet || "—"}</td>
                    <td className="p-2 align-top font-mono">{outcome}</td>
                    <td className="text-muted-foreground p-2 align-top">{note}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {mvpRows.length > 80 ? (
          <p className="text-muted-foreground text-xs">{t("benchmarkPerItemTruncated", { n: mvpRows.length })}</p>
        ) : null}
      </div>
    </div>
  );
}
