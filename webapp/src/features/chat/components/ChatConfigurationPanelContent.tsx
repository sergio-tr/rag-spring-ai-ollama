"use client";

import { useId, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useRuntimeConfigCapabilities } from "@/features/chat/hooks/use-runtime-config-capabilities";
import { resolveChatPresetLabel } from "@/features/chat/lib/conversation-preset-ui";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { useActiveProjectSnapshot } from "@/features/projects/hooks/use-active-project-snapshot";
import { useProjectIndexProfile } from "@/features/projects/hooks/use-project-index-profile";
import { cn } from "@/lib/utils";

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

function MenuHint({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <output className="text-muted-foreground mt-0.5 block max-w-[22rem] text-xs font-normal leading-snug">
      {children}
    </output>
  );
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
  if (!p.chatSelectable)
    return `${base} [${p.supportStatus || "NOT_SUPPORTED"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""}]`;
  if (p.supported) return base;
  return `${base} [${p.supportStatus || "NOT_SUPPORTED"}${p.reasonIfUnsupported ? `: ${p.reasonIfUnsupported}` : ""}]`;
}

type EffectiveConfigMap = Record<string, unknown>;
const NOT_LOADED_LABEL = "Not loaded";

/**
 * Shared content for both the desktop side panel and the mobile drawer.
 *
 * Order is intentionally fixed (user requirement):
 * 1) Document scope
 * 2) Index/project capabilities
 * 3) Model & preset
 * 4) Runtime configuration
 */
export function ChatConfigurationPanelContent() {
  const t = useTranslations("SectionActions");
  const tChat = useTranslations("Chat");
  const api = useChatToolbarStore((s) => s.api);

  const modelSelectId = useId();
  const presetSelectId = useId();
  const uploadInputRef = useRef<HTMLInputElement>(null);

  const [runtimeOpen, setRuntimeOpen] = useState(false);
  const [advancedError, setAdvancedError] = useState<string | null>(null);
  const [advancedValidationText, setAdvancedValidationText] = useState<string | null>(null);

  const capabilitiesQuery = useRuntimeConfigCapabilities();

  const needsProject = !api?.projectId;
  const needsConversation = !api?.conversationId;
  const hasCustomOverride = api?.runtimeOverride && Object.keys(api.runtimeOverride).length > 0;

  const effectiveConfig = api?.effectiveRuntimeConfig ?? null;
  const indexProfileQuery = useProjectIndexProfile(api?.projectId);
  const activeSnapQuery = useActiveProjectSnapshot(api?.projectId);

  const effectiveLoading = Boolean(api?.effectiveRuntimeConfigLoading);
  const effectiveError = api?.effectiveRuntimeConfigError ?? null;

  const caps = capabilitiesQuery.data?.capabilities ?? [];
  const capByKey = new Map(caps.map((c) => [c.key, c]));

  const mergedRuntimeFlagValues = useMemo(() => {
    const base =
      effectiveConfig && typeof effectiveConfig === "object" ? { ...(effectiveConfig as Record<string, unknown>) } : {};
    const ov = api?.runtimeOverride && typeof api.runtimeOverride === "object" ? api.runtimeOverride : {};
    return { ...base, ...ov } as Record<string, unknown>;
  }, [effectiveConfig, api?.runtimeOverride]);

  function coerceBool(v: unknown): boolean {
    return v === true || v === "true";
  }

  const disabledReason = (key: string): string | null => {
    const c = capByKey.get(key);
    if (!c) return null;
    if (!c.configurable) return "not configurable";
    if (!c.implemented) return c.reasonIfNotImplemented ?? "not implemented";
    if (c.requires?.length) {
      for (const reqKey of c.requires) {
        if (!coerceBool(mergedRuntimeFlagValues[reqKey])) {
          return `Requires ${reqKey}=true (e.g. turn on “Use retrieval” first).`;
        }
      }
    }
    return null;
  };

  const selectedPresetId = api?.presetSelectValue ?? "";
  const selectedInProduct = !!api?.presets?.some((p) => p.id === selectedPresetId);
  const selectedExperimental = (Array.isArray(api?.experimentalPresets) ? api?.experimentalPresets : []).find(
    (p) => p.productPresetId === selectedPresetId,
  );

  const presetKindBadge = selectedInProduct
    ? "Product"
    : selectedExperimental
      ? "TFG"
      : selectedPresetId
        ? null
        : "Recommended";

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
    "clarificationEnabled",
    "memoryEnabled",
    "adaptiveRoutingEnabled",
    "judgeEnabled",
  ] as const;

  const documentScopeHint =
    api?.limitDocs && api?.limitDocsToggleNotice ? api.limitDocsToggleNotice : null;

  // Ensure the draft starts from persisted overrides, not from the preset.
  // (We still display effective config separately via validate.)
  const productIds = useMemo(() => new Set((api?.presets ?? []).map((p) => p.id)), [api?.presets]);
  const experimentalUnique = useMemo(
    () => (api?.experimentalPresets ?? []).filter((p) => !productIds.has(p.productPresetId)),
    [api?.experimentalPresets, productIds],
  );

  const getBooleanValue = (key: string): boolean => {
    const override = api?.runtimeOverride ? (api.runtimeOverride as Record<string, unknown>)[key] : undefined;
    if (typeof override === "boolean") return override;
    const base = effectiveConfig ? (effectiveConfig as Record<string, unknown>)[key] : undefined;
    return typeof base === "boolean" ? base : false;
  };

  const setOverrideBoolean = (key: string, next: boolean) => {
    const current = api?.runtimeOverride ?? {};
    const updated = { ...current, [key]: next };
    api?.saveRuntimeOverride(updated);
  };

  return (
    <div className="flex flex-col gap-6">
      <Section title="Document scope">
        <Box>
          <div className="space-y-3">
            <div className="flex items-start justify-between gap-3">
              <label className="flex cursor-pointer items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  className="border-input size-4 rounded"
                  checked={Boolean(api?.limitDocs)}
                  disabled={needsProject || needsConversation || Boolean(api?.limitDocsDisabled) || Boolean(api?.patchConvPending)}
                  onChange={(e) => api?.onLimitDocsChange(e.target.checked)}
                />
                <span>{tChat("limitDocumentsLabel")}</span>
              </label>
              {api?.limitDocsDisabled ? (
                <MenuHint>{tChat("limitDocumentsNoReadyHint")}</MenuHint>
              ) : null}
            </div>

            {documentScopeHint ? <MenuHint>{documentScopeHint}</MenuHint> : null}

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                disabled={needsProject || needsConversation}
                onClick={() => api?.openDocumentsSheet()}
              >
                {tChat("manageDocuments")}
              </button>

              <input
                ref={uploadInputRef}
                type="file"
                className="sr-only"
                multiple
                aria-label={tChat("documentsSheetUploadInputAria")}
                onChange={(e) => api?.onAddDocuments(e.target.files)}
              />
              <button
                type="button"
                className={cn(buttonVariants({ variant: "secondary", size: "sm" }))}
                disabled={needsProject || needsConversation || Boolean(api?.uploadPending)}
                onClick={() => uploadInputRef.current?.click()}
              >
                {tChat("addDocuments")}
              </button>
            </div>
          </div>
        </Box>
      </Section>

      <Section title="Index/project capabilities">
        <Box>
          <div className="space-y-2">
            <MenuHint>
              These capabilities are fixed by the project index profile and require reindexing to change.
            </MenuHint>

            {activeSnapQuery.data ? (
              <div className="rounded-md border bg-background/50 px-3 py-2 text-xs">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Active snapshot</span>
                  <span className="font-mono">{activeSnapQuery.data.id}</span>
                </div>
                <div className="mt-1 flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Status</span>
                  <span className="font-mono">{activeSnapQuery.data.status}</span>
                </div>
                {activeSnapQuery.data.indexProfileHash ? (
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">Profile hash</span>
                    <span className="font-mono">{activeSnapQuery.data.indexProfileHash}</span>
                  </div>
                ) : null}
              </div>
            ) : (
              <MenuHint>No active index snapshot yet.</MenuHint>
            )}

            <div className="grid grid-cols-1 gap-2 text-sm">
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Materialization strategy</span>
                <span className="font-mono text-xs">
                  {indexProfileQuery.data?.materializationStrategy ??
                    (effectiveConfig && typeof (effectiveConfig as EffectiveConfigMap).materializationStrategy === typeof ""
                      ? String((effectiveConfig as EffectiveConfigMap).materializationStrategy)
                      : NOT_LOADED_LABEL)}
                </span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Metadata index</span>
                <span className="font-mono text-xs">
                  {indexProfileQuery.data
                    ? String(Boolean(indexProfileQuery.data.metadataEnabled))
                    : effectiveConfig && typeof (effectiveConfig as EffectiveConfigMap).metadataEnabled === typeof true
                      ? String(Boolean((effectiveConfig as EffectiveConfigMap).metadataEnabled))
                      : NOT_LOADED_LABEL}
                </span>
              </div>
            </div>
          </div>
        </Box>
      </Section>

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

                <optgroup label="Product presets">
                  {api?.presets?.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </optgroup>
                <optgroup label="TFG experimental presets (P0–P14)">
                  {experimentalUnique.map((p) => (
                    <option key={p.productPresetId} value={p.productPresetId} disabled={!p.chatSelectable}>
                      {experimentalPresetOptionLabel(p)}
                    </option>
                  ))}
                </optgroup>
              </select>

              {!api?.presetsLoading && !api?.presetsError && (api?.presets?.length ?? 0) === 0 ? (
                <output role="status" className="text-muted-foreground text-xs">
                  {tChat("presetCatalogEmpty")}
                </output>
              ) : null}
              {api?.presetsError ? (
                <output role="status" className="text-destructive text-xs">
                  {tChat("presetsLoadError")}
                </output>
              ) : null}
            </div>
          </div>
        </Box>
      </Section>

      <Section title="Runtime configuration">
        <Box>
          <div className="space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <button
                  type="button"
                  data-testid="chat-config-runtime-collapsible"
                  className="hover:bg-muted inline-flex w-full items-center justify-between gap-3 rounded-md px-2 py-1 text-left"
                  aria-expanded={runtimeOpen}
                  onClick={() => setRuntimeOpen((p) => !p)}
                >
                  <span className="text-sm font-medium">Advanced configuration</span>
                  <span className="text-muted-foreground text-xs">{runtimeOpen ? "Hide" : "Show"}</span>
                </button>
                <p className="text-muted-foreground text-xs">
                  Hot-swappable overrides for this conversation. Overrides are marked Custom.
                </p>
              </div>
              <button
                type="button"
                data-testid="chat-config-runtime-refresh-effective"
                className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                disabled={needsProject || needsConversation || effectiveLoading}
                onClick={() => api?.refreshEffectiveRuntimeConfig()}
              >
                {effectiveLoading ? "Loading..." : "Refresh"}
              </button>
            </div>

            <div className="rounded-lg border bg-background/40 p-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium">Effective config</p>
              </div>
              <p className="text-muted-foreground mt-1 text-xs">
                Values come from the selected preset + runtime overrides.
              </p>
              {effectiveError ? (
                <p className="text-destructive mt-2 text-xs" role="alert">
                  {effectiveError}
                </p>
              ) : null}
              {effectiveConfig ? (
                <div className="mt-2 grid grid-cols-1 gap-1 text-xs">
                  <output className="text-muted-foreground mb-1 block text-[11px]">
                    Effective keys: {Object.keys(effectiveConfig).length}
                  </output>
                  {Object.entries(effectiveConfig)
                    .slice(0, 24)
                    .map(([k, v]) => (
                      <div key={k} className="flex items-baseline justify-between gap-3">
                        <span className="truncate font-mono">
                          {k}
                          {api?.runtimeOverride &&
                          Object.prototype.hasOwnProperty.call(api.runtimeOverride, k) ? (
                            <span className="ml-2 text-muted-foreground">[override]</span>
                          ) : null}
                        </span>
                        <span className="font-mono text-muted-foreground">
                          {typeof v === typeof "" ||
                          typeof v === "number" ||
                          typeof v === typeof true
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

            {runtimeOpen ? (
              <div className="space-y-4">
                <div className="grid grid-cols-1 gap-3">
                  {hotSwappableKeys.map((key) => {
                    const reason = disabledReason(key);
                    const rid = `disabled-${key}`;
                    const capMeta = capByKey.get(key);
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
                                : key === "postRetrievalEnabled"
                                  ? "Post-retrieval"
                                  : key === "clarificationEnabled"
                                    ? "Clarification"
                                    : key === "memoryEnabled"
                                      ? "Memory"
                                      : key === "adaptiveRoutingEnabled"
                                        ? "Adaptive routing"
                                        : "Judge";
                    return (
                      <div key={key} className="flex flex-col gap-1">
                        <div className="flex items-start justify-between gap-3">
                          <label className="flex cursor-pointer items-center gap-3 text-sm">
                            <input
                              type="checkbox"
                              className="border-input size-4 rounded"
                              checked={getBooleanValue(key)}
                              disabled={!!reason}
                              onChange={(e) =>
                                setOverrideBoolean(key, e.target.checked)
                              }
                              aria-describedby={reason ? rid : undefined}
                            />
                            <span>{label}</span>
                          </label>
                          {reason ? <DisabledReason id={rid} reason={reason} /> : null}
                        </div>
                        {(key === "clarificationEnabled" || key === "memoryEnabled") &&
                        capMeta?.supportMode === "MULTI_TURN_REQUIRED" ? (
                          <MenuHint>{tChat("runtimeMultiTurnHint")}</MenuHint>
                        ) : null}
                      </div>
                    );
                  })}
                </div>

                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                    disabled={needsConversation || !!api?.patchConvPending}
                    onClick={() => {
                      api?.clearRuntimeOverride();
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

            <div className="border-border border-t pt-4">
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  data-testid="chat-move-project-button"
                  className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                  disabled={needsProject || needsConversation}
                  onClick={() => {
                    if (needsProject || needsConversation) return;
                    api?.openMoveDialog();
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
                    if (needsProject || needsConversation) return;
                    api?.openDeleteForActiveConversation();
                  }}
                >
                  {t("chatDelete")}
                </button>
              </div>
            </div>
          </div>
        </Box>
      </Section>
    </div>
  );
}

