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
import { LabHyperparametersForm } from "@/features/lab/components/lab-hyperparameters-form";
import { LabEmbeddingRetrievalParametersSection } from "@/features/lab/components/lab-embedding-retrieval-parameters-section";
import { LabRagIndexingMaterializationPlan } from "@/features/lab/components/lab-rag-indexing-materialization-plan";
import { LabRagTaskLlmCallout } from "@/features/lab/components/lab-rag-task-llm-callout";
import { buildLabBenchmarkRuntimeParametersPayload } from "@/features/lab/lib/lab-benchmark-runtime-payload";
import { LabModelConfigurationSection } from "@/features/lab/components/lab-model-configuration-section";
import { RagDraftIssuesAlert } from "@/features/lab/components/rag-draft-issues-alert";
import { computeLabDraftIssues } from "@/features/lab/lib/lab-draft-issues";
import { resolveRagIndexReadinessDisplay } from "@/features/lab/lib/rag-index-readiness";
import { LabJobPanel } from "@/features/lab/components/lab-job-panel";
import { LabJobStopConfirmDialog } from "@/features/lab/components/lab-job-stop-confirm-dialog";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { activeJobMatchesCard } from "@/features/lab/hooks/use-lab-active-job-resumption";
import { useAutoResumeLabJobs } from "@/features/lab/hooks/use-auto-resume-lab-jobs";
import { useLatestLabBenchmarkRun } from "@/features/lab/hooks/use-latest-lab-benchmark-run";
import {
  labJobAcceptedFromLatestRun,
  latestLabBenchmarkRunQueryKey,
  shouldFetchLatestLabRun,
  taskStatusFromLatestRun,
} from "@/features/lab/lib/lab-run-resumption";
import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useExperimentalPresetCatalog } from "@/features/lab/hooks/use-experimental-preset-catalog";
import {
  filterLabBenchmarkSelectablePresets,
  findInvalidLabPresetSelections,
  isLabBenchmarkPresetSelectable,
  listCoreExperimentalPresetCodes,
} from "@/features/lab/lib/experimental-preset-selection";
import { useLabEvaluationDraft } from "@/features/lab/hooks/use-lab-evaluation-draft";
import { useLabEvaluationModels } from "@/features/lab/hooks/use-lab-evaluation-models";
import { useLabJobLiveStream } from "@/features/lab/hooks/use-lab-job-live-stream";
import { fetchLabJobStatusOnce, pollLabJob } from "@/lib/async-task";
import {
  type LabEvaluationDraftKind,
} from "@/features/lab/lib/lab-evaluation-draft";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { isLabComparisonAvailabilityBlocked } from "@/features/lab/lib/lab-comparison-availability";
import {
  compatibleEmbeddingEvalModelNames,
  defaultEmbeddingModelId,
  defaultLlmModelId,
  labComparisonBlockedMessageKey,
  selectableEvalModelNames,
} from "@/features/lab/lib/lab-evaluation-models";
import {
  asyncTaskDtoFromSnapshot,
  type LabJobSectionKey,
  type PersistedLabJobRecord,
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
  isCorpusBlockingRun,
  isDocumentCentricCorpusBenchmark,
  selectedEmbeddingModelCount,
  selectedLlmModelCount,
} from "@/features/lab/lib/lab-eval-run-gating";
import { mapKnowledgeBaseApiError } from "@/features/lab/lib/evaluation-corpus-upload";
import { resolveDocumentCentricReadinessDisplay } from "@/features/lab/lib/evaluation-corpus-readiness-display";
import { extractTechnicalErrorCode } from "@/lib/user-facing-error-messages";
import { formatBenchmarkKindLabel, formatPresetSupportMessage } from "@/lib/product-copy";
import { createPresetCopyFn } from "@/lib/preset-copy-i18n";
import { formatLabExperimentalPresetLabel, sortPresetsByRank } from "@/features/presets/lib/preset-display";
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
import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

export type LabEvaluationRunCardProps = {
  benchmarkKind: LabEvaluationDraftKind;
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
  const tChat = useTranslations("Chat");
  const presetCopyT = createPresetCopyFn(t, tChat);
  const tHelp = useTranslations("Help");
  const queryClient = useQueryClient();
  const { data: labStatus } = useLabStatus();
  const activeJobs = useActiveLabJobs();
  const experimentalDatasets = useExperimentalDatasetsQuery();
  const experimentalPresets = useExperimentalPresetCatalog();
  const sortedExperimentalPresets = useMemo(
    () => sortPresetsByRank(experimentalPresets.data ?? []),
    [experimentalPresets.data],
  );
  const chatCatalog = useLabEvaluationModels("CHAT");
  const embeddingCatalog = useLabEvaluationModels("EMBEDDING");
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
  const cancellingRef = useRef(false);
  const latestRunAppliedRef = useRef(false);
  const [ragBaselineEmbedding, setRagBaselineEmbedding] = useState<string | null>(null);
  const resumeNonce = useLabJobSessionStore((s) => s.resumeNonce);
  const forgetWatchNonce = useLabJobSessionStore((s) => s.forgetWatchNonce);
  const sessionRecordForSection = useLabJobSessionStore((s) => s.pickLatestForSection(sectionKey));

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

  const chatCatalogModels = useMemo(() => chatCatalog.data?.models ?? [], [chatCatalog.data?.models]);
  const embeddingCatalogModels = useMemo(
    () => embeddingCatalog.data?.models ?? [],
    [embeddingCatalog.data?.models],
  );

  const availableLlmModels = useMemo(
    () => selectableEvalModelNames(chatCatalogModels),
    [chatCatalogModels],
  );
  const availableEmbeddingModels = useMemo(
    () => selectableEvalModelNames(embeddingCatalogModels),
    [embeddingCatalogModels],
  );
  const compatibleEmbeddingModels = useMemo(
    () => compatibleEmbeddingEvalModelNames(embeddingCatalogModels),
    [embeddingCatalogModels],
  );
  const selectableCompatibleEmbeddings = useMemo(
    () => selectableEvalModelNames(embeddingCatalogModels.filter((m) => m.compatibleWithCurrentVectorStore === true)),
    [embeddingCatalogModels],
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
      catalogPresets: experimentalPresets.data ?? [],
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

  const { draft, patchDraft, clearDraft, resetToRecommended, setLastEvaluationRunId, warnings, sanitizedRemovedPresets } =
    useLabEvaluationDraft(benchmarkKind, draftValidation);

  const selectedRagEmbeddingModels = useMemo(() => {
    const id = draft.embeddingModelId.trim();
    if (!id) return [];
    return embeddingCatalogModels.filter((m) => m.modelName === id);
  }, [draft.embeddingModelId, embeddingCatalogModels]);

  const selectedEmbeddingCampaignModels = useMemo(
    () => embeddingCatalogModels.filter((m) => draft.embeddingModelIds.includes(m.modelName)),
    [draft.embeddingModelIds, embeddingCatalogModels],
  );

  const llmComparisonAvailabilityBlocked = useMemo(() => {
    if (benchmarkKind !== "LLM_JUDGE_QA") return false;
    const fromList = draft.llmModelIds.map((x) => x.trim()).filter(Boolean).length;
    const selected = fromList > 0 ? fromList : draft.llmModelId.trim() ? 1 : 0;
    return isLabComparisonAvailabilityBlocked(selected, availableLlmModels.length);
  }, [benchmarkKind, availableLlmModels.length, draft.llmModelId, draft.llmModelIds]);
  const embeddingComparisonAvailabilityBlocked = useMemo(() => {
    if (benchmarkKind !== "EMBEDDING_RETRIEVAL") return false;
    const selected = draft.embeddingModelIds.map((x) => x.trim()).filter(Boolean).length;
    return isLabComparisonAvailabilityBlocked(selected, compatibleEmbeddingModels.length);
  }, [benchmarkKind, compatibleEmbeddingModels.length, draft.embeddingModelIds]);

  const catalogModelBlockReason = useMemo(() => {
    if (chatCatalog.isLoading || embeddingCatalog.isLoading) return null;
    if (chatCatalog.isError || embeddingCatalog.isError) return t("evalCatalogLoadError");
    const needsChat =
      benchmarkKind === "LLM_JUDGE_QA" ||
      benchmarkKind === "RAG_PRESET_END_TO_END" ||
      (benchmarkKind === "EMBEDDING_RETRIEVAL" && draft.embeddingDownstreamRag);
    const needsEmbedding = benchmarkKind === "EMBEDDING_RETRIEVAL" || benchmarkKind === "RAG_PRESET_END_TO_END";
    if (needsChat && chatCatalog.isSuccess && chatCatalogModels.length === 0) {
      return t("evalCatalogNoChatModels");
    }
    if (needsEmbedding && embeddingCatalog.isSuccess && !embeddingCatalog.data?.hasCompatibleEmbeddingModels) {
      return t("evalCatalogNoCompatibleEmbeddings");
    }
    return null;
  }, [
    benchmarkKind,
    chatCatalog.isError,
    chatCatalog.isLoading,
    chatCatalog.isSuccess,
    chatCatalogModels.length,
    draft.embeddingDownstreamRag,
    embeddingCatalog.data?.hasCompatibleEmbeddingModels,
    embeddingCatalog.isError,
    embeddingCatalog.isLoading,
    embeddingCatalog.isSuccess,
    t,
  ]);

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
      if (cancellingRef.current) return;
      const recoverViaPoll = async () => {
        const jobId = accepted?.jobId?.trim();
        if (!jobId) return false;
        try {
          const status = await fetchLabJobStatusOnce(jobId);
          useLabJobSessionStore.getState().patchLabJobFromTick(jobId, status);
          if (status.terminal) {
            setTaskStatus(status);
            setResult(status.result);
            setRunning(false);
            setWatchLive(false);
            return true;
          }
        } catch {
          // fall through to user-facing error
        }
        return false;
      };
      if (e instanceof ApiError && e.status === 404 && accepted?.jobId) {
        useLabJobSessionStore.getState().markLabJobStaleNotFound(accepted.jobId);
        setErr(t("jobRecoveryStaleShort"));
      } else if (
        (e instanceof ApiError && e.status === 401) ||
        (e instanceof Error && "status" in e && Number((e as Error & { status?: number }).status) === 401)
      ) {
        void recoverViaPoll().then((recovered) => {
          if (!recovered) {
            setErr(t("jobRecoverySessionExpired"));
          }
        });
      } else if (e instanceof Error) {
        setUserFacingErr(e.message || t("evalError"));
      } else {
        setUserFacingErr(t("evalError"));
      }
      if (!(e instanceof ApiError && e.status === 401)) {
        setRunning(false);
        setWatchLive(false);
      }
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

  const resumeFromPersisted = useCallback(
    async (rec: PersistedLabJobRecord) => {
      if (rec.lastStatus?.terminal === true) {
        setAccepted(rec.accepted);
        setEvaluationRunId(rec.evaluationRunId ?? null);
        const snapStatus = asyncTaskDtoFromSnapshot(rec.jobId, rec.lastStatus);
        if (snapStatus) {
          setTaskStatus(snapStatus);
          setResult(snapStatus.result);
        }
        setRunning(false);
        setWatchLive(false);
        return;
      }
      await beginLiveWatch({
        accepted: rec.accepted,
        evaluationRunId: rec.evaluationRunId,
        jobId: rec.jobId,
      });
    },
    [beginLiveWatch],
  );

  useEffect(() => {
    const rec = useLabJobSessionStore.getState().consumePendingResume(sectionKey);
    if (!rec) return;
    queueMicrotask(() => {
      void resumeFromPersisted(rec);
    });
  }, [resumeNonce, resumeFromPersisted, sectionKey]);

  useEffect(() => {
    if (forgetWatchNonce === 0) return;
    queueMicrotask(() => {
      setWatchLive(false);
      setRunning(false);
      setAccepted(null);
      setEvaluationRunId(null);
      setTaskStatus(null);
      setResult(null);
      setCancelling(false);
      setErr(null);
      latestRunAppliedRef.current = false;
      void queryClient.invalidateQueries({
        queryKey: latestLabBenchmarkRunQueryKey(benchmarkKind, activeProject?.id ?? null),
      });
    });
  }, [forgetWatchNonce, benchmarkKind, activeProject?.id, queryClient]);

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

  const latestRunQueryEnabled = shouldFetchLatestLabRun({
    activeJobsLoading: labRecovery.activeJobsLoading,
    resumptionDecisionKind: labRecovery.decision.kind,
    running,
    watchLive,
  });

  const latestRun = useLatestLabBenchmarkRun({
    benchmarkKind,
    projectId: activeProject?.id ?? null,
    enabled: latestRunQueryEnabled,
  });

  useEffect(() => {
    if (!latestRun.data || latestRunAppliedRef.current || running || watchLive) {
      return;
    }
    const dto = latestRun.data;
    latestRunAppliedRef.current = true;
    queueMicrotask(() => {
      setEvaluationRunId(dto.evaluationRunId);
      setLastEvaluationRunId(dto.evaluationRunId);
      if (dto.campaignId?.trim()) {
        setCampaignId(dto.campaignId.trim());
      }
      const status = taskStatusFromLatestRun(dto, taskTypeHint);
      setTaskStatus(status);
      if (dto.terminal) {
        const acceptedFromLatest = labJobAcceptedFromLatestRun(dto);
        if (acceptedFromLatest) {
          setAccepted(acceptedFromLatest);
        }
        setResult(dto.result);
        setRunning(false);
        setWatchLive(false);
        return;
      }
      const acceptedFromLatest = labJobAcceptedFromLatestRun(dto);
      if (acceptedFromLatest) {
        void beginLiveWatch({
          accepted: acceptedFromLatest,
          evaluationRunId: dto.evaluationRunId,
          jobId: acceptedFromLatest.jobId,
        });
      }
    });
  }, [beginLiveWatch, latestRun.data, running, watchLive, setLastEvaluationRunId, taskTypeHint]);

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

  const draftIssues = useMemo(
    () =>
      computeLabDraftIssues({
        kind: benchmarkKind,
        draft,
        warnings,
        invalidLabPresetSelections,
        needsEvaluationCorpus,
        corpusReadiness: evaluationCorpus.readiness,
        availableLlmModelIds: availableLlmModels,
        chatCatalogProvider: chatCatalog.data?.effectiveProvider,
        embeddingCatalogProvider: embeddingCatalog.data?.effectiveProvider,
      }),
    [
      benchmarkKind,
      draft,
      warnings,
      invalidLabPresetSelections,
      needsEvaluationCorpus,
      evaluationCorpus.readiness,
      availableLlmModels,
      chatCatalog.data?.effectiveProvider,
      embeddingCatalog.data?.effectiveProvider,
    ],
  );

  const draftBlocksRun = draftIssues.length > 0 || Boolean(catalogModelBlockReason);

  const hasEvaluationCorpus = Boolean(resolvedCorpusId);
  const corpusPrimaryBlocker = evaluationCorpus.readiness?.primaryBlocker ?? null;
  const documentCentricCorpus = isDocumentCentricCorpusBenchmark(benchmarkKind);
  const corpusSnapshotBlocker =
    !documentCentricCorpus &&
    evaluationCorpus.readiness?.snapshotBlocker &&
    !evaluationCorpus.readiness?.primaryBlocker &&
    evaluationCorpus.readiness?.reindexRequired &&
    !evaluationCorpus.readiness?.activeSnapshotId
      ? evaluationCorpus.readiness.snapshotBlocker
      : null;
  const corpusBlocksRun = isCorpusBlockingRun({
    needsEvaluationCorpus,
    hasEvaluationCorpus,
    corpusRunnable: evaluationCorpus.corpusRunnable,
    corpusProcessing: evaluationCorpus.corpusProcessing,
    corpusReady: evaluationCorpus.corpusReady,
    corpusIndexReady: evaluationCorpus.corpusIndexReady,
    preparingIndex: evaluationCorpus.preparingIndex,
    primaryBlocker: corpusPrimaryBlocker,
    documentCentricCorpus,
  });
  const ragCorpusNotReadyMessage = useMemo(() => {
    if (!documentCentricCorpus) {
      return null;
    }
    const display = resolveDocumentCentricReadinessDisplay(
      evaluationCorpus.readiness,
      evaluationCorpus.summary,
    );
    if (!display || display.kind !== "blocker") {
      return null;
    }
    if (display.messageKey === "labCorpusReadinessBlocked") {
      return t("labCorpusReadinessBlocked", {
        reason: mapKnowledgeBaseApiError(
          corpusPrimaryBlocker ?? "",
          t,
          evaluationCorpus.readiness?.primaryBlockerMessage ?? t("benchmarkCorpusNotReady"),
        ),
      });
    }
    return t(display.messageKey);
  }, [
    documentCentricCorpus,
    evaluationCorpus.readiness,
    evaluationCorpus.summary,
    corpusPrimaryBlocker,
    t,
  ]);

  const selectedDataset = useMemo(() => {
    const id = draft.datasetId?.trim();
    if (!id) return null;
    return compatibleRows.find((r) => r.id === id) ?? null;
  }, [compatibleRows, draft.datasetId]);

  const datasetSelectValue = draft.datasetId ?? "";

  const showStaleDatasetOption =
    Boolean(draft.datasetId) &&
    (warnings.datasetDeletedOrUnknown || warnings.datasetIncompatibleWithBenchmark);

  const recommendedDraftPartial = useMemo(() => {
    const defaultEmbedding = defaultEmbeddingModelId(embeddingCatalogModels) ?? "";
    const defaultLlm = defaultLlmModelId(chatCatalogModels) ?? availableLlmModels[0] ?? "";
    return {
      datasetId: defaultDataset?.id ?? null,
      llmModelId: defaultLlm,
      llmModelIds: benchmarkKind === "LLM_JUDGE_QA" && defaultLlm ? [defaultLlm] : [],
      embeddingModelId: defaultEmbedding,
      embeddingModelIds: defaultEmbedding ? [defaultEmbedding] : [],
      autoReindex: true,
      reuseCompatibleActiveSnapshot: true,
      benchmarkRuntimeParameters: {},
    };
  }, [availableLlmModels, benchmarkKind, chatCatalogModels, defaultDataset?.id, embeddingCatalogModels]);

  useEffect(() => {
    if (benchmarkKind !== "LLM_JUDGE_QA") return;
    if (draft.llmModelIds.length > 0) return;
    const fromLegacy = draft.llmModelId.trim();
    const defaultLlm = fromLegacy || availableLlmModels[0];
    if (!defaultLlm) return;
    patchDraft({ llmModelIds: [defaultLlm], llmModelId: "" });
  }, [benchmarkKind, draft.llmModelId, draft.llmModelIds.length, availableLlmModels, patchDraft]);

  useEffect(() => {
    if (benchmarkKind !== "EMBEDDING_RETRIEVAL" && benchmarkKind !== "RAG_PRESET_END_TO_END") return;
    const defaultEmbedding = defaultEmbeddingModelId(embeddingCatalogModels);
    if (!defaultEmbedding) return;
    const patch: Partial<typeof draft> = {};
    if (draft.embeddingModelId.trim() === "") {
      patch.embeddingModelId = defaultEmbedding;
    }
    if (benchmarkKind === "EMBEDDING_RETRIEVAL" && draft.embeddingModelIds.length === 0) {
      patch.embeddingModelIds = [defaultEmbedding];
    }
    if (Object.keys(patch).length > 0) {
      patchDraft(patch);
    }
  }, [
    benchmarkKind,
    draft.embeddingModelId,
    draft.embeddingModelIds.length,
    embeddingCatalogModels,
    patchDraft,
  ]);

  let effectiveRagBaselineEmbedding = ragBaselineEmbedding;
  if (benchmarkKind === "RAG_PRESET_END_TO_END") {
    const current = draft.embeddingModelId.trim();
    if (current && effectiveRagBaselineEmbedding === null) {
      effectiveRagBaselineEmbedding = current;
      setRagBaselineEmbedding(current);
    }
  }

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
  const hasSelectedEmbeddingModels =
    benchmarkKind === "EMBEDDING_RETRIEVAL"
      ? selectedEmbeddingModelCount(draft.embeddingModelIds, draft.embeddingModelId) > 0
      : benchmarkKind === "RAG_PRESET_END_TO_END"
        ? draft.embeddingModelId.trim() !== ""
        : true;
  const hasSelectedLlmModels =
    benchmarkKind === "LLM_JUDGE_QA"
      ? selectedLlmModelCount(draft.llmModelIds, draft.llmModelId) > 0
      : true;
  const canStart =
    hasCompatibleDataset &&
    datasetIsValid &&
    !hardBlocked &&
    !draftBlocksRun &&
    !corpusBlocksRun &&
    hasSelectedEmbeddingModels &&
    hasSelectedLlmModels;

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

  const runDisabledReason = useMemo(() => {
    if (running || canStart) return null;
    if (otherActiveJobExists) return t("evalRunDisabledJobRunning");
    if (hardBlocked) return t("datasetBlockedDemo");
    if (!hasCompatibleDataset) return t("evalRunDisabledNoDataset");
    if (!datasetIsValid) return t("evalRunDisabledDatasetInvalid");
    if (benchmarkKind === "EMBEDDING_RETRIEVAL" && !hasSelectedEmbeddingModels) {
      return t("evalRunDisabledNoEmbeddingModel");
    }
    if (benchmarkKind === "RAG_PRESET_END_TO_END" && !hasSelectedEmbeddingModels) {
      return t("evalRunDisabledNoEmbeddingModel");
    }
    if (benchmarkKind === "LLM_JUDGE_QA" && !hasSelectedLlmModels) {
      return t("evalRunDisabledNoLlmModel");
    }
    if (catalogModelBlockReason) return catalogModelBlockReason;
    if (draftBlocksRun) return t("evalRunDisabledDraftWarnings");
    if (needsEvaluationCorpus && !hasEvaluationCorpus) return t("evalRunDisabledNoCorpus");
    if (corpusBlocksRun) {
      return (
        ragCorpusNotReadyMessage ??
        (corpusPrimaryBlocker
          ? t("labCorpusReadinessBlocked", {
              reason: mapKnowledgeBaseApiError(
                corpusPrimaryBlocker,
                t,
                evaluationCorpus.readiness?.primaryBlockerMessage ?? t("benchmarkCorpusNotReady"),
              ),
            })
          : t("evalRunDisabledCorpusNotReady"))
      );
    }
    return t("evalRunDisabledUnknown");
  }, [
    benchmarkKind,
    canStart,
    catalogModelBlockReason,
    corpusBlocksRun,
    corpusPrimaryBlocker,
    datasetIsValid,
    draftBlocksRun,
    evaluationCorpus.readiness?.primaryBlockerMessage,
    hardBlocked,
    hasCompatibleDataset,
    hasEvaluationCorpus,
    hasSelectedEmbeddingModels,
    hasSelectedLlmModels,
    needsEvaluationCorpus,
    otherActiveJobExists,
    ragCorpusNotReadyMessage,
    running,
    t,
  ]);

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

  const ragIndexReadiness = useMemo(() => {
    if (benchmarkKind !== "RAG_PRESET_END_TO_END") return null;
    return resolveRagIndexReadinessDisplay({
      selectedEmbeddingModelId: draft.embeddingModelId,
      baselineEmbeddingModelId: effectiveRagBaselineEmbedding,
      autoReindex: draft.autoReindex,
      reuseCompatibleActiveSnapshot: draft.reuseCompatibleActiveSnapshot,
      readiness: evaluationCorpus.readiness,
      summary: evaluationCorpus.summary,
    });
  }, [
    benchmarkKind,
    draft.autoReindex,
    draft.embeddingModelId,
    draft.reuseCompatibleActiveSnapshot,
    effectiveRagBaselineEmbedding,
    evaluationCorpus.readiness,
    evaluationCorpus.summary,
  ]);

  const comparisonSelectionCount = useMemo(() => {
    if (benchmarkKind === "LLM_JUDGE_QA") {
      return selectedLlmModelCount(draft.llmModelIds, draft.llmModelId);
    }
    if (benchmarkKind === "EMBEDDING_RETRIEVAL") {
      return draft.embeddingModelIds.map((x) => x.trim()).filter(Boolean).length;
    }
    if (benchmarkKind === "RAG_PRESET_END_TO_END") {
      return draft.selectedExperimentalPresetCodes.length;
    }
    return 0;
  }, [benchmarkKind, draft.embeddingModelIds, draft.llmModelId, draft.llmModelIds, draft.selectedExperimentalPresetCodes]);

  const runButtonLabel = useMemo(() => {
    if (running) {
      if (documentCentricCorpus) {
        return t("labEvalPreparingDocumentsAndIndexes");
      }
      return t("evalRunning");
    }
    if (comparisonSelectionCount === 1) {
      if (benchmarkKind === "LLM_JUDGE_QA") return t("runEvalSelectedModel");
      if (benchmarkKind === "RAG_PRESET_END_TO_END") return t("runEvalSelectedPreset");
      if (benchmarkKind === "EMBEDDING_RETRIEVAL") return t("runEvalSelectedEmbedding");
    }
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
  }, [benchmarkKind, comparisonSelectionCount, documentCentricCorpus, running, t]);

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
        body.autoReindex = draft.autoReindex;
        body.allowActiveSnapshotMutation = true;
        body.reuseCompatibleActiveSnapshot = draft.reuseCompatibleActiveSnapshot;
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
      if (benchmarkKind !== "RAG_PRESET_END_TO_END") {
        if (lmList.length >= 2) {
          body.llmModelIds = lmList;
          body.campaignName = body.campaignName ?? `LLM campaign (${lmList.length})`;
        } else if (lmList.length === 1) {
          body.llmModelId = lmList[0];
        } else if (lm) {
          body.llmModelId = lm;
        }
      }
      if (emList.length >= 2) {
        body.embeddingModelIds = emList;
        body.campaignName = body.campaignName ?? `Embedding campaign (${emList.length})`;
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
      } else if (emList.length === 1) {
        body.embeddingModelId = emList[0];
        if (activeProject?.id) {
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
        body.autoReindex = true;
      }
      const runtimeParameters = buildLabBenchmarkRuntimeParametersPayload(
        benchmarkKind,
        draft.benchmarkRuntimeParameters ?? {},
      );
      if (runtimeParameters) {
        body.benchmarkRuntimeParameters = runtimeParameters;
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
        setErr(t("embeddingCampaignMissingSnapshots", { models: "-" }));
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
      cancellingRef.current = false;
      return;
    }
    const jobId = accepted.jobId;
    setCancelling(true);
    cancellingRef.current = true;
    liveJob.stop();
    setWatchLive(false);
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
    } catch (e) {
      setUserFacingErr(e instanceof Error ? e.message : t("evalError"));
      throw e;
    } finally {
      cancellingRef.current = false;
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
            <p className="text-muted-foreground text-xs">{t("labEvalTechnicalHint")}</p>
            <p className="text-muted-foreground text-xs" data-testid="lab-eval-benchmark-kind-label">
              {formatBenchmarkKindLabel(benchmarkKind, t)}
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
              documentCentric={documentCentricCorpus}
              optionalProjectId={null}
              disabled={running}
              onCorpusIdChange={(id) => patchDraft({ corpusId: id })}
              onRefreshed={() => {
                void latestRun.refetch();
              }}
            />
          ) : null}

          {benchmarkKind !== "RAG_PRESET_END_TO_END" && benchmarkKind !== "EMBEDDING_RETRIEVAL" ? (
            activeProject ? (
              <p className="text-muted-foreground text-xs">
                {t("projectScopeActive", { name: activeProject.name })}
              </p>
            ) : (
              <p className="text-muted-foreground text-xs">{t("projectScopeNone")}</p>
            )
          ) : null}

          {referenceKindsReady ? null : (
            <output
              data-testid="lab-datasets-disabled-warn"
              className="block text-amber-600 text-sm dark:text-amber-500"
            >
              {t("datasetsDisabledWarn")}
            </output>
          )}

          {corpusSnapshotBlocker && needsEvaluationCorpus ? (
            <output
              role="status"
              data-testid="lab-corpus-index-hint"
              className="block text-muted-foreground text-xs"
            >
              {mapKnowledgeBaseApiError(corpusSnapshotBlocker, t, t("userError_REINDEX_REQUIRED"))}
            </output>
          ) : null}

          {corpusBlocksRun && needsEvaluationCorpus ? (
            <output
              role="status"
              data-testid="lab-corpus-not-ready-hint"
              className="block text-muted-foreground text-xs"
            >
              {ragCorpusNotReadyMessage ??
                (corpusPrimaryBlocker
                  ? t("labCorpusReadinessBlocked", {
                      reason: mapKnowledgeBaseApiError(
                        corpusPrimaryBlocker,
                        t,
                        evaluationCorpus.readiness?.primaryBlockerMessage ?? t("benchmarkCorpusNotReady"),
                      ),
                    })
                  : t("benchmarkCorpusNotReady"))}
            </output>
          ) : null}

          {running && documentCentricCorpus ? (
            <output
              role="status"
              data-testid="lab-eval-preparation-progress"
              className="block text-muted-foreground text-xs"
            >
              {t("labEvalPreparingDocumentsAndIndexes")}
            </output>
          ) : null}

          {sanitizedRemovedPresets.length > 0 ? (
            <output
              role="status"
              data-testid="lab-draft-presets-sanitized"
              className="block rounded-md border border-sky-500/40 bg-sky-500/10 p-3 text-sky-950 text-sm dark:text-sky-100"
            >
              {t("evalDraftPresetsSanitized")}
            </output>
          ) : null}

          {catalogModelBlockReason ? (
            <output
              role="alert"
              data-testid="lab-eval-catalog-block"
              className="block rounded-md border border-destructive/40 bg-destructive/10 p-3 text-destructive text-sm"
            >
              {catalogModelBlockReason}
            </output>
          ) : null}

          <RagDraftIssuesAlert issues={draftIssues} />

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

          {benchmarkKind === "LLM_JUDGE_QA" && availableLlmModels.length > 0 ? (
            <ModelCheckboxGroup
              id={`lab-llm-model-${sectionKey}`}
              label={t("benchmarkLlmModelsToCompare")}
              availableModelIds={availableLlmModels}
              selectedIds={draft.llmModelIds}
              disabled={running}
              testIdPrefix="lab-benchmark-llm-models"
              hint={t("benchmarkLlmMultiHint")}
              onChange={(llmModelIds) => patchDraft({ llmModelIds, llmModelId: "" })}
            />
          ) : null}

          {benchmarkKind === "EMBEDDING_RETRIEVAL" && selectableCompatibleEmbeddings.length > 0 ? (
            <ModelCheckboxGroup
              id={`lab-emb-model-${sectionKey}`}
              label={t("benchmarkEmbeddingModelsToCompare")}
              availableModelIds={selectableCompatibleEmbeddings}
              selectedIds={draft.embeddingModelIds}
              disabled={running}
              testIdPrefix="lab-benchmark-embedding-models"
              hint={t("benchmarkEmbeddingMultiHint")}
              onChange={(embeddingModelIds) => patchDraft({ embeddingModelIds })}
            />
          ) : null}

          {benchmarkKind === "RAG_PRESET_END_TO_END" ? <LabRagTaskLlmCallout /> : null}

          {benchmarkKind === "RAG_PRESET_END_TO_END" && selectableCompatibleEmbeddings.length > 0 ? (
            <LabModelConfigurationSection
              sectionKey={sectionKey}
              disabled={running}
              embeddingModelId={draft.embeddingModelId}
              embeddingModelIds={selectableCompatibleEmbeddings}
              selectedEmbeddingLabel={draft.embeddingModelId.trim() || undefined}
              onEmbeddingChange={(embeddingModelId) => patchDraft({ embeddingModelId })}
            />
          ) : null}

          {benchmarkKind === "RAG_PRESET_END_TO_END" ? (
            <LabEmbeddingRetrievalParametersSection
              variant="rag"
              disabled={running}
              value={draft.benchmarkRuntimeParameters ?? {}}
              selectedModels={selectedRagEmbeddingModels}
              onChange={(benchmarkRuntimeParameters) => patchDraft({ benchmarkRuntimeParameters })}
            />
          ) : null}

          {benchmarkKind === "RAG_PRESET_END_TO_END" ? (
            <LabRagIndexingMaterializationPlan
              disabled={running}
              selectedPresetCodes={draft.selectedExperimentalPresetCodes}
              experimentalPresets={experimentalPresets.data}
              autoReindex={draft.autoReindex}
              reuseCompatibleActiveSnapshot={draft.reuseCompatibleActiveSnapshot}
              indexReadiness={ragIndexReadiness}
              onAutoReindexChange={(autoReindex) =>
                patchDraft({
                  autoReindex,
                  reuseCompatibleActiveSnapshot: autoReindex ? draft.reuseCompatibleActiveSnapshot : false,
                })
              }
              onReuseCompatibleChange={(reuseCompatibleActiveSnapshot) =>
                patchDraft({ reuseCompatibleActiveSnapshot })
              }
            />
          ) : null}

          {benchmarkKind === "LLM_JUDGE_QA" || benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
            <LabHyperparametersForm
              benchmarkKind={benchmarkKind}
              value={draft.benchmarkRuntimeParameters ?? {}}
              onChange={(benchmarkRuntimeParameters) => patchDraft({ benchmarkRuntimeParameters })}
              embeddingModels={selectedEmbeddingCampaignModels}
            />
          ) : null}

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
                {sortedExperimentalPresets.map((p) => {
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
                          {formatLabExperimentalPresetLabel(p, presetCopyT)}
                        </span>
                      </span>
                      {!labSelectable ? (
                        <span className="text-destructive block text-xs" data-testid={`lab-preset-blocked-${p.code}`}>
                          {formatPresetSupportMessage(p.supportStatus, p.reasonIfUnsupported, t)}
                        </span>
                      ) : p.supportStatus && p.supportStatus !== "EXECUTABLE" ? (
                        <span className="text-muted-foreground block text-xs">
                          {formatPresetSupportMessage(p.supportStatus, p.reasonIfUnsupported, t)}
                        </span>
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
              title={runDisabledReason ?? undefined}
              onClick={() => void run()}
            >
              {runButtonLabel}
            </Button>
            {!running && !canStart && runDisabledReason ? (
              <p
                className="text-muted-foreground w-full text-xs"
                data-testid="lab-eval-run-disabled-reason"
                role="status"
              >
                {runDisabledReason}
              </p>
            ) : null}
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
              {t(labComparisonBlockedMessageKey("CHAT", chatCatalog.data?.effectiveProvider))}
            </output>
          ) : null}

          {embeddingComparisonAvailabilityBlocked ? (
            <output
              role="status"
              data-testid="lab-embedding-model-availability-blocked"
              className="block rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-950 dark:text-amber-100"
            >
              {t(labComparisonBlockedMessageKey("EMBEDDING", embeddingCatalog.data?.effectiveProvider))}
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
              showResumeFallback={
                !watchLive &&
                !!accepted &&
                !!taskStatus &&
                taskStatus.terminal !== true &&
                ((sessionRecordForSection?.jobId === accepted.jobId &&
                  sessionRecordForSection.stoppedWatching === true) ||
                  liveJob.connectionState === "configuration_error")
              }
              onResumeLive={() => {
                if (!accepted) return;
                void beginLiveWatch({
                  accepted,
                  evaluationRunId: evaluationRunId ?? undefined,
                  jobId: accepted.jobId,
                });
                liveJob.resume();
              }}
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
