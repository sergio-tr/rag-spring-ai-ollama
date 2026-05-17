"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  downloadCampaignExport,
  downloadCampaignMvpItemsJson,
  downloadMvpExport,
  fetchCampaignComparison,
  fetchLabCampaignRuns,
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
  campaignId?: string | null;
  /** When false, the panel does not fetch (job still running or failed). */
  loadEnabled: boolean;
};

/**
 * After a successful benchmark async task, loads run metadata, MVP rollups, and per-item MVP rows for UX (not the legacy CSV).
 */
export function LabBenchmarkResultsPanel({ evaluationRunId, campaignId, loadEnabled }: LabBenchmarkResultsPanelProps) {
  const t = useTranslations("Lab");
  const runId = evaluationRunId?.trim() ?? "";
  const enabled = loadEnabled && runId.length > 0;
  const campId = campaignId?.trim() ?? "";

  const query = useQuery({
    queryKey: ["lab", "benchmark-mvp-results", runId],
    enabled,
    queryFn: async () => {
      const [run, rollups, itemsBundle] = await Promise.all([
        fetchLabEvaluationRun(runId),
        fetchMvpRollupsJson(runId),
        fetchMvpItemsBundle(runId),
      ]);
      const campaignRuns = campId ? await fetchLabCampaignRuns(campId) : null;
      const campaignComparison = campId ? await fetchCampaignComparison(campId).catch(() => null) : null;
      return { run, rollups, itemsBundle, campaignRuns, campaignComparison };
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
  const uniqueQuestionIds = new Set<string>();
  const uniquePresetCodes = new Set<string>();
  for (const row of mvpRows) {
    if (!row || typeof row !== "object") continue;
    const mvp = (row as Record<string, unknown>).mvp;
    if (!mvp || typeof mvp !== "object") continue;
    const m = mvp as Record<string, unknown>;
    const dq = m.datasetQuestionId;
    if (typeof dq === "string" && dq.trim()) uniqueQuestionIds.add(dq.trim());
    const op = m.operational;
    if (op && typeof op === "object") {
      const pc = (op as Record<string, unknown>).presetCode;
      if (typeof pc === "string" && pc.trim()) uniquePresetCodes.add(pc.trim());
    }
  }

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
          <p className="text-muted-foreground text-xs" data-testid="lab-benchmark-results-counts">
            {t("benchmarkResultsCountsLine", {
              items: mvpRows.length,
              questions: uniqueQuestionIds.size,
              presets: uniquePresetCodes.size,
            })}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {campId ? (
            <>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-items-json"
                onClick={() => void downloadCampaignMvpItemsJson(campId)}
              >
                {t("benchmarkExportCampaignItemsJson")}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-items-csv"
                onClick={() => void downloadCampaignExport(campId, "items.csv")}
              >
                Campaign items.csv
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-summary-csv"
                onClick={() => void downloadCampaignExport(campId, "summary.csv")}
              >
                Campaign summary.csv
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-bundle-json"
                onClick={() => void downloadCampaignExport(campId, "bundle.json")}
              >
                Campaign bundle.json
              </Button>
            </>
          ) : null}
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

      {campId && payload.campaignRuns && payload.campaignRuns.length > 0 ? (
        <div className="space-y-2" data-testid="lab-campaign-runs-panel">
          <span className="text-muted-foreground text-xs font-medium">{t("benchmarkCampaignRunsTitle")}</span>
          <div className="max-h-40 overflow-auto rounded-md border bg-background/40 p-2 text-xs">
            <ul className="space-y-1">
              {payload.campaignRuns.slice(0, 40).map((r, idx) => {
                const row = r as Record<string, unknown>;
                const rid = typeof row.runId === "string" ? row.runId : `row-${idx}`;
                const model = typeof row.llmModelId === "string" ? row.llmModelId : "—";
                const status = typeof row.status === "string" ? row.status : "—";
                return (
                  <li key={rid} className="flex flex-wrap items-center justify-between gap-2 border-border border-b py-1 last:border-b-0">
                    <span className="font-mono">{rid.slice(0, 8)}</span>
                    <span className="truncate">{model}</span>
                    <span className="text-muted-foreground font-mono">{status}</span>
                  </li>
                );
              })}
            </ul>
            {payload.campaignRuns.length > 40 ? (
              <p className="text-muted-foreground mt-2">{t("benchmarkCampaignRunsTruncated", { n: payload.campaignRuns.length })}</p>
            ) : null}
          </div>
          <p className="text-muted-foreground text-[11px]">{t("benchmarkCampaignRunsHint")}</p>
        </div>
      ) : null}

      {campId && payload.campaignComparison && typeof payload.campaignComparison === "object" ? (
        <div className="space-y-2" data-testid="lab-campaign-comparison-panel">
          <span className="text-muted-foreground text-xs font-medium">Campaign comparison</span>
          <div className="max-h-56 overflow-auto rounded-md border">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted/50 sticky top-0">
                <tr>
                  <th className="p-2 font-medium">Group</th>
                  <th className="p-2 font-medium">Total</th>
                  <th className="p-2 font-medium">Executed</th>
                  <th className="p-2 font-medium">NOT_SUPPORTED</th>
                  <th className="p-2 font-medium">Failed</th>
                  <th className="p-2 font-medium">Exact</th>
                  <th className="p-2 font-medium">Semantic</th>
                  <th className="p-2 font-medium">Recall@1</th>
                  <th className="p-2 font-medium">Latency</th>
                </tr>
              </thead>
              <tbody>
                {Array.isArray((payload.campaignComparison as Record<string, unknown>).rows)
                  ? ((payload.campaignComparison as Record<string, unknown>).rows as Array<Record<string, unknown>>)
                      .slice(0, 50)
                      .map((r, idx) => (
                        <tr key={idx} className="border-border border-t">
                          <td className="p-2 font-mono">
                            {String(r.groupKey ?? "")}:{String(r.groupValue ?? "")}
                          </td>
                          <td className="p-2">{String(r.totalItems ?? "")}</td>
                          <td className="p-2">{String(r.executed ?? "")}</td>
                          <td className="p-2">{String(r.notSupported ?? "")}</td>
                          <td className="p-2">{String(r.failed ?? "")}</td>
                          <td className="p-2">{r.meanExactMatch == null ? "—" : String(r.meanExactMatch)}</td>
                          <td className="p-2">{r.meanSemanticScore == null ? "—" : String(r.meanSemanticScore)}</td>
                          <td className="p-2">{r.meanRecallAt1 == null ? "—" : String(r.meanRecallAt1)}</td>
                          <td className="p-2">{r.meanLatencyMs == null ? "—" : String(r.meanLatencyMs)}</td>
                        </tr>
                      ))
                  : null}
              </tbody>
            </table>
          </div>
          <p className="text-muted-foreground text-[11px]">
            Exports are designed for tables: use summary.csv for aggregated rows and items.csv for per-item analysis.
          </p>
        </div>
      ) : null}

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
