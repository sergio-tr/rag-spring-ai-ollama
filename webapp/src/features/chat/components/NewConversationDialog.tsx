"use client";

import { useState } from "react";
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
import {
  CreateConversationPresetSelector,
  resolveCreateConversationPresetSelection,
} from "@/features/chat/components/CreateConversationPresetSelector";
import { useProjectCompatiblePresets } from "@/features/chat/hooks/use-project-compatible-presets";
import { useCreateConversation } from "@/features/chat/hooks/use-conversations";
import { useMeEffectiveEmbeddingDefaults } from "@/features/settings/hooks/use-me-effective-embedding-defaults";
import { retrievalParameterSourceLabelKey } from "@/features/chat/lib/retrieval-parameter-source";
import { buildInitialRuntimeOverrideForNewConversation, toRetrievalDefaults } from "@/features/chat/lib/retrieval-override-mode";
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
  const catalog = useProjectCompatiblePresets(projectId, { enabled: open });
  const docs = useProjectDocuments(projectId);
  const createConv = useCreateConversation(projectId);
  const effectiveEmbedding = useMeEffectiveEmbeddingDefaults();

  const [title, setTitle] = useState("");
  const [presetValue, setPresetValue] = useState("");
  const [showIncompatiblePresets, setShowIncompatiblePresets] = useState(false);
  const [scope, setScope] = useState<"all" | "pick">("all");
  const [selectedDocIds, setSelectedDocIds] = useState<Record<string, boolean>>({});
  const [localError, setLocalError] = useState<string | null>(null);
  const [useAssistantRetrievalDefaults, setUseAssistantRetrievalDefaults] = useState(false);

  function handleOpenChange(next: boolean) {
    if (!next) {
      setTitle("");
      setPresetValue("");
      setShowIncompatiblePresets(false);
      setScope("all");
      setSelectedDocIds({});
      setLocalError(null);
      setUseAssistantRetrievalDefaults(false);
    }
    onOpenChange(next);
  }

  const readyDocs = (docs.data ?? []).filter((d) => d.status === "READY");

  async function submit() {
    setLocalError(null);
    if (!projectId.trim()) {
      setLocalError(t("presetCompatibilityProjectRequired"));
      return;
    }
    if (catalog.isError) {
      setLocalError(t("presetsLoadError"));
      return;
    }
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
    const { selectedValue, compatibility } = resolveCreateConversationPresetSelection(
      catalog,
      presetValue,
      showIncompatiblePresets,
    );
    if (compatibility && !compatibility.selectable) {
      setLocalError(compatibility.disabledReason ?? t("presetsLoadError"));
      return;
    }
    try {
      const assistantDefaults = toRetrievalDefaults(effectiveEmbedding.data?.retrievalOptions);
      const initialRuntimeOverride = buildInitialRuntimeOverrideForNewConversation(
        useAssistantRetrievalDefaults,
        assistantDefaults,
      );
      const created = await createConv.mutateAsync({
        title: title.trim() ? title.trim() : undefined,
        documentFilter: filter,
        initialPresetId: selectedValue || undefined,
        initialRuntimeOverride,
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

          <CreateConversationPresetSelector
            projectId={projectId}
            value={presetValue}
            onChange={setPresetValue}
            showIncompatiblePresets={showIncompatiblePresets}
            onShowIncompatiblePresetsChange={setShowIncompatiblePresets}
            enabled={open}
          />

          <div
            className="rounded-md border bg-muted/20 p-3 text-xs"
            data-testid="new-conversation-retrieval-summary"
          >
            <p className="font-medium">{t("newConversationRetrievalSummaryTitle")}</p>
            <p className="text-muted-foreground mt-1">{t("newConversationRetrievalSummaryHint")}</p>
            {effectiveEmbedding.data?.retrievalOptions ? (
              <p className="mt-2" data-testid="new-conversation-retrieval-values">
                {t("configRetrievalTopKLabel")}: {effectiveEmbedding.data.retrievalOptions.topK} (
                {t(retrievalParameterSourceLabelKey("USER_DEFAULTS"))}) ·{" "}
                {t("configRetrievalSimilarityLabel")}: {effectiveEmbedding.data.retrievalOptions.similarityThreshold} (
                {t(retrievalParameterSourceLabelKey("USER_DEFAULTS"))})
              </p>
            ) : null}
            <label className="mt-3 flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                data-testid="new-conversation-use-assistant-retrieval-defaults"
                checked={useAssistantRetrievalDefaults}
                onChange={(e) => setUseAssistantRetrievalDefaults(e.target.checked)}
              />
              {t("newConversationUseAssistantRetrievalDefaults")}
            </label>
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
            disabled={createConv.isPending || catalog.isLoading || catalog.isError || !projectId.trim()}
          >
            {t("wizardCreate")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
