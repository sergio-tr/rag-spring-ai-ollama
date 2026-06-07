"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useChatPresetsCatalog } from "@/features/chat/hooks/use-chat-presets-catalog";
import { useCreateConversation } from "@/features/chat/hooks/use-conversations";
import { useProjectDocuments } from "@/features/documents/hooks/use-project-documents";
import type { ConversationDto } from "@/types/api";
import { getSafeApiErrorMessage } from "@/lib/api-client";

export type NewConversationDialogProps = {
  projectId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (conversation: ConversationDto) => void | Promise<void>;
};

export function NewConversationDialog({
  projectId,
  open,
  onOpenChange,
  onCreated,
}: Readonly<NewConversationDialogProps>) {
  const t = useTranslations("Chat");
  const catalog = useChatPresetsCatalog();
  const docs = useProjectDocuments(projectId);
  const createConv = useCreateConversation(projectId);

  const [title, setTitle] = useState("");
  const [presetValue, setPresetValue] = useState("");
  const [scope, setScope] = useState<"all" | "pick">("all");
  const [selectedDocIds, setSelectedDocIds] = useState<Record<string, boolean>>({});
  const [localError, setLocalError] = useState<string | null>(null);

  function handleOpenChange(next: boolean) {
    if (!next) {
      setTitle("");
      setPresetValue("");
      setScope("all");
      setSelectedDocIds({});
      setLocalError(null);
    }
    onOpenChange(next);
  }

  const readyDocs = useMemo(
    () => (docs.data ?? []).filter((d) => d.status === "READY"),
    [docs.data],
  );

  async function submit() {
    setLocalError(null);
    let filter: string[] = [];
    if (scope === "pick") {
      filter = Object.entries(selectedDocIds)
        .filter(([, v]) => v)
        .map(([id]) => id);
      if (filter.length === 0) {
        setLocalError(t("wizardPickDocsError"));
        return;
      }
    }
    try {
      const created = await createConv.mutateAsync({
        title: title.trim() ? title.trim() : undefined,
        documentFilter: filter,
        initialPresetId: presetValue.trim() ? presetValue.trim() : undefined,
      });
      handleOpenChange(false);
      await onCreated(created);
    } catch (e) {
      setLocalError(getSafeApiErrorMessage(e));
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        data-testid="chat-new-conversation-dialog"
        className="max-h-[min(90vh,720px)] gap-4 overflow-y-auto sm:max-w-lg"
      >
        <DialogHeader>
          <DialogTitle>{t("newConversationWizardTitle")}</DialogTitle>
          <DialogDescription>{t("newConversationWizardDescription")}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="new-conv-title" className="text-xs">
              {t("chatTitleLabel")}
            </Label>
            <Input
              id="new-conv-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={t("chatTitlePlaceholder")}
            />
          </div>

          <div className="flex flex-col gap-1">
            <Label htmlFor="new-conv-preset" className="text-xs">
              {t("presetInitialLabel")}
            </Label>
            <select
              id="new-conv-preset"
              data-testid="chat-new-conversation-preset"
              className="border-input bg-background h-9 w-full rounded-md border px-2 text-sm"
              value={presetValue}
              disabled={catalog.isLoading}
              onChange={(e) => setPresetValue(e.target.value)}
            >
              <option value="">{t("presetServerDefault")}</option>
              {(catalog.data?.productPresets ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                  {p.system ? ` (${t("presetSystem")})` : ""}
                </option>
              ))}
              {(catalog.data?.experimentalPresets ?? []).map((exp) => (
                <option
                  key={exp.productPresetId}
                  value={exp.productPresetId}
                  disabled={!exp.chatSelectable || !exp.supported}
                >
                  {exp.code} — {exp.label}
                  {!exp.supported || !exp.chatSelectable ? " (N/A)" : ""}
                </option>
              ))}
            </select>
            {catalog.isError ? (
              <p className="text-destructive text-xs" role="alert">
                {t("presetsLoadError")}
              </p>
            ) : null}
          </div>

          <fieldset className="flex flex-col gap-2 rounded-md border p-3">
            <legend className="px-1 text-xs font-medium">{t("documentScopeLegend")}</legend>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="doc-scope"
                checked={scope === "all"}
                onChange={() => setScope("all")}
              />
              {t("scopeAllDocs")}
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="doc-scope"
                checked={scope === "pick"}
                onChange={() => setScope("pick")}
              />
              {t("scopePickDocs")}
            </label>
            {scope === "pick" ? (
              <div className="max-h-40 space-y-2 overflow-y-auto border-t pt-2">
                {readyDocs.length === 0 ? (
                  <p className="text-muted-foreground text-xs">{t("wizardNoReadyDocs")}</p>
                ) : (
                  readyDocs.map((d) => (
                    <label key={d.id} className="flex items-center gap-2 text-xs">
                      <input
                        type="checkbox"
                        checked={Boolean(selectedDocIds[d.id])}
                        onChange={(e) =>
                          setSelectedDocIds((prev) => ({
                            ...prev,
                            [d.id]: e.target.checked,
                          }))
                        }
                      />
                      <span className="break-all">{d.fileName}</span>
                    </label>
                  ))
                )}
              </div>
            ) : null}
          </fieldset>

          {localError ? (
            <p className="text-destructive text-sm" role="alert">
              {localError}
            </p>
          ) : null}
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            {t("wizardCancel")}
          </Button>
          <Button
            type="button"
            data-testid="chat-new-conversation-create"
            onClick={() => void submit()}
            disabled={createConv.isPending}
          >
            {t("wizardCreate")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
