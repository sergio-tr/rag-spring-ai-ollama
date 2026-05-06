"use client";

import { MoreVertical } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef, useState } from "react";
import { buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useRuntimeConfigCapabilities } from "@/features/chat/hooks/use-runtime-config-capabilities";
import { useRuntimeConfigValidate } from "@/features/chat/hooks/use-runtime-config-validate";
import { resolveChatPresetLabel } from "@/features/chat/lib/conversation-preset-ui";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { cn } from "@/lib/utils";

function MenuHint({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <output className="text-muted-foreground mt-0.5 block max-w-[18rem] text-xs font-normal leading-snug">
      {children}
    </output>
  );
}

function Section({
  title,
  children,
}: Readonly<{
  title: string;
  children: React.ReactNode;
}>) {
  return (
    <section className="space-y-3" aria-label={title}>
      <h3 className="text-muted-foreground text-xs font-semibold uppercase tracking-wide">{title}</h3>
      {children}
    </section>
  );
}

function Box({ children }: Readonly<{ children: React.ReactNode }>) {
  return <div className="rounded-lg border bg-background/60 p-3">{children}</div>;
}

function DisabledReason({
  reason,
  id,
}: Readonly<{
  reason: string;
  id: string;
}>) {
  return (
    <Popover>
      <PopoverTrigger
        aria-describedby={id}
        className="text-muted-foreground hover:text-foreground inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium"
        type="button"
      >
        Disabled
      </PopoverTrigger>
      <PopoverContent id={id} className="w-80 text-xs">
        <p className="text-muted-foreground text-xs leading-relaxed">{reason}</p>
      </PopoverContent>
    </Popover>
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
  if (p.requiresMultiTurn) return `${base} [REQUIRES_MULTI_TURN]`;
  if (!p.chatSelectable) return `${base} [${p.supportStatus || "NOT_SUPPORTED"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""}]`;
  if (p.supported) return base;
  return `${base} [${p.supportStatus || "NOT_SUPPORTED"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""}]`;
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
  const [runtimeOpen, setRuntimeOpen] = useState(false);
  const [advancedDraft, setAdvancedDraft] = useState<Record<string, unknown>>({});
  const [advancedError, setAdvancedError] = useState<string | null>(null);
  const [advancedValidationText, setAdvancedValidationText] = useState<string | null>(null);
  const [effectivePresetConfig, setEffectivePresetConfig] = useState<Record<string, unknown> | null>(null);
  const [effectiveConversationConfig, setEffectiveConversationConfig] = useState<Record<string, unknown> | null>(
    null,
  );
  const capabilitiesQuery = useRuntimeConfigCapabilities();
  const validateMutation = useRuntimeConfigValidate();

  const needsProject = !api?.projectId;
  const needsConversation = !api?.conversationId;
  const hasCustomOverride =
    api?.runtimeOverride && Object.keys(api.runtimeOverride).length > 0;

  const caps = capabilitiesQuery.data?.capabilities ?? [];
  const capByKey = new Map(caps.map((c) => [c.key, c]));
  const disabledReason = (key: string): string | null => {
    const c = capByKey.get(key);
    if (!c) return null;
    if (!c.configurable) return "not configurable";
    if (!c.implemented) return c.reasonIfNotImplemented ?? "not implemented";
    return null;
  };

  const selectedPresetId = api?.presetSelectValue ?? "";
  const selectedInProduct = !!api?.presets?.some((p) => p.id === selectedPresetId);
  const selectedExperimental = (Array.isArray(api?.experimentalPresets) ? api?.experimentalPresets : []).find(
    (p) => p.productPresetId === selectedPresetId,
  );
  const presetKindBadge = selectedInProduct ? "Product" : selectedExperimental ? "TFG" : selectedPresetId ? null : "Recommended";
  const presetSupportBadge =
    selectedExperimental && !selectedExperimental.chatSelectable
      ? selectedExperimental.supportStatus || "NOT_SUPPORTED"
      : null;

  const hotSwappableKeys = [
    "useRetrieval",
    "naiveFullCorpusInPromptEnabled",
    "useAdvisor",
    "reasoningEnabled",
    "rankerEnabled",
    "postRetrievalEnabled",
  ] as const;

  const unavailable = caps.filter((c) => !c.implemented || !c.configurable);

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
          "flex max-h-dvh flex-col",
        )}
      >
        <SheetHeader className="px-4 sm:px-6 py-4">
          <SheetTitle>{t("chatMenuLabel")}</SheetTitle>
          <p className="text-muted-foreground mt-1 text-sm">{tChat("chatTitleHelp")}</p>
        </SheetHeader>

        <div
          data-testid="chat-actions-menu-body"
          className="flex-1 overflow-y-auto px-4 sm:px-6 py-4"
        >
          <div className="flex flex-col gap-6">
            <Section title="Model & preset">
              <Box>
                <div className="space-y-3">
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
                    <div className="flex items-center justify-between gap-2">
                      <Label htmlFor={presetSelectId} className="text-xs">
                        {tChat("presetLabel")}
                      </Label>
                      <div className="flex items-center gap-2">
                        {presetKindBadge ? (
                          <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                            {presetKindBadge}
                          </span>
                        ) : null}
                        {selectedExperimental ? (
                          <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                            {selectedExperimental.code}
                          </span>
                        ) : null}
                        {hasCustomOverride ? (
                          <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                            Custom
                          </span>
                        ) : null}
                        {presetSupportBadge ? (
                          <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium">
                            {presetSupportBadge}
                          </span>
                        ) : null}
                      </div>
                    </div>
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
                      {api?.syntheticPresetOptionNeeded ? (
                        <option value={api.presetSelectValue}>
                          {resolveChatPresetLabel(
                            api.presets,
                            api.experimentalPresets,
                            api.presetSelectValue,
                            api.presetLabelOpts,
                          )}
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
                      <p className="text-muted-foreground text-xs" role="status">
                        {tChat("presetCatalogLoading")}
                      </p>
                    ) : null}
                    {api?.presets?.length === 0 && !api.presetsError ? (
                      <output className="text-muted-foreground block text-xs" role="status">
                        {tChat("presetCatalogEmpty")}
                      </output>
                    ) : null}
                    {!api?.experimentalPresetsLoading && (api?.experimentalPresets?.length ?? 0) === 0 ? (
                      <output className="text-muted-foreground block text-xs" role="status">
                        {tChat("presetExperimentalEmpty")}
                      </output>
                    ) : null}
                    {api?.presetsError ? (
                      <p className="text-destructive text-xs" role="alert">
                        {tChat("presetsLoadError")}
                      </p>
                    ) : null}
                    {api?.experimentalPresetsError ? (
                      <p className="text-destructive text-xs" role="alert">
                        {tChat("presetsExperimentalLoadError")}
                      </p>
                    ) : null}
                    <MenuHint>{tChat("presetExperimentalHint")}</MenuHint>
                  </div>
                </div>
              </Box>
            </Section>

            <Section title="Document scope">
              <Box>
                <div className="space-y-3">
                  <div className="space-y-2">
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
                    <p className="text-muted-foreground text-xs">
                      {api?.limitDocs ? "Limited to selected documents." : "All project documents are in scope."}
                    </p>
                    {api?.limitDocsToggleNotice ? <MenuHint>{api.limitDocsToggleNotice}</MenuHint> : null}
                  </div>
                  <div className="flex flex-wrap gap-2">
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
                  </div>
                  {api?.uploadError ? (
                    <p className="text-destructive w-full text-xs" role="alert">
                      {api.uploadError}
                    </p>
                  ) : null}
                  {api?.uploadNotice ? <p className="text-muted-foreground w-full text-xs">{api.uploadNotice}</p> : null}
                </div>
              </Box>
            </Section>

            <Section title="Runtime configuration">
              <Box>
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <button
                        type="button"
                        data-testid="chat-actions-runtime-collapsible"
                        className="hover:bg-muted inline-flex w-full items-center justify-between gap-3 rounded-md px-2 py-1 text-left"
                        aria-expanded={runtimeOpen}
                        onClick={() => setRuntimeOpen((p) => !p)}
                      >
                        <span className="text-sm font-medium">Advanced configuration</span>
                        <span className="text-muted-foreground text-xs">{runtimeOpen ? "Hide" : "Show"}</span>
                      </button>
                      <p className="text-muted-foreground text-xs">
                        Hot-swappable overrides for this conversation. Use Validate/Save to persist overrides.
                      </p>
                    </div>
                    <button
                      type="button"
                      data-testid="chat-actions-runtime-load-overrides"
                      className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                      disabled={needsProject || needsConversation || !!api?.patchConvPending}
                      onClick={() => {
                        setAdvancedError(null);
                        setAdvancedValidationText(null);
                        setEffectivePresetConfig(null);
                        setEffectiveConversationConfig(null);
                        setAdvancedDraft(api?.runtimeOverride ?? {});
                        setRuntimeOpen(true);
                      }}
                    >
                      Load
                    </button>
                  </div>

                  {runtimeOpen ? (
                    <div className="space-y-4">
                      <div className="rounded-lg border bg-background/40 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <p className="text-sm font-medium">Effective config</p>
                          <button
                            type="button"
                            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                            disabled={needsConversation || validateMutation.isPending}
                            onClick={async () => {
                              if (!api?.conversationId) return;
                              setAdvancedError(null);
                              try {
                                const [presetOnly, withOverrides] = await Promise.all([
                                  validateMutation.mutateAsync({
                                    conversationId: api.conversationId,
                                    presetId: api.presetSelectValue,
                                    overrides: null,
                                  }),
                                  validateMutation.mutateAsync({
                                    conversationId: api.conversationId,
                                    presetId: api.presetSelectValue,
                                    overrides: api.runtimeOverride ?? {},
                                  }),
                                ]);
                                setEffectivePresetConfig(presetOnly.effectiveConfig ?? {});
                                setEffectiveConversationConfig(withOverrides.effectiveConfig ?? {});
                              } catch (e) {
                                setAdvancedError(String(e));
                              }
                            }}
                          >
                            {validateMutation.isPending ? "Loading..." : "Load"}
                          </button>
                        </div>
                        <p className="text-muted-foreground mt-1 text-xs">
                          Shows preset vs conversation effective values. Keys present in overrides are marked as overridden.
                        </p>
                        {effectiveConversationConfig ? (
                          <div className="mt-2 grid grid-cols-1 gap-1 text-xs">
                            {effectivePresetConfig ? (
                              <output className="text-muted-foreground mb-1 block text-[11px]">
                                Preset keys: {Object.keys(effectivePresetConfig).length} · Effective keys:{" "}
                                {Object.keys(effectiveConversationConfig).length}
                              </output>
                            ) : null}
                            {Object.entries(effectiveConversationConfig)
                              .slice(0, 24)
                              .map(([k, v]) => (
                                <div key={k} className="flex items-baseline justify-between gap-3">
                                  <span className="truncate font-mono">
                                    {k}
                                    {api?.runtimeOverride && Object.prototype.hasOwnProperty.call(api.runtimeOverride, k) ? (
                                      <span className="ml-2 text-muted-foreground">[override]</span>
                                    ) : null}
                                  </span>
                                  <span className="font-mono text-muted-foreground">
                                    {typeof v === "string" || typeof v === "number" || typeof v === "boolean"
                                      ? String(v)
                                      : "[object]"}
                                  </span>
                                </div>
                              ))}
                          </div>
                        ) : (
                          <output className="text-muted-foreground mt-2 block text-xs">Not loaded.</output>
                        )}
                      </div>

                      <div className="grid grid-cols-1 gap-3">
                        {hotSwappableKeys.map((key) => {
                          const reason = disabledReason(key);
                          const rid = `disabled-${key}`;
                          const label =
                            key === "useRetrieval"
                              ? "Use retrieval"
                              : key === "naiveFullCorpusInPromptEnabled"
                                ? "Naive full corpus (no retrieval)"
                                : key === "useAdvisor"
                                  ? "Advisor"
                                  : key === "reasoningEnabled"
                                    ? "Reasoning"
                                    : key === "rankerEnabled"
                                      ? "Ranker"
                                      : "Post-retrieval";
                          return (
                            <div key={key} className="flex items-start justify-between gap-3">
                              <label className="flex cursor-pointer items-center gap-3 text-sm">
                                <input
                                  type="checkbox"
                                  className="border-input size-4 rounded"
                                  checked={Boolean((advancedDraft as Record<string, unknown>)[key])}
                                  disabled={!!reason}
                                  onChange={(e) =>
                                    setAdvancedDraft((p) => ({ ...p, [key]: e.target.checked }))
                                  }
                                  aria-describedby={reason ? rid : undefined}
                                />
                                <span>{label}</span>
                              </label>
                              {reason ? <DisabledReason id={rid} reason={reason} /> : null}
                            </div>
                          );
                        })}
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
                            setEffectivePresetConfig(null);
                            setEffectiveConversationConfig(null);
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
                  ) : null}
                </div>
              </Box>
            </Section>

            <Section title="Index/project capabilities">
              <Box>
                <div className="space-y-3">
                  <p className="text-muted-foreground text-xs">
                    These values may require reindexing to change. In this phase, they are surfaced separately for clarity.
                  </p>

                  <div className="space-y-2">
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

                    <div className="flex items-start justify-between gap-3">
                      <label className="flex cursor-pointer items-center gap-3 text-sm">
                        <input
                          type="checkbox"
                          className="border-input size-4 rounded"
                          checked={Boolean(advancedDraft.metadataEnabled)}
                          disabled={!!disabledReason("metadataEnabled")}
                          onChange={(e) =>
                            setAdvancedDraft((p) => ({ ...p, metadataEnabled: e.target.checked }))
                          }
                        />
                        <span>Metadata index</span>
                      </label>
                      {disabledReason("metadataEnabled") ? (
                        <DisabledReason id="disabled-metadataEnabled" reason={disabledReason("metadataEnabled") ?? ""} />
                      ) : null}
                    </div>
                  </div>
                </div>
              </Box>
            </Section>

            {unavailable.length ? (
              <Section title="Unavailable capabilities">
                <Box>
                  <div className="space-y-2">
                    <p className="text-muted-foreground text-xs">
                      These capabilities exist in the config but are not currently usable/configurable in Chat.
                    </p>
                    <div className="grid grid-cols-1 gap-2">
                      {unavailable.map((c) => {
                        const rid = `unavail-${c.key}`;
                        const reason = !c.configurable
                          ? "not configurable"
                          : c.reasonIfNotImplemented ?? "not implemented";
                        return (
                          <div key={c.key} className="flex items-center justify-between gap-3">
                            <span className="text-sm">{c.label}</span>
                            <DisabledReason id={rid} reason={reason} />
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </Box>
              </Section>
            ) : null}

            <Section title="Chat actions">
              <Box>
                <div className="flex flex-wrap gap-2">
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
                </div>
              </Box>
            </Section>
          </div>
        </div>

        <div
          data-testid="chat-actions-menu-footer"
          className="sticky bottom-0 border-t bg-popover px-4 sm:px-6 py-3"
        >
          <div className="flex items-center justify-end">
            <button
              type="button"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
              aria-label={tChat("chatMenuClose")}
              onClick={() => setOpen(false)}
            >
              {tChat("documentsSheetDone")}
            </button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
