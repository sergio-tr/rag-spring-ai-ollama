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
  return downloadCampaignItemsJson(campaignId);
}

export async function downloadCampaignItemsJson(campaignId: string): Promise<void> {
  const path = apiProductPath(
    `/lab/campaigns/${encodeURIComponent(campaignId)}/export/campaign-items.json`,
  );
  const blob = await apiDownloadBlob(path);
  triggerBrowserBlobDownload(blob, `lab-campaign-${campaignId}-campaign-items.json`);
}

export async function downloadCampaignSummaryJson(campaignId: string): Promise<void> {
  const path = apiProductPath(
    `/lab/campaigns/${encodeURIComponent(campaignId)}/export/campaign-summary.json`,
  );
  const blob = await apiDownloadBlob(path);
  triggerBrowserBlobDownload(blob, `lab-campaign-${campaignId}-campaign-summary.json`);
}

export async function downloadCampaignExport(
  campaignId: string,
  kind: "items.csv" | "summary.csv" | "bundle.json",
): Promise<void> {
  const path = apiProductPath(`/lab/campaigns/${encodeURIComponent(campaignId)}/export/${kind}`);
  const blob = await apiDownloadBlob(path);
  const filename = `lab-campaign-${campaignId}-${kind}`;
  triggerBrowserBlobDownload(blob, filename);
}

export async function fetchCampaignComparison(campaignId: string): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>(
    apiProductPath(`/lab/campaigns/${encodeURIComponent(campaignId)}/comparison`),
  );
}

export async function fetchCampaignItemsBundle(campaignId: string): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>(
    apiProductPath(`/lab/campaigns/${encodeURIComponent(campaignId)}/export/campaign-items.json`),
  );
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
