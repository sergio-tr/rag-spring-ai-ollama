import type { EvaluationCorpusReadinessDto, EvaluationCorpusSummaryDto } from "@/types/api";

export type RagIndexReadinessKind = "info" | "success" | "warning" | "blocking";

export type RagIndexReadinessDisplay = {
  kind: RagIndexReadinessKind;
  messageKey: string;
  messageParams?: Record<string, string>;
  testId: string;
};

export type ResolveRagIndexReadinessInput = {
  selectedEmbeddingModelId: string;
  baselineEmbeddingModelId: string | null;
  autoReindex: boolean;
  reuseCompatibleActiveSnapshot: boolean;
  readiness: EvaluationCorpusReadinessDto | null | undefined;
  summary: EvaluationCorpusSummaryDto | null | undefined;
};

export function resolveRagIndexReadinessDisplay(
  input: ResolveRagIndexReadinessInput,
): RagIndexReadinessDisplay | null {
  const selected = input.selectedEmbeddingModelId.trim();
  if (!selected) {
    return null;
  }

  const readiness = input.readiness;
  const documentCount = input.summary?.documentCount ?? readiness?.documentCount ?? 0;
  const readyCount = input.summary?.readyCount ?? readiness?.readyCount ?? 0;
  if (documentCount === 0) {
    return null;
  }

  const activeSnapshotId = readiness?.activeSnapshotId ?? null;
  const reindexRequired = readiness?.reindexRequired ?? false;
  const snapshotBlocker = readiness?.snapshotBlocker ?? null;
  const needsIndex = reindexRequired || Boolean(snapshotBlocker) || !activeSnapshotId;
  const embeddingChanged =
    input.baselineEmbeddingModelId != null && input.baselineEmbeddingModelId !== selected;

  if (embeddingChanged) {
    return {
      kind: "warning",
      messageKey: "benchmarkRagEmbeddingChangedWarning",
      messageParams: { model: selected },
      testId: "lab-rag-embedding-changed-warning",
    };
  }

  if (!input.autoReindex && needsIndex) {
    return {
      kind: "blocking",
      messageKey: "benchmarkRagIndexBlockingWarning",
      testId: "lab-rag-embedding-index-warning",
    };
  }

  if (
    input.autoReindex &&
    input.reuseCompatibleActiveSnapshot &&
    activeSnapshotId &&
    !reindexRequired &&
    readyCount > 0
  ) {
    return {
      kind: "success",
      messageKey: "benchmarkRagReuseCompatibleSnapshotInfo",
      testId: "lab-rag-reuse-compatible-info",
    };
  }

  if (input.autoReindex && needsIndex && readyCount > 0) {
    return {
      kind: "info",
      messageKey: "labEvalIndexWillPrepare",
      testId: "lab-rag-index-will-prepare-info",
    };
  }

  if (reindexRequired && readyCount > 0) {
    return {
      kind: "warning",
      messageKey: "benchmarkRagStaleSnapshotWarning",
      testId: "lab-rag-stale-snapshot-warning",
    };
  }

  return null;
}
