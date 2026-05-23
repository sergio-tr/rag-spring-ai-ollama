"use client";

import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import type { EvaluationCorpusSummaryDto } from "@/types/api";
import { useCallback, useEffect, useState } from "react";

export function useEvaluationCorpus(corpusId: string | null) {
  const [summary, setSummary] = useState<EvaluationCorpusSummaryDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async (id: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiFetch<EvaluationCorpusSummaryDto>(apiProductPath(`/lab/evaluation-corpora/${id}`));
      setSummary(data);
      return data;
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Failed to load evaluation corpus";
      setError(msg);
      throw e;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!corpusId) {
      setSummary(null);
      return;
    }
    void refresh(corpusId).catch(() => undefined);
  }, [corpusId, refresh]);

  const ensureCorpus = useCallback(async () => {
    if (corpusId) {
      return refresh(corpusId);
    }
    setLoading(true);
    setError(null);
    try {
      const created = await apiFetch<EvaluationCorpusSummaryDto>(apiProductPath("/lab/evaluation-corpora"), {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ name: "Lab evaluation corpus" }),
      });
      setSummary(created);
      return created;
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Failed to create evaluation corpus";
      setError(msg);
      throw e;
    } finally {
      setLoading(false);
    }
  }, [corpusId, refresh]);

  const uploadDocument = useCallback(
    async (id: string, file: File) => {
      const form = new FormData();
      form.append("file", file);
      const data = await apiFetch<EvaluationCorpusSummaryDto>(
        apiProductPath(`/lab/evaluation-corpora/${id}/documents/upload`),
        { method: "POST", body: form },
      );
      setSummary(data);
      return data;
    },
    [],
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
      setSummary(data);
      return data;
    },
    [],
  );

  return { summary, loading, error, refresh, ensureCorpus, uploadDocument, attachFromProject };
}
