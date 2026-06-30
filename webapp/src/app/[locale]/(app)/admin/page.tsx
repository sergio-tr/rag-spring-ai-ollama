"use client";

import { apiFetch, apiProductPath } from "@/lib/api-client";
import { pollLabJob } from "@/lib/async-task";
import { AdminLlmCatalogSection } from "@/features/admin/components/AdminLlmCatalogSection";
import { useLlmCatalog, llmCatalogQueryKey } from "@/features/admin/hooks/use-llm-catalog";
import { catalogRowKey, groupCatalogByCapability } from "@/features/admin/lib/llm-catalog-admin";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { LabJobAcceptedDto, LlmCatalogModelDto } from "@/types/api";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useMemo, useRef, useState } from "react";

export default function AdminHomePage() {
  const t = useTranslations("Admin");
  const tLab = useTranslations("Lab");
  const qc = useQueryClient();
  const catalogQ = useLlmCatalog(true);
  const [rowMessage, setRowMessage] = useState<string | null>(null);
  const [rowErr, setRowErr] = useState<string | null>(null);
  const [busyRowKey, setBusyRowKey] = useState<string | null>(null);
  const [pullModel, setPullModel] = useState("");
  const [pullErr, setPullErr] = useState<string | null>(null);
  const [pullOk, setPullOk] = useState<string | null>(null);
  const [pullRunning, setPullRunning] = useState(false);
  const [pullProgress, setPullProgress] = useState<string | null>(null);
  const pullAbortRef = useRef<AbortController | null>(null);

  const grouped = useMemo(
    () => groupCatalogByCapability(catalogQ.data?.models ?? []),
    [catalogQ.data?.models],
  );

  const catalogT = {
    catalogEmptySection: t("catalogEmptySection"),
    catalogRuntimeUnavailable: t("catalogRuntimeUnavailable"),
    catalogRuntimeUnavailableOpenAI: t("catalogRuntimeUnavailableOpenAI"),
    catalogVectorIncompatible: t("catalogVectorIncompatible"),
    catalogIndexingDisabledTooltip: t("catalogIndexingDisabledTooltip"),
    catalogPull: t("catalogPull"),
    catalogProvider: t("catalogProvider"),
    catalogProviderRemoteApi: t("catalogProviderRemoteApi"),
    catalogProviderLocalServer: t("catalogProviderLocalServer"),
    catalogCapability: t("catalogCapability"),
    catalogModelName: t("catalogModelName"),
    catalogDisplayName: t("catalogDisplayName"),
    catalogConfigured: t("catalogConfigured"),
    catalogConfiguredYes: t("catalogConfiguredYes"),
    catalogConfiguredNo: t("catalogConfiguredNo"),
    catalogSource: t("catalogSource"),
    catalogRuntimeStatus: t("catalogRuntimeStatus"),
    catalogRuntimeStatusConfigured: t("catalogRuntimeStatusConfigured"),
    catalogRuntimeStatusNotProbed: t("catalogRuntimeStatusNotProbed"),
    catalogRuntimeStatusNotProbedOpenAI: t("catalogRuntimeStatusNotProbedOpenAI"),
    catalogRuntimeStatusAvailable: t("catalogRuntimeStatusAvailable"),
    catalogRuntimeStatusUnavailable: t("catalogRuntimeStatusUnavailable"),
    catalogRuntimeStatusUnavailableOpenAI: t("catalogRuntimeStatusUnavailableOpenAI"),
    catalogRuntimeStatusProbeFailed: t("catalogRuntimeStatusProbeFailed"),
    catalogSourceLitellmConfigured: t("catalogSourceLitellmConfigured"),
    catalogSourceConfiguredCatalog: t("catalogSourceConfiguredCatalog"),
    catalogSourceOllamaLive: t("catalogSourceOllamaLive"),
    catalogSourceUnknown: t("catalogSourceUnknown"),
    catalogSourceProperties: t("catalogSourceProperties"),
    catalogRuntimeNotProbedNote: t("catalogRuntimeNotProbedNote"),
    catalogEmbeddingDimensions: t("catalogEmbeddingDimensions"),
    catalogVectorCompatible: t("catalogVectorCompatible"),
    catalogVectorCompatibleYes: t("catalogVectorCompatibleYes"),
    catalogVectorCompatibleNo: t("catalogVectorCompatibleNo"),
    catalogVectorCompatibleNa: t("catalogVectorCompatibleNa"),
    modelAvailable: t("modelAvailable"),
    modelMissing: t("modelMissing"),
  };

  const invalidateCatalog = () => void qc.invalidateQueries({ queryKey: llmCatalogQueryKey });

  async function runPull(modelName: string, onProgress?: (text: string) => void) {
    pullAbortRef.current?.abort();
    pullAbortRef.current = new AbortController();
    const signal = pullAbortRef.current.signal;
    const accepted = await apiFetch<LabJobAcceptedDto>(apiProductPath("/admin/models/pull"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model: modelName.trim() }),
      signal,
    });
    onProgress?.(tLab("jobQueued"));
    const done = await pollLabJob(accepted.jobId, (s) => onProgress?.(s.progressText ?? ""), { signal });
    const r = done.result;
    if (r && typeof r["status"] === "string" && typeof r["model"] === "string") {
      return `${r["status"]}: ${r["model"]}`;
    }
    return t("pullDone");
  }

  async function runGlobalPull() {
    setPullErr(null);
    setPullOk(null);
    setPullProgress(null);
    setPullRunning(true);
    try {
      const msg = await runPull(pullModel, setPullProgress);
      setPullOk(msg);
      invalidateCatalog();
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        setPullErr(tLab("jobCancelled"));
      } else {
        setPullErr(e instanceof Error ? e.message : t("pullError"));
      }
    } finally {
      setPullRunning(false);
    }
  }

  async function handleRowPull(row: LlmCatalogModelDto) {
    setRowMessage(null);
    setRowErr(null);
    const key = catalogRowKey(row);
    setBusyRowKey(key);
    try {
      await runPull(row.modelName);
      setRowMessage(t("pullDone"));
      invalidateCatalog();
    } catch {
      setRowErr(t("pullError"));
    } finally {
      setBusyRowKey(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
      </div>

      <Card data-testid="admin-models-card">
        <CardHeader>
          <CardTitle>{t("catalogTitle")}</CardTitle>
          <CardDescription>{t("catalogDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          {catalogQ.isError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("catalogLoadError")}
            </p>
          ) : null}
          {rowErr ? (
            <p className="text-destructive text-sm" role="alert">
              {rowErr}
            </p>
          ) : null}
          {rowMessage ? <p className="text-muted-foreground text-sm">{rowMessage}</p> : null}

          <AdminLlmCatalogSection
            title={t("catalogSectionLlm")}
            rows={grouped.chat}
            emptyLabel={t("catalogEmptySection")}
            t={catalogT}
            busyKey={busyRowKey}
            onPull={(row) => void handleRowPull(row)}
          />

          <AdminLlmCatalogSection
            title={t("catalogSectionEmbedding")}
            rows={grouped.embedding}
            emptyLabel={t("catalogEmptySection")}
            t={catalogT}
            busyKey={busyRowKey}
            onPull={(row) => void handleRowPull(row)}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("pullTitle")}</CardTitle>
          <CardDescription>{t("pullDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          <Label htmlFor="pullm">{t("pullModel")}</Label>
          <Input id="pullm" value={pullModel} onChange={(e) => setPullModel(e.target.value)} />
          <div className="flex flex-wrap gap-2">
            <Button type="button" disabled={!pullModel.trim() || pullRunning} onClick={() => void runGlobalPull()}>
              {pullRunning ? tLab("evalRunning") : t("pullSubmit")}
            </Button>
            {pullRunning ? (
              <Button type="button" variant="outline" onClick={() => pullAbortRef.current?.abort()}>
                {tLab("jobCancel")}
              </Button>
            ) : null}
          </div>
          {pullErr ? <p className="text-destructive text-sm">{pullErr}</p> : null}
          {pullProgress != null && pullProgress !== "" ? (
            <pre className="bg-muted/40 max-h-[120px] overflow-auto rounded-md border p-3 text-xs whitespace-pre-wrap">
              {pullProgress}
            </pre>
          ) : null}
          {pullOk ? <p className="text-muted-foreground text-sm">{pullOk}</p> : null}
        </CardContent>
      </Card>
    </div>
  );
}
