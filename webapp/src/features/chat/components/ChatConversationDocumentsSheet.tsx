"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import type { ProjectDocumentDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useRef } from "react";

export type ChatConversationDocumentsSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectName: string;
  docs: ProjectDocumentDto[] | undefined;
  limitDocs: boolean;
  selectedDocIds: readonly string[];
  patchPending: boolean;
  uploadPending: boolean;
  uploadError: string | null;
  uploadNotice: string | null;
  uploadItems?: Array<{
    id?: string;
    fileName: string;
    phase: "uploading" | "ingesting" | "ready" | "error" | "stalled";
    docId?: string | null;
    chunkCount?: number | null;
    errorMessage?: string | null;
  }>;
  onRetryUploadItem?: (id: string) => void;
  onCheckUploadItem?: (id: string) => void;
  onDocToggle: (documentId: string, checked: boolean) => void;
  onUploadFiles: (files: FileList | null) => void;
  onCleanSelection?: () => void;
  staleSelectionWarning?: string | null;
};

export function ChatConversationDocumentsSheet({
  open,
  onOpenChange,
  projectName,
  docs,
  limitDocs,
  selectedDocIds,
  patchPending,
  uploadPending,
  uploadError,
  uploadNotice,
  uploadItems,
  onDocToggle,
  onUploadFiles,
  onRetryUploadItem,
  onCheckUploadItem,
  onCleanSelection,
  staleSelectionWarning,
}: Readonly<ChatConversationDocumentsSheetProps>) {
  const t = useTranslations("Chat");
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        data-testid="chat-documents-sheet"
        side="right"
        className="flex w-full flex-col gap-0 overflow-hidden sm:max-w-md"
      >
        <SheetHeader>
          <SheetTitle>{t("documentsSheetTitle")}</SheetTitle>
          <SheetDescription>
            {t("documentsSheetScope", { project: projectName })}
          </SheetDescription>
          <p className="text-muted-foreground text-xs">{t("documentsSheetLimitHint")}</p>
        </SheetHeader>

        <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-hidden px-4">
          <div className="flex flex-col gap-2 border-border border-b pb-3">
            <Label className="text-xs">{t("documentsSheetUploadLabel")}</Label>
            <input
              ref={inputRef}
              type="file"
              className="sr-only"
              multiple
              disabled={uploadPending || patchPending}
              aria-label={t("documentsSheetUploadInputAria")}
              onChange={(e) => {
                onUploadFiles(e.target.files);
                e.target.value = "";
              }}
            />
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-fit"
              disabled={uploadPending || patchPending}
              onClick={() => inputRef.current?.click()}
            >
              {uploadPending ? t("documentsSheetUploading") : t("documentsSheetUploadBrowse")}
            </Button>
            {uploadError ? (
              <p className="text-destructive text-xs" role="alert">
                {uploadError}
              </p>
            ) : null}
            {uploadNotice ? (
              <p className="text-muted-foreground text-xs">{uploadNotice}</p>
            ) : null}
            {uploadItems && uploadItems.length > 0 ? (
              <div className="rounded-md border bg-muted/20 p-2 text-[11px]" data-testid="chat-upload-items">
                <p className="font-medium">Uploads</p>
                <ul className="mt-1 space-y-1">
                  {uploadItems.slice(0, 6).map((it) => (
                    <li key={it.id ?? `${it.fileName}-${it.phase}`} className="flex flex-col">
                      <span className="flex items-center justify-between gap-2">
                        <span className="truncate">{it.fileName}</span>
                        <span className="flex items-center gap-2">
                          <span className="font-mono text-muted-foreground">{it.phase.toUpperCase()}</span>
                          {it.phase === "stalled" && it.id && onCheckUploadItem ? (
                            <button
                              type="button"
                              className="text-primary underline underline-offset-2"
                              onClick={() => onCheckUploadItem(it.id!)}
                            >
                              Check again
                            </button>
                          ) : null}
                          {it.phase === "error" && it.id && onRetryUploadItem ? (
                            <button
                              type="button"
                              className="text-primary underline underline-offset-2"
                              onClick={() => onRetryUploadItem(it.id!)}
                            >
                              Retry ingest
                            </button>
                          ) : null}
                        </span>
                      </span>
                      {typeof it.chunkCount === "number" ? (
                        <span className="text-muted-foreground">chunks: {it.chunkCount}</span>
                      ) : null}
                      {it.errorMessage ? <span className="text-destructive break-all">{it.errorMessage}</span> : null}
                      {it.phase === "stalled" ? (
                        <span className="text-muted-foreground">
                          Still ingesting; check again to refresh status.
                        </span>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>

          <div className="flex min-h-0 flex-1 flex-col gap-2">
            <Label className="text-xs">{t("documentsSheetPickLabel")}</Label>
            {staleSelectionWarning ? (
              <div className="flex items-center justify-between gap-2 rounded-md border border-amber-500/30 bg-amber-500/10 p-2 text-[11px]">
                <span className="text-muted-foreground">{staleSelectionWarning}</span>
                {onCleanSelection ? (
                  <button
                    type="button"
                    className="text-primary underline underline-offset-2"
                    onClick={() => onCleanSelection()}
                  >
                    Clean selection
                  </button>
                ) : null}
              </div>
            ) : null}
            <ScrollArea className="max-h-64 rounded-md border p-2">
              {!docs?.length ? (
                <p className="text-muted-foreground text-xs">{t("noDocumentsInProject")}</p>
              ) : (
                docs.map((d) => (
                  <label
                    key={d.id}
                    className="flex cursor-pointer items-start gap-2 py-1 text-xs"
                  >
                    <input
                      type="checkbox"
                      data-testid={`chat-document-include-checkbox-${d.id}`}
                      className="mt-0.5 size-3.5 shrink-0 rounded border"
                      checked={selectedDocIds.includes(d.id)}
                      disabled={d.status !== "READY"}
                      aria-label={t("documentsSheetToggleDocAria", { name: d.fileName })}
                      onChange={(e) => onDocToggle(d.id, e.target.checked)}
                    />
                    <span className="break-all">
                      {d.fileName}
                      {d.status !== "READY" ? (
                        <span className="text-muted-foreground"> ({d.status})</span>
                      ) : null}
                    </span>
                  </label>
                ))
              )}
            </ScrollArea>
            {!limitDocs ? (
              <p className="text-muted-foreground text-[11px]">{t("documentsSheetLimitOffHint")}</p>
            ) : null}
          </div>
        </div>

        <SheetFooter className="border-border border-t">
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="chat-documents-sheet-close"
            onClick={() => onOpenChange(false)}
          >
            {t("documentsSheetDone")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
