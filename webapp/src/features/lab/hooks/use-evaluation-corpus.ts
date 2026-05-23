"use client";

import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import type { EvaluationCorpusSummaryDto } from "@/types/api";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

export function evaluationCorpusQueryKey(corpusId: string | null) {
  return ["lab", "evaluation-corpus", corpusId] as const;
}

async function fetchEvaluationCorpus(id: string): Promise<EvaluationCorpusSummaryDto> {
  return apiFetch<EvaluationCorpusSummaryDto>(apiProductPath(`/lab/evaluation-corpora/${id}`));
}

export function useEvaluationCorpus(corpusId: string | null) {
  const qc = useQueryClient();

  const query = useQuery({
    queryKey: evaluationCorpusQueryKey(corpusId),
    enabled: Boolean(corpusId),
    queryFn: () => fetchEvaluationCorpus(corpusId!),
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

  const ensureCorpus = useCallback(async () => {
    if (corpusId) {
      return refresh(corpusId);
    }
    try {
      const created = await apiFetch<EvaluationCorpusSummaryDto>(apiProductPath("/lab/evaluation-corpora"), {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ name: "Lab evaluation corpus" }),
      });
      qc.setQueryData(evaluationCorpusQueryKey(created.id), created);
      return created;
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Failed to create evaluation corpus";
      throw new Error(msg, { cause: e });
    }
  }, [corpusId, qc, refresh]);

  const uploadDocument = useCallback(
    async (id: string, file: File) => {
      const form = new FormData();
      form.append("file", file);
      const data = await apiFetch<EvaluationCorpusSummaryDto>(
        apiProductPath(`/lab/evaluation-corpora/${id}/documents/upload`),
        { method: "POST", body: form },
      );
      qc.setQueryData(evaluationCorpusQueryKey(id), data);
      return data;
    },
    [qc],
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
      return data;
    },
    [qc],
  );

  const error =
    query.error instanceof ApiError
      ? query.error.message
      : query.error instanceof Error
        ? query.error.message
        : query.error
          ? "Failed to load evaluation corpus"
          : null;

  return {
    summary: corpusId ? (query.data ?? null) : null,
    loading: query.isFetching,
    error,
    refresh,
    ensureCorpus,
    uploadDocument,
    attachFromProject,
  };
}
