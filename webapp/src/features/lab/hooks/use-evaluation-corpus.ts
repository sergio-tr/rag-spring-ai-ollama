"use client";

import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import {
  corpusHasProcessingDocuments,
  corpusHasReadyDocuments,
} from "@/features/lab/lib/evaluation-corpus-upload";
import type {
  EvaluationCorpusDocumentsUploadResponseDto,
  EvaluationCorpusSummaryDto,
} from "@/types/api";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

export function evaluationCorpusQueryKey(corpusId: string | null) {
  return ["lab", "evaluation-corpus", corpusId] as const;
}

async function fetchEvaluationCorpus(id: string): Promise<EvaluationCorpusSummaryDto> {
  return apiFetch<EvaluationCorpusSummaryDto>(apiProductPath(`/lab/evaluation-corpora/${id}`));
}

const CORPUS_POLL_MS = 2_000;
const CORPUS_POLL_MAX_ATTEMPTS = 45;

export function useEvaluationCorpus(corpusId: string | null) {
  const qc = useQueryClient();

  const query = useQuery({
    queryKey: evaluationCorpusQueryKey(corpusId),
    enabled: Boolean(corpusId),
    queryFn: () => fetchEvaluationCorpus(corpusId!),
    refetchInterval: (q) => {
      const data = q.state.data;
      if (!data || !corpusHasProcessingDocuments(data)) {
        return false;
      }
      return CORPUS_POLL_MS;
    },
  });

  const refresh = useCallback(
    async (id: string) => {
      return qc.fetchQuery({
        queryKey: evaluationCorpusQueryKey(id),
        queryFn: () => fetchEvaluationCorpus(id),
      });
    },
    [qc],
  );

  const waitForReadyDocuments = useCallback(
    async (id: string) => {
      let latest = await refresh(id);
      for (let attempt = 0; attempt < CORPUS_POLL_MAX_ATTEMPTS; attempt += 1) {
        if (corpusHasReadyDocuments(latest) || !corpusHasProcessingDocuments(latest)) {
          return latest;
        }
        await new Promise((resolve) => setTimeout(resolve, CORPUS_POLL_MS));
        latest = await refresh(id);
      }
      return latest;
    },
    [refresh],
  );

  const ensureCorpus = useCallback(async () => {
    if (corpusId) {
      return refresh(corpusId);
    }
    try {
      const created = await apiFetch<EvaluationCorpusSummaryDto>(apiProductPath("/lab/evaluation-corpora"), {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ name: "Lab knowledge base" }),
      });
      qc.setQueryData(evaluationCorpusQueryKey(created.id), created);
      return created;
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Failed to create evaluation corpus";
      throw new Error(msg, { cause: e });
    }
  }, [corpusId, qc, refresh]);

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
      qc.setQueryData(evaluationCorpusQueryKey(id), data.corpus);
      const refreshed = await waitForReadyDocuments(id);
      return { response: data, corpus: refreshed };
    },
    [qc, waitForReadyDocuments],
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
      return waitForReadyDocuments(id);
    },
    [qc, waitForReadyDocuments],
  );

  const summary = corpusId ? (query.data ?? null) : null;
  const error =
    query.error instanceof ApiError
      ? query.error.message
      : query.error instanceof Error
        ? query.error.message
        : query.error
          ? "Failed to load evaluation corpus"
          : null;

  return {
    summary,
    loading: query.isFetching,
    error,
    corpusReady: corpusHasReadyDocuments(summary),
    corpusProcessing: corpusHasProcessingDocuments(summary),
    refresh,
    ensureCorpus,
    uploadDocuments,
    attachFromProject,
  };
}
