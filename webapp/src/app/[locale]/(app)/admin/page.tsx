"use client";

import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { pollLabJob } from "@/lib/async-task";
import type { AdminAllowlistEntryDto, LabJobAcceptedDto } from "@/types/api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

const allowlistKey = ["admin", "allowlist"] as const;

export default function AdminHomePage() {
  const t = useTranslations("Admin");
  const tLab = useTranslations("Lab");
  const qc = useQueryClient();
  const [health, setHealth] = useState<unknown>(null);
  const [healthErr, setHealthErr] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [newType, setNewType] = useState<"LLM" | "EMBEDDING">("LLM");
  const [newInList, setNewInList] = useState(true);
  const [pullModel, setPullModel] = useState("");
  const [pullErr, setPullErr] = useState<string | null>(null);
  const [pullOk, setPullOk] = useState<string | null>(null);
  const [pullRunning, setPullRunning] = useState(false);
  const [pullProgress, setPullProgress] = useState<string | null>(null);
  const pullAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const h = await apiFetch<unknown>("/api/admin/health");
        if (!cancelled) setHealth(h);
      } catch {
        if (!cancelled) setHealthErr(t("forbiddenOrError"));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [t]);

  const listQ = useQuery({
    queryKey: allowlistKey,
    queryFn: () => apiFetch<AdminAllowlistEntryDto[]>("/api/admin/allowlist"),
    retry: false,
  });

  const createM = useMutation({
    mutationFn: () =>
      apiFetch<AdminAllowlistEntryDto>("/api/admin/allowlist", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: newName.trim(), type: newType, inAllowlist: newInList }),
      }),
    onSuccess: () => {
      setNewName("");
      void qc.invalidateQueries({ queryKey: allowlistKey });
    },
  });

  const deleteM = useMutation({
    mutationFn: (id: string) => apiFetch(`/api/admin/allowlist/${id}`, { method: "DELETE" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: allowlistKey }),
  });

  async function runPull() {
    pullAbortRef.current?.abort();
    pullAbortRef.current = new AbortController();
    const signal = pullAbortRef.current.signal;
    setPullErr(null);
    setPullOk(null);
    setPullProgress(null);
    setPullRunning(true);
    try {
      const accepted = await apiFetch<LabJobAcceptedDto>("/api/admin/ollama/pull", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ model: pullModel.trim() }),
        signal,
      });
      setPullProgress(tLab("jobQueued"));
      const done = await pollLabJob(accepted.jobId, (s) => setPullProgress(s.progressText ?? ""), {
        signal,
      });
      const r = done.result;
      if (
        r &&
        typeof r["status"] === "string" &&
        typeof r["model"] === "string"
      ) {
        setPullOk(`${r["status"]}: ${r["model"]}`);
      } else {
        setPullOk(t("pullDone"));
      }
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

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-semibold text-2xl tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{t("healthCardTitle")}</CardTitle>
          <CardDescription>{t("healthCardDescription")}</CardDescription>
        </CardHeader>
        <CardContent>
          {healthErr && (
            <p className="text-destructive text-sm" role="alert">
              {healthErr}
            </p>
          )}
          {!healthErr && health != null ? (
            <pre className="bg-muted/40 max-h-[200px] overflow-auto rounded-md border border-border p-3 text-xs">
              {JSON.stringify(health, null, 2)}
            </pre>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("allowlistTitle")}</CardTitle>
          <CardDescription>{t("allowlistDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {listQ.isError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("allowlistLoadError")}
            </p>
          ) : null}
          <ul className="flex flex-col gap-2 text-sm">
            {(listQ.data ?? []).map((row) => (
              <li
                key={row.id}
                className="bg-muted/40 flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2"
              >
                <span>
                  <span className="font-medium">{row.name}</span>{" "}
                  <span className="text-muted-foreground">
                    ({row.type}) {row.inAllowlist ? "✓" : "—"}
                  </span>
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={deleteM.isPending}
                  onClick={() => deleteM.mutate(row.id)}
                >
                  {t("allowlistDelete")}
                </Button>
              </li>
            ))}
          </ul>
          <div className="grid gap-2 border-t border-border pt-4">
            <Label htmlFor="aname">{t("allowlistName")}</Label>
            <Input id="aname" value={newName} onChange={(e) => setNewName(e.target.value)} />
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
                checked={newInList}
                onChange={(e) => setNewInList(e.target.checked)}
              />
              {t("allowlistInList")}
            </label>
            <Button
              type="button"
              disabled={createM.isPending || !newName.trim()}
              onClick={() => createM.mutate()}
            >
              {t("allowlistCreate")}
            </Button>
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
            <Button type="button" disabled={!pullModel.trim() || pullRunning} onClick={() => void runPull()}>
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
