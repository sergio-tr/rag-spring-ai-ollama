"use client";

import { apiFetch, apiProductPath } from "@/lib/api-client";
import { adminModelCheckSummary, adminModelUserMessage } from "@/lib/admin-model-errors";
import { pollLabJob } from "@/lib/async-task";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type {
  AdminModelCheckRequest,
  AdminModelCheckResponse,
  AdminModelDeleteResponse,
  AdminModelEntryDto,
  AdminModelUpdateRequest,
  AdminModelUpsertRequest,
  LabJobAcceptedDto,
} from "@/types/api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useMemo, useRef, useState } from "react";

const modelsKey = ["admin", "models"] as const;

function parseApiErrorCode(err: unknown): string | null {
  if (!(err instanceof Error)) return null;
  const match = err.message.match(/\b(MODEL_[A-Z_]+|OLLAMA_UNAVAILABLE)\b/);
  return match?.[1] ?? null;
}

function ModelTableSection({
  title,
  rows,
  emptyLabel,
  t,
  onProbe,
  onPull,
  onToggle,
  onDelete,
  busyId,
}: {
  title: string;
  rows: AdminModelEntryDto[];
  emptyLabel: string;
  t: ReturnType<typeof useTranslations<"Admin">>;
  onProbe: (row: AdminModelEntryDto) => void;
  onPull: (row: AdminModelEntryDto) => void;
  onToggle: (row: AdminModelEntryDto) => void;
  onDelete: (row: AdminModelEntryDto) => void;
  busyId: string | null;
}) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="font-medium text-sm">{title}</h3>
      {rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">{emptyLabel}</p>
      ) : (
        <ul className="flex flex-col gap-2 text-sm">
          {rows.map((row) => (
            <li
              key={row.id}
              data-testid={`admin-model-row-${row.modelType}-${row.modelId}`}
              className="bg-muted/40 flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2"
            >
              <div className="min-w-0 flex-1">
                <div className="font-medium">{row.displayName ?? row.modelId}</div>
                <div className="text-muted-foreground text-xs">
                  {row.modelId} · {row.enabled ? t("allowlistInList") : t("allowlistDisable")} ·{" "}
                  {row.available ? t("modelAvailable") : t("modelMissing")}
                  {row.lastPullStatus ? ` · ${row.lastPullStatus}` : ""}
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={busyId === row.id}
                  onClick={() => onProbe(row)}
                >
                  {t("allowlistActionProbe")}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={busyId === row.id}
                  onClick={() => onPull(row)}
                >
                  {t("allowlistActionPull")}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={busyId === row.id}
                  onClick={() => onToggle(row)}
                >
                  {row.enabled ? t("allowlistDisable") : t("allowlistEnable")}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="destructive"
                  disabled={busyId === row.id}
                  onClick={() => onDelete(row)}
                >
                  {t("allowlistActionDelete")}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function AdminHomePage() {
  const t = useTranslations("Admin");
  const tLab = useTranslations("Lab");
  const qc = useQueryClient();
  const [newModelId, setNewModelId] = useState("");
  const [newDisplayName, setNewDisplayName] = useState("");
  const [newType, setNewType] = useState<"LLM" | "EMBEDDING">("LLM");
  const [newEnabled, setNewEnabled] = useState(true);
  const [pullIfMissing, setPullIfMissing] = useState(false);
  const [checkRes, setCheckRes] = useState<AdminModelCheckResponse | null>(null);
  const [checkErr, setCheckErr] = useState<string | null>(null);
  const [rowMessage, setRowMessage] = useState<string | null>(null);
  const [rowErr, setRowErr] = useState<string | null>(null);
  const [busyRowId, setBusyRowId] = useState<string | null>(null);
  const [pullModel, setPullModel] = useState("");
  const [pullErr, setPullErr] = useState<string | null>(null);
  const [pullOk, setPullOk] = useState<string | null>(null);
  const [pullRunning, setPullRunning] = useState(false);
  const [pullProgress, setPullProgress] = useState<string | null>(null);
  const pullAbortRef = useRef<AbortController | null>(null);

  const listQ = useQuery({
    queryKey: modelsKey,
    queryFn: () => apiFetch<AdminModelEntryDto[]>(apiProductPath("/admin/models")),
    retry: false,
  });

  const grouped = useMemo(() => {
    const rows = listQ.data ?? [];
    return {
      llm: rows.filter((r) => r.modelType === "LLM"),
      embedding: rows.filter((r) => r.modelType === "EMBEDDING"),
    };
  }, [listQ.data]);

  const invalidate = () => void qc.invalidateQueries({ queryKey: modelsKey });

  const checkM = useMutation({
    mutationFn: (body: AdminModelCheckRequest) =>
      apiFetch<AdminModelCheckResponse>(apiProductPath("/admin/models/check"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: (data) => {
      setCheckRes(data);
      setCheckErr(null);
    },
    onError: (e) => setCheckErr(adminModelUserMessage(parseApiErrorCode(e), t)),
  });

  const upsertM = useMutation({
    mutationFn: (body: AdminModelUpsertRequest) =>
      apiFetch<AdminModelEntryDto>(apiProductPath("/admin/models"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: () => {
      setNewModelId("");
      setNewDisplayName("");
      setCheckRes(null);
      invalidate();
    },
  });

  const updateM = useMutation({
    mutationFn: ({ id, body }: { id: string; body: AdminModelUpdateRequest }) =>
      apiFetch<AdminModelEntryDto>(apiProductPath(`/admin/models/${id}`), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: () => invalidate(),
  });

  const deleteM = useMutation({
    mutationFn: (id: string) =>
      apiFetch<AdminModelDeleteResponse>(apiProductPath(`/admin/models/${id}`), {
        method: "DELETE",
      }),
    onSuccess: (data) => {
      setRowMessage(
        data.outcome === "DISABLED" ? t("allowlistDeleteDisabled") : t("allowlistDeleteRemoved"),
      );
      setRowErr(null);
      invalidate();
    },
    onError: () => setRowErr(t("allowlistDeleteError")),
  });

  const reprobeM = useMutation({
    mutationFn: (id: string) =>
      apiFetch<AdminModelCheckResponse>(apiProductPath(`/admin/models/${id}/check`), {
        method: "POST",
      }),
  });

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
      invalidate();
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

  async function handleRowPull(row: AdminModelEntryDto) {
    setRowMessage(null);
    setRowErr(null);
    setBusyRowId(row.id);
    try {
      await runPull(row.modelId);
      setRowMessage(t("pullDone"));
      invalidate();
    } catch {
      setRowErr(t("pullError"));
    } finally {
      setBusyRowId(null);
    }
  }

  async function handleRowProbe(row: AdminModelEntryDto) {
    setRowMessage(null);
    setRowErr(null);
    setBusyRowId(row.id);
    try {
      const res = await reprobeM.mutateAsync(row.id);
      setRowMessage(adminModelCheckSummary(res, t));
      invalidate();
    } catch (e) {
      setRowErr(adminModelUserMessage(parseApiErrorCode(e), t));
    } finally {
      setBusyRowId(null);
    }
  }

  async function handleRowToggle(row: AdminModelEntryDto) {
    setRowMessage(null);
    setRowErr(null);
    setBusyRowId(row.id);
    try {
      await updateM.mutateAsync({ id: row.id, body: { enabled: !row.enabled } });
      setRowMessage(row.enabled ? t("allowlistDisable") : t("allowlistEnable"));
    } catch {
      setRowErr(t("allowlistToggleError"));
    } finally {
      setBusyRowId(null);
    }
  }

  async function handleRowDelete(row: AdminModelEntryDto) {
    setRowMessage(null);
    setRowErr(null);
    setBusyRowId(row.id);
    try {
      await deleteM.mutateAsync(row.id);
    } finally {
      setBusyRowId(null);
    }
  }

  const canAddEnabled =
    !newEnabled ||
    (checkRes?.existsLocal === true &&
      (newType !== "EMBEDDING" || checkRes.embeddingProbeOk === true));

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
      </div>

      <Card data-testid="admin-models-card">
        <CardHeader>
          <CardTitle>{t("allowlistTitle")}</CardTitle>
          <CardDescription>{t("allowlistDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          {listQ.isError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("allowlistLoadError")}
            </p>
          ) : null}
          {rowErr ? (
            <p className="text-destructive text-sm" role="alert">
              {rowErr}
            </p>
          ) : null}
          {rowMessage ? <p className="text-muted-foreground text-sm">{rowMessage}</p> : null}

          <ModelTableSection
            title={t("allowlistSectionLlm")}
            rows={grouped.llm}
            emptyLabel={t("allowlistEmptySection")}
            t={t}
            busyId={busyRowId}
            onProbe={(row) => void handleRowProbe(row)}
            onPull={(row) => void handleRowPull(row)}
            onToggle={(row) => void handleRowToggle(row)}
            onDelete={(row) => void handleRowDelete(row)}
          />

          <ModelTableSection
            title={t("allowlistSectionEmbedding")}
            rows={grouped.embedding}
            emptyLabel={t("allowlistEmptySection")}
            t={t}
            busyId={busyRowId}
            onProbe={(row) => void handleRowProbe(row)}
            onPull={(row) => void handleRowPull(row)}
            onToggle={(row) => void handleRowToggle(row)}
            onDelete={(row) => void handleRowDelete(row)}
          />

          <div className="grid gap-2 border-t border-border pt-4">
            <Label htmlFor="aname">{t("allowlistName")}</Label>
            <Input id="aname" value={newModelId} onChange={(e) => setNewModelId(e.target.value)} />
            <Label htmlFor="dname">{t("modelDisplayName")}</Label>
            <Input id="dname" value={newDisplayName} onChange={(e) => setNewDisplayName(e.target.value)} />
            <Label htmlFor="atype">{t("allowlistType")}</Label>
            <select
              id="atype"
              className="border-input bg-background h-9 rounded-md border px-2 text-sm"
              value={newType}
              onChange={(e) => setNewType(e.target.value as "LLM" | "EMBEDDING")}
            >
              <option value="LLM">LLM</option>
              <option value="EMBEDDING">EMBEDDING</option>
            </select>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={newEnabled}
                onChange={(e) => setNewEnabled(e.target.checked)}
              />
              {t("allowlistInList")}
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={pullIfMissing}
                onChange={(e) => setPullIfMissing(e.target.checked)}
              />
              {t("pullIfMissing")}
            </label>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={checkM.isPending || !newModelId.trim()}
                onClick={() =>
                  checkM.mutate({
                    modelId: newModelId.trim(),
                    modelType: newType,
                    pullIfMissing,
                  })
                }
              >
                {t("modelCheck")}
              </Button>
              <Button
                type="button"
                disabled={upsertM.isPending || !newModelId.trim() || !canAddEnabled}
                onClick={() =>
                  upsertM.mutate({
                    modelId: newModelId.trim(),
                    displayName: newDisplayName.trim() ? newDisplayName.trim() : null,
                    modelType: newType,
                    enabled: newEnabled,
                    pullIfMissing,
                    tags: [],
                  })
                }
              >
                {t("allowlistCreate")}
              </Button>
            </div>
            {checkErr ? (
              <p className="text-destructive text-sm" role="alert">
                {checkErr}
              </p>
            ) : null}
            {checkRes ? (
              <div className="bg-muted/40 rounded-md border border-border p-3 text-sm">
                <p className="font-medium">{t("probeResultTitle")}</p>
                <p>{adminModelCheckSummary(checkRes, t)}</p>
                {checkRes.matchedLocalIds.length > 0 ? (
                  <p className="text-muted-foreground mt-1 text-xs">
                    {checkRes.matchedLocalIds.join(", ")}
                  </p>
                ) : null}
              </div>
            ) : null}
          </div>
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
