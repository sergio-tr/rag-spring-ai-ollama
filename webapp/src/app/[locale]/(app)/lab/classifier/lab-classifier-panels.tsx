"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LabJobPanel } from "@/features/lab/components/lab-job-panel";
import { classifierModelsQueryKey } from "@/features/lab/hooks/use-classifier-registry";
import { useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import { followLabJob } from "@/lib/lab-job-follow";
import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";

export function LabClassifierTrainPanel(
  props: Readonly<{ classifierOk: boolean; projectId?: string }>,
) {
  const { classifierOk, projectId } = props;
  const t = useTranslations("Lab");
  const qc = useQueryClient();

  const [modelName, setModelName] = useState("lab-model");
  const [trainFile, setTrainFile] = useState<File | null>(null);
  const [trainSync, setTrainSync] = useState(false);
  const [trainFollowMode, setTrainFollowMode] = useState<LabJobFollowMode>("poll");
  const [trainOut, setTrainOut] = useState<unknown>(null);
  const [trainErr, setTrainErr] = useState<string | null>(null);
  const [trainRunning, setTrainRunning] = useState(false);
  const [trainAccepted, setTrainAccepted] = useState<LabJobAcceptedDto | null>(null);
  const [trainStatus, setTrainStatus] = useState<AsyncTaskStatusDto | null>(null);
  const trainAbortRef = useRef<AbortController | null>(null);

  async function runTrain() {
    if (!trainFile) {
      setTrainErr(t("classifierTrainFileRequired"));
      return;
    }
    trainAbortRef.current?.abort();
    trainAbortRef.current = new AbortController();
    const signal = trainAbortRef.current.signal;
    setTrainRunning(true);
    setTrainErr(null);
    setTrainOut(null);
    setTrainAccepted(null);
    setTrainStatus(null);
    try {
      const fd = new FormData();
      fd.append("file", trainFile);
      fd.append("model_name", modelName);
      fd.append("epochs", "50");
      fd.append("batch_size", "8");

      const params = new URLSearchParams();
      if (trainSync) {
        params.set("sync", "true");
      }
      if (projectId) {
        params.set("projectId", projectId);
      }
      const q = params.toString();
      const path = `/lab/classifier/train${q ? `?${q}` : ""}`;

      if (trainSync) {
        const data = await apiFetch<unknown>(apiProductPath(path), {
          method: "POST",
          body: fd,
          signal,
        });
        setTrainOut(data);
        return;
      }

      const accepted = await apiFetch<LabJobAcceptedDto>(apiProductPath(path), {
        method: "POST",
        body: fd,
        signal,
      });
      setTrainAccepted(accepted);
      const done = await followLabJob(accepted, (s) => setTrainStatus(s), {
        mode: trainFollowMode,
        signal,
      });
      setTrainOut(done.result);
      void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        setTrainErr(t("jobCancelled"));
      } else {
        setTrainErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      setTrainRunning(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("classifierTrainSubmit")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4 rounded border"
            checked={trainSync}
            onChange={(e) => setTrainSync(e.target.checked)}
            disabled={trainRunning}
          />
          {t("syncModeLabel")}
        </label>
        {trainSync ? null : (
          <div className="flex flex-wrap gap-3 text-sm">
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="follow-train"
                checked={trainFollowMode === "poll"}
                onChange={() => setTrainFollowMode("poll")}
                disabled={trainRunning}
              />
              {t("followModePoll")}
            </label>
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="follow-train"
                checked={trainFollowMode === "sse"}
                onChange={() => setTrainFollowMode("sse")}
                disabled={trainRunning}
              />
              {t("followModeSse")}
            </label>
          </div>
        )}
        <div className="grid gap-2">
          <Label htmlFor="cmodel">{t("classifierModelName")}</Label>
          <Input id="cmodel" value={modelName} onChange={(e) => setModelName(e.target.value)} />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="cfile">{t("classifierTrainFile")}</Label>
          <Input
            id="cfile"
            type="file"
            accept=".xlsx,.xls"
            onChange={(e) => setTrainFile(e.target.files?.[0] ?? null)}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            data-testid="lab-classifier-train"
            disabled={trainRunning || !classifierOk}
            onClick={() => void runTrain()}
          >
            {trainRunning ? t("evalRunning") : t("classifierTrainSubmit")}
          </Button>
          {trainRunning ? (
            <Button type="button" variant="outline" onClick={() => trainAbortRef.current?.abort()}>
              {t("jobCancel")}
            </Button>
          ) : null}
        </div>
        {trainErr ? <p className="text-destructive text-sm">{trainErr}</p> : null}
        {!trainSync && (trainAccepted || trainStatus) ? (
          <LabJobPanel
            accepted={trainAccepted}
            taskStatus={trainStatus}
            queuedHint={!!trainAccepted && !trainStatus}
          />
        ) : null}
        {trainOut != null ? (
          <pre className="bg-muted/40 max-h-[240px] overflow-auto rounded-md border p-3 text-xs">
            {JSON.stringify(trainOut, null, 2)}
          </pre>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function LabClassifierEvalPanel(
  props: Readonly<{ classifierOk: boolean; projectId?: string }>,
) {
  const { classifierOk, projectId } = props;
  const t = useTranslations("Lab");
  const qc = useQueryClient();

  const [evalModelId, setEvalModelId] = useState("");
  const [evalFile, setEvalFile] = useState<File | null>(null);
  const [evalSync, setEvalSync] = useState(false);
  const [evalFollowMode, setEvalFollowMode] = useState<LabJobFollowMode>("poll");
  const [evalOut, setEvalOut] = useState<unknown>(null);
  const [evalErr, setEvalErr] = useState<string | null>(null);
  const [evalRunning, setEvalRunning] = useState(false);
  const [evalAccepted, setEvalAccepted] = useState<LabJobAcceptedDto | null>(null);
  const [evalStatus, setEvalStatus] = useState<AsyncTaskStatusDto | null>(null);
  const evalAbortRef = useRef<AbortController | null>(null);

  async function runEval() {
    evalAbortRef.current?.abort();
    evalAbortRef.current = new AbortController();
    const signal = evalAbortRef.current.signal;
    setEvalRunning(true);
    setEvalErr(null);
    setEvalOut(null);
    setEvalAccepted(null);
    setEvalStatus(null);
    try {
      const fd = new FormData();
      if (evalFile) {
        fd.append("file", evalFile);
      }
      const params = new URLSearchParams({ includeImages: "true" });
      if (evalSync) {
        params.set("sync", "true");
      }
      if (evalModelId.trim()) {
        params.set("modelId", evalModelId.trim());
      }
      if (projectId) {
        params.set("projectId", projectId);
      }

      const path = `/lab/classifier/evaluate?${params.toString()}`;

      if (evalSync) {
        const data = await apiFetch<unknown>(apiProductPath(path), {
          method: "POST",
          body: evalFile ? fd : undefined,
          signal,
        });
        setEvalOut(data);
        void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
        return;
      }

      const accepted = await apiFetch<LabJobAcceptedDto>(apiProductPath(path), {
        method: "POST",
        body: evalFile ? fd : undefined,
        signal,
      });
      setEvalAccepted(accepted);
      const done = await followLabJob(accepted, (s) => setEvalStatus(s), {
        mode: evalFollowMode,
        signal,
      });
      setEvalOut(done.result);
      void qc.invalidateQueries({ queryKey: classifierModelsQueryKey });
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        setEvalErr(t("jobCancelled"));
      } else {
        setEvalErr(e instanceof Error ? e.message : t("evalError"));
      }
    } finally {
      setEvalRunning(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("classifierEvalSubmit")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4 rounded border"
            checked={evalSync}
            onChange={(e) => setEvalSync(e.target.checked)}
            disabled={evalRunning}
          />
          {t("syncModeLabel")}
        </label>
        {evalSync ? null : (
          <div className="flex flex-wrap gap-3 text-sm">
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="follow-eval"
                checked={evalFollowMode === "poll"}
                onChange={() => setEvalFollowMode("poll")}
                disabled={evalRunning}
              />
              {t("followModePoll")}
            </label>
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="follow-eval"
                checked={evalFollowMode === "sse"}
                onChange={() => setEvalFollowMode("sse")}
                disabled={evalRunning}
              />
              {t("followModeSse")}
            </label>
          </div>
        )}
        <div className="grid gap-2">
          <Label htmlFor="emodel">{t("classifierEvalModelId")}</Label>
          <Input id="emodel" value={evalModelId} onChange={(e) => setEvalModelId(e.target.value)} />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="efile">{t("classifierEvalFile")}</Label>
          <Input
            id="efile"
            type="file"
            accept=".xlsx,.xls"
            onChange={(e) => setEvalFile(e.target.files?.[0] ?? null)}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" disabled={evalRunning || !classifierOk} onClick={() => void runEval()}>
            {evalRunning ? t("evalRunning") : t("classifierEvalSubmit")}
          </Button>
          {evalRunning ? (
            <Button type="button" variant="outline" onClick={() => evalAbortRef.current?.abort()}>
              {t("jobCancel")}
            </Button>
          ) : null}
        </div>
        {evalErr ? <p className="text-destructive text-sm">{evalErr}</p> : null}
        {!evalSync && (evalAccepted || evalStatus) ? (
          <LabJobPanel
            accepted={evalAccepted}
            taskStatus={evalStatus}
            queuedHint={!!evalAccepted && !evalStatus}
          />
        ) : null}
        {evalOut != null ? (
          <pre className="bg-muted/40 max-h-[240px] overflow-auto rounded-md border p-3 text-xs">
            {JSON.stringify(evalOut, null, 2)}
          </pre>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function LabClassifierClassifyPanel(props: Readonly<{ classifierOk: boolean }>) {
  const { classifierOk } = props;
  const t = useTranslations("Lab");

  const [clsQuery, setClsQuery] = useState("How many meetings?");
  const [clsModelId, setClsModelId] = useState("default");
  const [clsOut, setClsOut] = useState<unknown>(null);
  const [clsErr, setClsErr] = useState<string | null>(null);
  const [clsRunning, setClsRunning] = useState(false);

  async function runClassify() {
    setClsRunning(true);
    setClsErr(null);
    setClsOut(null);
    try {
      const data = await apiFetch<unknown>(apiProductPath("/lab/classifier/classify"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query: clsQuery, modelId: clsModelId }),
      });
      setClsOut(data);
    } catch {
      setClsErr(t("evalError"));
    } finally {
      setClsRunning(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("classifierClassifySubmit")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="grid gap-2">
          <Label htmlFor="q">{t("classifierQuery")}</Label>
          <Input id="q" value={clsQuery} onChange={(e) => setClsQuery(e.target.value)} />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="mid">{t("classifierModelIdField")}</Label>
          <Input id="mid" value={clsModelId} onChange={(e) => setClsModelId(e.target.value)} />
        </div>
        <Button type="button" disabled={clsRunning || !classifierOk} onClick={() => void runClassify()}>
          {clsRunning ? t("evalRunning") : t("classifierClassifySubmit")}
        </Button>
        {clsErr ? <p className="text-destructive text-sm">{clsErr}</p> : null}
        {clsOut != null ? (
          <pre className="bg-muted/40 max-h-[240px] overflow-auto rounded-md border p-3 text-xs">
            {JSON.stringify(clsOut, null, 2)}
          </pre>
        ) : null}
      </CardContent>
    </Card>
  );
}
