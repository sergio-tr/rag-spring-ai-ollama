"use client";

import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import {
  corpusHasProcessingDocuments,
  corpusHasReadyDocuments,
  extractApiErrorCode,
} from "@/features/lab/lib/evaluation-corpus-upload";
import { mergeCorpusAfterUpload } from "@/features/lab/lib/evaluation-corpus-ingestion";
import type {
  EvaluationCorpusDocumentsUploadResponseDto,
  EvaluationCorpusReadinessDto,
  EvaluationCorpusSummaryDto,
} from "@/types/api";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";

export function evaluationCorpusQueryKey(corpusId: string | null) {
  return ["lab", "evaluation-corpus", corpusId] as const;
}

export function evaluationCorpusReadinessQueryKey(corpusId: string | null) {
  return [...evaluationCorpusQueryKey(corpusId), "readiness"] as const;
}

export function evaluationCorpusAttachableDocsQueryKey(projectId: string | null) {
  return ["lab", "corpus-attachable-project-docs", projectId] as const;
}

async function fetchEvaluationCorpus(id: string): Promise<EvaluationCorpusSummaryDto> {
  return apiFetch<EvaluationCorpusSummaryDto>(apiProductPath(`/lab/evaluation-corpora/${id}`));
}

async function fetchEvaluationCorpusReadiness(id: string): Promise<EvaluationCorpusReadinessDto> {
  return apiFetch<EvaluationCorpusReadinessDto>(
    apiProductPath(`/lab/evaluation-corpora/${id}/readiness`),
  );
}

const CORPUS_POLL_MS = 2_000;
const CORPUS_POLL_MAX_ATTEMPTS = 45;

export type UseEvaluationCorpusOptions = {
  /** Called when persisted corpus id is invalid (404 KB_NOT_FOUND). */
  onCorpusStale?: () => void;
};

export type EvaluationCorpusApi = ReturnType<typeof useEvaluationCorpus>;

export type RefreshCorpusOptions = {
  /** When set, also refetches attachable project document counts for the Lab KB panel. */
  invalidateAttachableProjectId?: string | null;
};

export function useEvaluationCorpus(corpusId: string | null, options?: UseEvaluationCorpusOptions) {
  const qc = useQueryClient();
  /** Corpus id created locally when the draft prop is still null (e.g. first upload). */
  const [localCorpusId, setLocalCorpusId] = useState<string | null>(null);
  const [preparingIndex, setPreparingIndex] = useState(false);

  const effectiveCorpusId = corpusId ?? localCorpusId;

  const query = useQuery({
    queryKey: evaluationCorpusQueryKey(effectiveCorpusId),
    enabled: Boolean(effectiveCorpusId),
    queryFn: () => fetchEvaluationCorpus(effectiveCorpusId!),
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) {
        return false;
      }
      return failureCount < 1;
    },
    refetchInterval: (q) => {
      const data = q.state.data;
      if (!data || !corpusHasProcessingDocuments(data)) {
        return false;
      }
      return CORPUS_POLL_MS;
    },
  });

  const readinessQuery = useQuery({
    queryKey: evaluationCorpusReadinessQueryKey(effectiveCorpusId),
    enabled: Boolean(effectiveCorpusId),
    queryFn: () => fetchEvaluationCorpusReadiness(effectiveCorpusId!),
    refetchInterval: preparingIndex
      ? CORPUS_POLL_MS
      : (q) => {
          const summary = qc.getQueryData<EvaluationCorpusSummaryDto>(
            evaluationCorpusQueryKey(effectiveCorpusId),
          );
          if (summary && corpusHasProcessingDocuments(summary)) {
            return CORPUS_POLL_MS;
          }
          const readiness = q.state.data;
          if (readiness && !readiness.runnable && (readiness.processingCount ?? 0) > 0) {
            return CORPUS_POLL_MS;
          }
          return false;
        },
  });

  useEffect(() => {
    const err = query.error;
    if (!err || !effectiveCorpusId) return;
    if (!(err instanceof ApiError) || err.status !== 404) return;
    const code = extractApiErrorCode(err);
    if (code === "KB_NOT_FOUND" || code === "CORPUS_UNAVAILABLE") {
      qc.removeQueries({ queryKey: evaluationCorpusQueryKey(effectiveCorpusId) });
      qc.removeQueries({ queryKey: evaluationCorpusReadinessQueryKey(effectiveCorpusId) });
      queueMicrotask(() => {
        setLocalCorpusId(null);
        options?.onCorpusStale?.();
      });
    }
  }, [query.error, effectiveCorpusId, options, qc]);

  const syncCorpusQueries = useCallback(
    async (id: string) => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: evaluationCorpusQueryKey(id), refetchType: "active" }),
        qc.invalidateQueries({ queryKey: evaluationCorpusReadinessQueryKey(id), refetchType: "active" }),
      ]);
      const [summary, readiness] = await Promise.all([
        qc.fetchQuery({
          queryKey: evaluationCorpusQueryKey(id),
          queryFn: () => fetchEvaluationCorpus(id),
        }),
        qc.fetchQuery({
          queryKey: evaluationCorpusReadinessQueryKey(id),
          queryFn: () => fetchEvaluationCorpusReadiness(id),
        }),
      ]);
      return { summary, readiness };
    },
    [qc],
  );

  const refreshAll = useCallback(
    async (id: string, refreshOptions?: RefreshCorpusOptions) => {
      const result = await syncCorpusQueries(id);
      const projectId = refreshOptions?.invalidateAttachableProjectId;
      if (projectId) {
        await qc.invalidateQueries({
          queryKey: evaluationCorpusAttachableDocsQueryKey(projectId),
          refetchType: "active",
        });
      }
      return result;
    },
    [qc, syncCorpusQueries],
  );

  const refresh = useCallback(
    async (id: string, refreshOptions?: RefreshCorpusOptions) => {
      const { summary } = await refreshAll(id, refreshOptions);
      return summary;
    },
    [refreshAll],
  );

  const waitForReadyDocuments = useCallback(
    async (id: string, minDocumentCount = 0) => {
      let latest = (await refreshAll(id)).summary;
      for (let attempt = 0; attempt < CORPUS_POLL_MAX_ATTEMPTS; attempt += 1) {
        const readiness = qc.getQueryData<EvaluationCorpusReadinessDto>(
          evaluationCorpusReadinessQueryKey(id),
        );
        const hasExpectedDocuments = latest.documentCount >= minDocumentCount;
        if (
          hasExpectedDocuments &&
          corpusHasReadyDocuments(latest) &&
          (readiness?.runnable || !corpusHasProcessingDocuments(latest))
        ) {
          return latest;
        }
        if (
          hasExpectedDocuments &&
          corpusHasReadyDocuments(latest) &&
          !corpusHasProcessingDocuments(latest)
        ) {
          return latest;
        }
        await new Promise((resolve) => setTimeout(resolve, CORPUS_POLL_MS));
        latest = (await refreshAll(id)).summary;
      }
      return latest;
    },
    [qc, refreshAll],
  );

  const waitForIndexReady = useCallback(
    async (id: string) => {
      for (let attempt = 0; attempt < CORPUS_POLL_MAX_ATTEMPTS; attempt += 1) {
        const { summary, readiness } = await syncCorpusQueries(id);
        if (readiness.activeSnapshotId || !readiness.reindexRequired) {
          return { summary, readiness };
        }
        await new Promise((resolve) => setTimeout(resolve, CORPUS_POLL_MS));
      }
      return syncCorpusQueries(id);
    },
    [syncCorpusQueries],
  );

  const ensureCorpus = useCallback(async () => {
    if (effectiveCorpusId) {
      return refresh(effectiveCorpusId);
    }
    try {
      const created = await apiFetch<EvaluationCorpusSummaryDto>(apiProductPath("/lab/evaluation-corpora"), {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ name: "Lab knowledge base" }),
      });
      setLocalCorpusId(created.id);
      qc.setQueryData(evaluationCorpusQueryKey(created.id), created);
      await qc.prefetchQuery({
        queryKey: evaluationCorpusReadinessQueryKey(created.id),
        queryFn: () => fetchEvaluationCorpusReadiness(created.id),
      });
      return created;
    } catch (e) {
      const code = extractApiErrorCode(e);
      const msg = code ?? (e instanceof ApiError ? e.message : "LAB_KB_CREATE_FAILED");
      throw new Error(msg, { cause: e });
    }
  }, [effectiveCorpusId, qc, refresh]);

  const uploadDocuments = useCallback(
    async (id: string, files: File[], onProgress?: (current: number, total: number) => void) => {
      if (files.length === 0) {
        throw new Error("No files to upload");
      }
      onProgress?.(0, files.length);
      const form = new FormData();
      for (const file of files) {
        form.append("files", file);
      }
      const data = await apiFetch<EvaluationCorpusDocumentsUploadResponseDto>(
        apiProductPath(`/lab/evaluation-corpora/${id}/documents`),
        { method: "POST", body: form },
      );
      onProgress?.(files.length, files.length);
      const key = evaluationCorpusQueryKey(id);
      const previous = qc.getQueryData<EvaluationCorpusSummaryDto>(key);
      const merged = mergeCorpusAfterUpload(previous ?? data.corpus, data);
      qc.setQueryData(key, merged);
      const minDocumentCount = merged.documentCount;
      const refreshed = await waitForReadyDocuments(id, minDocumentCount);
      qc.setQueryData(key, refreshed);
      await syncCorpusQueries(id);
      return { response: data, corpus: refreshed };
    },
    [qc, syncCorpusQueries, waitForReadyDocuments],
  );

  const deleteDocument = useCallback(
    async (id: string, documentId: string) => {
      const key = evaluationCorpusQueryKey(id);
      const previous = qc.getQueryData<EvaluationCorpusSummaryDto>(key);
      if (previous) {
        const remaining = previous.documents.filter((d) => d.id !== documentId);
        qc.setQueryData<EvaluationCorpusSummaryDto>(key, {
          ...previous,
          documents: remaining,
          documentCount: remaining.length,
          readyCount: remaining.filter((d) => d.status === "READY").length,
          failedCount: remaining.filter((d) => d.status === "ERROR").length,
        });
      }
      try {
        const data = await apiFetch<EvaluationCorpusSummaryDto>(
          apiProductPath(`/lab/evaluation-corpora/${id}/documents/${documentId}`),
          { method: "DELETE" },
        );
        qc.setQueryData(key, data);
        await syncCorpusQueries(id);
        return data;
      } catch (e) {
        if (previous) {
          qc.setQueryData(key, previous);
        }
        throw e;
      }
    },
    [qc, syncCorpusQueries],
  );

  const deleteAllDocuments = useCallback(
    async (id: string) => {
      const key = evaluationCorpusQueryKey(id);
      const previous = qc.getQueryData<EvaluationCorpusSummaryDto>(key);
      const empty: EvaluationCorpusSummaryDto = previous
        ? {
            ...previous,
            documents: [],
            documentCount: 0,
            readyCount: 0,
            failedCount: 0,
          }
        : {
            id,
            name: "Lab knowledge base",
            sourceType: "UPLOADED",
            documentCount: 0,
            readyCount: 0,
            failedCount: 0,
            documents: [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          };
      qc.setQueryData(key, empty);
      try {
        const data = await apiFetch<EvaluationCorpusSummaryDto>(
          apiProductPath(`/lab/evaluation-corpora/${id}/documents`),
          { method: "DELETE" },
        );
        qc.setQueryData(key, data);
        await syncCorpusQueries(id);
        return data;
      } catch (e) {
        if (previous) {
          qc.setQueryData(key, previous);
        }
        throw e;
      }
    },
    [qc, syncCorpusQueries],
  );

  const retryDocumentIngest = useCallback(
    async (id: string, documentId: string) => {
      const data = await apiFetch<EvaluationCorpusSummaryDto>(
        apiProductPath(`/lab/evaluation-corpora/${id}/documents/${documentId}/retry-ingest`),
        { method: "POST" },
      );
      const key = evaluationCorpusQueryKey(id);
      qc.setQueryData(key, data);
      const refreshed = await waitForReadyDocuments(id);
      qc.setQueryData(key, refreshed);
      await syncCorpusQueries(id);
      return refreshed;
    },
    [qc, syncCorpusQueries, waitForReadyDocuments],
  );

  const attachFromProject = useCallback(
    async (id: string, projectId: string, documentIds: string[]) => {
      const data = await apiFetch<EvaluationCorpusSummaryDto>(
        apiProductPath(`/lab/evaluation-corpora/${id}/documents/from-project`),
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ projectId, documentIds }),
        },
      );
      qc.setQueryData(evaluationCorpusQueryKey(id), data);
      const refreshed = await waitForReadyDocuments(id);
      qc.setQueryData(evaluationCorpusQueryKey(id), refreshed);
      await syncCorpusQueries(id);
      return refreshed;
    },
    [qc, syncCorpusQueries, waitForReadyDocuments],
  );

  const prepareIndex = useCallback(
    async (id: string) => {
      setPreparingIndex(true);
      try {
        const initialReadiness = await apiFetch<EvaluationCorpusReadinessDto>(
          apiProductPath(`/lab/evaluation-corpora/${id}/prepare-index`),
          {
            method: "POST",
            headers: { Accept: "application/json" },
          },
        );
        qc.setQueryData(evaluationCorpusReadinessQueryKey(id), initialReadiness);
        const result = await waitForIndexReady(id);
        return result;
      } finally {
        setPreparingIndex(false);
      }
    },
    [qc, waitForIndexReady],
  );

  const summary = effectiveCorpusId ? (query.data ?? null) : null;
  const readiness = effectiveCorpusId ? (readinessQuery.data ?? null) : null;
  const errorCode =
    query.error instanceof ApiError ? extractApiErrorCode(query.error) : null;
  const error =
    query.error instanceof ApiError
      ? errorCode ?? query.error.message
      : query.error instanceof Error
        ? query.error.message
        : query.error
          ? "LAB_KB_LOAD_FAILED"
          : null;

  const corpusRunnable = readiness?.runnable ?? corpusHasReadyDocuments(summary);
  const indexReady = Boolean(readiness?.activeSnapshotId) || !readiness?.reindexRequired;

  return {
    summary,
    readiness,
    effectiveCorpusId,
    loading: query.isLoading,
    fetching: query.isFetching,
    readinessLoading: readinessQuery.isLoading,
    error,
    errorCode,
    corpusReady: corpusHasReadyDocuments(summary),
    corpusRunnable,
    corpusIndexReady: indexReady,
    corpusProcessing: corpusHasProcessingDocuments(summary),
    preparingIndex,
    refresh,
    refreshAll,
    ensureCorpus,
    uploadDocuments,
    attachFromProject,
    deleteDocument,
    deleteAllDocuments,
    retryDocumentIngest,
    prepareIndex,
  };
}
