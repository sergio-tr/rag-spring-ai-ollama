"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { HelpPopover } from "@/features/help/HelpPopover";
import { Label } from "@/components/ui/label";
import { LabEvaluationCorpusPanel } from "@/features/lab/components/lab-evaluation-corpus-panel";
import { LabBenchmarkResultsPanel } from "@/features/lab/components/lab-benchmark-results-panel";
import { LabJobPanel } from "@/features/lab/components/lab-job-panel";
import { useActiveLabJobs } from "@/features/lab/hooks/use-active-lab-jobs";
import { activeJobMatchesCard, useLabActiveJobRecovery } from "@/features/lab/hooks/use-lab-active-job-recovery";
import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useExperimentalPresetCatalog } from "@/features/lab/hooks/use-experimental-preset-catalog";
import { useLabEvaluationDraft } from "@/features/lab/hooks/use-lab-evaluation-draft";
import { useLabJobLiveEvents } from "@/features/lab/hooks/use-lab-job-live-events";
import type { LabEvaluationDraftKind } from "@/features/lab/lib/lab-evaluation-draft";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useModelsByType } from "@/features/chat/hooks/use-models-by-type";
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
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { fetchLabJobStatusOnce } from "@/lib/async-task";
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
import { useEffect, useMemo, useRef, useState } from "react";

export type LabEvaluationRunCardProps = {
  benchmarkKind: BenchmarkKind;
  sectionKey: LabJobSectionKey;
  taskTypeHint: string;
  cardTitle: string;
  cardDescription: string;
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

function taskSucceeded(taskStatus: AsyncTaskStatusDto | null): boolean {
  return taskStatus?.terminal === true && taskStatus.status?.toUpperCase() === "SUCCEEDED";
}

/**
 * Shared lab benchmark runner (canonical async POST + poll/SSE) for LLM, embedding retrieval, and RAG preset benchmarks.
 */
export function LabEvaluationRunCard({
  benchmarkKind,
  sectionKey,
  taskTypeHint,
  cardTitle,
  cardDescription,
  runButtonTestId,
  radioGroupName,
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
  const [stoppedWaiting, setStoppedWaiting] = useState(false);
  const [watchLive, setWatchLive] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const traceDedupeRef = useRef(createLabJobTraceDedupe());
  const mountedEvalCardRef = useRef(true);
  /** Prevents duplicate backend-driven auto-follow for the same job (incl. React strict double-invoke). */
  const backendAutoFollowHandledRef = useRef<string | null>(null);
  /** R4: one-shot probe per session job when active list is empty (reset when list becomes non-empty). */
  const r4SessionProbeKeyRef = useRef<string | null>(null);

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
  const availableEmbeddingModels = useMemo(
    () => embeddingModels.data?.map((m) => m.modelId).sort((a, b) => a.localeCompare(b)) ?? [],
    [embeddingModels.data],
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

  const liveJob = useLabJobLiveEvents({
    accepted,
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
      setWatchLive(false);
      setStoppedWaiting(false);
    },
    onStreamError: (e) => {
      if (!mountedEvalCardRef.current) return;
      if (e instanceof ApiError && e.status === 404 && accepted?.jobId) {
        useLabJobSessionStore.getState().markLabJobStaleNotFound(accepted.jobId);
        setErr(t("jobRecoveryStaleShort"));
      } else if (e instanceof Error) {
        setErr(e.message || t("evalError"));
      } else {
        setErr(t("evalError"));
      }
      setRunning(false);
      setWatchLive(false);
    },
  });

  const sessionRecords = useLabJobSessionStore((s) => s.records);
  const clearOtherLabJobsForSection = useLabJobSessionStore((s) => s.clearOtherLabJobsForSection);
  const labRecovery = useLabActiveJobRecovery({
    sectionKey,
    benchmarkKind,
    activeProjectId: activeProject?.id ?? null,
    draftFollowMode: draft.followMode,
    backendActiveJobs: activeJobs.data ?? null,
    backendActiveJobsLoading: !activeJobs.isFetched,
    backendActiveJobsError: activeJobs.isError ? activeJobs.error : null,
    sessionRecords,
  });

  const autoFollowJobId =
    labRecovery.decision.kind === "auto_follow" ? labRecovery.decision.candidate.jobId : null;

  const draftBlocksRun =
    warnings.datasetDeletedOrUnknown ||
    warnings.datasetIncompatibleWithBenchmark ||
    warnings.llmModelInvalid ||
    warnings.llmModelsInvalid.length > 0 ||
    warnings.embeddingModelInvalid ||
    warnings.embeddingModelsInvalid.length > 0 ||
    warnings.presetsUnknown.length > 0;

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
    }),
    [defaultDataset?.id],
  );

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

  const hydratedJobUiRef = useRef(false);
  useEffect(() => {
    if (hydratedJobUiRef.current) return;
    hydratedJobUiRef.current = true;

    const rec = useLabJobSessionStore.getState().pickLatestForSection(sectionKey);
    if (!rec || rec.staleNotFound) return;
    queueMicrotask(() => {
      setAccepted(rec.accepted);
      setEvaluationRunId(rec.evaluationRunId ?? null);
      patchDraft({ followMode: rec.followMode });
      if (rec.lastStatus) {
        setTaskStatus(asyncTaskDtoFromSnapshot(rec.jobId, rec.lastStatus));
      }
      setStoppedWaiting(rec.stoppedWatching);
    });
  }, [patchDraft, sectionKey]);

  const resumeNonceEvalCard = useLabJobSessionStore((s) => s.resumeNonce);

  async function beginLiveWatch(rec: PersistedLabJobRecord) {
    traceDedupeRef.current = createLabJobTraceDedupe();
    traceLabJobResumedWatching(rec.jobId, t("traceJobResumedWatching"));
    setAccepted(rec.accepted);
    setEvaluationRunId(rec.evaluationRunId ?? null);
    patchDraft({ followMode: rec.followMode });
    setRunning(true);
    setErr(null);
    setStoppedWaiting(false);
    setWatchLive(true);
  }

  async function resumeEvalFromPersisted(rec: PersistedLabJobRecord) {
    await beginLiveWatch(rec);
  }

  useEffect(() => {
    const rec = useLabJobSessionStore.getState().consumePendingResume(sectionKey);
    if (!rec) return;
    queueMicrotask(() => {
      void resumeEvalFromPersisted(rec);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- resumeNonce-driven only
  }, [resumeNonceEvalCard]);

  const autoFollowCandidate =
    labRecovery.decision.kind === "auto_follow" ? labRecovery.decision.candidate : null;
  const autoFollowCandidateJobId = autoFollowCandidate?.jobId ?? "";
  const autoFollowCandidateEvaluationRunId = autoFollowCandidate?.evaluationRunId ?? "";

  useEffect(() => {
    if (!autoFollowJobId || !autoFollowCandidate) return;
    const candidate = autoFollowCandidate;
    if (backendAutoFollowHandledRef.current === autoFollowJobId) {
      if (running) return;
      if (taskStatus?.id === autoFollowJobId && taskStatus.terminal) return;
      return;
    }
    if (running && accepted?.jobId && accepted.jobId !== autoFollowJobId) return;

    backendAutoFollowHandledRef.current = autoFollowJobId;
    void (async () => {
      try {
        clearOtherLabJobsForSection(sectionKey, candidate.jobId);
        useLabJobSessionStore.getState().upsertLabJobOnAccepted({
          accepted: candidate.accepted,
          sectionKey,
          followMode: candidate.resolvedFollowMode,
          taskTypeHint,
          evaluationRunId: candidate.evaluationRunId,
        });
        const status = await fetchLabJobStatusOnce(candidate.jobId);
        if (!mountedEvalCardRef.current) return;
        useLabJobSessionStore.getState().patchLabJobFromTick(candidate.jobId, status);
        const rec = useLabJobSessionStore.getState().pickLatestForSection(sectionKey);
        if (!rec) return;
        await beginLiveWatch(rec);
      } catch (e) {
        backendAutoFollowHandledRef.current = null;
        if (!mountedEvalCardRef.current) return;
        if (e instanceof ApiError && e.status === 404) {
          useLabJobSessionStore.getState().markLabJobStaleNotFound(candidate.jobId);
          setErr(t("jobRecoveryStaleShort"));
        } else if (!(e instanceof DOMException && e.name === "AbortError")) {
          setErr(e instanceof Error ? e.message : t("evalError"));
        }
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- resumeEvalFromPersisted is intentionally stable-by-closure; narrow deps avoid session-record churn.
  }, [
    autoFollowJobId,
    labRecovery.decision.kind,
    autoFollowCandidate,
    autoFollowCandidateJobId,
    autoFollowCandidateEvaluationRunId,
    running,
    accepted?.jobId,
    taskStatus?.id,
    taskStatus?.terminal,
    sectionKey,
    taskTypeHint,
    clearOtherLabJobsForSection,
    t,
  ]);

  useEffect(() => {
    if (!activeJobs.isFetched) return;
    const jobs = activeJobs.data ?? [];
    if (jobs.length > 0) {
      r4SessionProbeKeyRef.current = null;
      return;
    }
    const rec = useLabJobSessionStore.getState().pickLatestForSection(sectionKey);
    if (!rec?.accepted?.jobId) return;
    if (rec.staleNotFound || rec.lastStatus?.terminal) return;
    const probeKey = `${sectionKey}:${rec.jobId}`;
    if (r4SessionProbeKeyRef.current === probeKey) return;
    r4SessionProbeKeyRef.current = probeKey;
    let cancelled = false;
    void (async () => {
      try {
        const s = await fetchLabJobStatusOnce(rec.jobId);
        if (cancelled || !mountedEvalCardRef.current) return;
        useLabJobSessionStore.getState().patchLabJobFromTick(rec.jobId, s);
      } catch (e) {
        if (cancelled || !mountedEvalCardRef.current) return;
        if (e instanceof ApiError && e.status === 404) {
          useLabJobSessionStore.getState().markLabJobStaleNotFound(rec.jobId);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [activeJobs.isFetched, activeJobs.data, sectionKey]);

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
  const canStart = hasCompatibleDataset && datasetIsValid && !hardBlocked && !draftBlocksRun;

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
    setStoppedWaiting(false);
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
        if (!draft.corpusId) {
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
        projectId: activeProject?.id ?? undefined,
        corpusId: draft.corpusId ?? undefined,
      };
      if (benchmarkKind === "RAG_PRESET_END_TO_END" && draft.selectedExperimentalPresetCodes.length > 0) {
        body.experimentalPresetCodes = draft.selectedExperimentalPresetCodes;
      }
      if (benchmarkKind === "RAG_PRESET_END_TO_END") {
        body.autoReindex = true;
        body.allowActiveSnapshotMutation = true;
        body.reuseCompatibleActiveSnapshot = true;
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
        followMode: draft.followMode,
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
      } else {
        if (!mountedEvalCardRef.current) return;
        setErr(e instanceof Error ? e.message : t("evalError"));
        setRunning(false);
        setWatchLive(false);
      }
    }
  }

  async function cancelBackendJob() {
    liveJob.stop();
    setWatchLive(false);
    if (!accepted?.jobId) {
      abortRef.current?.abort();
      setRunning(false);
      return;
    }
    try {
      await apiFetch<void>(apiProductPath(`/lab/jobs/${accepted.jobId}/cancel`), { method: "POST" });
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }

  const showResultsPanel =
    taskSucceeded(watchLive ? (liveJob.taskStatus ?? taskStatus) : taskStatus) && !!evaluationRunId?.trim();

  const staleDatasetRow =
    draft.datasetId != null
      ? (experimentalDatasets.data ?? []).find((d) => d.id === draft.datasetId)
      : undefined;

  return (
    <div className="space-y-4">
      <p className="text-muted-foreground border-l-4 border-primary/40 pl-3 text-sm">{t("adrDisclaimer")}</p>
      {introBeforeCard ?? null}

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
          <div className="min-w-0 flex-1 space-y-1.5">
            <CardTitle>{cardTitle}</CardTitle>
            <CardDescription>{cardDescription}</CardDescription>
          </div>
          <HelpPopover
            triggerAriaLabel={tHelp("labEvalRunnerTriggerLabel")}
            title={tHelp("labEvalRunnerTitle")}
            message={tHelp("labEvalRunnerMessage")}
            details={tHelp("labEvalRunnerDetails")}
          />
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <details className="text-xs">
            <summary className="cursor-pointer text-muted-foreground">{t("labAdvancedOptionsSummary")}</summary>
            <div className="mt-2 space-y-3">
              <div className="flex flex-col gap-1">
                <span className="text-muted-foreground text-xs">{t("followModeLabel")}</span>
                <div className="flex gap-3 text-sm">
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      name={radioGroupName}
                      checked={draft.followMode === "poll"}
                      onChange={() => patchDraft({ followMode: "poll" })}
                      disabled={running}
                    />
                    {t("followModePoll")}
                  </label>
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      name={radioGroupName}
                      checked={draft.followMode === "sse"}
                      onChange={() => patchDraft({ followMode: "sse" })}
                      disabled={running}
                    />
                    {t("followModeSse")}
                  </label>
                </div>
              </div>
              <p className="text-muted-foreground leading-relaxed">{t("labAdvancedEvalHelp")}</p>
              <details className="rounded-md border bg-muted/20 p-2">
                <summary className="cursor-pointer text-muted-foreground">{t("labDeveloperDetailsSummary")}</summary>
                <p className="text-muted-foreground mt-2 leading-relaxed">
                  {t("labDeveloperEvalEndpoint", { kind: benchmarkKind })}
                </p>
              </details>
            </div>
          </details>

          <details className="rounded-md border bg-muted/20 p-3 text-xs">
            <summary className="cursor-pointer font-medium text-foreground">{t("benchmarkModelHintsSummary")}</summary>
            <p className="text-muted-foreground mt-2 leading-relaxed">{t("benchmarkPromptProfileInfo")}</p>
            {benchmarkKind === "RAG_PRESET_END_TO_END" ? (
              <p className="text-muted-foreground mt-2 leading-relaxed">{t("benchmarkRagPresetCatalogInfo")}</p>
            ) : null}
          </details>

          {benchmarkKind === "RAG_PRESET_END_TO_END" || benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
            <LabEvaluationCorpusPanel
              corpusId={draft.corpusId}
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

          {draft.lastEvaluationRunId ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-eval-draft-last-run">
              {t("evalDraftLastRunHint", { runId: draft.lastEvaluationRunId })}
            </p>
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
                  {(row.name ?? t("experimentalDatasetUnnamed")) +
                    ` · ${datasetOriginLabel(row, t)}` +
                    ` · ${row.experimentalDatasetType}` +
                    (row.validationStatus ? ` · ${row.validationStatus}` : "") +
                    ` · ${datasetCountsLabel(row, t)}`}
                </option>
              ))}
            </select>
            <p className="text-muted-foreground text-xs">
              {t("benchmarkDatasetCompatibilityHint", { kind: datasetCompatibilityLabel(benchmarkKind, t) })}
            </p>
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
            <div className="rounded-md border bg-muted/20 p-3 text-xs" data-testid="lab-selected-dataset-details">
              <p className="font-medium">{t("datasetDetailsTitle")}</p>
              <div className="text-muted-foreground mt-1 space-y-1">
                <p>
                  <span className="font-medium">{t("datasetDetailsName")}:</span>{" "}
                  {selectedDataset.name ?? t("experimentalDatasetUnnamed")}
                </p>
                <p>
                  <span className="font-medium">{t("datasetDetailsType")}:</span> {selectedDataset.experimentalDatasetType}
                </p>
                <p>
                  <span className="font-medium">{t("datasetDetailsOrigin")}:</span>{" "}
                  {datasetOriginLabel(selectedDataset, t)}
                </p>
                <p>
                  <span className="font-medium">{t("datasetDetailsCounts")}:</span> {datasetCountsLabel(selectedDataset, t)}
                </p>
                <p>
                  <span className="font-medium">{t("datasetDetailsStatus")}:</span>{" "}
                  {selectedDataset.validationStatus ?? t("experimentalDatasetValidationStatusUnknown")}
                </p>
              </div>
              {!datasetIsValid ? (
                <output className="mt-2 block text-amber-600 dark:text-amber-500" data-testid="lab-dataset-invalid-warn">
                  {t("benchmarkNeedsValidDataset")}
                </output>
              ) : null}
            </div>
          ) : null}

          {(benchmarkKind === "LLM_JUDGE_QA" ||
            benchmarkKind === "RAG_PRESET_END_TO_END" ||
            (benchmarkKind === "EMBEDDING_RETRIEVAL" && draft.embeddingDownstreamRag)) && (
            <div className="space-y-2">
              <Label htmlFor={`lab-llm-model-${sectionKey}`}>{t("benchmarkLlmModelOptional")}</Label>
              {benchmarkKind === "LLM_JUDGE_QA" && availableLlmModels.length > 0 ? (
                <>
                  <select
                    multiple
                    data-testid="lab-benchmark-llm-models-multi"
                    className="border-input bg-background ring-offset-background focus-visible:ring-ring min-h-28 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
                    value={draft.llmModelIds}
                    disabled={running}
                    onChange={(e) =>
                      patchDraft({
                        llmModelIds: Array.from(e.target.selectedOptions).map((o) => o.value),
                      })
                    }
                  >
                    {draft.llmModelIds
                      .filter((id) => id.trim() !== "" && !availableLlmModels.includes(id))
                      .map((name) => (
                        <option key={`stale-${name}`} value={name} disabled>
                          {name}
                        </option>
                      ))}
                    {availableLlmModels.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                  <p className="text-muted-foreground text-xs">{t("benchmarkLlmMultiHint")}</p>
                </>
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
              <Label htmlFor={`lab-emb-model-${sectionKey}`}>{t("benchmarkEmbeddingModelOptional")}</Label>
              {benchmarkKind === "EMBEDDING_RETRIEVAL" && availableEmbeddingModels.length > 0 ? (
                <>
                  <select
                    multiple
                    data-testid="lab-benchmark-embedding-models-multi"
                    className="border-input bg-background ring-offset-background focus-visible:ring-ring min-h-28 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
                    value={draft.embeddingModelIds}
                    disabled={running}
                    onChange={(e) =>
                      patchDraft({
                        embeddingModelIds: Array.from(e.target.selectedOptions).map((o) => o.value),
                      })
                    }
                  >
                    {draft.embeddingModelIds
                      .filter((id) => id.trim() !== "" && !availableEmbeddingModels.includes(id))
                      .map((name) => (
                        <option key={`stale-${name}`} value={name} disabled>
                          {name}
                        </option>
                      ))}
                    {availableEmbeddingModels.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                  <output className="text-muted-foreground block text-xs">
                    {t("benchmarkEmbeddingMultiHint")}
                  </output>
                </>
              ) : (
                <>
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
                      selectedExperimentalPresetCodes: (experimentalPresets.data ?? []).map((p) => p.code),
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
                      selectedExperimentalPresetCodes: (experimentalPresets.data ?? [])
                        .map((p) => p.code)
                        .filter((c) => /^P[0-8]$/.test(c)),
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
                  return (
                    <label key={p.code} className="block space-y-0.5 rounded border px-2 py-1 text-sm">
                      <span className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          data-testid={`lab-experimental-preset-${p.code}`}
                          disabled={running}
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
                      <span className="text-muted-foreground block text-xs">
                        {p.supportStatus}
                        {p.reasonIfUnsupported ? ` · ${p.reasonIfUnsupported}` : ""}
                        {!p.supported ? ` · ${t("ragPresetExplainerNotSupported")}` : ""}
                      </span>
                    </label>
                  );
                })}
                {experimentalPresets.isSuccess && (experimentalPresets.data?.length ?? 0) === 0 ? (
                  <p className="text-muted-foreground text-xs">{t("benchmarkExperimentalPresetsEmpty")}</p>
                ) : null}
              </div>
              <p className="text-muted-foreground text-xs">{t("benchmarkExperimentalPresetsHint")}</p>
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
            {running ? (
              <Button type="button" variant="outline" onClick={() => void cancelBackendJob()}>
                {t("jobCancel")}
              </Button>
            ) : null}
          </div>

          {otherActiveJobExists ? (
            <p className="text-destructive text-sm" role="alert">
              {t("jobAlreadyRunning")}
            </p>
          ) : null}

          {err && !watchLive ? (
            <p className="text-destructive text-sm" role="alert">
              {err}
            </p>
          ) : null}

          {watchLive && liveJob.connectionState === "reconnecting" ? (
            <output role="status" className="block text-amber-700 text-sm dark:text-amber-300">
              {t("jobUiReconnecting")}
            </output>
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
                      clearOtherLabJobsForSection(sectionKey, c.jobId);
                      useLabJobSessionStore.getState().upsertLabJobOnAccepted({
                        accepted: c.accepted,
                        sectionKey,
                        followMode: c.resolvedFollowMode,
                        taskTypeHint,
                        evaluationRunId: c.evaluationRunId,
                      });
                      useLabJobSessionStore.getState().requestResumeLabJob(sectionKey, c.jobId);
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
              stoppedWaiting={stoppedWaiting}
              connectionState={watchLive ? liveJob.connectionState : null}
              onResumeLive={watchLive ? () => liveJob.resume() : undefined}
            />
          )}

          <LabBenchmarkResultsPanel
            evaluationRunId={evaluationRunId}
            campaignId={campaignId}
            loadEnabled={showResultsPanel}
          />

          {result != null ? (
            <details className="text-xs">
              <summary className="cursor-pointer text-muted-foreground">{t("benchmarkRawTaskResultSummary")}</summary>
              <pre className="bg-muted/40 mt-2 max-h-[320px] overflow-auto rounded-md border border-border p-3 text-xs">
                {JSON.stringify(result, null, 2)}
              </pre>
            </details>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
