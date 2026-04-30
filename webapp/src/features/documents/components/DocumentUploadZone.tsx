"use client";

import { useTranslations } from "next-intl";
import { useCallback, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { useUploadProjectDocument } from "@/features/documents/hooks/use-project-documents";
import { ApiError, getSafeApiErrorMessage } from "@/lib/api-client";
import { cn } from "@/lib/utils";

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

export function DocumentUploadZone({ projectId }: Readonly<DocumentUploadZoneProps>) {
  const t = useTranslations("Documents");
  const upload = useUploadProjectDocument(projectId);
  const inputRef = useRef<HTMLInputElement>(null);
  const [drag, setDrag] = useState(false);

  const detail = upload.error ? uploadErrorDetail(upload.error, t) : null;

  const onFiles = useCallback(
    (files: FileList | null) => {
      if (!files?.length || !projectId) return;
      for (const f of Array.from(files)) {
        void upload.mutateAsync(f);
      }
    },
    [projectId, upload],
  );

  return (
    <div
      className={cn(
        "flex min-h-[140px] flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-6 transition-colors",
        drag ? "border-primary bg-muted/40" : "border-border bg-muted/20",
      )}
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
      <Button type="button" variant="secondary" size="sm" onClick={() => inputRef.current?.click()}>
        {t("browse")}
      </Button>
      {upload.isError && (
        <p className="text-destructive text-xs" role="alert">
          {t("uploadError")} {detail ? `${detail}` : ""}
        </p>
      )}
    </div>
  );
}
