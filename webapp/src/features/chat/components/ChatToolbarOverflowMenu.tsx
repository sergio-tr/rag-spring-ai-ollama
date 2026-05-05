"use client";

import { MoreVertical } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef, useState } from "react";
import { buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { useRuntimeConfigCapabilities } from "@/features/chat/hooks/use-runtime-config-capabilities";
import { useRuntimeConfigValidate } from "@/features/chat/hooks/use-runtime-config-validate";
import { resolvePresetSelectLabel } from "@/features/chat/lib/conversation-preset-ui";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { cn } from "@/lib/utils";

function MenuHint({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <output className="text-muted-foreground mt-0.5 block max-w-[18rem] text-xs font-normal leading-snug">
      {children}
    </output>
  );
}

function experimentalPresetOptionLabel(p: {
  code: string;
  label: string;
  supported: boolean;
  supportStatus: string;
  reasonIfUnsupported: string | null;
  requiresMultiTurn: boolean;
  chatSelectable: boolean;
}) {
  const base = `${p.code} — ${p.label}`;
  if (p.requiresMultiTurn) return `${base} (REQUIRES_MULTI_TURN)`;
  if (!p.chatSelectable) return `${base} (${p.supportStatus || "LAB_ONLY"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""})`;
  if (p.supported) return base;
  return `${base} (${p.supportStatus || "NOT_SUPPORTED"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""})`;
}

/**
 * Shell toolbar overflow for Chat: model, preset, retrieval limits, documents sheet, move, delete.
 * State comes from {@link useChatToolbarStore} populated by the chat page.
 */
export function ChatToolbarOverflowMenu() {
  const t = useTranslations("SectionActions");
  const tChat = useTranslations("Chat");
  const api = useChatToolbarStore((s) => s.api);
  const modelSelectId = useId();
  const presetSelectId = useId();
  const uploadInputRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [advancedDraft, setAdvancedDraft] = useState<Record<string, unknown>>({});
  const [advancedError, setAdvancedError] = useState<string | null>(null);
  const [advancedValidationText, setAdvancedValidationText] = useState<string | null>(null);
  const capabilitiesQuery = useRuntimeConfigCapabilities();
  const validateMutation = useRuntimeConfigValidate();

  const needsProject = !api?.projectId;
  const needsConversation = !api?.conversationId;

  const caps = capabilitiesQuery.data?.capabilities ?? [];
  const capByKey = new Map(caps.map((c) => [c.key, c]));
  const disabledReason = (key: string): string | null => {
    const c = capByKey.get(key);
    if (!c) return null;
    if (!c.configurable) return "not configurable";
    if (!c.implemented) return c.reasonIfNotImplemented ?? "not implemented";
    return null;
  };

  return (
    <Sheet open={open} onOpenChange={(next) => setOpen(next)} modal={false}>
      <button
        type="button"
        data-testid="chat-actions-menu-trigger"
        className={cn(buttonVariants({ variant: "ghost", size: "icon-sm" }), "shrink-0")}
        aria-label={t("chatMenuLabel")}
        onClick={() => setOpen(true)}
      >
        <MoreVertical className="size-4" aria-hidden />
      </button>
      <SheetContent
        side="right"
        aria-label={t("chatMenuLabel")}
        className={cn(
          "w-full sm:max-w-lg",
          "flex max-h-dvh flex-col gap-4 overflow-y-auto",
        )}
      >
        <SheetHeader>
          <SheetTitle>{t("chatMenuLabel")}</SheetTitle>
          <p className="text-muted-foreground mt-1 text-sm">{tChat("chatTitleHelp")}</p>
        </SheetHeader>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <Label htmlFor={modelSelectId} className="text-xs">
              {tChat("modelLabel")}
            </Label>
            <select
              id={modelSelectId}
              className={cn(
                "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                api?.modelsError && "border-destructive",
              )}
              value={api?.llmModelChoice ?? ""}
              onChange={(e) => api?.setLlmModelChoice(e.target.value)}
              disabled={needsProject || needsConversation || !!api?.modelsError}
              aria-label={tChat("modelLabel")}
            >
              <option value="">{tChat("modelDefault")}</option>
              {api?.modelsCatalog?.allowlist
                ?.filter((e) => e.type === "LLM")
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((m) => {
                  const usable = m.inAllowlist && m.installedInOllama;
                  return (
                    <option key={m.name} value={m.name} disabled={!usable}>
                      {m.name}
                      {!m.installedInOllama ? ` (${tChat("modelNotInstalled")})` : ""}
                      {!m.inAllowlist ? ` (${tChat("modelNotAllowlisted")})` : ""}
                    </option>
                  );
                })}
            </select>
            {api?.modelsError ? (
              <p className="text-destructive text-xs" role="alert">
                {api.modelsErrorMessage || tChat("modelsLoadError")}
              </p>
            ) : null}
            {!api?.modelsError && !api?.modelsCatalog?.ollamaReachable && api ? (
              <p className="text-muted-foreground text-xs">{tChat("ollamaUnreachable")}</p>
            ) : null}
          </div>

          <div className="flex flex-col gap-1">
            <Label htmlFor={presetSelectId} className="text-xs">
              {tChat("presetLabel")}
            </Label>
            <select
              id={presetSelectId}
              className={cn(
                "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                api?.presetsError && "border-destructive",
              )}
              value={api?.presetSelectValue ?? ""}
              onChange={(e) => api?.onPresetChange(e.target.value)}
              disabled={needsProject || needsConversation || !!api?.presetSelectDisabled}
              aria-label={tChat("presetLabel")}
            >
              {api?.syntheticPresetOptionNeeded && api.presets ? (
                <option value={api.presetSelectValue}>
                  {resolvePresetSelectLabel(api.presets, api.presetSelectValue, api.presetLabelOpts)}
                </option>
              ) : null}
              <optgroup label={tChat("presetGroupProduct")}>
                {api?.presets?.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.system ? ` (${tChat("presetSystem")})` : ""}
                  </option>
                ))}
              </optgroup>
              <optgroup label={tChat("presetGroupExperimental")}>
                {(Array.isArray(api?.experimentalPresets) ? api?.experimentalPresets : []).map((p) => (
                  <option key={p.productPresetId} value={p.productPresetId} disabled={!p.chatSelectable}>
                    {experimentalPresetOptionLabel(p)}
                  </option>
                ))}
              </optgroup>
            </select>
            {api?.presetsLoading && !api.presetsError ? (
              <p className="text-muted-foreground text-xs">{tChat("presetCatalogLoading")}</p>
            ) : null}
            {api?.presets?.length === 0 && !api.presetsError ? (
              <output className="text-muted-foreground block text-xs">{tChat("presetCatalogEmpty")}</output>
            ) : null}
            {!api?.experimentalPresetsLoading && (api?.experimentalPresets?.length ?? 0) === 0 ? (
              <output className="text-muted-foreground block text-xs">{tChat("presetExperimentalEmpty")}</output>
            ) : null}
            {api?.presetsError ? (
              <p className="text-destructive text-xs">{tChat("presetsLoadError")}</p>
            ) : null}
            {api?.experimentalPresetsError ? (
              <p className="text-destructive text-xs">{tChat("presetsExperimentalLoadError")}</p>
            ) : null}
            <MenuHint>{tChat("presetExperimentalHint")}</MenuHint>
          </div>

          <div className="flex flex-col gap-2 border-border border-t pt-3">
            <label className="flex cursor-pointer items-start gap-2 text-sm leading-snug">
              <input
                type="checkbox"
                className="border-input mt-0.5 size-4 shrink-0 rounded"
                checked={api?.limitDocs ?? false}
                onChange={(e) => api?.onLimitDocsChange(e.target.checked)}
                disabled={
                  needsProject ||
                  needsConversation ||
                  !!api?.patchConvPending ||
                  !!api?.limitDocsDisabled
                }
              />
              <span>{tChat("limitDocuments")}</span>
            </label>
            {api?.limitDocsToggleNotice ? <MenuHint>{api.limitDocsToggleNotice}</MenuHint> : null}
          </div>

          <div className="flex flex-col gap-3 border-border border-t pt-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-medium">Advanced configuration</p>
                <MenuHint>Manually override runtime flags for this conversation. Invalid or unsupported combinations will be blocked.</MenuHint>
              </div>
              <button
                type="button"
                className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                disabled={needsProject || needsConversation || !!api?.patchConvPending}
                onClick={() => {
                  setAdvancedError(null);
                  setAdvancedValidationText(null);
                  setAdvancedDraft(api?.runtimeOverride ?? {});
                }}
              >
                Load current
              </button>
            </div>

            <div className="grid grid-cols-1 gap-3">
              <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
                <span>Use retrieval</span>
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(advancedDraft.useRetrieval)}
                  disabled={!!disabledReason("useRetrieval")}
                  onChange={(e) =>
                    setAdvancedDraft((p) => ({ ...p, useRetrieval: e.target.checked }))
                  }
                />
              </label>
              {disabledReason("useRetrieval") ? (
                <MenuHint>{disabledReason("useRetrieval")}</MenuHint>
              ) : null}

              <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
                <span>Naive full corpus (no retrieval)</span>
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(advancedDraft.naiveFullCorpusInPromptEnabled)}
                  disabled={!!disabledReason("naiveFullCorpusInPromptEnabled")}
                  onChange={(e) =>
                    setAdvancedDraft((p) => ({
                      ...p,
                      naiveFullCorpusInPromptEnabled: e.target.checked,
                    }))
                  }
                />
              </label>
              {disabledReason("naiveFullCorpusInPromptEnabled") ? (
                <MenuHint>{disabledReason("naiveFullCorpusInPromptEnabled")}</MenuHint>
              ) : null}

              <div className="flex flex-col gap-1">
                <Label className="text-xs">Materialization strategy</Label>
                <select
                  className={cn("border-input bg-background h-9 w-full rounded-md border px-2 text-sm")}
                  value={String(advancedDraft.materializationStrategy ?? "")}
                  disabled={!!disabledReason("materializationStrategy")}
                  onChange={(e) =>
                    setAdvancedDraft((p) => ({
                      ...p,
                      materializationStrategy: e.target.value || null,
                    }))
                  }
                >
                  <option value="">Default</option>
                  {Array.isArray(capByKey.get("materializationStrategy")?.options?.allowedValues)
                    ? (capByKey.get("materializationStrategy")?.options?.allowedValues as string[]).map((v) => (
                        <option key={v} value={v}>
                          {v}
                        </option>
                      ))
                    : null}
                </select>
                {disabledReason("materializationStrategy") ? (
                  <MenuHint>{disabledReason("materializationStrategy")}</MenuHint>
                ) : null}
              </div>

              <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
                <span>Metadata enabled</span>
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(advancedDraft.metadataEnabled)}
                  disabled={!!disabledReason("metadataEnabled")}
                  onChange={(e) =>
                    setAdvancedDraft((p) => ({ ...p, metadataEnabled: e.target.checked }))
                  }
                />
              </label>

              <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
                <span>Use advisor</span>
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(advancedDraft.useAdvisor)}
                  disabled={!!disabledReason("useAdvisor")}
                  onChange={(e) => setAdvancedDraft((p) => ({ ...p, useAdvisor: e.target.checked }))}
                />
              </label>

              <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
                <span>Reasoning (not implemented)</span>
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(advancedDraft.reasoningEnabled)}
                  disabled={!!disabledReason("reasoningEnabled")}
                  onChange={(e) =>
                    setAdvancedDraft((p) => ({ ...p, reasoningEnabled: e.target.checked }))
                  }
                />
              </label>
              {disabledReason("reasoningEnabled") ? <MenuHint>{disabledReason("reasoningEnabled")}</MenuHint> : null}

              <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
                <span>Ranker (not implemented)</span>
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(advancedDraft.rankerEnabled)}
                  disabled={!!disabledReason("rankerEnabled")}
                  onChange={(e) => setAdvancedDraft((p) => ({ ...p, rankerEnabled: e.target.checked }))}
                />
              </label>
              {disabledReason("rankerEnabled") ? <MenuHint>{disabledReason("rankerEnabled")}</MenuHint> : null}

              <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
                <span>Post-retrieval (not implemented)</span>
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(advancedDraft.postRetrievalEnabled)}
                  disabled={!!disabledReason("postRetrievalEnabled")}
                  onChange={(e) =>
                    setAdvancedDraft((p) => ({ ...p, postRetrievalEnabled: e.target.checked }))
                  }
                />
              </label>
              {disabledReason("postRetrievalEnabled") ? <MenuHint>{disabledReason("postRetrievalEnabled")}</MenuHint> : null}
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className={cn(buttonVariants({ variant: "secondary", size: "sm" }))}
                disabled={needsConversation || validateMutation.isPending}
                onClick={async () => {
                  if (!api?.conversationId) return;
                  setAdvancedError(null);
                  setAdvancedValidationText(null);
                  try {
                    const res = await validateMutation.mutateAsync({
                      conversationId: api.conversationId,
                      presetId: api.presetSelectValue,
                      overrides: advancedDraft,
                    });
                    if (!res.valid || !res.supported) {
                      const msg = res.errors?.[0]?.message ?? "Configuration is not supported.";
                      setAdvancedError(msg);
                      setAdvancedValidationText(`Selected workflow: ${res.selectedWorkflow ?? "-"}`);
                      return;
                    }
                    setAdvancedValidationText(
                      `Configuration is valid. Selected workflow: ${res.selectedWorkflow ?? "-"}`,
                    );
                  } catch (e) {
                    setAdvancedError(String(e));
                  }
                }}
              >
                {validateMutation.isPending ? "Validating..." : "Validate"}
              </button>

              <button
                type="button"
                className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                disabled={needsConversation || !!api?.patchConvPending}
                onClick={async () => {
                  if (!api?.conversationId) return;
                  setAdvancedError(null);
                  try {
                    const res = await validateMutation.mutateAsync({
                      conversationId: api.conversationId,
                      presetId: api.presetSelectValue,
                      overrides: advancedDraft,
                    });
                    if (!res.valid || !res.supported) {
                      setAdvancedError(res.errors?.[0]?.message ?? "Configuration is not supported.");
                      return;
                    }
                    api?.saveRuntimeOverride(advancedDraft);
                    setAdvancedValidationText("Saved.");
                  } catch (e) {
                    setAdvancedError(String(e));
                  }
                }}
              >
                Save
              </button>

              <button
                type="button"
                className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                disabled={needsConversation || !!api?.patchConvPending}
                onClick={() => {
                  api?.clearRuntimeOverride();
                  setAdvancedDraft({});
                  setAdvancedError(null);
                  setAdvancedValidationText("Cleared.");
                }}
              >
                Clear
              </button>
            </div>

            {advancedError ? (
              <p className="text-destructive text-xs" role="alert">
                {advancedError}
              </p>
            ) : null}
            {advancedValidationText ? (
              <p className="text-muted-foreground text-xs">{advancedValidationText}</p>
            ) : null}
          </div>

          <div className="flex flex-wrap gap-2 border-border border-t pt-3">
            <input
              ref={uploadInputRef}
              type="file"
              className="sr-only"
              multiple
              aria-label={tChat("documentsSheetUploadInputAria")}
              disabled={needsProject || needsConversation || !!api?.uploadPending || !!api?.patchConvPending}
              onChange={(e) => {
                api?.onAddDocuments(e.target.files);
                e.target.value = "";
              }}
            />
            <button
              type="button"
              data-testid="chat-add-documents-button"
              className={cn(buttonVariants({ variant: "secondary", size: "sm" }))}
              disabled={needsProject || needsConversation || !!api?.uploadPending || !!api?.patchConvPending}
              onClick={() => uploadInputRef.current?.click()}
            >
              {api?.uploadPending ? tChat("documentsSheetUploading") : tChat("chatAddDocuments")}
            </button>
            <button
              type="button"
              data-testid="chat-open-documents-sheet"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
              disabled={needsProject || needsConversation || !!api?.patchConvPending}
              onClick={() => api?.openDocumentsSheet()}
            >
              {tChat("chatManageDocuments")}
            </button>
            <button
              type="button"
              data-testid="chat-move-project-button"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
              disabled={needsProject || needsConversation}
              onClick={() => {
                if (!needsProject && !needsConversation) api?.openMoveDialog();
              }}
            >
              {t("chatMoveProject")}
            </button>
            <button
              type="button"
              data-testid="chat-delete-menu-item"
              className={cn(buttonVariants({ variant: "destructive", size: "sm" }))}
              disabled={needsProject || needsConversation}
              onClick={() => {
                if (!needsProject && !needsConversation) api?.openDeleteForActiveConversation();
              }}
            >
              {t("chatDelete")}
            </button>
            {(needsProject || needsConversation) && (
              <MenuHint>
                {needsProject ? t("needsActiveProject") : ""}
                {needsProject && needsConversation ? " · " : ""}
                {needsConversation ? t("needsActiveConversation") : ""}
              </MenuHint>
            )}
            {api?.uploadError ? (
              <p className="text-destructive w-full text-xs" role="alert">
                {api.uploadError}
              </p>
            ) : null}
            {api?.uploadNotice ? <p className="text-muted-foreground w-full text-xs">{api.uploadNotice}</p> : null}
          </div>
        </div>
        <div className="flex items-center justify-end border-t pt-3">
          <button
            type="button"
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
            aria-label={tChat("chatMenuClose")}
            onClick={() => setOpen(false)}
          >
            {tChat("documentsSheetDone")}
          </button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
