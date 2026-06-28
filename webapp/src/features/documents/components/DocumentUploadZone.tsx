"use client";

import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { ApiError, apiFetch, apiProductPath, getSafeApiErrorMessage } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import type { ProjectDocumentDto } from "@/types/api";
import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";

type DocumentUploadZoneProps = {
  projectId: string | undefined;
};

function extractJsonDetailFromUnknown(parsed: unknown): string | null {
  if (parsed === null || parsed === undefined) return null;
  if (typeof parsed === "string") return parsed.trim() || null;
  if (typeof parsed !== "object") return null;
  const o = parsed as Record<string, unknown>;
  const nested = o.error as Record<string, unknown> | undefined;
  const candidates = [o.detail, o.message, o.title, nested?.detail, nested?.message];
  for (const c of candidates) {
    if (typeof c === "string" && c.length > 0) return c;
  }
  return null;
}

function uploadErrorDetail(err: unknown, t: (key: string) => string): string | null {
  if (!err) return null;
  if (err instanceof ApiError) {
    if (err.status === 503) return t("uploadErrorOllamaNotReady");
    if (err.status === 409) return t("uploadErrorDuplicate");
    if (err.status === 401 || err.status === 403) return t("uploadErrorUnauthorized");
    if (err.status === 502 || err.status === 503 || err.status === 504) return t("uploadErrorGateway");
    if (err.status === 0 && err.meta?.kind === "network") return t("uploadErrorGateway");
    const fromDetails = extractJsonDetailFromUnknown(err.meta?.details);
    if (fromDetails) return fromDetails;

    const msg = err.message?.trim() ?? "";
    if (msg.startsWith("{") || msg.startsWith("[")) {
      try {
        const parsed = JSON.parse(msg) as unknown;
        return extractJsonDetailFromUnknown(parsed);
      } catch {
        return msg || null;
      }
    }
    return getSafeApiErrorMessage(err);
  }
  return err instanceof Error ? err.message : String(err);
}

type UploadPhase = "queued" | "uploading" | "ingesting" | "ready" | "error" | "stalled";

type UploadItem = {
  clientId: string;
  file: File;
  fileName: string;
  phase: UploadPhase;
  docId: string | null;
  status: string | null;
  chunkCount: number | null;
  errorMessage: string | null;
  /** Raw error from upload/poll/reindex; used to generate a friendly message. */
  lastError: unknown;
  startedAtMs: number;
};

function nextClientId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `upload-${Date.now().toString(36)}`;
}

async function sleep(ms: number): Promise<void> {
  await new Promise<void>((resolve) => setTimeout(resolve, ms));
}

function ActiveIndexProfileCallout({ projectId }: Readonly<{ projectId: string | undefined }>) {
  const t = useTranslations("Documents");
  const profile = useProjectIndexProfile(projectId ?? null);
  if (!projectId) return null;
  return (
    <div className="rounded-lg border bg-muted/20 px-3 py-2 text-xs">
      <p className="font-medium">{t("activeIndexProfileTitle")}</p>
      {profile.isLoading ? (
        <p className="text-muted-foreground mt-1">{t("activeIndexProfileLoading")}</p>
      ) : profile.isError ? (
        <p className="text-destructive mt-1" role="alert">
          {t("activeIndexProfileError")}
        </p>
      ) : profile.data ? (
        <ul className="text-muted-foreground mt-2 grid gap-1 sm:grid-cols-2">
          <li>
            <span className="font-medium text-foreground">strategy:</span> {profile.data.materializationStrategy ?? "—"}
          </li>
          <li>
            <span className="font-medium text-foreground">metadata:</span>{" "}
            {profile.data.metadataEnabled ? "on" : "off"}
          </li>
          <li className="sm:col-span-2">
            <span className="font-medium text-foreground">embedding:</span>{" "}
            {profile.data.embeddingModelId?.trim() ? profile.data.embeddingModelId : "—"}
          </li>
          <li>
            <span className="font-medium text-foreground">chunk:</span> {profile.data.chunkMaxChars}
          </li>
        </ul>
      ) : null}
      <p className="text-muted-foreground mt-2">{t("indexChangeRequiresReindex")}</p>
    </div>
  );
}

async function pollDocumentStatus(options: {
  documentId: string;
  timeoutMs: number;
  intervalMs: number;
  onTick: (dto: ProjectDocumentDto) => void;
}): Promise<ProjectDocumentDto> {
  const started = Date.now();
  while (true) {
    const dto = await apiFetch<ProjectDocumentDto>(apiProductPath(`/documents/${options.documentId}/status`));
    options.onTick(dto);
    if (dto.status === "READY" || dto.status === "ERROR") return dto;
    if (Date.now() - started > options.timeoutMs) return dto;
    await sleep(options.intervalMs);
  }
}

export function DocumentUploadZone({ projectId }: Readonly<DocumentUploadZoneProps>) {
  const t = useTranslations("Documents");
  const tRef = useRef(t);
  tRef.current = t;
  const qc = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [drag, setDrag] = useState(false);
  const [items, setItems] = useState<UploadItem[]>([]);
  const itemsRef = useRef<UploadItem[]>([]);
  const drainingRef = useRef(false);
  const cancelledRef = useRef(false);

  const inProgressCount = useMemo(
    () => items.filter((i) => i.phase === "queued" || i.phase === "uploading" || i.phase === "ingesting").length,
    [items],
  );

  const uploadSingle = useCallback(async (file: File): Promise<ProjectDocumentDto> => {
    if (!projectId) throw new Error("no_project");
    const fd = new FormData();
    fd.append("file", file);
    return apiFetch<ProjectDocumentDto>(apiProductPath(`/projects/${projectId}/documents`), {
      method: "POST",
      body: fd,
    });
  }, [projectId]);

  async function reindexSingle(documentId: string, file: File): Promise<ProjectDocumentDto> {
    const fd = new FormData();
    fd.append("file", file);
    return apiFetch<ProjectDocumentDto>(apiProductPath(`/documents/${documentId}/reindex`), {
      method: "POST",
      body: fd,
    });
  }

  const onFiles = useCallback(
    (files: FileList | null) => {
      if (!files?.length || !projectId) return;
      const next: UploadItem[] = Array.from(files).map((f) => ({
        clientId: nextClientId(),
        file: f,
        fileName: f.name,
        phase: "queued",
        docId: null,
        status: null,
        chunkCount: null,
        errorMessage: null,
        lastError: null,
        startedAtMs: Date.now(),
      }));
      setItems((prev) => [...next, ...prev].slice(0, 30));
    },
    [projectId],
  );

  const activateFilePicker = useCallback(() => {
    inputRef.current?.click();
  }, []);

  itemsRef.current = items;

  const invalidateProjectDocuments = useCallback(() => {
    if (projectId) {
      void qc.invalidateQueries({ queryKey: ["project-documents", projectId] });
    }
  }, [projectId, qc]);

  const processOneQueued = useCallback(
    async (item: UploadItem) => {
      if (!projectId || cancelledRef.current) return;
      const live = itemsRef.current.find((x) => x.clientId === item.clientId);
      if (!live || live.phase !== "queued") return;
      setItems((prev) =>
        prev.map((x) => (x.clientId === item.clientId ? { ...x, phase: "uploading", lastError: null } : x)),
      );
      try {
        const created = await uploadSingle(item.file);
        if (cancelledRef.current) return;
        const terminalPhase =
          created.status === "READY" ? "ready" : created.status === "ERROR" ? "error" : "ingesting";
        setItems((prev) =>
          prev.map((x) =>
            x.clientId === item.clientId
              ? {
                  ...x,
                  phase: terminalPhase,
                  docId: created.id,
                  status: created.status,
                  chunkCount: created.chunkCount ?? null,
                  errorMessage: created.errorMessage ?? null,
                }
              : x,
          ),
        );
        invalidateProjectDocuments();
        if (created.status !== "READY" && created.status !== "ERROR") {
          const terminal = await pollDocumentStatus({
            documentId: created.id,
            timeoutMs: 5 * 60_000,
            intervalMs: 1500,
            onTick: (dto) => {
              if (cancelledRef.current) return;
              setItems((prev) =>
                prev.map((x) =>
                  x.clientId === item.clientId
                    ? {
                        ...x,
                        status: dto.status,
                        chunkCount: dto.chunkCount ?? null,
                        errorMessage: dto.errorMessage ?? null,
                      }
                    : x,
                ),
              );
              invalidateProjectDocuments();
            },
          });
          if (cancelledRef.current) return;
          setItems((prev) =>
            prev.map((x) =>
              x.clientId === item.clientId
                ? {
                    ...x,
                    status: terminal.status,
                    chunkCount: terminal.chunkCount ?? null,
                    errorMessage: terminal.errorMessage ?? null,
                    phase:
                      terminal.status === "READY"
                        ? "ready"
                        : terminal.status === "ERROR"
                          ? "error"
                          : "stalled",
                  }
                : x,
            ),
          );
          invalidateProjectDocuments();
        }
      } catch (e) {
        if (cancelledRef.current) return;
        setItems((prev) =>
          prev.map((x) =>
            x.clientId === item.clientId
              ? { ...x, phase: "error", lastError: e, errorMessage: uploadErrorDetail(e, tRef.current) }
              : x,
          ),
        );
        invalidateProjectDocuments();
      }
    },
    [invalidateProjectDocuments, projectId, uploadSingle],
  );

  const processOneQueuedRef = useRef(processOneQueued);
  processOneQueuedRef.current = processOneQueued;

  useEffect(() => {
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, [projectId]);

  useEffect(() => {
    if (!projectId || drainingRef.current) return;
    const next = items.find((i) => i.phase === "queued");
    if (!next) return;
    drainingRef.current = true;
    void processOneQueuedRef.current(next).finally(() => {
      drainingRef.current = false;
    });
  }, [items, projectId]);

  const anyError = items.some((i) => i.phase === "error" || i.phase === "stalled");

  return (
    <div className="space-y-3">
      <ActiveIndexProfileCallout projectId={projectId} />
      <div
        role="group"
        tabIndex={0}
        aria-label={t("dropHint")}
        className={cn(
          "flex min-h-[140px] flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-6 outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
          drag ? "border-primary bg-muted/40" : "border-border bg-muted/20",
        )}
        onClick={(e) => {
          if ((e.target as HTMLElement).closest("button")) return;
          activateFilePicker();
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            activateFilePicker();
          }
        }}
        onDragOver={(e) => {
          e.preventDefault();
          setDrag(true);
        }}
        onDragLeave={() => setDrag(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDrag(false);
          onFiles(e.dataTransfer.files);
        }}
      >
        <p className="text-muted-foreground text-sm text-center">{t("dropHint")}</p>
        <input
          ref={inputRef}
          type="file"
          className="sr-only"
          multiple
          onChange={(e) => onFiles(e.target.files)}
        />
        <Button type="button" variant="secondary" size="sm" onClick={activateFilePicker} disabled={!projectId}>
          {t("browse")}
        </Button>
        {inProgressCount > 0 ? (
          <p className="text-muted-foreground text-xs" data-testid="doc-upload-progress">
            Processing {inProgressCount} file{inProgressCount === 1 ? "" : "s"}…
          </p>
        ) : null}
        {anyError ? (
          <p className="text-destructive text-xs" role="alert">
            Some files failed. Check the list below.
          </p>
        ) : null}
      </div>

      {items.length > 0 ? (
        <div className="rounded-md border bg-muted/20 p-3" data-testid="doc-upload-items">
          <p className="text-xs font-medium">Uploads</p>
          <div className="mt-2 space-y-2">
            {items.slice(0, 12).map((it) => {
              const badge =
                it.phase === "ready"
                  ? "READY"
                  : it.phase === "error"
                    ? "ERROR"
                    : it.phase === "stalled"
                      ? "INGESTING (stalled)"
                      : it.phase === "ingesting"
                        ? "INGESTING"
                        : it.phase === "uploading"
                          ? "UPLOADING"
                          : "QUEUED";
              const detail =
                it.phase === "error" || it.phase === "stalled"
                  ? it.errorMessage || uploadErrorDetail(it.lastError, t) || it.errorMessage
                  : null;
              return (
                <div key={it.clientId} className="flex flex-col gap-1 rounded border bg-background p-2 text-xs">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium break-all">{it.fileName}</span>
                    <span className="font-mono text-[11px] text-muted-foreground">{badge}</span>
                  </div>
                  <div className="text-muted-foreground flex flex-wrap gap-3">
                    {it.docId ? <span>id: {it.docId.slice(0, 8)}…</span> : null}
                    {typeof it.chunkCount === "number" ? <span>chunks: {it.chunkCount}</span> : null}
                  </div>
                  {detail ? <p className="text-destructive break-words">{detail}</p> : null}
                  {(it.phase === "error" || it.phase === "stalled") ? (
                    <div className="flex flex-wrap gap-2 pt-1">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        className="h-7 text-xs"
                        onClick={() => {
                          void (async () => {
                            setItems((prev) =>
                              prev.map((x) =>
                                x.clientId === it.clientId
                                  ? { ...x, phase: "uploading", lastError: null, errorMessage: null }
                                  : x,
                              ),
                            );
                            try {
                              const dto = it.docId
                                ? await reindexSingle(it.docId, it.file)
                                : await uploadSingle(it.file);
                              setItems((prev) =>
                                prev.map((x) =>
                                  x.clientId === it.clientId
                                    ? {
                                        ...x,
                                        docId: dto.id,
                                        status: dto.status,
                                        chunkCount: dto.chunkCount ?? null,
                                        errorMessage: dto.errorMessage ?? null,
                                        phase: dto.status === "READY" ? "ready" : "ingesting",
                                      }
                                    : x,
                                ),
                              );
                              if (dto.status !== "READY" && dto.status !== "ERROR") {
                                const terminal = await pollDocumentStatus({
                                  documentId: dto.id,
                                  timeoutMs: 5 * 60_000,
                                  intervalMs: 1500,
                                  onTick: (tick) => {
                                    setItems((prev) =>
                                      prev.map((x) =>
                                        x.clientId === it.clientId
                                          ? {
                                              ...x,
                                              status: tick.status,
                                              chunkCount: tick.chunkCount ?? null,
                                              errorMessage: tick.errorMessage ?? null,
                                            }
                                          : x,
                                      ),
                                    );
                                  },
                                });
                                setItems((prev) =>
                                  prev.map((x) =>
                                    x.clientId === it.clientId
                                      ? {
                                          ...x,
                                          status: terminal.status,
                                          chunkCount: terminal.chunkCount ?? null,
                                          errorMessage: terminal.errorMessage ?? null,
                                          phase:
                                            terminal.status === "READY"
                                              ? "ready"
                                              : terminal.status === "ERROR"
                                                ? "error"
                                                : "stalled",
                                        }
                                      : x,
                                  ),
                                );
                              }
                            } catch (e) {
                              setItems((prev) =>
                                prev.map((x) =>
                                  x.clientId === it.clientId
                                    ? { ...x, phase: "error", lastError: e, errorMessage: uploadErrorDetail(e, t) }
                                    : x,
                                ),
                              );
                            }
                          })();
                        }}
                      >
                        Retry
                      </Button>
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}
