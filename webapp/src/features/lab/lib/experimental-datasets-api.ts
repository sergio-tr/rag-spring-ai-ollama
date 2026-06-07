import {
  ApiError,
  apiDownloadBlob,
  apiFetch,
  apiProductPath,
} from "@/lib/api-client";
import type {
  ExperimentalDatasetListItemDto,
  ExperimentalDatasetTemplateKind,
  ExperimentalDatasetUploadResponseDto,
  ExperimentalDatasetValidationFailedDto,
  ExperimentalDatasetValidationReportDto,
} from "@/types/api";

export const EXPERIMENTAL_DATASET_TEMPLATE_KINDS: readonly ExperimentalDatasetTemplateKind[] = [
  "llm-model-baseline",
  "embedding-baseline",
  "rag-preset-benchmark",
  "classifier-question-querytype",
] as const;

export function suggestedTemplateFilename(kind: ExperimentalDatasetTemplateKind): string {
  return `${kind}-template.xlsx`;
}

export async function downloadExperimentalDatasetTemplate(kind: ExperimentalDatasetTemplateKind): Promise<Blob> {
  return apiDownloadBlob(apiProductPath(`/lab/dataset-templates/${encodeURIComponent(kind)}`));
}

export function triggerBrowserBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.rel = "noopener";
    a.click();
  } finally {
    queueMicrotask(() => URL.revokeObjectURL(url));
  }
}

export async function fetchExperimentalDatasets(): Promise<ExperimentalDatasetListItemDto[]> {
  return apiFetch<ExperimentalDatasetListItemDto[]>(apiProductPath("/lab/experimental-datasets"));
}

export async function fetchExperimentalDatasetValidation(
  datasetId: string,
): Promise<ExperimentalDatasetValidationReportDto> {
  return apiFetch<ExperimentalDatasetValidationReportDto>(
    apiProductPath(`/lab/experimental-datasets/${encodeURIComponent(datasetId)}/validation`),
  );
}

export function parseExperimentalDatasetValidation422(
  meta: ApiError["meta"] | undefined,
): ExperimentalDatasetValidationFailedDto | null {
  const raw = meta?.parsedJson;
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const o = raw as Record<string, unknown>;
  if (o.error !== "EXPERIMENTAL_DATASET_INVALID") {
    return null;
  }
  const vr = o.validationReport;
  if (!vr || typeof vr !== "object") {
    return null;
  }
  const report = vr as Record<string, unknown>;
  const issues = report.issues;
  if (!Array.isArray(issues)) {
    return null;
  }
  return {
    error: String(o.error),
    validationReport: {
      issues,
      hasErrors: Boolean(report.hasErrors),
      hasWarnings: Boolean(report.hasWarnings),
    } as ExperimentalDatasetValidationReportDto,
  };
}

export async function uploadExperimentalDataset(args: {
  file: File;
  datasetType: string;
  name?: string;
  description?: string;
}): Promise<ExperimentalDatasetUploadResponseDto> {
  const fd = new FormData();
  fd.append("file", args.file);
  fd.append("datasetType", args.datasetType.trim());
  const name = args.name?.trim();
  const description = args.description?.trim();
  if (name) {
    fd.append("name", name);
  }
  if (description) {
    fd.append("description", description);
  }
  return apiFetch<ExperimentalDatasetUploadResponseDto>(apiProductPath("/lab/experimental-datasets"), {
    method: "POST",
    body: fd,
  });
}

export type UploadExperimentalDatasetOutcome =
  | { ok: true; data: ExperimentalDatasetUploadResponseDto }
  | { ok: false; failed: ExperimentalDatasetValidationFailedDto };

export async function uploadExperimentalDatasetAllow422(args: {
  file: File;
  datasetType: string;
  name?: string;
  description?: string;
}): Promise<UploadExperimentalDatasetOutcome> {
  try {
    const data = await uploadExperimentalDataset(args);
    return { ok: true, data };
  } catch (e) {
    if (e instanceof ApiError && e.status === 422) {
      const failed = parseExperimentalDatasetValidation422(e.meta);
      if (failed) {
        return { ok: false, failed };
      }
    }
    throw e;
  }
}
