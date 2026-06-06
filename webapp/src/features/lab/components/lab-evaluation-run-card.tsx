"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CompactHelp, TechnicalDetails } from "@/features/lab/components/compact-lab-ui";
import { Input } from "@/components/ui/input";
import { HelpPopover } from "@/features/help/HelpPopover";
import { Label } from "@/components/ui/label";
import { LabEvaluationCorpusPanel } from "@/features/lab/components/lab-evaluation-corpus-panel";
import { useEvaluationCorpus } from "@/features/lab/hooks/use-evaluation-corpus";
import { ModelCheckboxGroup } from "@/features/lab/components/model-checkbox-group";
import { LabBenchmarkResultsPanel } from "@/features/lab/components/lab-benchmark-results-panel";
import { LabFailedJobResultsNotice } from "@/features/lab/components/lab-failed-job-results-notice";
import { LabJobPanel } from "@/features/lab/components/lab-job-panel";
import { LabJobStopConfirmDialog } from "@/features/lab/components/lab-job-stop-confirm-dialog";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { activeJobMatchesCard } from "@/features/lab/hooks/use-lab-active-job-recovery";
import { useAutoResumeLabJobs } from "@/features/lab/hooks/use-auto-resume-lab-jobs";
import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useExperimentalPresetCatalog } from "@/features/lab/hooks/use-experimental-preset-catalog";
import {
  filterLabBenchmarkSelectablePresets,
  findInvalidLabPresetSelections,
  isLabBenchmarkPresetSelectable,
  listCoreExperimentalPresetCodes,
} from "@/features/lab/lib/experimental-preset-selection";
import { useLabEvaluationDraft } from "@/features/lab/hooks/use-lab-evaluation-draft";
import { useLabJobLiveStream } from "@/features/lab/hooks/use-lab-job-live-stream";
import { pollLabJob } from "@/lib/async-task";
import {
  LAB_DEFAULT_EMBEDDING_MODEL_ID,
  type LabEvaluationDraftKind,
} from "@/features/lab/lib/lab-evaluation-draft";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useModelsByType } from "@/features/chat/hooks/use-models-by-type";
import {
  type LabJobSectionKey,
} from "@/features/lab/lib/lab-job-persistence";
import {
  createLabJobTraceDedupe,
  emitLabJobTraceForTick,
  traceLabJobQueued,
  traceLabJobResumedWatching,
} from "@/features/lab/lib/lab-job-trace";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { resolveEmbeddingCampaignIndexSnapshotIds } from "@/features/lab/lib/embedding-campaign-index-snapshots";
import {
  EMBEDDING_CAMPAIGN_PREFERRED_MODEL_IDS,
  EMBEDDING_CAMPAIGN_STORE_DIMENSION,
  filterCampaignCompatibleEmbeddingIds,
  missingPreferredEmbeddingModels,
} from "@/features/lab/lib/embedding-campaign-preferred-models";
import {
  LLM_CAMPAIGN_PREFERRED_MODEL_IDS,
  missingPreferredLlmModels,
} from "@/features/lab/lib/llm-campaign-preferred-models";
import { mapKnowledgeBaseApiError } from "@/features/lab/lib/evaluation-corpus-upload";
import { extractTechnicalErrorCode } from "@/lib/user-facing-error-messages";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { beginTraceSession, endTraceSession } from "@/lib/trace-session";
import { useAppStore } from "@/store/app.store";
import type {
  AsyncTaskStatusDto,
  BenchmarkJobAcceptedDto,
  BenchmarkKind,
  ExperimentalDatasetListItemDto,
  LabJobAcceptedDto,
  StartBenchmarkRunRequest,
} from "@/types/api";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

export type LabEvaluationRunCardProps = {
  benchmarkKind: BenchmarkKind;
  sectionKey: LabJobSectionKey;
  taskTypeHint: string;
  cardTitle: string;
  /** Optional; omit for compact run section (title only). */
  cardDescription?: string;
  runButtonTestId: string;
  radioGroupName: string;
  /** Optional copy above the card (e.g. RAG-specific help). */
  introBeforeCard?: ReactNode;
};

function compatibleExperimentalTypes(kind: BenchmarkKind): Set<string> {
  switch (kind) {
    case "LLM_JUDGE_QA":
      return new Set(["LLM_MODEL_BASELINE", "REFERENCE_BUNDLE"]);
    case "EMBEDDING_RETRIEVAL":
      return new Set(["EMBEDDING_MODEL_BASELINE", "REFERENCE_BUNDLE"]);
    case "RAG_PRESET_END_TO_END":
      return new Set(["RAG_PRESET_BENCHMARK", "REFERENCE_BUNDLE"]);
    default:
      return new Set();
  }
}

function rankDataset(a: ExperimentalDatasetListItemDto): number {
  if (a.validationStatus === "VALID") return 0;
  if (a.validationStatus === "INVALID") return 2;
  return 1;
}

function pickDefaultDataset(
  items: ExperimentalDatasetListItemDto[],
  kind: BenchmarkKind,
): ExperimentalDatasetListItemDto | null {
  const allowed = compatibleExperimentalTypes(kind);
  const candidates = items.filter((d) => allowed.has(d.experimentalDatasetType));
  if (candidates.length === 0) return null;
  candidates.sort((x, y) => rankDataset(x) - rankDataset(y));
  return candidates[0];
}

function datasetOriginLabel(row: ExperimentalDatasetListItemDto, t: (key: string) => string): string {
  if (row.experimentalDatasetType === "REFERENCE_BUNDLE" || row.readOnly) return t("datasetOriginReference");
  return t("datasetOriginUpload");
}

function datasetCompatibilityLabel(kind: BenchmarkKind, t: (key: string) => string): string {
  switch (kind) {
    case "LLM_JUDGE_QA":
      return t("datasetCompatibilityLlm");
    case "EMBEDDING_RETRIEVAL":
      return t("datasetCompatibilityEmbedding");
    case "RAG_PRESET_END_TO_END":
      return t("datasetCompatibilityRag");
    default:
      return t("datasetCompatibilityUnknown");
  }
}

function datasetCountsLabel(row: ExperimentalDatasetListItemDto, t: (key: string, values?: Record<string, string | number>) => string) {
  const q =
    row.experimentalDatasetType === "LLM_MODEL_BASELINE"
      ? row.questionCounts.llmReaderQuestions
      : row.experimentalDatasetType === "EMBEDDING_MODEL_BASELINE"
        ? row.questionCounts.embeddingQueries
        : row.experimentalDatasetType === "RAG_PRESET_BENCHMARK"
          ? row.questionCounts.ragPresetQuestions
          : // Reference bundle is multi-kind; default to the max "primary" count for the active benchmark.
            Math.max(row.questionCounts.llmReaderQuestions, row.questionCounts.embeddingQueries, row.questionCounts.ragPresetQuestions);
  const r =
    row.questionCounts.llmReaderQuestions +
    row.questionCounts.embeddingQueries +
    row.questionCounts.ragPresetQuestions +
    row.questionCounts.presetCatalog +
    row.questionCounts.chunkRegistry;
  return t("datasetCountsLine", { q, r });
}

function benchmarkAcceptedToLabAccepted(acc: BenchmarkJobAcceptedDto): LabJobAcceptedDto {
  return {
    jobId: acc.asyncTaskId,
    status: acc.status,
    pollPath: acc.pollPath,
    streamPath: acc.streamPath,
  };
}

const BENCHMARK_NON_VIEWABLE_FAILURE_CODES = new Set([
  "BENCHMARK_NO_EXECUTABLE_ITEMS",
  "BENCHMARK_ALL_ITEMS_SKIPPED",
  "COMPLETED_WITH_NO_EXECUTED_ITEMS",
  "FAILED_VALIDATION",
  "BENCHMARK_SKIPPED_WITHOUT_REASON",
  "BENCHMARK_NOT_SUPPORTED_WITHOUT_REASON",
]);

function taskHasViewableResults(taskStatus: AsyncTaskStatusDto | null): boolean {
  if (taskStatus?.terminal !== true) return false;
  const failureCode = taskStatus.failureCode?.trim();
  if (failureCode && BENCHMARK_NON_VIEWABLE_FAILURE_CODES.has(failureCode)) {
    return false;
  }
  const st = (taskStatus.status ?? "").trim().toUpperCase();
  if (st === "FAILED") return false;
  return st === "SUCCEEDED" || st === "CANCELLED" || st === "CANCELED";
}

/**
 * Shared lab benchmark runner (async POST + SSE live progress) for LLM, embedding retrieval, and RAG preset benchmarks.
 */
export function LabEvaluationRunCard({
  benchmarkKind,
  sectionKey,
  taskTypeHint,
  cardTitle,
  cardDescription,
  runButtonTestId,
  introBeforeCard,
}: LabEvaluationRunCardProps) {
  const t = useTranslations("Lab");
  const tHelp = useTranslations("Help");
  const { data: labStatus } = useLabStatus();
  const activeJobs = useActiveLabJobs();
  const experimentalDatasets = useExperimentalDatasetsQuery();
  const experimentalPresets = useExperimentalPresetCatalog();
  const llmModels = useModelsByType("LLM");
  const embeddingModels = useModelsByType("EMBEDDING");
  const activeProject = useAppStore((s) => s.activeProject);

  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<unknown>(null);
  const [accepted, setAccepted] = useState<LabJobAcceptedDto | null>(null);
  const [evaluationRunId, setEvaluationRunId] = useState<string | null>(null);
  const [campaignId, setCampaignId] = useState<string | null>(null);
  const [taskStatus, setTaskStatus] = useState<AsyncTaskStatusDto | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [technicalErrCode, setTechnicalErrCode] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [cancelConfirmOpen, setCancelConfirmOpen] = useState(false);
  const [watchLive, setWatchLive] = useState(false);
  const [watchStartedAtMs, setWatchStartedAtMs] = useState<number | null>(null);
  const [elapsedClockTick, setElapsedClockTick] = useState(0);
  const abortRef = useRef<AbortController | null>(null);
  const traceDedupeRef = useRef(createLabJobTraceDedupe());
  const mountedEvalCardRef = useRef(true);

  const compatibleRows = useMemo(() => {
    const allowed = compatibleExperimentalTypes(benchmarkKind);
    return (experimentalDatasets.data ?? []).filter((d) => {
      if (!allowed.has(d.experimentalDatasetType)) return false;
      if (benchmarkKind === "LLM_JUDGE_QA") return d.canRunLlmBaseline;
      if (benchmarkKind === "EMBEDDING_RETRIEVAL") return d.canRunEmbeddingBaseline;
      if (benchmarkKind === "RAG_PRESET_END_TO_END") return d.canRunRagPresetBenchmark;
      return false;
    });
  }, [benchmarkKind, experimentalDatasets.data]);

  const defaultDataset = useMemo(
    () => pickDefaultDataset(experimentalDatasets.data ?? [], benchmarkKind),
    [benchmarkKind, experimentalDatasets.data],
  );

  const availableLlmModels = useMemo(
    () => llmModels.data?.map((m) => m.modelId).sort((a, b) => a.localeCompare(b)) ?? [],
    [llmModels.data],
  );
  const llmComparisonAvailabilityBlocked = useMemo(() => {
    if (benchmarkKind !== "LLM_JUDGE_QA") return false;
    return availableLlmModels.length > 0 && availableLlmModels.length < 2;
  }, [benchmarkKind, availableLlmModels.length]);
  const missingPreferredLlmTags = useMemo(
    () => missingPreferredLlmModels(availableLlmModels, LLM_CAMPAIGN_PREFERRED_MODEL_IDS),
    [availableLlmModels],
  );
  const availableEmbeddingModels = useMemo(
    () => embeddingModels.data?.map((m) => m.modelId).sort((a, b) => a.localeCompare(b)) ?? [],
    [embeddingModels.data],
  );
  const compatibleEmbeddingModels = useMemo(
    () => filterCampaignCompatibleEmbeddingIds(availableEmbeddingModels),
    [availableEmbeddingModels],
  );
  const embeddingComparisonAvailabilityBlocked = useMemo(() => {
    if (benchmarkKind !== "EMBEDDING_RETRIEVAL") return false;
    return compatibleEmbeddingModels.length > 0 && compatibleEmbeddingModels.length < 2;
  }, [benchmarkKind, compatibleEmbeddingModels.length]);
  const missingPreferredEmbeddingTags = useMemo(
    () => missingPreferredEmbeddingModels(compatibleEmbeddingModels, EMBEDDING_CAMPAIGN_PREFERRED_MODEL_IDS),
    [compatibleEmbeddingModels],
  );

  const setUserFacingErr = useCallback(
    (raw: string | null | undefined) => {
      const trimmed = raw?.trim() ?? "";
      if (!trimmed) {
        setErr(null);
        setTechnicalErrCode(null);
        return;
      }
      setTechnicalErrCode(extractTechnicalErrorCode(trimmed));
      setErr(mapKnowledgeBaseApiError(trimmed, t, t("evalError")));
    },
    [t],
  );

  const draftValidation = useMemo(
    () => ({
      compatibleDatasetRows: compatibleRows,
      allDatasetRows: experimentalDatasets.data ?? [],
      datasetsFetched: experimentalDatasets.isFetched,
      availableLlmModelIds: availableLlmModels,
      availableEmbeddingModelIds: availableEmbeddingModels,
      catalogPresetCodes: (experimentalPresets.data ?? []).map((p) => p.code),
      presetsCatalogReady: experimentalPresets.isSuccess,
    }),
    [
      compatibleRows,
      experimentalDatasets.data,
      experimentalDatasets.isFetched,
      availableLlmModels,
      availableEmbeddingModels,
      experimentalPresets.data,
      experimentalPresets.isSuccess,
    ],
  );

  const { draft, patchDraft, clearDraft, resetToRecommended, setLastEvaluationRunId, warnings } =
    useLabEvaluationDraft(benchmarkKind as LabEvaluationDraftKind, draftValidation);

  useEffect(() => {
    if (!watchLive) {
      const resetId = globalThis.setTimeout(() => {
        setWatchStartedAtMs(null);
        setElapsedClockTick(0);
      }, 0);
      return () => globalThis.clearTimeout(resetId);
    }
    if (watchStartedAtMs != null) {
      return;
    }
    const id = globalThis.setTimeout(() => setWatchStartedAtMs(Date.now()), 0);
    return () => globalThis.clearTimeout(id);
  }, [watchLive, watchStartedAtMs]);

  useEffect(() => {
    if (watchStartedAtMs == null) return undefined;
    const id = globalThis.setInterval(() => setElapsedClockTick((x) => x + 1), 1000);
    return () => globalThis.clearInterval(id);
  }, [watchStartedAtMs]);

  const watchElapsedSeconds = watchStartedAtMs != null ? elapsedClockTick : undefined;

  const liveJob = useLabJobLiveStream({
    jobId: accepted?.jobId ?? null,
    streamPath: accepted?.streamPath,
    pollPath: accepted?.pollPath,
    status: accepted?.status,
    enabled: watchLive && !!accepted,
    onTick: (s) => {
      if (!accepted?.jobId) return;
      setTaskStatus(s);
      useLabJobSessionStore.getState().patchLabJobFromTick(accepted.jobId, s);
      emitLabJobTraceForTick(traceDedupeRef.current, s, accepted.jobId, {
        queued: t("traceJobQueued"),
        running: t("traceJobRunning"),
        completed: t("traceJobCompleted"),
        failed: t("traceJobFailed"),
        cancelled: t("traceJobCancelled"),
      });
    },
    onTerminal: (s) => {
      if (!mountedEvalCardRef.current) return;
      setResult(s.result);
      setRunning(false);
      setCancelling(false);
      setWatchLive(false);
      endTraceSession();
    },
    onStreamError: (e) => {
      if (!mountedEvalCardRef.current) return;
      if (e instanceof ApiError && e.status === 404 && accepted?.jobId) {
        useLabJobSessionStore.getState().markLabJobStaleNotFound(accepted.jobId);
        setErr(t("jobRecoveryStaleShort"));
      } else if (e instanceof Error) {
        setUserFacingErr(e.message || t("evalError"));
      } else {
        setUserFacingErr(t("evalError"));
      }
      setRunning(false);
      setWatchLive(false);
    },
  });

  const beginLiveWatch = useCallback(
    async (rec: { accepted: LabJobAcceptedDto; evaluationRunId?: string | null; jobId: string }) => {
      traceDedupeRef.current = createLabJobTraceDedupe();
      traceLabJobResumedWatching(rec.jobId, t("traceJobResumedWatching"));
      setAccepted(rec.accepted);
      setEvaluationRunId(rec.evaluationRunId ?? null);
      setRunning(true);
      setErr(null);
      setWatchLive(true);
    },
    [t],
  );

  const labRecovery = useAutoResumeLabJobs({
    sectionKey,
    benchmarkKind,
    activeProjectId: activeProject?.id ?? null,
    taskTypeHint,
    canAutoFollow: !running,
    watchingJobId: watchLive ? accepted?.jobId ?? null : null,
    onAutoFollow: async ({ candidate, status }) => {
      if (!mountedEvalCardRef.current) return;
      if (status.terminal) {
        setAccepted(candidate.accepted);
        setEvaluationRunId(candidate.evaluationRunId);
        setTaskStatus(status);
        setResult(status.result);
        setRunning(false);
        setWatchLive(false);
        return;
      }
      await beginLiveWatch({
        accepted: candidate.accepted,
        evaluationRunId: candidate.evaluationRunId,
        jobId: candidate.jobId,
      });
    },
    onFollowError: (e) => {
      if (!mountedEvalCardRef.current) return;
      if (e instanceof ApiError && e.status === 404) {
        setErr(t("jobRecoveryStaleShort"));
      } else if (!(e instanceof DOMException && e.name === "AbortError")) {
        setUserFacingErr(e instanceof Error ? e.message : t("evalError"));
      }
    },
  });

  const needsEvaluationCorpus =
    benchmarkKind === "RAG_PRESET_END_TO_END" || benchmarkKind === "EMBEDDING_RETRIEVAL";
  const evaluationCorpus = useEvaluationCorpus(needsEvaluationCorpus ? draft.corpusId : null, {
    onCorpusStale: () => patchDraft({ corpusId: null }),
  });
  const resolvedCorpusId =
    draft.corpusId ?? evaluationCorpus.effectiveCorpusId ?? null;

  useEffect(() => {
    if (!needsEvaluationCorpus) return;
    const id = evaluationCorpus.effectiveCorpusId;
    if (id && !draft.corpusId) {
      patchDraft({ corpusId: id });
    }
  }, [needsEvaluationCorpus, evaluationCorpus.effectiveCorpusId, draft.corpusId, patchDraft]);

  const invalidLabPresetSelections = useMemo(
    () =>
      benchmarkKind === "RAG_PRESET_END_TO_END"
        ? findInvalidLabPresetSelections(
            draft.selectedExperimentalPresetCodes,
            experimentalPresets.data,
          )
        : [],
    [benchmarkKind, draft.selectedExperimentalPresetCodes, experimentalPresets.data],
  );

  const ragPresetConfigBlocksRun =
    benchmarkKind === "RAG_PRESET_END_TO_END" &&
    (draft.selectedExperimentalPresetCodes.length === 0 || invalidLabPresetSelections.length > 0);

  const draftBlocksRun =
    warnings.datasetDeletedOrUnknown ||
    warnings.datasetIncompatibleWithBenchmark ||
    warnings.llmModelInvalid ||
    warnings.llmModelsInvalid.length > 0 ||
    warnings.embeddingModelInvalid ||
    warnings.embeddingModelsInvalid.length > 0 ||
    warnings.presetsUnknown.length > 0 ||
    ragPresetConfigBlocksRun;

  const hasEvaluationCorpus = Boolean(resolvedCorpusId);
  const corpusPrimaryBlocker = evaluationCorpus.readiness?.primaryBlocker ?? null;
  const corpusBlocksRun =
    needsEvaluationCorpus &&
    (!hasEvaluationCorpus ||
      !evaluationCorpus.corpusRunnable ||
      evaluationCorpus.corpusProcessing ||
      Boolean(corpusPrimaryBlocker));

  const selectedDataset = useMemo(() => {
    const id = draft.datasetId?.trim();
    if (!id) return null;
    return compatibleRows.find((r) => r.id === id) ?? null;
  }, [compatibleRows, draft.datasetId]);

  const datasetSelectValue = draft.datasetId ?? "";

  const showStaleDatasetOption =
    Boolean(draft.datasetId) &&
    (warnings.datasetDeletedOrUnknown || warnings.datasetIncompatibleWithBenchmark);

  const recommendedDraftPartial = useMemo(
    () => ({
      datasetId: defaultDataset?.id ?? null,
      embeddingModelId: availableEmbeddingModels.includes(LAB_DEFAULT_EMBEDDING_MODEL_ID)
        ? LAB_DEFAULT_EMBEDDING_MODEL_ID
        : (availableEmbeddingModels[0] ?? ""),
      embeddingModelIds: availableEmbeddingModels.includes(LAB_DEFAULT_EMBEDDING_MODEL_ID)
        ? [LAB_DEFAULT_EMBEDDING_MODEL_ID]
        : [],
    }),
    [defaultDataset?.id, availableEmbeddingModels],
  );

  useEffect(() => {
    if (benchmarkKind !== "EMBEDDING_RETRIEVAL" && benchmarkKind !== "RAG_PRESET_END_TO_END") return;
    if (draft.embeddingModelId.trim() !== "") return;
    if (!availableEmbeddingModels.includes(LAB_DEFAULT_EMBEDDING_MODEL_ID)) return;
    patchDraft({ embeddingModelId: LAB_DEFAULT_EMBEDDING_MODEL_ID });
  }, [benchmarkKind, draft.embeddingModelId, availableEmbeddingModels, patchDraft]);

  useEffect(() => {
    if (draft.datasetId != null || draft.explicitDraftClear) return;
    if (!defaultDataset?.id) return;
    patchDraft({ datasetId: defaultDataset.id, explicitDraftClear: false });
  }, [draft.datasetId, draft.explicitDraftClear, defaultDataset?.id, patchDraft]);

  const referenceKindsReady =
    labStatus?.datasetKindsReady ??
    labStatus?.datasets?.datasetKindsReady ??
    labStatus?.datasets?.enabled ??
    false;

  useEffect(() => {
    mountedEvalCardRef.current = true;
    return () => {
      mountedEvalCardRef.current = false;
      abortRef.current?.abort();
    };
  }, []);

  const hasCompatibleDataset = !experimentalDatasets.isLoading && selectedDataset != null;
  const datasetIsValid = selectedDataset?.validationStatus === "VALID";
  const demoBlocked = selectedDataset?.isDemoDataset === true;
  const tooSmallBlocked =
    benchmarkKind === "LLM_JUDGE_QA"
      ? (selectedDataset?.questionCounts.llmReaderQuestions ?? 0) <= 1
      : benchmarkKind === "EMBEDDING_RETRIEVAL"
        ? (selectedDataset?.questionCounts.embeddingQueries ?? 0) <= 1
        : benchmarkKind === "RAG_PRESET_END_TO_END"
          ? (selectedDataset?.questionCounts.ragPresetQuestions ?? 0) <= 1
          : false;
  const hardBlocked = demoBlocked || tooSmallBlocked;
  const canStart =
    hasCompatibleDataset && datasetIsValid && !hardBlocked && !draftBlocksRun && !corpusBlocksRun;

  const otherActiveJobExists = useMemo(() => {
    const jobs = activeJobs.data ?? [];
    if (jobs.length === 0) return false;
    const scopeId = activeProject?.id ?? null;
    const matching = jobs.filter((j) => activeJobMatchesCard(j, benchmarkKind, scopeId));
    if (matching.length > 1) return true;
    const currentId = accepted?.jobId ?? null;
    if (currentId) {
      return jobs.some((j) => Boolean(j.jobId) && j.jobId !== currentId);
    }
    return jobs.some((j) => !activeJobMatchesCard(j, benchmarkKind, scopeId));
  }, [accepted?.jobId, activeJobs.data, activeProject?.id, benchmarkKind]);

  const expectedSummary = useMemo(() => {
    if (!selectedDataset) return "";
    if (benchmarkKind === "LLM_JUDGE_QA") {
      const q = selectedDataset.questionCounts.llmReaderQuestions ?? 0;
      const models = draft.llmModelIds.length > 0 ? draft.llmModelIds.length : 1;
      return t("benchmarkExpectedItemsLlm", { q, models, items: q * models });
    }
    if (benchmarkKind === "EMBEDDING_RETRIEVAL") {
      const q = selectedDataset.questionCounts.embeddingQueries ?? 0;
      const models = draft.embeddingModelIds.length > 0 ? draft.embeddingModelIds.length : 1;
      return t("benchmarkExpectedItemsEmbedding", { q, items: q * models });
    }
    if (benchmarkKind === "RAG_PRESET_END_TO_END") {
      const q = selectedDataset.questionCounts.ragPresetQuestions ?? 0;
      const catalog = selectedDataset.questionCounts.presetCatalog ?? 0;
      const presets =
        draft.selectedExperimentalPresetCodes.length > 0 ? draft.selectedExperimentalPresetCodes.length : catalog;
      const items = catalog > 0 ? q * presets : q;
      return t("benchmarkExpectedItemsRag", { q, presets: catalog > 0 ? presets : 1, items });
    }
    return "";
  }, [benchmarkKind, draft.embeddingModelIds.length, draft.llmModelIds.length, draft.selectedExperimentalPresetCodes.length, selectedDataset, t]);

  const comparisonSelectionCount = useMemo(() => {
    if (benchmarkKind === "LLM_JUDGE_QA") {
      return draft.llmModelIds.map((x) => x.trim()).filter(Boolean).length;
    }
    if (benchmarkKind === "EMBEDDING_RETRIEVAL") {
      return draft.embeddingModelIds.map((x) => x.trim()).filter(Boolean).length;
    }
    if (benchmarkKind === "RAG_PRESET_END_TO_END") {
      return draft.selectedExperimentalPresetCodes.length;
    }
    return 0;
  }, [benchmarkKind, draft.embeddingModelIds, draft.llmModelIds, draft.selectedExperimentalPresetCodes]);

  const runButtonLabel = useMemo(() => {
    if (running) return t("evalRunning");
    if (benchmarkKind === "LLM_JUDGE_QA" && comparisonSelectionCount >= 2) {
      return t("runEvalComparisonLlm");
    }
    if (benchmarkKind === "RAG_PRESET_END_TO_END" && comparisonSelectionCount >= 2) {
      return t("runEvalComparisonPreset");
    }
    if (benchmarkKind === "EMBEDDING_RETRIEVAL" && comparisonSelectionCount >= 2) {
      return t("runEvalComparisonEmbedding");
    }
    return t("runEval");
  }, [benchmarkKind, comparisonSelectionCount, running, t]);

  const comparisonHint = useMemo(() => {
    if (comparisonSelectionCount >= 2) {
      if (benchmarkKind === "LLM_JUDGE_QA") {
        return t("benchmarkComparingModels", { count: comparisonSelectionCount });
      }
      if (benchmarkKind === "RAG_PRESET_END_TO_END") {
        return t("benchmarkComparingPresets", { count: comparisonSelectionCount });
      }
      if (benchmarkKind === "EMBEDDING_RETRIEVAL") {
        return t("benchmarkComparingEmbeddings", { count: comparisonSelectionCount });
      }
    }
    if (comparisonSelectionCount === 1) {
      return t("benchmarkSingleRunNote");
    }
    return null;
  }, [benchmarkKind, comparisonSelectionCount, t]);

  async function run() {
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const signal = abortRef.current.signal;
    setRunning(true);
    setErr(null);
    setResult(null);
    setAccepted(null);
    setEvaluationRunId(null);
    setCampaignId(null);
    setTaskStatus(null);
    setWatchLive(false);
    liveJob.stop();
    traceDedupeRef.current = createLabJobTraceDedupe();
    let asyncAccepted: LabJobAcceptedDto | null = null;
    try {
      if (!selectedDataset) {
        setErr(t("benchmarkNeedsCompatibleDataset"));
        setRunning(false);
        return;
      }
      if (benchmarkKind === "RAG_PRESET_END_TO_END" || benchmarkKind === "EMBEDDING_RETRIEVAL") {
        if (!resolvedCorpusId) {
          setErr(t("benchmarkNeedsCorpus"));
          setRunning(false);
          return;
        }
      }
      if (selectedDataset.validationStatus !== "VALID") {
        setErr(t("benchmarkNeedsValidDataset"));
        setRunning(false);
        return;
      }
      if (otherActiveJobExists) {
        setErr(t("jobAlreadyRunning"));
        setRunning(false);
        return;
      }
      const body: StartBenchmarkRunRequest = {
        datasetId: selectedDataset.id,
        corpusId: resolvedCorpusId ?? undefined,
      };
      if (benchmarkKind !== "RAG_PRESET_END_TO_END") {
        body.projectId = activeProject?.id ?? undefined;
      }
      if (benchmarkKind === "RAG_PRESET_END_TO_END" && draft.selectedExperimentalPresetCodes.length > 0) {
        body.experimentalPresetCodes = draft.selectedExperimentalPresetCodes;
      }
      if (benchmarkKind === "RAG_PRESET_END_TO_END") {
        body.autoReindex = true;
        body.allowActiveSnapshotMutation = true;
        body.reuseCompatibleActiveSnapshot = true;
        body.bootstrapCorpusFromClasspathDocs = true;
        body.bootstrapSkipExisting = true;
      }
      if (draft.runName.trim()) {
        body.campaignName = draft.runName.trim();
      }
      const lm = draft.llmModelId.trim();
      const em = draft.embeddingModelId.trim();
      const lmList = draft.llmModelIds.map((x) => x.trim()).filter(Boolean);
      const emList = draft.embeddingModelIds.map((x) => x.trim()).filter(Boolean);
      const presetList = draft.selectedExperimentalPresetCodes.map((x) => x.trim()).filter(Boolean);
      if (lmList.length > 0) {
        body.llmModelIds = lmList;
        body.campaignName = body.campaignName ?? `LLM campaign (${lmList.length})`;
      } else if (lm) {
        body.llmModelId = lm;
      }
      if (emList.length > 0) {
        body.embeddingModelIds = emList;
        body.campaignName = body.campaignName ?? `Embedding campaign (${emList.length})`;
        if (emList.length >= 2) {
          const projectId = activeProject?.id?.trim();
          if (projectId) {
            const { snapshotIds, unresolvedModels } = await resolveEmbeddingCampaignIndexSnapshotIds(
              projectId,
              emList,
            );
            if (unresolvedModels.length === 0 && snapshotIds.every((id) => id.trim().length > 0)) {
              body.indexSnapshotIds = snapshotIds;
            }
          }
          // Without a UI project, backend aligns from evaluation-corpus index project and may prepare snapshots.
        } else if (emList.length === 1 && activeProject?.id) {
          const { snapshotIds } = await resolveEmbeddingCampaignIndexSnapshotIds(activeProject.id, emList);
          if (snapshotIds[0]?.trim()) {
            body.indexSnapshotId = snapshotIds[0];
          }
        }
      } else if (em) {
        body.embeddingModelId = em;
      }
      if (benchmarkKind === "RAG_PRESET_END_TO_END" && presetList.length >= 2) {
        body.campaignName = body.campaignName ?? `RAG preset campaign (${presetList.length})`;
      }
      if (benchmarkKind === "EMBEDDING_RETRIEVAL") {
        body.embeddingDownstreamRag = draft.embeddingDownstreamRag;
      }
      const url = apiProductPath(`/lab/benchmarks/${benchmarkKind}/runs`);
      beginTraceSession();
      const accRaw = await apiFetch<BenchmarkJobAcceptedDto>(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(body),
        signal,
      });
      const acc = benchmarkAcceptedToLabAccepted(accRaw);
      asyncAccepted = acc;
      setAccepted(acc);
      setEvaluationRunId(accRaw.evaluationRunId);
      setCampaignId(accRaw.campaignId ?? null);
      setLastEvaluationRunId(accRaw.evaluationRunId);
      useLabJobSessionStore.getState().upsertLabJobOnAccepted({
        accepted: acc,
        sectionKey,
        followMode: "sse",
        taskTypeHint,
        evaluationRunId: accRaw.evaluationRunId,
      });
      if (!traceDedupeRef.current.acceptedEmitted) {
        traceDedupeRef.current.acceptedEmitted = true;
        traceLabJobQueued(acc.jobId, t("traceJobQueued"));
      }
      setWatchLive(true);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404 && asyncAccepted?.jobId) {
        if (!mountedEvalCardRef.current) return;
        useLabJobSessionStore.getState().markLabJobStaleNotFound(asyncAccepted.jobId);
        setErr(t("jobRecoveryStaleShort"));
        setRunning(false);
        setWatchLive(false);
      } else if (e instanceof ApiError && e.status === 409) {
        if (!mountedEvalCardRef.current) return;
        setErr(t("jobAlreadyRunning"));
        setRunning(false);
        setWatchLive(false);
      } else if (
        e instanceof ApiError &&
        e.status === 400 &&
        typeof e.message === "string" &&
        (e.message.includes("EMBEDDING_CAMPAIGN_MISSING_INDEX_SNAPSHOT") ||
          e.message.includes("EMBEDDING_CAMPAIGN_REQUIRES_ALIGNED_INDEX_SNAPSHOT_IDS"))
      ) {
        if (!mountedEvalCardRef.current) return;
        setErr(t("embeddingCampaignMissingSnapshots", { models: "—" }));
        setRunning(false);
        setWatchLive(false);
      } else {
        if (!mountedEvalCardRef.current) return;
        const raw = e instanceof ApiError ? e.message : e instanceof Error ? e.message : "";
        setUserFacingErr(raw);
        setRunning(false);
        setWatchLive(false);
      }
    }
  }

  async function cancelBackendJob() {
    if (!accepted?.jobId) {
      abortRef.current?.abort();
      setRunning(false);
      setCancelling(false);
      return;
    }
    const jobId = accepted.jobId;
    setCancelling(true);
    setWatchLive(true);
    try {
      await apiFetch<void>(apiProductPath(`/lab/jobs/${jobId}/cancel`), { method: "POST" });
      const terminal = await pollLabJob(
        jobId,
        (tick) => {
          setTaskStatus(tick);
          useLabJobSessionStore.getState().patchLabJobFromTick(jobId, tick);
        },
        { maxWaitMs: 90_000, throwOnFailed: false },
      );
      setResult(terminal.result);
      setTaskStatus(terminal);
      useLabJobSessionStore.getState().patchLabJobFromTick(jobId, terminal);
      setRunning(false);
      setCancelling(false);
      setWatchLive(false);
      liveJob.stop();
    } catch (e) {
      setUserFacingErr(e instanceof Error ? e.message : t("evalError"));
      throw e;
    } finally {
      setCancelling(false);
      setRunning(false);
      setWatchLive(false);
    }
  }

  const effectiveTaskStatus = watchLive ? (liveJob.taskStatus ?? taskStatus) : taskStatus;
  const showResultsPanel = taskHasViewableResults(effectiveTaskStatus) && !!evaluationRunId?.trim();
  const terminalStatus = (effectiveTaskStatus?.status ?? "").trim().toUpperCase();
  const showFailedJobNotice =
    effectiveTaskStatus?.terminal === true &&
    terminalStatus === "FAILED" &&
    !!evaluationRunId?.trim();
  const showStopButton = running || cancelling;

  const staleDatasetRow =
    draft.datasetId != null
      ? (experimentalDatasets.data ?? []).find((d) => d.id === draft.datasetId)
      : undefined;

  function datasetOptionLabel(row: ExperimentalDatasetListItemDto): string {
    const name = row.name ?? t("experimentalDatasetUnnamed");
    const status = row.validationStatus ?? "";
    return status ? `${name} · ${status}` : name;
  }

  return (
    <div className="space-y-3">
      {introBeforeCard ?? null}

      <Card data-testid="lab-eval-run-card">
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2 space-y-0 pb-2">
          <CardTitle className="text-base">{cardTitle}</CardTitle>
          <HelpPopover
            triggerAriaLabel={tHelp("labEvalRunnerTriggerLabel")}
            title={tHelp("labEvalRunnerTitle")}
            message={tHelp("labEvalRunnerMessage")}
            details={tHelp("labEvalRunnerDetails")}
          />
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {cardDescription ? (
            <p className="text-muted-foreground -mt-1 text-xs">{cardDescription}</p>
          ) : null}

          <TechnicalDetails summary={t("labDeveloperDetailsSummary")} testId="lab-eval-technical-details">
            {technicalErrCode ? (
              <p className="text-muted-foreground text-xs" data-testid="lab-eval-technical-error-code">
                <span className="font-medium">{t("labTechnicalErrorCodeLabel")}: </span>
                <code>{technicalErrCode}</code>
              </p>
            ) : null}
            <CompactHelp summary={t("adrDisclaimerSummary")}>
              <p className="text-muted-foreground text-xs leading-relaxed">{t("adrDisclaimer")}</p>
            </CompactHelp>
            <p className="text-muted-foreground text-xs">{t("labAdvancedEvalHelp")}</p>
            <p className="text-muted-foreground text-xs">
              {t("labDeveloperEvalEndpoint", { kind: benchmarkKind })}
            </p>
            <p className="text-muted-foreground text-xs">{t("benchmarkPromptProfileInfo")}</p>
            {benchmarkKind === "RAG_PRESET_END_TO_END" ? (
              <p className="text-muted-foreground text-xs">{t("benchmarkRagPresetCatalogInfo")}</p>
            ) : null}
            {draft.lastEvaluationRunId ? (
              <p className="text-muted-foreground text-xs" data-testid="lab-eval-draft-last-run">
                {t("evalDraftLastRunHint", { runId: draft.lastEvaluationRunId })}
              </p>
            ) : null}
          </TechnicalDetails>

          {benchmarkKind === "RAG_PRESET_END_TO_END" || benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
            <LabEvaluationCorpusPanel
              corpusId={resolvedCorpusId}
              evaluationCorpus={evaluationCorpus}
              optionalProjectId={activeProject?.id ?? null}
              disabled={running}
              onCorpusIdChange={(id) => patchDraft({ corpusId: id })}
            />
          ) : null}

          {activeProject ? (
            <p className="text-muted-foreground text-xs">
              {t("projectScopeActive", { name: activeProject.name })}
            </p>
          ) : (
            <p className="text-muted-foreground text-xs">{t("projectScopeNone")}</p>
          )}

          {referenceKindsReady ? null : (
            <output
              data-testid="lab-datasets-disabled-warn"
              className="block text-amber-600 text-sm dark:text-amber-500"
            >
              {t("datasetsDisabledWarn")}
            </output>
          )}

          {corpusBlocksRun && needsEvaluationCorpus ? (
            <output
              role="status"
              data-testid="lab-corpus-not-ready-hint"
              className="block text-muted-foreground text-xs"
            >
              {corpusPrimaryBlocker
                ? t("labCorpusReadinessBlocked", {
                    reason: mapKnowledgeBaseApiError(
                      corpusPrimaryBlocker,
                      t,
                      evaluationCorpus.readiness?.primaryBlockerMessage ?? t("benchmarkCorpusNotReady"),
                    ),
                  })
                : t("benchmarkCorpusNotReady")}
            </output>
          ) : null}

          {draftBlocksRun ? (
            <output
              role="alert"
              data-testid="lab-evaluation-draft-warnings"
              className="block rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-amber-800 text-sm dark:text-amber-200"
            >
              <p className="font-medium">{t("evalDraftWarningsTitle")}</p>
              <ul className="mt-1 list-inside list-disc space-y-0.5">
                {warnings.datasetDeletedOrUnknown ? (
                  <li>{t("evalDraftWarnDatasetMissing")}</li>
                ) : null}
                {warnings.datasetIncompatibleWithBenchmark ? (
                  <li>{t("evalDraftWarnDatasetIncompatible")}</li>
                ) : null}
                {warnings.llmModelInvalid ? (
                  <li>{t("evalDraftWarnLlmInvalid", { modelId: draft.llmModelId.trim() })}</li>
                ) : null}
                {warnings.llmModelsInvalid.length > 0 ? (
                  <li>{t("evalDraftWarnLlmListInvalid", { models: warnings.llmModelsInvalid.join(", ") })}</li>
                ) : null}
                {warnings.embeddingModelInvalid ? (
                  <li>{t("evalDraftWarnEmbeddingInvalid", { modelId: draft.embeddingModelId.trim() })}</li>
                ) : null}
                {warnings.embeddingModelsInvalid.length > 0 ? (
                  <li>{t("evalDraftWarnEmbeddingListInvalid", { models: warnings.embeddingModelsInvalid.join(", ") })}</li>
                ) : null}
                {warnings.presetsUnknown.length > 0 ? (
                  <li>{t("evalDraftWarnPresetsUnknown", { codes: warnings.presetsUnknown.join(", ") })}</li>
                ) : null}
                {invalidLabPresetSelections.length > 0 ? (
                  <li>{t("evalDraftWarnPresetsNotLabSelectable", { codes: invalidLabPresetSelections.join(", ") })}</li>
                ) : null}
                {benchmarkKind === "RAG_PRESET_END_TO_END" &&
                draft.selectedExperimentalPresetCodes.length === 0 &&
                !corpusBlocksRun ? (
                  <li>{t("labConfigNoPresets")}</li>
                ) : null}
              </ul>
            </output>
          ) : null}

          <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end">
            <div className="min-w-[200px] flex-1 space-y-2">
              <Label htmlFor={`lab-eval-run-name-${sectionKey}`}>{t("evalDraftRunNameLabel")}</Label>
              <Input
                id={`lab-eval-run-name-${sectionKey}`}
                data-testid="lab-eval-run-name"
                value={draft.runName}
                disabled={running}
                placeholder={t("evalDraftRunNamePlaceholder")}
                onChange={(e) => patchDraft({ runName: e.target.value })}
              />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={running}
                data-testid="lab-eval-draft-clear"
                onClick={() => clearDraft()}
              >
                {t("evalDraftClear")}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={running}
                data-testid="lab-eval-draft-reset-recommended"
                onClick={() => resetToRecommended(recommendedDraftPartial)}
              >
                {t("evalDraftResetRecommended")}
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor={`lab-benchmark-dataset-${sectionKey}`}>{t("benchmarkDatasetLabel")}</Label>
            <select
              id={`lab-benchmark-dataset-${sectionKey}`}
              data-testid="lab-benchmark-dataset-select"
              className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
              value={datasetSelectValue}
              disabled={running || compatibleRows.length === 0}
              onChange={(e) => {
                const v = e.target.value;
                patchDraft({
                  datasetId: v ? v : null,
                  explicitDraftClear: !v,
                });
              }}
            >
              {compatibleRows.length === 0 ? (
                <option value="">{t("benchmarkDatasetPlaceholderNone")}</option>
              ) : (
                <option value="">{t("benchmarkDatasetPlaceholderChoose")}</option>
              )}
              {showStaleDatasetOption && draft.datasetId ? (
                <option value={draft.datasetId} disabled>
                  {warnings.datasetDeletedOrUnknown
                    ? t("benchmarkDatasetStaleMissingOption", { id: draft.datasetId })
                    : t("benchmarkDatasetStaleIncompatibleOption", {
                        name: staleDatasetRow?.name ?? t("experimentalDatasetUnnamed"),
                        id: draft.datasetId,
                      })}
                </option>
              ) : null}
              {compatibleRows.map((row) => (
                <option key={row.id} value={row.id}>
                  {datasetOptionLabel(row)}
                </option>
              ))}
            </select>
          </div>

          {experimentalDatasets.isFetched && compatibleRows.length === 0 ? (
            <output
              data-testid="lab-benchmark-needs-dataset-warn"
              className="block rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-amber-800 text-sm dark:text-amber-200"
            >
              <p className="font-medium">{t("benchmarkNeedsCompatibleDataset")}</p>
              <p className="text-muted-foreground mt-1 text-xs" data-testid="lab-eval-empty-state">
                {benchmarkKind === "LLM_JUDGE_QA"
                  ? t("llmEvalEmptyHint")
                  : benchmarkKind === "EMBEDDING_RETRIEVAL"
                    ? t("embeddingEvalEmptyHint")
                    : t("ragEvalEmptyHint")}
              </p>
            </output>
          ) : null}

          {selectedDataset ? (
            <>
              <p className="text-muted-foreground text-xs" data-testid="lab-selected-dataset-summary">
                {t("compactSelectedDataset", {
                  name: selectedDataset.name ?? t("experimentalDatasetUnnamed"),
                  status: selectedDataset.validationStatus ?? t("experimentalDatasetValidationStatusUnknown"),
                })}
              </p>
              {!datasetIsValid ? (
                <output className="block text-amber-600 text-xs dark:text-amber-500" data-testid="lab-dataset-invalid-warn">
                  {t("benchmarkNeedsValidDataset")}
                </output>
              ) : null}
              <TechnicalDetails
                summary={t("datasetDetailsTitle")}
                testId="lab-selected-dataset-details"
              >
                <div className="text-muted-foreground space-y-1 text-xs">
                  <p>
                    <span className="font-medium">{t("datasetDetailsType")}:</span>{" "}
                    {selectedDataset.experimentalDatasetType}
                  </p>
                  <p>
                    <span className="font-medium">{t("datasetDetailsOrigin")}:</span>{" "}
                    {datasetOriginLabel(selectedDataset, t)}
                  </p>
                  <p>
                    <span className="font-medium">{t("datasetDetailsCounts")}:</span>{" "}
                    {datasetCountsLabel(selectedDataset, t)}
                  </p>
                  <p className="text-xs">
                    {t("benchmarkDatasetCompatibilityHint", {
                      kind: datasetCompatibilityLabel(benchmarkKind, t),
                    })}
                  </p>
                </div>
              </TechnicalDetails>
            </>
          ) : null}

          {(benchmarkKind === "LLM_JUDGE_QA" ||
            benchmarkKind === "RAG_PRESET_END_TO_END" ||
            (benchmarkKind === "EMBEDDING_RETRIEVAL" && draft.embeddingDownstreamRag)) && (
            <div className="space-y-2">
              {benchmarkKind === "LLM_JUDGE_QA" && availableLlmModels.length > 0 ? (
                <ModelCheckboxGroup
                  id={`lab-llm-model-${sectionKey}`}
                  label={t("benchmarkLlmModelOptional")}
                  availableModelIds={availableLlmModels}
                  selectedIds={draft.llmModelIds}
                  disabled={running}
                  testIdPrefix="lab-benchmark-llm-models"
                  hint={t("benchmarkLlmMultiHint")}
                  onChange={(llmModelIds) => patchDraft({ llmModelIds })}
                />
              ) : (
                <>
                  <select
                    id={`lab-llm-model-${sectionKey}`}
                    data-testid="lab-benchmark-llm-model"
                    className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
                    value={draft.llmModelId}
                    disabled={running || availableLlmModels.length === 0}
                    onChange={(e) => patchDraft({ llmModelId: e.target.value })}
                  >
                    {availableLlmModels.length === 0 ? (
                      <option value="">{t("benchmarkLlmModelPlaceholder")}</option>
                    ) : (
                      <option value="">{t("benchmarkLlmModelPlaceholder")}</option>
                    )}
                    {warnings.llmModelInvalid && draft.llmModelId.trim() !== "" ? (
                      <option value={draft.llmModelId} disabled>
                        {draft.llmModelId}
                      </option>
                    ) : null}
                    {availableLlmModels.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                  {availableLlmModels.length === 0 ? (
                    <output className="text-muted-foreground block text-xs">{t("noLlmModelsAvailable")}</output>
                  ) : null}
                </>
              )}
            </div>
          )}

          {(benchmarkKind === "EMBEDDING_RETRIEVAL" || benchmarkKind === "RAG_PRESET_END_TO_END") && (
            <div className="space-y-2">
              {benchmarkKind === "EMBEDDING_RETRIEVAL" && availableEmbeddingModels.length > 0 ? (
                <ModelCheckboxGroup
                  id={`lab-emb-model-${sectionKey}`}
                  label={t("benchmarkEmbeddingModelOptional")}
                  availableModelIds={availableEmbeddingModels}
                  selectedIds={draft.embeddingModelIds}
                  disabled={running}
                  testIdPrefix="lab-benchmark-embedding-models"
                  hint={t("benchmarkEmbeddingMultiHint")}
                  onChange={(embeddingModelIds) => patchDraft({ embeddingModelIds })}
                />
              ) : (
                <>
                  <Label htmlFor={`lab-emb-model-${sectionKey}`}>{t("benchmarkEmbeddingModelOptional")}</Label>
                  <select
                    id={`lab-emb-model-${sectionKey}`}
                    data-testid="lab-benchmark-embedding-model"
                    className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
                    value={draft.embeddingModelId}
                    disabled={running || availableEmbeddingModels.length === 0}
                    onChange={(e) => patchDraft({ embeddingModelId: e.target.value })}
                  >
                    <option value="">{t("benchmarkEmbeddingModelPlaceholder")}</option>
                    {warnings.embeddingModelInvalid && draft.embeddingModelId.trim() !== "" ? (
                      <option value={draft.embeddingModelId} disabled>
                        {draft.embeddingModelId}
                      </option>
                    ) : null}
                    {availableEmbeddingModels.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                  {availableEmbeddingModels.length === 0 ? (
                    <output className="text-muted-foreground block text-xs">{t("noEmbeddingModelsAvailable")}</output>
                  ) : null}
                </>
              )}
            </div>
          )}

          {benchmarkKind === "RAG_PRESET_END_TO_END" ? (
            <div className="space-y-2">
              <Label>{t("benchmarkExperimentalPresetsLabel")}</Label>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={running}
                  data-testid="lab-experimental-presets-select-all"
                  onClick={() =>
                    patchDraft({
                      selectedExperimentalPresetCodes: filterLabBenchmarkSelectablePresets(
                        experimentalPresets.data,
                      ).map((p) => p.code),
                    })
                  }
                >
                  {t("benchmarkExperimentalPresetsSelectAll")}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={running}
                  data-testid="lab-experimental-presets-select-core"
                  onClick={() =>
                    patchDraft({
                      selectedExperimentalPresetCodes: listCoreExperimentalPresetCodes(
                        experimentalPresets.data ?? [],
                      ),
                    })
                  }
                >
                  {t("benchmarkExperimentalPresetsSelectCore")}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={running}
                  data-testid="lab-experimental-presets-clear"
                  onClick={() => patchDraft({ selectedExperimentalPresetCodes: [] })}
                >
                  {t("benchmarkExperimentalPresetsClear")}
                </Button>
              </div>
              <div
                data-testid="lab-experimental-presets-list"
                className="max-h-60 space-y-2 overflow-auto rounded-md border p-2"
              >
                {(experimentalPresets.data ?? []).map((p) => {
                  const checked = draft.selectedExperimentalPresetCodes.includes(p.code);
                  const labSelectable = isLabBenchmarkPresetSelectable(p);
                  return (
                    <label
                      key={p.code}
                      className={`block space-y-0.5 rounded border px-2 py-1 text-sm ${labSelectable ? "" : "opacity-70"}`}
                    >
                      <span className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          data-testid={`lab-experimental-preset-${p.code}`}
                          disabled={running || !labSelectable}
                          checked={checked}
                          onChange={(e) =>
                            patchDraft((prev) => ({
                              selectedExperimentalPresetCodes: e.target.checked
                                ? Array.from(new Set([...prev.selectedExperimentalPresetCodes, p.code]))
                                : prev.selectedExperimentalPresetCodes.filter((x) => x !== p.code),
                            }))
                          }
                        />
                        <span className="font-medium">
                          {p.code} — {p.label}
                        </span>
                      </span>
                      {!labSelectable ? (
                        <span className="text-destructive block text-xs" data-testid={`lab-preset-blocked-${p.code}`}>
                          {p.reasonIfUnsupported?.trim() || p.supportStatus || t("labConfigUnsupportedPreset")}
                        </span>
                      ) : p.supportStatus && p.supportStatus !== "EXECUTABLE" ? (
                        <span className="text-muted-foreground block text-xs">{p.supportStatus}</span>
                      ) : null}
                    </label>
                  );
                })}
                {experimentalPresets.isSuccess && (experimentalPresets.data?.length ?? 0) === 0 ? (
                  <p className="text-muted-foreground text-xs">{t("benchmarkExperimentalPresetsEmpty")}</p>
                ) : null}
              </div>
            </div>
          ) : null}

          {benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                data-testid="lab-benchmark-embedding-downstream"
                checked={draft.embeddingDownstreamRag}
                disabled={running}
                onChange={(e) => patchDraft({ embeddingDownstreamRag: e.target.checked })}
              />
              {t("benchmarkEmbeddingDownstreamLabel")}
            </label>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              data-testid={runButtonTestId}
              disabled={running || !canStart || otherActiveJobExists}
              onClick={() => void run()}
            >
              {runButtonLabel}
            </Button>
            {showStopButton ? (
              <Button
                type="button"
                variant="outline"
                data-testid="lab-eval-stop"
                disabled={cancelling}
                onClick={() => setCancelConfirmOpen(true)}
              >
                {cancelling ? t("jobCancelling") : t("jobStopEvaluation")}
              </Button>
            ) : null}
          </div>

          <LabJobStopConfirmDialog
            open={cancelConfirmOpen}
            onOpenChange={setCancelConfirmOpen}
            jobIdFragment={accepted?.jobId?.slice(0, 8) ?? null}
            onConfirm={cancelBackendJob}
          />

          {otherActiveJobExists ? (
            <p className="text-destructive text-sm" role="alert">
              {t("jobAlreadyRunning")}
            </p>
          ) : null}

          {err && !watchLive ? (
            <p className="text-destructive text-sm" role="alert" data-testid="lab-eval-user-error">
              {err}
            </p>
          ) : null}

          {hardBlocked ? (
            <p className="text-destructive text-sm" role="alert" data-testid="lab-dataset-blocked-demo">
              {t("datasetBlockedDemo")}
            </p>
          ) : null}

          {!hardBlocked && canStart && expectedSummary ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-expected-items-summary">
              {expectedSummary}
            </p>
          ) : null}

          {comparisonHint ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-comparison-selection-hint">
              {comparisonHint}
            </p>
          ) : null}

          {llmComparisonAvailabilityBlocked ? (
            <output
              role="status"
              data-testid="lab-llm-model-availability-blocked"
              className="block rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-950 dark:text-amber-100"
            >
              {t("labLlmBlockedByModelAvailability", {
                available: availableLlmModels.join(", ") || "—",
                missing: missingPreferredLlmTags.join(", ") || "—",
              })}
            </output>
          ) : null}

          {embeddingComparisonAvailabilityBlocked ? (
            <output
              role="status"
              data-testid="lab-embedding-model-availability-blocked"
              className="block rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-950 dark:text-amber-100"
            >
              {t("labEmbeddingBlockedByModelAvailability", {
                dimension: String(EMBEDDING_CAMPAIGN_STORE_DIMENSION),
                available: compatibleEmbeddingModels.join(", ") || "—",
                missing: missingPreferredEmbeddingTags.join(", ") || "—",
              })}
            </output>
          ) : null}

          {labRecovery.decision.kind === "cta" ? (
            <output
              role="status"
              data-testid="lab-active-job-recovery-cta"
              className="block rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-950 dark:text-amber-100"
            >
              <p className="font-medium">{t("labRecoveryMultipleTitle")}</p>
              <p className="text-muted-foreground mt-1 text-xs">{t("labRecoveryMultipleHint")}</p>
              <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
                {labRecovery.decision.candidates.map((c) => (
                  <Button
                    key={c.jobId}
                    type="button"
                    size="sm"
                    variant="secondary"
                    disabled={running}
                    data-testid={`lab-recovery-resume-${c.jobId}`}
                    onClick={() => {
                      void labRecovery.followCandidate(c);
                    }}
                  >
                    {t("labRecoveryResumeJob", { jobId: c.jobId.slice(0, 8) })}
                  </Button>
                ))}
              </div>
            </output>
          ) : null}

          {!accepted && !taskStatus ? null : (
            <LabJobPanel
              accepted={accepted}
              taskStatus={watchLive ? (liveJob.taskStatus ?? taskStatus) : taskStatus}
              queuedHint={!!accepted && !taskStatus && !liveJob.taskStatus}
              connectionState={watchLive ? liveJob.connectionState : null}
              watchElapsedSeconds={watchLive ? watchElapsedSeconds : undefined}
              recentEvents={watchLive ? liveJob.recentEvents : []}
              progressSnapshot={watchLive ? liveJob.progressSnapshot : undefined}
            />
          )}

          {showFailedJobNotice ? (
            <LabFailedJobResultsNotice
              evaluationRunId={evaluationRunId!}
              taskStatus={effectiveTaskStatus}
            />
          ) : null}

          <LabBenchmarkResultsPanel
            evaluationRunId={evaluationRunId}
            campaignId={campaignId}
            loadEnabled={showResultsPanel}
          />

          {result != null ? (
            <TechnicalDetails summary={t("benchmarkViewLogsSummary")} testId="lab-eval-raw-result">
              <pre className="bg-muted/40 max-h-[320px] overflow-auto rounded-md border border-border p-3 text-xs">
                {JSON.stringify(result, null, 2)}
              </pre>
            </TechnicalDetails>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
