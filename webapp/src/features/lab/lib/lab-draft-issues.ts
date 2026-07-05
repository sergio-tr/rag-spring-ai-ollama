import type { LabEvaluationDraftKind, LabEvaluationDraftStored } from "@/features/lab/lib/lab-evaluation-draft";
import type { LabEvaluationDraftWarnings } from "@/features/lab/lib/lab-evaluation-draft";
import type { EvaluationCorpusReadinessDto } from "@/types/api";

export type LabDraftIssueCode =
  | "NO_DOCUMENTS"
  | "NO_READY_DOCUMENTS"
  | "STALE_EMBEDDING_MODEL"
  | "STALE_CHAT_MODEL"
  | "STALE_SECONDARY_MODEL"
  | "MISSING_DATASET"
  | "MISSING_PRESET"
  | "MISSING_COMPATIBLE_CHUNK_SNAPSHOT"
  | "MISSING_COMPATIBLE_HYBRID_SNAPSHOT"
  | "AUTO_REINDEX_DISABLED"
  | "REUSE_SNAPSHOT_DISABLED";

export type LabDraftIssueSeverity = "error" | "warning";

export type LabDraftIssue = {
  code: LabDraftIssueCode;
  severity: LabDraftIssueSeverity;
  messageKey: string;
  messageParams?: Record<string, string>;
  actionKey?: string;
};

export type ComputeLabDraftIssuesInput = {
  kind: LabEvaluationDraftKind;
  draft: Omit<LabEvaluationDraftStored, "v">;
  warnings: LabEvaluationDraftWarnings;
  invalidLabPresetSelections: string[];
  needsEvaluationCorpus: boolean;
  corpusReadiness: EvaluationCorpusReadinessDto | null | undefined;
  availableLlmModelIds: string[];
  chatCatalogProvider?: string | null;
  embeddingCatalogProvider?: string | null;
};

function issue(
  code: LabDraftIssueCode,
  severity: LabDraftIssueSeverity,
  messageKey: string,
  messageParams?: Record<string, string>,
  actionKey?: string,
): LabDraftIssue {
  return { code, severity, messageKey, messageParams, actionKey };
}

export function computeLabDraftIssues(input: ComputeLabDraftIssuesInput): LabDraftIssue[] {
  const issues: LabDraftIssue[] = [];
  const { kind, draft, warnings, invalidLabPresetSelections, needsEvaluationCorpus, corpusReadiness } =
    input;

  if (warnings.datasetDeletedOrUnknown) {
    issues.push(
      issue("MISSING_DATASET", "error", "evalDraftIssueMissingDataset", undefined, "evalDraftIssueActionChooseDataset"),
    );
  } else if (warnings.datasetIncompatibleWithBenchmark) {
    issues.push(
      issue(
        "MISSING_DATASET",
        "error",
        "evalDraftIssueIncompatibleDataset",
        undefined,
        "evalDraftIssueActionChooseDataset",
      ),
    );
  }

  if (warnings.llmModelInvalid) {
    issues.push(
      issue(
        "STALE_CHAT_MODEL",
        "error",
        input.chatCatalogProvider === "OPENAI_COMPATIBLE"
          ? "evalDraftWarnLlmInvalidOpenAI"
          : "evalDraftWarnLlmInvalid",
        { modelId: draft.llmModelId.trim() },
        "evalDraftIssueActionChooseModel",
      ),
    );
  }
  if (warnings.llmModelsInvalid.length > 0) {
    issues.push(
      issue(
        "STALE_CHAT_MODEL",
        "error",
        input.chatCatalogProvider === "OPENAI_COMPATIBLE"
          ? "evalDraftWarnLlmListInvalidOpenAI"
          : "evalDraftWarnLlmListInvalid",
        { models: warnings.llmModelsInvalid.join(", ") },
        "evalDraftIssueActionChooseModel",
      ),
    );
  }

  if (warnings.embeddingModelInvalid) {
    issues.push(
      issue(
        "STALE_EMBEDDING_MODEL",
        "error",
        input.embeddingCatalogProvider === "OPENAI_COMPATIBLE"
          ? "evalDraftWarnEmbeddingInvalidOpenAI"
          : "evalDraftWarnEmbeddingInvalid",
        { modelId: draft.embeddingModelId.trim() },
        "evalDraftIssueActionChooseEmbedding",
      ),
    );
  }
  if (warnings.embeddingModelsInvalid.length > 0) {
    issues.push(
      issue(
        "STALE_EMBEDDING_MODEL",
        "error",
        input.embeddingCatalogProvider === "OPENAI_COMPATIBLE"
          ? "evalDraftWarnEmbeddingListInvalidOpenAI"
          : "evalDraftWarnEmbeddingListInvalid",
        { models: warnings.embeddingModelsInvalid.join(", ") },
        "evalDraftIssueActionChooseEmbedding",
      ),
    );
  }

  if (warnings.presetsUnknown.length > 0) {
    issues.push(
      issue(
        "MISSING_PRESET",
        "error",
        "evalDraftWarnPresetsUnknown",
        { codes: warnings.presetsUnknown.join(", ") },
        "evalDraftIssueActionChoosePreset",
      ),
    );
  }
  if (invalidLabPresetSelections.length > 0) {
    issues.push(
      issue(
        "MISSING_PRESET",
        "error",
        "evalDraftWarnPresetsNotLabSelectable",
        { codes: invalidLabPresetSelections.join(", ") },
        "evalDraftIssueActionChoosePreset",
      ),
    );
  }
  if (kind === "RAG_PRESET_END_TO_END" && draft.selectedExperimentalPresetCodes.length === 0) {
    issues.push(
      issue("MISSING_PRESET", "error", "labConfigNoPresets", undefined, "evalDraftIssueActionChoosePreset"),
    );
  }

  if (needsEvaluationCorpus && corpusReadiness) {
    const primary = corpusReadiness.primaryBlocker;
    if (primary === "NO_DOCUMENTS" || primary === "KB_EMPTY" || corpusReadiness.documentCount === 0) {
      issues.push(
        issue(
          "NO_DOCUMENTS",
          "error",
          "evalDraftIssueNoDocuments",
          undefined,
          "evalDraftIssueActionAddDocuments",
        ),
      );
    } else if (primary === "NO_READY_DOCUMENTS") {
      issues.push(
        issue(
          "NO_READY_DOCUMENTS",
          "warning",
          "evalDraftIssueNoReadyDocuments",
          undefined,
          "evalDraftIssueActionWaitForDocuments",
        ),
      );
    }

    if (kind === "RAG_PRESET_END_TO_END") {
      const snapshotBlocker = corpusReadiness.snapshotBlocker;
      const needsIndex =
        !corpusReadiness.activeSnapshotId ||
        corpusReadiness.reindexRequired ||
        Boolean(snapshotBlocker);

      if (needsIndex && !draft.autoReindex) {
        issues.push(
          issue(
            "AUTO_REINDEX_DISABLED",
            "error",
            "evalDraftIssueAutoReindexDisabled",
            undefined,
            "evalDraftIssueActionEnableAutoReindex",
          ),
        );
      }

      if (
        draft.autoReindex &&
        !draft.reuseCompatibleActiveSnapshot &&
        corpusReadiness.activeSnapshotId &&
        !corpusReadiness.reindexRequired
      ) {
        issues.push(
          issue(
            "REUSE_SNAPSHOT_DISABLED",
            "warning",
            "evalDraftIssueReuseSnapshotDisabled",
            undefined,
            "evalDraftIssueActionEnableReuse",
          ),
        );
      }

      if (snapshotBlocker === "NO_COMPATIBLE_SNAPSHOT" && !draft.autoReindex) {
        const detail = corpusReadiness.snapshotBlockerDetailCode ?? "";
        if (detail.includes("HYBRID")) {
          issues.push(
            issue(
              "MISSING_COMPATIBLE_HYBRID_SNAPSHOT",
              "error",
              "evalDraftIssueMissingCompatibleHybridSnapshot",
              undefined,
              "evalDraftIssueActionEnableAutoReindex",
            ),
          );
        } else {
          issues.push(
            issue(
              "MISSING_COMPATIBLE_CHUNK_SNAPSHOT",
              "error",
              "evalDraftIssueMissingCompatibleChunkSnapshot",
              undefined,
              "evalDraftIssueActionEnableAutoReindex",
            ),
          );
        }
      }
    }
  }

  return issues;
}
