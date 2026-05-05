import { apiDownloadBlob, apiFetch, apiProductPath } from "@/lib/api-client";
import type { EvaluationRunDetailDto } from "@/types/api";
import { triggerBrowserBlobDownload } from "@/features/lab/lib/experimental-datasets-api";

export async function fetchLabEvaluationRun(runId: string): Promise<EvaluationRunDetailDto> {
  return apiFetch<EvaluationRunDetailDto>(apiProductPath(`/lab/runs/${encodeURIComponent(runId)}`));
}

export async function fetchLabCampaignRuns(campaignId: string): Promise<Array<Record<string, unknown>>> {
  return apiFetch<Array<Record<string, unknown>>>(
    apiProductPath(`/lab/campaigns/${encodeURIComponent(campaignId)}/runs`),
  );
}

export async function downloadCampaignMvpItemsJson(campaignId: string): Promise<void> {
  const path = apiProductPath(`/lab/campaigns/${encodeURIComponent(campaignId)}/export/mvp/items.json`);
  const blob = await apiDownloadBlob(path);
  const filename = `lab-campaign-${campaignId}-mvp-items.json`;
  triggerBrowserBlobDownload(blob, filename);
}

export async function fetchMvpRollupsJson(runId: string): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>(
    apiProductPath(`/lab/runs/${encodeURIComponent(runId)}/export/mvp/rollups.json`),
  );
}

export async function fetchMvpItemsBundle(runId: string): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>(
    apiProductPath(`/lab/runs/${encodeURIComponent(runId)}/export/mvp/items.json`),
  );
}

export async function downloadMvpExport(runId: string, kind: "items.csv" | "items.json" | "rollups.json"): Promise<void> {
  const path = apiProductPath(`/lab/runs/${encodeURIComponent(runId)}/export/mvp/${kind}`);
  const blob = await apiDownloadBlob(path);
  const filename =
    kind === "items.csv" ? `lab-run-${runId}-mvp-items.csv` : `lab-run-${runId}-mvp-${kind}`;
  triggerBrowserBlobDownload(blob, filename);
}
