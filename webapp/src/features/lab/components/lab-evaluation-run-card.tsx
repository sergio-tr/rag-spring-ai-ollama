"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { HelpPopover } from "@/features/help/HelpPopover";
import { Label } from "@/components/ui/label";
import { LabBenchmarkResultsPanel } from "@/features/lab/components/lab-benchmark-results-panel";
import { LabJobPanel } from "@/features/lab/components/lab-job-panel";
import { useExperimentalDatasetsQuery } from "@/features/lab/hooks/use-experimental-datasets";
import { useExperimentalPresetCatalog } from "@/features/lab/hooks/use-experimental-preset-catalog";
import { useLabStatus } from "@/features/lab/hooks/use-lab-status";
import { useModelsCatalog } from "@/features/chat/hooks/use-models-catalog";
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
  traceLabJobStoppedWaiting,
} from "@/features/lab/lib/lab-job-trace";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { followLabJob } from "@/lib/lab-job-follow";
import type { LabJobFollowMode } from "@/lib/lab-job-follow";
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
  const experimentalDatasets = useExperimentalDatasetsQuery();
  const experimentalPresets = useExperimentalPresetCatalog();
  const modelsCatalog = useModelsCatalog();
  const activeProject = useAppStore((s) => s.activeProject);

  const [followMode, setFollowMode] = useState<LabJobFollowMode>("poll");
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<unknown>(null);
  const [accepted, setAccepted] = useState<LabJobAcceptedDto | null>(null);
  const [evaluationRunId, setEvaluationRunId] = useState<string | null>(null);
  const [campaignId, setCampaignId] = useState<string | null>(null);
  const [taskStatus, setTaskStatus] = useState<AsyncTaskStatusDto | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [stoppedWaiting, setStoppedWaiting] = useState(false);
  /** When null, the default compatible dataset is used without pinning user intent. */
  const [userDatasetId, setUserDatasetId] = useState<string | null>(null);
  const [llmModelId, setLlmModelId] = useState("");
  const [embeddingModelId, setEmbeddingModelId] = useState("");
  const [llmModelIds, setLlmModelIds] = useState<string[]>([]);
  const [embeddingModelIds, setEmbeddingModelIds] = useState<string[]>([]);
  const [embeddingDownstreamRag, setEmbeddingDownstreamRag] = useState(false);
  const [selectedExperimentalPresetCodes, setSelectedExperimentalPresetCodes] = useState<string[]>([]);
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

  const selectedDataset = useMemo(() => {
    const id = userDatasetId ?? defaultDataset?.id ?? null;
    if (!id) return null;
    return compatibleRows.find((r) => r.id === id) ?? null;
  }, [compatibleRows, defaultDataset, userDatasetId]);

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

  const hydratedEvalCardRef = useRef(false);
  useEffect(() => {
    if (hydratedEvalCardRef.current) return;
    hydratedEvalCardRef.current = true;
    const rec = useLabJobSessionStore.getState().pickLatestForSection(sectionKey);
    if (!rec || rec.staleNotFound) return;
    queueMicrotask(() => {
      setAccepted(rec.accepted);
      setEvaluationRunId(rec.evaluationRunId ?? null);
      setFollowMode(rec.followMode);
      if (rec.lastStatus) {
        setTaskStatus(asyncTaskDtoFromSnapshot(rec.jobId, rec.lastStatus));
      }
      setStoppedWaiting(rec.stoppedWatching);
    });
  }, [sectionKey]);

  const resumeNonceEvalCard = useLabJobSessionStore((s) => s.resumeNonce);

  async function resumeEvalFromPersisted(rec: PersistedLabJobRecord) {
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const signal = abortRef.current.signal;
    traceDedupeRef.current = createLabJobTraceDedupe();
    traceLabJobResumedWatching(rec.jobId, t("traceJobResumedWatching"));
    setAccepted(rec.accepted);
    setEvaluationRunId(rec.evaluationRunId ?? null);
    setFollowMode(rec.followMode);
    setRunning(true);
    setErr(null);
    setStoppedWaiting(false);
    try {
      const traceMessages = {
        queued: t("traceJobQueued"),
        running: t("traceJobRunning"),
        completed: t("traceJobCompleted"),
        failed: t("traceJobFailed"),
        cancelled: t("traceJobCancelled"),
      };
      const done = await followLabJob(
        rec.accepted,
        (s) => {
          setTaskStatus(s);
          useLabJobSessionStore.getState().patchLabJobFromTick(rec.jobId, s);
          emitLabJobTraceForTick(traceDedupeRef.current, s, rec.jobId, traceMessages);
        },
        { mode: rec.followMode, signal },
      );
      setResult(done.result);
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        if (!mountedEvalCardRef.current) return;
        setErr(t("jobCancelled"));
        setStoppedWaiting(true);
        traceLabJobStoppedWaiting(rec.jobId, t("traceStoppedWaiting"));
        useLabJobSessionStore.getState().setLabJobStoppedWatching(rec.jobId, true);
      } else if (e instanceof ApiError && e.status === 404) {
        if (!mountedEvalCardRef.current) return;
        useLabJobSessionStore.getState().markLabJobStaleNotFound(rec.jobId);
        setErr(t("jobRecoveryStaleShort"));
      } else {
        if (!mountedEvalCardRef.current) return;
        setErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      if (mountedEvalCardRef.current) {
        setRunning(false);
      }
    }
  }

  useEffect(() => {
    const rec = useLabJobSessionStore.getState().consumePendingResume(sectionKey);
    if (!rec) return;
    queueMicrotask(() => {
      void resumeEvalFromPersisted(rec);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- resumeNonce-driven only
  }, [resumeNonceEvalCard]);

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
  const canStart = hasCompatibleDataset && datasetIsValid && !hardBlocked;

  const expectedSummary = useMemo(() => {
    if (!selectedDataset) return "";
    if (benchmarkKind === "LLM_JUDGE_QA") {
      const q = selectedDataset.questionCounts.llmReaderQuestions ?? 0;
      const models = llmModelIds.length > 0 ? llmModelIds.length : 1;
      return t("benchmarkExpectedItemsLlm", { q, models, items: q * models });
    }
    if (benchmarkKind === "EMBEDDING_RETRIEVAL") {
      const q = selectedDataset.questionCounts.embeddingQueries ?? 0;
      return t("benchmarkExpectedItemsEmbedding", { q, items: q });
    }
    if (benchmarkKind === "RAG_PRESET_END_TO_END") {
      const q = selectedDataset.questionCounts.ragPresetQuestions ?? 0;
      const catalog = selectedDataset.questionCounts.presetCatalog ?? 0;
      const presets =
        selectedExperimentalPresetCodes.length > 0 ? selectedExperimentalPresetCodes.length : catalog;
      const items = catalog > 0 ? q * presets : q;
      return t("benchmarkExpectedItemsRag", { q, presets: catalog > 0 ? presets : 1, items });
    }
    return "";
  }, [benchmarkKind, llmModelIds.length, selectedDataset, selectedExperimentalPresetCodes.length, t]);

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
    traceDedupeRef.current = createLabJobTraceDedupe();
    let asyncAccepted: LabJobAcceptedDto | null = null;
    try {
      if (!selectedDataset) {
        setErr(t("benchmarkNeedsCompatibleDataset"));
        return;
      }
      if (selectedDataset.validationStatus !== "VALID") {
        setErr(t("benchmarkNeedsValidDataset"));
        return;
      }
      const body: StartBenchmarkRunRequest = {
        datasetId: selectedDataset.id,
        projectId: activeProject?.id ?? undefined,
      };
      if (benchmarkKind === "RAG_PRESET_END_TO_END" && selectedExperimentalPresetCodes.length > 0) {
        body.experimentalPresetCodes = selectedExperimentalPresetCodes;
      }
      const lm = llmModelId.trim();
      const em = embeddingModelId.trim();
      const lmList = llmModelIds.map((x) => x.trim()).filter(Boolean);
      const emList = embeddingModelIds.map((x) => x.trim()).filter(Boolean);
      if (lmList.length > 0) {
        body.llmModelIds = lmList;
        body.campaignName = body.campaignName ?? `LLM campaign (${lmList.length})`;
      } else if (lm) {
        body.llmModelId = lm;
      }
      if (emList.length > 0) {
        body.embeddingModelIds = emList;
      } else if (em) {
        body.embeddingModelId = em;
      }
      if (benchmarkKind === "EMBEDDING_RETRIEVAL") {
        body.embeddingDownstreamRag = embeddingDownstreamRag;
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
      useLabJobSessionStore.getState().upsertLabJobOnAccepted({
        accepted: acc,
        sectionKey,
        followMode,
        taskTypeHint,
        evaluationRunId: accRaw.evaluationRunId,
      });
      if (!traceDedupeRef.current.acceptedEmitted) {
        traceDedupeRef.current.acceptedEmitted = true;
        traceLabJobQueued(acc.jobId, t("traceJobQueued"));
      }
      const traceMessages = {
        queued: t("traceJobQueued"),
        running: t("traceJobRunning"),
        completed: t("traceJobCompleted"),
        failed: t("traceJobFailed"),
        cancelled: t("traceJobCancelled"),
      };
      const done = await followLabJob(
        acc,
        (s) => {
          setTaskStatus(s);
          useLabJobSessionStore.getState().patchLabJobFromTick(acc.jobId, s);
          emitLabJobTraceForTick(traceDedupeRef.current, s, acc.jobId, traceMessages);
        },
        {
          mode: followMode,
          signal,
        },
      );
      setResult(done.result);
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        if (!mountedEvalCardRef.current) return;
        setErr(t("jobCancelled"));
        setStoppedWaiting(true);
        if (asyncAccepted?.jobId) {
          traceLabJobStoppedWaiting(asyncAccepted.jobId, t("traceStoppedWaiting"));
          useLabJobSessionStore.getState().setLabJobStoppedWatching(asyncAccepted.jobId, true);
        }
      } else if (e instanceof ApiError && e.status === 404 && asyncAccepted?.jobId) {
        if (!mountedEvalCardRef.current) return;
        useLabJobSessionStore.getState().markLabJobStaleNotFound(asyncAccepted.jobId);
        setErr(t("jobRecoveryStaleShort"));
      } else {
        if (!mountedEvalCardRef.current) return;
        setErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      if (mountedEvalCardRef.current) {
        setRunning(false);
      }
    }
  }

  const showResultsPanel = taskSucceeded(taskStatus) && !!evaluationRunId?.trim();
  const availableLlmModels =
    modelsCatalog.data?.allowlist
      ?.filter((m) => m.type === "LLM" && m.inAllowlist && m.installedInOllama)
      .map((m) => m.name)
      .sort((a, b) => a.localeCompare(b)) ?? [];
  const availableEmbeddingModels =
    modelsCatalog.data?.allowlist
      ?.filter((m) => m.type === "EMBEDDING" && m.inAllowlist && m.installedInOllama)
      .map((m) => m.name)
      .sort((a, b) => a.localeCompare(b)) ?? [];

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
                      checked={followMode === "poll"}
                      onChange={() => setFollowMode("poll")}
                      disabled={running}
                    />
                    {t("followModePoll")}
                  </label>
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      name={radioGroupName}
                      checked={followMode === "sse"}
                      onChange={() => setFollowMode("sse")}
                      disabled={running}
                    />
                    {t("followModeSse")}
                  </label>
                </div>
              </div>
              <p className="text-muted-foreground leading-relaxed">{t("labAdvancedEvalHelp")}</p>
            </div>
          </details>

          <details className="rounded-md border bg-muted/20 p-3 text-xs">
            <summary className="cursor-pointer font-medium text-foreground">{t("benchmarkModelHintsSummary")}</summary>
            <p className="text-muted-foreground mt-2 leading-relaxed">{t("benchmarkPromptProfileInfo")}</p>
            {benchmarkKind === "RAG_PRESET_END_TO_END" ? (
              <p className="text-muted-foreground mt-2 leading-relaxed">{t("benchmarkRagPresetCatalogInfo")}</p>
            ) : null}
          </details>

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

          <div className="space-y-2">
            <Label htmlFor={`lab-benchmark-dataset-${sectionKey}`}>{t("benchmarkDatasetLabel")}</Label>
            <select
              id={`lab-benchmark-dataset-${sectionKey}`}
              data-testid="lab-benchmark-dataset-select"
              className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
              value={selectedDataset?.id ?? ""}
              disabled={running || compatibleRows.length === 0}
              onChange={(e) => setUserDatasetId(e.target.value || null)}
            >
              {compatibleRows.length === 0 ? (
                <option value="">{t("benchmarkDatasetPlaceholderNone")}</option>
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
              className="block text-amber-600 text-sm dark:text-amber-500"
            >
              {t("benchmarkNeedsCompatibleDataset")}
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
            (benchmarkKind === "EMBEDDING_RETRIEVAL" && embeddingDownstreamRag)) && (
            <div className="space-y-2">
              <Label htmlFor={`lab-llm-model-${sectionKey}`}>{t("benchmarkLlmModelOptional")}</Label>
              {benchmarkKind === "LLM_JUDGE_QA" && availableLlmModels.length > 0 ? (
                <>
                  <select
                    multiple
                    data-testid="lab-benchmark-llm-models-multi"
                    className="border-input bg-background ring-offset-background focus-visible:ring-ring min-h-28 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
                    value={llmModelIds}
                    disabled={running}
                    onChange={(e) =>
                      setLlmModelIds(Array.from(e.target.selectedOptions).map((o) => o.value))
                    }
                  >
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
                    value={llmModelId}
                    disabled={running || availableLlmModels.length === 0}
                    onChange={(e) => setLlmModelId(e.target.value)}
                  >
                    {availableLlmModels.length === 0 ? (
                      <option value="">{t("benchmarkLlmModelPlaceholder")}</option>
                    ) : (
                      <option value="">{t("benchmarkLlmModelPlaceholder")}</option>
                    )}
                    {availableLlmModels.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                  {availableLlmModels.length === 0 ? (
                    <output className="text-muted-foreground block text-xs">
                      Model catalog is unavailable. Configure the classifier service registry to expose LLM models.
                    </output>
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
                    value={embeddingModelIds}
                    disabled={running}
                    onChange={(e) =>
                      setEmbeddingModelIds(Array.from(e.target.selectedOptions).map((o) => o.value))
                    }
                  >
                    {availableEmbeddingModels.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                  <output className="text-muted-foreground block text-xs">
                    {t("benchmarkEmbeddingMultiUnsupportedHint")}
                  </output>
                </>
              ) : (
                <>
                  <select
                    id={`lab-emb-model-${sectionKey}`}
                    data-testid="lab-benchmark-embedding-model"
                    className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
                    value={embeddingModelId}
                    disabled={running || availableEmbeddingModels.length === 0}
                    onChange={(e) => setEmbeddingModelId(e.target.value)}
                  >
                    <option value="">{t("benchmarkEmbeddingModelPlaceholder")}</option>
                    {availableEmbeddingModels.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                  {availableEmbeddingModels.length === 0 ? (
                    <output className="text-muted-foreground block text-xs">
                      Model catalog is unavailable. Configure the classifier service registry to expose embedding models.
                    </output>
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
                    setSelectedExperimentalPresetCodes((experimentalPresets.data ?? []).map((p) => p.code))
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
                    setSelectedExperimentalPresetCodes(
                      (experimentalPresets.data ?? []).map((p) => p.code).filter((c) => /^P[0-8]$/.test(c)),
                    )
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
                  onClick={() => setSelectedExperimentalPresetCodes([])}
                >
                  {t("benchmarkExperimentalPresetsClear")}
                </Button>
              </div>
              <div
                data-testid="lab-experimental-presets-list"
                className="max-h-60 space-y-2 overflow-auto rounded-md border p-2"
              >
                {(experimentalPresets.data ?? []).map((p) => {
                  const checked = selectedExperimentalPresetCodes.includes(p.code);
                  return (
                    <label key={p.code} className="block space-y-0.5 rounded border px-2 py-1 text-sm">
                      <span className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          data-testid={`lab-experimental-preset-${p.code}`}
                          disabled={running}
                          checked={checked}
                          onChange={(e) =>
                            setSelectedExperimentalPresetCodes((prev) =>
                              e.target.checked ? [...prev, p.code] : prev.filter((x) => x !== p.code),
                            )
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
                checked={embeddingDownstreamRag}
                disabled={running}
                onChange={(e) => setEmbeddingDownstreamRag(e.target.checked)}
              />
              {t("benchmarkEmbeddingDownstreamLabel")}
            </label>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              data-testid={runButtonTestId}
              disabled={running || !canStart}
              onClick={() => void run()}
            >
              {running ? t("evalRunning") : t("runEval")}
            </Button>
            {running ? (
              <Button type="button" variant="outline" onClick={() => abortRef.current?.abort()}>
                {t("jobCancel")}
              </Button>
            ) : null}
          </div>

          {err ? (
            <p className="text-destructive text-sm" role="alert">
              {err}
            </p>
          ) : null}

          {hardBlocked ? (
            <p className="text-destructive text-sm" role="alert" data-testid="lab-dataset-blocked-demo">
              {t("datasetBlockedDemoTfg")}
            </p>
          ) : null}

          {!hardBlocked && canStart && expectedSummary ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-expected-items-summary">
              {expectedSummary}
            </p>
          ) : null}

          {!accepted && !taskStatus ? null : (
            <LabJobPanel
              accepted={accepted}
              taskStatus={taskStatus}
              queuedHint={!!accepted && !taskStatus}
              stoppedWaiting={stoppedWaiting}
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
