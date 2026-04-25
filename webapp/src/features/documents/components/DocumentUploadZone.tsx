"use client";

import { useTranslations } from "next-intl";
import { useCallback, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { useUploadProjectDocument } from "@/features/documents/hooks/use-project-documents";
import { ApiError } from "@/lib/api-client";
import { cn } from "@/lib/utils";

type DocumentUploadZoneProps = {
  projectId: string | undefined;
};

export function DocumentUploadZone({ projectId }: Readonly<DocumentUploadZoneProps>) {
  const t = useTranslations("Documents");
  const upload = useUploadProjectDocument(projectId);
  const inputRef = useRef<HTMLInputElement>(null);
  const [drag, setDrag] = useState(false);

  const uploadErrorDetail = (() => {
    const err = upload.error;
    if (!err) return null;
    if (err instanceof ApiError) {
      if (err.status === 503) return t("uploadErrorOllamaNotReady");
      if (err.status === 409) return t("uploadErrorDuplicate");
      if (err.status === 401 || err.status === 403) return t("uploadErrorUnauthorized");
      try {
        const parsed = JSON.parse(err.message) as {
          message?: string;
          detail?: string;
          error?: { message?: string; detail?: string };
        };
        return (
          parsed?.detail ||
          parsed?.message ||
          parsed?.error?.detail ||
          parsed?.error?.message ||
          null
        );
      } catch {
        return err.message || null;
      }
    }
    return err instanceof Error ? err.message : null;
  })();

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
      <Button
        type="button"
        variant="secondary"
        size="sm"
        onClick={() => inputRef.current?.click()}
      >
        {t("browse")}
      </Button>
      {upload.isError && (
        <p className="text-destructive text-xs" role="alert">
          {t("uploadError")} {uploadErrorDetail ?? ""}
        </p>
      )}
    </div>
  );
}
